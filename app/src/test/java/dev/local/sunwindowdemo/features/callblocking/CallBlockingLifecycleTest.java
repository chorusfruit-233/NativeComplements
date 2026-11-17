package dev.local.sunwindowdemo.features.callblocking;

import android.content.pm.ApplicationInfo;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModuleInterface;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=36,manifest=Config.NONE)
public class CallBlockingLifecycleTest {
    private final List<String> events=new ArrayList<>();
    private final ClassLoader absent=new ClassLoader(null){
        @Override public Class<?> loadClass(String name)throws ClassNotFoundException{throw new ClassNotFoundException(name);}
    };
    private CallBlockingModule module(String process,boolean server){
        CallBlockingModule module=new CallBlockingModule();
        module.attachFramework((XposedInterface)Proxy.newProxyInstance(XposedInterface.class.getClassLoader(),
            new Class<?>[]{XposedInterface.class},(proxy,method,args)->{
                if(!method.getName().equals("log"))throw new AssertionError("Preflight must not install a partial hook: "+method);
                events.add((String)args[2]);return null;
            }));
        module.onModuleLoaded(new XposedModuleInterface.ModuleLoadedParam(){
            public boolean isSystemServer(){return server;}
            public String getProcessName(){return process;}
        });
        return module;
    }
    private XposedModuleInterface.PackageLoadedParam target(String pkg){
        return new XposedModuleInterface.PackageLoadedParam(){
            public String getPackageName(){return pkg;}
            public ApplicationInfo getApplicationInfo(){return new ApplicationInfo();}
            public boolean isFirstPackage(){return false;}
            public ClassLoader getDefaultClassLoader(){return absent;}
        };
    }
    @Test public void missingBootClassesStillAllowLaterTelecomPackageAttempt(){
        CallBlockingModule module=module("system_server",true);
        module.onSystemServerStarting(()->absent);
        module.onSystemServerStarting(()->absent);
        assertEquals(List.of("CALL_BLOCKING_WAIT_TELECOM_PACKAGE"),events);
        module.onPackageLoaded(target("android"));assertEquals(1,events.size());
        module.onPackageLoaded(target("com.android.server.telecom"));
        module.onPackageLoaded(target("com.android.server.telecom"));
        assertEquals(List.of("CALL_BLOCKING_WAIT_TELECOM_PACKAGE","CALL_BLOCKING_DISABLED ClassNotFoundException"),events);
    }
    @Test public void manifestSystemProcessAcceptsOnlyTheTelecomPackage(){
        CallBlockingModule module=module("system",false);
        module.onPackageLoaded(target("android"));assertTrue(events.isEmpty());
        module.onPackageLoaded(target("com.android.server.telecom"));
        assertEquals(List.of("CALL_BLOCKING_DISABLED ClassNotFoundException"),events);
    }
    @Test public void uiAndUnrelatedProcessesNeverAttemptTelecomInstallation(){
        for(String process:List.of("com.android.server.telecom:ui","ordinary.app","com.android.systemui")){
            CallBlockingModule module=module(process,false);
            module.onPackageLoaded(target("com.android.server.telecom"));
        }
        assertTrue(events.isEmpty());
    }
}
