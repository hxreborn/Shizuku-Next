package rikka.shizuku.server;

import android.os.Process;
import android.os.SystemClock;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Appends this server's own account of starting up to the file the manager reads.
 *
 * The manager cannot see this process. A start whose server never got going and one whose
 * server ran and then failed to hand the binder over are the same 60 second timeout from the
 * manager's side, and the logcat this process writes to needs a reader an app does not have.
 * The starter writes the same file just before it forks this process, so anything written
 * here lands after the line that says the server was started, and the two lines together say
 * which happened.
 *
 * Best effort throughout: this cannot be allowed to affect a start. The server runs as root,
 * the system uid or the adb shell, depending on how it was started, and which of the two
 * directories it may write is not the same for all three.
 */
public class ServerLog {

    private static final String TAG = "ShizukuServerLog";

    /**
     * Where `starter.log` lives, media first: it exists to be reachable from outside the app, so
     * it is the one another process is allowed to write.
     *
     * Templates rather than bare directories, because the two are not the same shape: the data
     * one keeps the file in `files`, which is the directory the starter writes to. Without that
     * segment this looked for a `starter.log` beside the package directory, where nothing ever
     * is, decided there was none and made one under media instead - one start's account split
     * across two files, which is exactly what the lookup below exists to prevent.
     */
    private static final String[] DIRECTORIES = {
            "/storage/emulated/0/Android/media/%s",
            "/storage/emulated/0/Android/data/%s/files",
    };

    private static File file;

    /** Whether the last attempt to write the file failed, so that a failure is said once and not at every line. */
    private static boolean writeFailed;

    /** When this class was first used, which is the first line of [main]: the zero of [phase]. */
    private static final long STARTED_AT = SystemClock.uptimeMillis();

    /**
     * A line saying how far into start-up the server is, in milliseconds since it entered main.
     *
     * Where the eleven seconds from the starter's exec to the first binder go is a question about
     * phases, and the manager's log only has wall-clock time for the lines it writes itself. One
     * short line per phase is the measurement.
     */
    public static void phase(String what) {
        mark("+" + (SystemClock.uptimeMillis() - STARTED_AT) + " ms " + what);
    }

    /**
     * Says [message] in the logcat, and appends it to the manager's file when the file can be
     * reached.
     *
     * The logcat first and always: a server started before the first unlock cannot write the file
     * (the storage it lives in is not there yet), and this used to return before logging at all,
     * so such a server left no account of itself anywhere. The file is tried again at the next
     * line rather than given up on, since the same server can reach it a minute later.
     */
    public static void mark(String message) {
        Log.i(TAG, message);

        try {
            File target = target();
            if (target == null) {
                return;
            }

            try (FileWriter writer = new FileWriter(target, true)) {
                writer.write(new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
                        .format(new Date()) + " server(" + Process.myPid() + "): " + message + "\n");
            }
            writeFailed = false;
        } catch (Throwable tr) {
            if (!writeFailed) {
                writeFailed = true;
                Log.w(TAG, "Couldn't append to the manager's log; will try again", tr);
            }
        }
    }

    /**
     * The manager's log file, whichever of the two it was the starter managed to write, or null
     * when neither can be reached yet.
     *
     * Only a file that was reached is remembered. A directory that cannot be made now (the storage
     * is locked until the first unlock) is not a decision, and remembering it as one left a server
     * started in that window without a file for the rest of its life.
     */
    private static synchronized File target() {
        if (file != null) {
            return file;
        }

        String pkg = ShizukuService.MANAGER_APPLICATION_ID;
        if (pkg == null || pkg.isEmpty()) {
            return null;
        }

        // Whichever exists is the one the starter wrote, and appending to it keeps one
        // account of the start in one place.
        for (String directory : DIRECTORIES) {
            File existing = new File(String.format(Locale.US, directory, pkg), "starter.log");
            if (existing.isFile()) {
                file = existing;
                return file;
            }
        }

        File media = new File(String.format(Locale.US, DIRECTORIES[0], pkg), "starter.log");
        File parent = media.getParentFile();
        if (parent == null) {
            return null;
        }
        //noinspection ResultOfMethodCallIgnored
        parent.mkdirs();
        if (!parent.isDirectory()) {
            return null;
        }
        file = media;
        return file;
    }
}
