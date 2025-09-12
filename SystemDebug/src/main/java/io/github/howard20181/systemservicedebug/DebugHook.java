package io.github.howard20181.systemservicedebug;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.VersionedPackage;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.Arrays;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.annotations.AfterInvocation;
import io.github.libxposed.api.annotations.BeforeInvocation;
import io.github.libxposed.api.annotations.XposedHooker;

@SuppressLint({"PrivateApi", "BlockedPrivateApi", "SoonBlockedPrivateApi"})
public class DebugHook extends XposedModule {
    private static XposedModule module;

    public DebugHook(XposedInterface base, ModuleLoadedParam param) {
        super(base, param);
        module = this;
    }

    private static PackageManager pm = null;

    @Override
    public void onSystemServerLoaded(@NonNull SystemServerLoadedParam param) {
        var classLoader = param.getClassLoader();
        try {
            hookRescuePartyPlusHelper(classLoader);
            hookOnHealthCheckFailed(classLoader);
            hookPackageWatchdogImpl(classLoader);
        } catch (Throwable tr) {
            log("Error hooking system service", tr);
        }
    }

    private void hookPackageWatchdogImpl(ClassLoader classLoader) throws NoSuchMethodException, ClassNotFoundException {
        var aClass = classLoader.loadClass("com.android.server.PackageWatchdogImpl");
        var method = aClass.getDeclaredMethod("setNowCrashApplicationLevel", int.class, int.class, VersionedPackage.class, Context.class);
        hook(method, SetNowCrashApplicationLevelHooker.class);
    }

    private void hookOnHealthCheckFailed(ClassLoader classLoader) throws NoSuchMethodException, ClassNotFoundException {
        var aClass = classLoader.loadClass("com.android.server.RescueParty$RescuePartyObserver");
        var bClass = classLoader.loadClass("com.android.server.rollback.RollbackPackageHealthObserver");
        var method = aClass.getDeclaredMethod("onHealthCheckFailed", VersionedPackage.class, int.class, int.class);
        var method2 = bClass.getDeclaredMethod("onHealthCheckFailed", VersionedPackage.class, int.class, int.class);
        hook(method, OnHealthCheckFailedHooker.class);
        hook(method2, OnHealthCheckFailedHooker.class);
    }

    private void hookRescuePartyPlusHelper(ClassLoader classLoader) throws NoSuchMethodException, ClassNotFoundException {
        var aClass = classLoader.loadClass("com.android.server.RescuePartyPlusHelper");
        var method = aClass.getDeclaredMethod("checkDisableRescuePartyPlus");
        hook(method, CheckDisableRescuePartyPlusHooker.class);
    }

    private void hookMethods(Class<?> clazz, Class<? extends Hooker> hooker, String... names) {
        var list = Arrays.asList(names);
        Arrays.stream(clazz.getDeclaredMethods())
                .filter(method -> list.contains(method.getName()))
                .forEach(method -> hook(method, hooker));
    }

    private static PackageManager getPackageManager(@NonNull Context context) {
        if (pm == null)
            pm = context.getPackageManager();
        return pm;
    }

    private static int getNormalizedUserId(int userId) {
        if (userId == -1 /* UserHandle.USER_ALL */) {
            userId = 0; /* UserHandle.USER_SYSTEM */
        }
        return userId;
    }

    @XposedHooker
    private static class CheckDisableRescuePartyPlusHooker implements Hooker {
        @BeforeInvocation
        public static void before(@NonNull BeforeHookCallback callback) {
            var here = new RuntimeException("checkDisableRescuePartyPlus");
            here.fillInStackTrace();
            Log.d("RescuePartyPlusHelper", here.getMessage(), here);
        }
    }

    @XposedHooker
    private static class OnHealthCheckFailedHooker implements Hooker {
        @AfterInvocation
        public static void after(@NonNull AfterHookCallback callback) {
            var args = callback.getArgs();
            var failedPackage = (VersionedPackage) args[0];
            var failureReason = (int) args[1];
            var mitigationCount = (int) args[2];
            var here = new RuntimeException("onHealthCheckFailed");
            here.fillInStackTrace();
            Log.d("RescueParty", "onHealthCheckFailed failedPackage=" + failedPackage + ", failureReason=" + failureReason + ", mitigationCount=" + mitigationCount + " return=" + callback.getResult(), here);
        }
    }

    @XposedHooker
    private static class SetNowCrashApplicationLevelHooker implements Hooker {
        @AfterInvocation
        public static void after(@NonNull AfterHookCallback callback) {
            var args = callback.getArgs();
            var failedPackage = (VersionedPackage) args[2];
            var mitigationCount = (int) args[0];
            var crashMember = (int) args[1];
//            var context = (Context) args[3];
            Log.d("PackageWatchdogImpl", "setNowCrashApplicationLevel failedPackage=" + failedPackage + ", crashMember=" + crashMember + ", mitigationCount=" + mitigationCount + " return=" + callback.getResult());
        }
    }
}
