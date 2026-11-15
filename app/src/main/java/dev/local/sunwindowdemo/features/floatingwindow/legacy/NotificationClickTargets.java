package dev.local.sunwindowdemo;

import android.app.PendingIntent;
import android.content.Intent;
import java.lang.reflect.*;

/** Notification-body adapters verified against API35, current API37 and QPR2. */
final class NotificationClickTargets {
    private static final String BASE = "com.android.systemui.statusbar.phone.StatusBarNotificationActivityStarter";
    final Method run, start;
    final Field runEntry, runPending, runAnimate, startEntry, startPending, startFill;
    private final Field displayOwner, displayId;

    static NotificationClickTargets resolve(ClassLoader loader, Class<?> entry) throws Exception {
        Class<?> starter = Class.forName(BASE, false, loader);
        Class<?> animation = Class.forName("android.view.RemoteAnimationAdapter", false, loader);
        // Keep known R8 variants bounded; never guess an arbitrary notification runnable.
        for (int[] variant : new int[][]{{7, 10}, {6, 12}, {4, 9}}) try {
            return new NotificationClickTargets(
                    Class.forName(BASE + "$$ExternalSyntheticLambda" + variant[0], false, loader),
                    Class.forName(BASE + "$$ExternalSyntheticLambda" + variant[1], false, loader),
                    entry, starter, animation);
        } catch (ClassNotFoundException | NoSuchFieldException | NoSuchMethodException mismatch) { }
        throw new NoSuchMethodException("notification click adapter");
    }

    NotificationClickTargets(Class<?> runner, Class<?> sender, Class<?> entry,
                             Class<?> starter, Class<?> animation) throws Exception {
        run = runner.getDeclaredMethod("run");
        start = sender.getDeclaredMethod("startPendingIntent", animation);
        if (run.getReturnType() != void.class || start.getReturnType() != int.class
                || Modifier.isStatic(run.getModifiers()) || Modifier.isStatic(start.getModifiers()))
            throw new NoSuchMethodException("notification click signatures");
        runEntry = field(runner, "f$1", entry);
        runPending = field(runner, "f$3", PendingIntent.class);
        runAnimate = field(runner, "f$5", boolean.class);
        boolean capturedDisplay;
        try { sender.getDeclaredField("f$5"); capturedDisplay = true; }
        catch (NoSuchFieldException api35) { capturedDisplay = false; }
        if (capturedDisplay) {
            startEntry = field(sender, "f$5", entry);
            startPending = field(sender, "f$3", PendingIntent.class);
            startFill = field(sender, "f$4", Intent.class);
            displayId = field(sender, "f$2", int.class);
            displayOwner = null;
        } else {
            startEntry = field(sender, "f$4", entry);
            startPending = field(sender, "f$2", PendingIntent.class);
            startFill = field(sender, "f$3", Intent.class);
            displayOwner = field(sender, "f$0", starter);
            displayId = field(starter, "mDisplayId", int.class);
        }
    }

    int display(Object sender) throws IllegalAccessException {
        return displayId.getInt(displayOwner == null ? sender : displayOwner.get(sender));
    }

    private static Field field(Class<?> owner, String name, Class<?> type) throws Exception {
        Field field = owner.getDeclaredField(name);
        if (field.getType() != type || Modifier.isStatic(field.getModifiers()))
            throw new NoSuchFieldException(owner.getName() + "." + name);
        field.setAccessible(true);
        return field;
    }
}
