package dev.local.sunwindowdemo;

import android.view.InputEvent;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class NativeBackInputTest {
    public interface Listener { void onInputEvent(InputEvent event); }
    public static class Monitor {
        int pilfered;
        public void pilferPointers() { pilfered++; }
    }
    public static class Compat { Monitor mInputMonitor = new Monitor(); }
    public static class DirectHandler { int mDisplayId; Compat mInputMonitor = new Compat(); }
    public static class Direct implements Listener {
        DirectHandler f$0 = new DirectHandler();
        int received;
        public void onInputEvent(InputEvent event) { received++; }
    }
    public static class Receiver {
        Listener val$listener;
        int finished;
        public void onInputEvent(InputEvent event) { val$listener.onInputEvent(event); finished++; }
    }
    public static class DisplayHandler { Compat inputMonitorCompat = new Compat(); }
    public static class MultiHandler { Map<Integer, DisplayHandler> mDisplayBackGestureHandlers = new HashMap<>(); }
    public static class Function {
        MultiHandler f$0 = new MultiHandler();
        public Object invoke(Object event) { return Unit.INSTANCE; }
    }
    public static class Unit { public static final Unit INSTANCE = new Unit(); }
    public static class WrongOwner implements Listener {
        Object f$0;
        public void onInputEvent(InputEvent event) { }
    }
    public static class WrongReceiver {
        Object val$listener;
        public void onInputEvent(InputEvent event) { }
    }

    private NativeBackInput direct() throws Exception {
        return new NativeBackInput(DirectHandler.class, Direct.class, null, Compat.class, Monitor.class, null, Receiver.class);
    }

    @Test public void api35UsesOnlyItsOwnDisplayAndMonitor() throws Exception {
        NativeBackInput target = direct();
        Direct callback = new Direct();
        assertSame(callback.f$0, target.display(callback, 0));
        assertNull(target.display(callback, 2));
        target.pilfer(target.display(callback, 0));
        assertEquals(1, callback.f$0.mInputMonitor.mInputMonitor.pilfered);
        callback.f$0.mDisplayId = 2;
        assertNull(target.display(callback, 0));
        assertSame(callback.f$0, target.display(callback, 2));
        assertNull(target.consumed);
    }

    @Test public void perDisplayFunctionKeepsUnitAndSelectsTheMatchingMonitor() throws Exception {
        NativeBackInput target = new NativeBackInput(MultiHandler.class, Function.class, DisplayHandler.class,
                Compat.class, Monitor.class, Unit.class, null);
        Function callback = new Function();
        DisplayHandler primary = new DisplayHandler(), external = new DisplayHandler();
        callback.f$0.mDisplayBackGestureHandlers.put(0, primary);
        callback.f$0.mDisplayBackGestureHandlers.put(2, external);
        target.pilfer(target.display(callback, 0));
        assertEquals(1, primary.inputMonitorCompat.mInputMonitor.pilfered);
        assertEquals(0, external.inputMonitorCompat.mInputMonitor.pilfered);
        assertNull(target.display(callback, 7));
        assertSame(Unit.INSTANCE, target.consumed);
        assertNull(target.receiver);
    }

    @Test public void unknownOwnerAndReceiverShapesAreRejectedBeforeHooking() {
        assertThrows(NoSuchFieldException.class, () -> new NativeBackInput(DirectHandler.class, WrongOwner.class,
                null, Compat.class, Monitor.class, null, Receiver.class));
        assertThrows(NoSuchFieldException.class, () -> new NativeBackInput(DirectHandler.class, Direct.class,
                null, Compat.class, Monitor.class, null, WrongReceiver.class));
    }
}
