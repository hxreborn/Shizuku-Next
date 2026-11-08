package rikka.shizuku.server;

import android.os.Handler;
import android.os.IBinder;
import android.os.Process;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.util.Log;

import rikka.shizuku.server.util.Logger;

/**
 * Watches this process from the inside, and ends it when it has stopped being able to work.
 *
 * The manager's watchdog works from the one signal it has: the binder dying. A server whose main
 * thread is stuck never produces that signal. It holds a live binder, accepts transactions and
 * queues them behind a looper that never returns, so from the manager it is indistinguishable from
 * a healthy server and every app using it waits forever - and the watchdog that exists to restart
 * a dead server never sees anything to restart, because nothing died.
 *
 * Two ways of ending up like that, one answer.
 *
 * The main thread stops beating. The beat is a handler posted from the main looper, so a blocked
 * looper stops beating with it, and the check runs on a thread of its own, so it is not stuck
 * behind whatever is blocking the looper.
 *
 * A system service this process is built on goes away. The compat layer holds proxies obtained
 * once, and a proxy whose binder has died cannot be replaced from inside here, so the process
 * cannot recover by itself either.
 *
 * In both cases the only recovery is a new process. This one says why in the manager's log and
 * then exits, which is the binder death the manager's watchdog is already listening for.
 */
public final class ServerWatchdog {

    private static final Logger LOGGER = new Logger("ServerWatchdog");

    /** How often the main thread says it is still there. */
    private static final long BEAT_INTERVAL_MS = 5_000;

    /**
     * How long the main thread may stay silent before this process is considered stuck.
     *
     * A minute is well past anything the server does on its own looper: the long work - listing
     * packages, handing the binder out - runs on threads of its own, and what is left on the main
     * looper is short by design. A minute of silence is not a slow operation, it is a deadlock.
     */
    private static final long HANG_TIMEOUT_MS = 60_000;

    /** How often both questions are asked. */
    private static final long CHECK_INTERVAL_MS = 5_000;

    /**
     * The system services this process cannot work without.
     *
     * The same four [ShizukuService] waits for before it starts: a server that outlives one of
     * them is holding proxies that answer nothing.
     */
    private static final String[] REQUIRED_SERVICES = {"package", "activity", "user", "appops"};

    /**
     * How long after start the checks begin.
     *
     * Starting is the one time this process is legitimately busy on its own looper, and it is
     * also the time nothing is waiting on the answer yet.
     */
    private static final long STARTUP_GRACE_MS = 30_000;

    private static volatile long lastBeat;
    private static volatile boolean stopped;
    private static Handler handler;
    private static Thread thread;

    private static final Runnable BEAT = new Runnable() {
        @Override
        public void run() {
            lastBeat = SystemClock.uptimeMillis();
            if (!stopped && handler != null) {
                handler.postDelayed(this, BEAT_INTERVAL_MS);
            }
        }
    };

    private ServerWatchdog() {
    }

    /**
     * Starts watching, on [mainHandler]'s looper and a thread of this class's own.
     *
     * Called once, from the constructor that has the looper to watch and the system services
     * already waited for.
     */
    public static synchronized void start(Handler mainHandler) {
        if (thread != null) {
            return;
        }

        handler = mainHandler;
        stopped = false;
        lastBeat = SystemClock.uptimeMillis();
        mainHandler.post(BEAT);

        final long startedAt = SystemClock.uptimeMillis();
        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (!stopped) {
                    try {
                        Thread.sleep(CHECK_INTERVAL_MS);
                    } catch (InterruptedException e) {
                        return;
                    }

                    if (stopped || SystemClock.uptimeMillis() - startedAt < STARTUP_GRACE_MS) {
                        continue;
                    }

                    long silentFor = SystemClock.uptimeMillis() - lastBeat;
                    if (silentFor > HANG_TIMEOUT_MS) {
                        logHang(silentFor);
                        die();
                        return;
                    }

                    String dead = deadSystemService();
                    if (dead != null) {
                        ServerLog.mark("system service " + dead + " is gone; this process cannot go on");
                        LOGGER.e("system service %s is gone, exiting", dead);
                        die();
                        return;
                    }
                }
            }
        }, "server-watchdog");
        thread.setDaemon(true);
        thread.start();
    }

    /** Stops watching, for the exits that are meant to happen. */
    public static synchronized void stop() {
        stopped = true;
        handler = null;
        Thread running = thread;
        thread = null;
        if (running != null) {
            running.interrupt();
        }
    }

    /**
     * The name of the first required system service that is gone, or null when all of them answer.
     *
     * Asked of [ServiceManager] every time rather than of a remembered binder: the question is
     * whether the platform still has the service, and a cached proxy is exactly what cannot be
     * trusted here.
     */
    private static String deadSystemService() {
        for (String name : REQUIRED_SERVICES) {
            try {
                IBinder binder = ServiceManager.getService(name);
                if (binder == null || !binder.pingBinder()) {
                    return name;
                }
            } catch (Throwable tr) {
                LOGGER.w(tr, "could not ask about the %s service", name);
                return name;
            }
        }
        return null;
    }

    /**
     * Writes what the stuck thread was doing, before the stack trace stops existing.
     *
     * The main thread's stack is the whole point of catching a hang this way: the process is
     * about to die, and "it stopped answering" without the frame it stopped in says nothing about
     * why. Logcat first and forever, since the manager may not be able to read the file yet.
     */
    private static void logHang(long silentFor) {
        Thread main = android.os.Looper.getMainLooper().getThread();
        StringBuilder trace = new StringBuilder();
        trace.append("the main thread has not answered for ").append(silentFor).append(" ms");
        for (StackTraceElement element : main.getStackTrace()) {
            trace.append("\n    at ").append(element);
        }

        Log.e("ServerWatchdog", trace.toString());
        serverLog(trace.toString());
    }

    /** [ServerLog.mark] over several lines, because it writes one, and this has many. */
    private static void serverLog(String message) {
        for (String line : message.split("\n")) {
            ServerLog.mark(line);
        }
    }

    /** Ends this process, which is the manager's cue to start another one. */
    private static void die() {
        stopped = true;
        LOGGER.e("exiting so the manager can start a fresh server");
        Process.killProcess(Process.myPid());
    }
}
