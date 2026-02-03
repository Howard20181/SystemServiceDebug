package io.github.howard20181.systemservicedebug;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.VersionedPackage;
import android.service.autofill.FillResponse;
import android.util.Log;

import androidx.annotation.NonNull;

import java.lang.reflect.Executable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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
    private static Method isCustomFillUi;

    public DebugHook(XposedInterface base, ModuleLoadedParam param) {
        super(base, param);
        module = this;
    }

    @Override
    public void onSystemServerLoaded(@NonNull SystemServerLoadedParam param) {
        var classLoader = param.getClassLoader();
        try {
            try {
                var cMiuiAutofillServiceHelper = classLoader.loadClass("com.android.server.autofill.MiuiAutofillServiceHelper");
                isCustomFillUi = cMiuiAutofillServiceHelper.getDeclaredMethod("isCustomFillUi", FillResponse.class);
            } catch (ClassNotFoundException | NoSuchMethodException e) {
                log("find isCustomFillUi", e);
            }
//            hookRescuePartyPlusHelper(classLoader);
//            hookOnHealthCheckFailed(classLoader);
//            hookPackageWatchdogImpl(classLoader);
//            hookPackageWatchdog(classLoader);
//            hookRescuePartyMonitorCallback(classLoader);
            try {
                hookCredentialManagerServiceImpl(classLoader);
            } catch (Exception e) {
                log("hook CredentialManagerServiceImpl failed", e);
            }
            try {
                hookCredentialManagerService(classLoader);
            } catch (Exception e) {
                log("hook CredentialManagerService failed", e);
            }
            try {
                hookMiuiAutofillServiceHelper(classLoader);
            } catch (Exception e) {
                log("hook MiuiAutofillServiceHelper failed", e);
            }
            try {
                hookMiuiAutofillServiceStubImpl(classLoader);
            } catch (Exception e) {
                log("hook MiuiAutofillServiceStubImpl failed", e);
            }
        } catch (Throwable tr) {
            log("Error hooking system service", tr);
        }
    }

    private void hookCredentialManagerServiceImpl(ClassLoader classLoader) throws NoSuchMethodException, ClassNotFoundException {
        var iClass = classLoader.loadClass("com.android.server.credentials.CredentialManagerServiceImpl");
        var aClass = classLoader.loadClass("com.android.server.credentials.CredentialManagerService");
        var bClass = classLoader.loadClass("android.credentials.CredentialProviderInfo");
        var sConstructor = iClass.getDeclaredConstructor(aClass, Object.class, int.class, String.class);
        var pConstructor = iClass.getDeclaredConstructor(aClass, Object.class, int.class, bClass);
        hook(sConstructor, DumpStackHooker.class);
        hook(pConstructor, DumpStackHooker.class);
    }

    private void hookCredentialManagerService(ClassLoader classLoader) throws NoSuchMethodException, ClassNotFoundException {
        var aClass = classLoader.loadClass("com.android.server.credentials.CredentialManagerService");
        var bClass = classLoader.loadClass("com.android.server.credentials.CredentialManagerService$SettingsWrapper");
        var method = aClass.getDeclaredMethod("updateProvidersWhenServiceRemoved", bClass, ComponentName.class, int.class);
        hook(method, DumpStackHooker.class);
    }

    private void hookMiuiAutofillServiceStubImpl(ClassLoader classLoader) throws NoSuchMethodException, ClassNotFoundException {
        var aClass = classLoader.loadClass("com.android.server.autofill.MiuiAutofillServiceStubImpl");
        var method = aClass.getDeclaredMethod("checkIsCoustomFillUiForAuthResponse", FillResponse.class);
        hook(method, CheckIsCoustomFillUiForAuthResponseHooker.class);
    }

    private void hookMiuiAutofillServiceHelper(ClassLoader classLoader) throws NoSuchMethodException, ClassNotFoundException {
        var aClass = classLoader.loadClass("com.android.server.autofill.MiuiAutofillServiceHelper");
        var method = aClass.getDeclaredMethod("checkIsMiuiConsume", String.class);
        hook(method, ReturnTrueHooker.class);
    }

    private void hookRescuePartyMonitorCallback(ClassLoader classLoader) throws NoSuchMethodException, ClassNotFoundException {
        var aClass = classLoader.loadClass("com.android.server.RescueParty$RescuePartyMonitorCallback");
        var method = aClass.getDeclaredMethod("onDeviceConfigAccess", String.class, String.class);
//        hook(method, DumpStackHooker.class);
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
            var args = callback.getArgs();
            var sb = new StringBuilder(callback.getMember() instanceof Method ? callback.getMember().getDeclaringClass().getSimpleName() + "." + callback.getMember().getName() : callback.getMember().getName());
            if (args.length > 0) {
                sb.append("(");
                for (int i = 0; i < args.length; i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    var arg = args[i];
                    if (arg == null) {
                        sb.append("null");
                    } else {
                        sb.append(arg.getClass().getName()).append(" \"").append(arg).append("\"");
                    }
                }
                sb.append(")");
            }
            here.fillInStackTrace();
            module.log(sb.toString(), here);
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

    @XposedHooker
    private static class ReturnTrueHooker implements Hooker {
        @BeforeInvocation
        public static void before(@NonNull BeforeHookCallback callback) {
            var args = callback.getArgs();
            var sb = new StringBuilder(callback.getMember() instanceof Method ? callback.getMember().getDeclaringClass().getSimpleName() + "." + callback.getMember().getName() : callback.getMember().getName());
            if (args.length > 0) {
                sb.append("(");
                for (int i = 0; i < args.length; i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    var arg = args[i];
                    if (arg == null) {
                        sb.append("null");
                    } else {
                        sb.append(arg.getClass().getName()).append(" \"").append(arg).append("\"");
                    }
                }
                sb.append(")");
            }
            module.log("return true and skip for " + sb);
            callback.returnAndSkip(true);
        }
    }

    @XposedHooker
    private static class CheckIsCoustomFillUiForAuthResponseHooker implements Hooker {
        @BeforeInvocation
        public static void before(@NonNull BeforeHookCallback callback) throws InvocationTargetException, IllegalAccessException {
            var arg1 = callback.getArgs()[0];
            var res = false;
            if (arg1 instanceof FillResponse response) {
                var ret = module.invokeOrigin(isCustomFillUi, callback.getThisObject(), response);
                if (ret != null) {
                    res = (boolean) ret;
                }
            }
            module.log("checkIsCoustomFillUiForAuthResponse return " + res);
            callback.returnAndSkip(res);
        }
    }
}
