package dev.local.sunwindowdemo;

import android.app.PendingIntent;
import android.content.Intent;
import org.junit.Test;
import static org.junit.Assert.*;

public class NotificationClickTargetsTest {
    public static class Entry { }
    public static class Animation { }
    public static class Starter { int mDisplayId; }
    public static class Runner {
        Entry f$1;
        PendingIntent f$3;
        boolean f$5;
        public void run() { }
    }
    public static class CapturedSender {
        int f$2;
        PendingIntent f$3;
        Intent f$4;
        Entry f$5;
        public int startPendingIntent(Animation animation) { return 0; }
    }
    public static class Api35Sender {
        Starter f$0 = new Starter();
        PendingIntent f$2;
        Intent f$3;
        Entry f$4;
        public int startPendingIntent(Animation animation) { return 0; }
    }
    public static class WrongSender {
        int f$2;
        PendingIntent f$3;
        Intent f$4;
        Object f$5;
        public int startPendingIntent(Animation animation) { return 0; }
    }
    public static class StaticRunner {
        Entry f$1;
        PendingIntent f$3;
        boolean f$5;
        public static void run() { }
    }

    private NotificationClickTargets targets(Class<?> sender) throws Exception {
        return new NotificationClickTargets(Runner.class, sender, Entry.class, Starter.class, Animation.class);
    }

    @Test public void api35ReadsTheStartersActualDisplayRatherThanAssumingPrimary() throws Exception {
        NotificationClickTargets target = targets(Api35Sender.class);
        Api35Sender sender = new Api35Sender();
        sender.f$0.mDisplayId = 3;
        assertEquals(3, target.display(sender));
        sender.f$0.mDisplayId = 0;
        assertEquals(0, target.display(sender));
        Entry entry = new Entry(); sender.f$4 = entry;
        assertSame(entry, target.startEntry.get(sender));
        assertEquals("f$2", target.startPending.getName());
        assertEquals("f$3", target.startFill.getName());
    }

    @Test public void currentAndQpr2KeepTheirCapturedDisplayAndFields() throws Exception {
        NotificationClickTargets target = targets(CapturedSender.class);
        CapturedSender sender = new CapturedSender(); sender.f$2 = 7;
        assertEquals(7, target.display(sender));
        assertEquals("f$5", target.startEntry.getName());
        assertEquals("f$3", target.startPending.getName());
        assertEquals("f$4", target.startFill.getName());
    }

    @Test public void resolvingAndReadingTargetsDoesNotChangeTheNativeAnimationFlag() throws Exception {
        NotificationClickTargets target = targets(Api35Sender.class);
        Runner runner = new Runner(); runner.f$5 = true;
        assertTrue(target.runAnimate.getBoolean(runner));
        target.display(new Api35Sender());
        assertTrue(runner.f$5);
    }

    @Test public void incompatibleOrStaticEntryPointsDoNotFallThroughToAnotherLayout() {
        assertThrows(NoSuchFieldException.class, () -> targets(WrongSender.class));
        assertThrows(NoSuchMethodException.class, () -> new NotificationClickTargets(StaticRunner.class,
                Api35Sender.class, Entry.class, Starter.class, Animation.class));
    }
}
