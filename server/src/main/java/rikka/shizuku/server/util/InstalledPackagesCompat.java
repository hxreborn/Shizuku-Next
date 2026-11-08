package rikka.shizuku.server.util;

import android.content.pm.PackageInfo;
import android.os.Build;
import android.util.Log;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

public final class InstalledPackagesCompat {

    private static final String TAG = "InstalledPackagesCompat";
    private static final int ANDROID_13 = 33;
    private static final String PARCELED_LIST_SLICE = "android.content.pm.ParceledListSlice";

    private InstalledPackagesCompat() {
    }

    public static List<PackageInfo> getInstalledPackagesNoThrow(long flags, int userId) {
        try {
            List<PackageInfo> packages = getInstalledPackages(flags, userId);
            return packages == null ? Collections.emptyList() : packages;
        } catch (Throwable e) {
            Log.w(TAG, "getInstalledPackages failed", e);
            return Collections.emptyList();
        }
    }

    @SuppressWarnings("unchecked")
    public static List<PackageInfo> getInstalledPackages(long flags, int userId) throws ReflectiveOperationException {
        // A process with an Application has a context and so a package manager of its own, and that
        // is the cheap way to the answer. A process without one - this server, a bare app_process -
        // has to build a system context first, which means ActivityThread.systemMain(): a
        // Resources and an overlay configuration, over a second of a loaded phone's time at start-up
        // measured in the server's log (service_manager_slow, idmap2, OverlayConfig) - for a list
        // that the package manager service hands out directly. So there the service is asked first,
        // and the system context is the way out only if it will not answer. The caller is the same
        // uid either way, and the list is the same.
        Object applicationPackageManager = applicationPackageManagerOrNull();
        if (applicationPackageManager != null) {
            try {
                return viaPackageManagerObject(applicationPackageManager, flags, userId);
            } catch (NoSuchMethodException ignored) {
            } catch (Exception e) {
                Log.d(TAG, "getInstalledPackagesAsUser failed, falling back to hidden API", e);
            }
            return viaHiddenService(flags, userId);
        }

        try {
            return viaHiddenService(flags, userId);
        } catch (Exception e) {
            Log.d(TAG, "the package manager service would not list packages, trying a system context", e);
        }
        return viaPackageManagerObject(getSystemContextPackageManager(), flags, userId);
    }

    @SuppressWarnings("unchecked")
    private static List<PackageInfo> viaPackageManagerObject(Object packageManager, long flags, int userId)
            throws ReflectiveOperationException {
        Method method = packageManager.getClass().getMethod("getInstalledPackagesAsUser", int.class, int.class);
        Object result = invoke(method, packageManager, (int) flags, userId);
        return result == null ? Collections.emptyList() : (List<PackageInfo>) result;
    }

    @SuppressWarnings("unchecked")
    private static List<PackageInfo> viaHiddenService(long flags, int userId) throws ReflectiveOperationException {
        Object packageManager = getPackageManager();
        Class<?> owner = packageManagerInterface(packageManager);
        Method method;
        Object result;

        if (Build.VERSION.SDK_INT >= ANDROID_13) {
            method = owner.getMethod("getInstalledPackages", long.class, int.class);
            result = invoke(method, packageManager, flags, userId);
        } else {
            method = owner.getMethod("getInstalledPackages", int.class, int.class);
            result = invoke(method, packageManager, (int) flags, userId);
        }

        if (result == null) {
            return Collections.emptyList();
        }

        String resultClassName = result.getClass().getName();
        if (resultClassName.startsWith(PARCELED_LIST_SLICE) || resultClassName.contains("PackageInfoList")) {
            Object list = result.getClass().getMethod("getList").invoke(result);
            return list == null ? Collections.emptyList() : (List<PackageInfo>) list;
        }

        throw new IllegalStateException("Unsupported getInstalledPackages return type: " + resultClassName);
    }

    /**
     * The class to look the method up on: the interface when the object implements it, otherwise
     * the object's own class. A binder proxy is a private nested class, and a method found on it
     * cannot be invoked from here, where the interface's is a public one the proxy answers to.
     */
    private static Class<?> packageManagerInterface(Object packageManager) {
        try {
            Class<?> iface = Class.forName("android.content.pm.IPackageManager");
            if (iface.isInstance(packageManager)) {
                return iface;
            }
        } catch (ClassNotFoundException ignored) {
        }
        return packageManager.getClass();
    }

    private static Object getPackageManager() throws ReflectiveOperationException {
        Class<?> servicesClass = Class.forName("rikka.hidden.compat.Services");
        var field = servicesClass.getDeclaredField("packageManager");
        field.setAccessible(true);
        Object service = field.get(null);
        return service.getClass().getMethod("get").invoke(service);
    }

    /** The package manager of the Application this process has, or null when it has none. */
    private static Object applicationPackageManagerOrNull() throws ReflectiveOperationException {
        Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
        Object activityThread = activityThreadClass.getMethod("currentActivityThread").invoke(null);
        if (activityThread != null) {
            Object application = activityThreadClass.getMethod("getApplication").invoke(activityThread);
            if (application != null) {
                return application.getClass().getMethod("getPackageManager").invoke(application);
            }
        }
        return null;
    }

    /** The package manager of a system context, which has to be built: the expensive way. */
    private static Object getSystemContextPackageManager() throws ReflectiveOperationException {
        Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
        Object activityThread = activityThreadClass.getMethod("systemMain").invoke(null);
        Object systemContext = activityThreadClass.getMethod("getSystemContext").invoke(activityThread);
        return systemContext.getClass().getMethod("getPackageManager").invoke(systemContext);
    }

    private static Object invoke(Method method, Object receiver, Object... args) throws ReflectiveOperationException {
        try {
            return method.invoke(receiver, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof ReflectiveOperationException) {
                throw (ReflectiveOperationException) cause;
            }
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw e;
        }
    }
}
