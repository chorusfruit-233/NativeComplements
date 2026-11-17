package dev.local.sunwindowdemo.core;

import android.app.BroadcastOptions;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.UserHandle;

/** Verified system launcher identities. ABI checks remain local to each optional adapter. */
public final class LauncherHosts {
    public static final String PIXEL = "com.google.android.apps.nexuslauncher";
    public static final String AOSP = "com.android.launcher3";
    private LauncherHosts() { }
    public static String[] packages() { return new String[]{PIXEL, AOSP}; }
    public static boolean isLauncher(String name) { return PIXEL.equals(name) || AOSP.equals(name); }
    public static boolean isProcess(String name) {
        if (name == null) return false;
        for (String host : packages()) if (host.equals(name) || name.startsWith(host + ":")) return true;
        return false;
    }
    public static String packageForProcess(String name) {
        if (name == null) return null;
        for (String host : packages()) if (host.equals(name) || name.startsWith(host + ":")) return host;
        return null;
    }
    public static boolean ownsUid(Context context, int uid) {
        if (!UserHandle.getUserHandleForUid(uid).equals(android.os.Process.myUserHandle())) return false;
        for (String host : packages()) try {
            if (context.getPackageManager().getApplicationInfo(host, 0).uid == uid) return true;
        } catch (PackageManager.NameNotFoundException absent) { }
        return false;
    }
    public static void broadcast(Context context, Intent template, boolean shareIdentity) {
        for (String host : packages()) {
            Intent intent = new Intent(template).setPackage(host);
            if (shareIdentity) context.sendBroadcast(intent, null, BroadcastOptions.makeBasic().setShareIdentityEnabled(true).toBundle());
            else context.sendBroadcast(intent);
        }
    }
}
