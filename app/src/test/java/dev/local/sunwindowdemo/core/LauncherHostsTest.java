package dev.local.sunwindowdemo.core;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
public class LauncherHostsTest {
    private final Context context=RuntimeEnvironment.getApplication();
    private void install(String name,int uid){
        PackageInfo info=new PackageInfo();info.packageName=name;info.applicationInfo=new ApplicationInfo();
        info.applicationInfo.packageName=name;info.applicationInfo.uid=uid;
        Shadows.shadowOf(context.getPackageManager()).installPackage(info);
    }
    @Test public void onlyTheVerifiedMainPackagesAndTheirActualUidsAreTrusted(){
        install(LauncherHosts.AOSP,10123);install(LauncherHosts.PIXEL,10124);install("com.example.launcher",10125);
        assertTrue(LauncherHosts.ownsUid(context,10123));assertTrue(LauncherHosts.ownsUid(context,10124));
        assertFalse(LauncherHosts.ownsUid(context,10125));assertFalse(LauncherHosts.ownsUid(context,110123));
        assertFalse(LauncherHosts.isLauncher(LauncherHosts.AOSP+":quickstep"));
        assertTrue(LauncherHosts.isProcess(LauncherHosts.AOSP+":quickstep"));
        assertEquals(LauncherHosts.AOSP,LauncherHosts.packageForProcess(LauncherHosts.AOSP+":quickstep"));
        assertNull(LauncherHosts.packageForProcess(LauncherHosts.AOSP+".spoof"));
    }
    @Test public void missingPixelDoesNotPreventLineageUidChecks(){
        install(LauncherHosts.AOSP,10123);
        assertTrue(LauncherHosts.ownsUid(context,10123));assertFalse(LauncherHosts.ownsUid(context,12345));
    }
    @Test public void explicitBroadcastsPreservePayloadAndDoNotChangeTheTemplate(){
        Intent template=new Intent("dev.nativecomplements.TEST").setPackage("old.target").putExtra("enabled",true);
        LauncherHosts.broadcast(context,template,true);
        var sent=Shadows.shadowOf((android.app.Application)context).getBroadcastIntents();
        assertEquals(2,sent.size());assertEquals(LauncherHosts.PIXEL,sent.get(0).getPackage());
        assertEquals(LauncherHosts.AOSP,sent.get(1).getPackage());
        assertTrue(sent.get(0).getBooleanExtra("enabled",false));assertTrue(sent.get(1).getBooleanExtra("enabled",false));
        assertEquals("old.target",template.getPackage());
    }
}
