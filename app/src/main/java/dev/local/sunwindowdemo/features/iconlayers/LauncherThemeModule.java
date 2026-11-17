package dev.local.sunwindowdemo.features.iconlayers;

import io.github.libxposed.api.*;

/** Standalone product entry: only observes the native themed-icon state. */
public final class LauncherThemeModule extends XposedModule {
    private boolean eligible,attempted;
    private String launcherPackage;
    @Override public void onModuleLoaded(ModuleLoadedParam p){
        eligible=!p.isSystemServer()&&dev.local.sunwindowdemo.core.LauncherHosts.isLauncher(p.getProcessName());
        launcherPackage=eligible?p.getProcessName():null;
    }
    @Override public synchronized void onPackageLoaded(PackageLoadedParam p){
        if(!eligible||attempted||!launcherPackage.equals(p.getPackageName()))return;
        attempted=true;
        LauncherThemeReporter reporter=new LauncherThemeReporter(themed->{});
        try{reporter.install(this,p.getDefaultClassLoader());log(4,"LauncherTheme","READY");}
        catch(ReflectiveOperationException|RuntimeException failure){
            reporter.close();log(4,"LauncherTheme","UNAVAILABLE "+failure.getClass().getSimpleName());
        }
    }
}
