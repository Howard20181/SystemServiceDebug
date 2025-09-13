package io.github.howard20181.systemservicedebug;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.VersionedPackage;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.concurrent.Executor;

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

    @Override
    public void onPackageLoaded(@NonNull PackageLoadedParam param) {
        var classLoader = param.getClassLoader();
        var pn = param.getPackageName();
        try {
            log("Loaded package: " + pn);
//            hookProviderSettings(classLoader);
        } catch (Throwable tr) {
            log("Error hooking system framework", tr);
        }
    }

    @Override
    public void onSystemServerLoaded(@NonNull SystemServerLoadedParam param) {
        var classLoader = param.getClassLoader();
        try {
//            hookRescuePartyPlusHelper(classLoader);
            hookOnHealthCheckFailed(classLoader);
            hookPackageWatchdogImpl(classLoader);
            hookPackageWatchdog(classLoader);
            hookRescuePartyMonitorCallback(classLoader);
        } catch (Throwable tr) {
            log("Error hooking system service", tr);
        }
    }

    private void hookRescuePartyMonitorCallback(ClassLoader classLoader) throws NoSuchMethodException, ClassNotFoundException {
        var aClass = classLoader.loadClass("com.android.server.RescueParty$RescuePartyMonitorCallback");
        var method = aClass.getDeclaredMethod("onDeviceConfigAccess", String.class, String.class);
        hook(method, DumpStackHooker.class);
        hook(method, OnDeviceConfigAccessHooker.class);
    }

    private void hookProviderSettings(ClassLoader classLoader) throws NoSuchMethodException, ClassNotFoundException {
        var aClass = classLoader.loadClass("android.provider.Settings$Config");
        var innClass = classLoader.loadClass("android.provider.DeviceConfig$MonitorCallback");
        var method2 = aClass.getDeclaredMethod("setMonitorCallback", ContentResolver.class, Executor.class, innClass);
        hook(method2, DumpStackHooker.class);
    }

    private void hookPackageWatchdog(ClassLoader classLoader) throws NoSuchMethodException, ClassNotFoundException {
        var aClass = classLoader.loadClass("com.android.server.PackageWatchdog$ObserverInternal");
        var bClass = classLoader.loadClass("com.android.server.PackageWatchdog");
        var innClass = classLoader.loadClass("com.android.server.PackageWatchdog$PackageHealthObserver");
        var method = aClass.getDeclaredMethod("updatePackagesLocked", List.class);
        var method2 = bClass.getDeclaredMethod("startObservingHealth", innClass, List.class, long.class);
        hook(method, DumpStackHooker.class);
        hook(method2, DumpStackHooker.class);
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
        hook(method, DumpStackHooker.class);
    }

    @XposedHooker
    private static class OnHealthCheckFailedHooker implements Hooker {
        @AfterInvocation
        public static void after(@NonNull AfterHookCallback callback) {
            var args = callback.getArgs();
            var failedPackage = (VersionedPackage) args[0];
            var failureReason = (int) args[1];
            var mitigationCount = (int) args[2];
            var className = callback.getMember().getDeclaringClass().getSimpleName();
            Log.d(className, "onHealthCheckFailed failedPackage=" + failedPackage + ", failureReason=" + failureReason + ", mitigationCount=" + mitigationCount + " return=" + callback.getResult());
        }
    }


    @XposedHooker
    private static class DumpStackHooker implements Hooker {
        @BeforeInvocation
        public static void before(@NonNull BeforeHookCallback callback) {
            var here = new RuntimeException("here");
            here.fillInStackTrace();
            Log.d("StackTrace", callback.getMember().getDeclaringClass().getSimpleName() + "." + callback.getMember().getName(), here);
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

    @XposedHooker
    private static class OnDeviceConfigAccessHooker implements Hooker {
        @AfterInvocation
        public static void after(@NonNull AfterHookCallback callback) {
            var args = callback.getArgs();
            var callingPackage = (String) args[0];
            var namespace = (String) args[1];
            var className = callback.getMember().getDeclaringClass().getSimpleName();
            Log.d(className, callback.getMember().getName() + " callingPackage=" + callingPackage + ", namespace=" + namespace);
        }
    }
}
