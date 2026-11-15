package dev.local.sunwindowdemo.features.shade;

import org.junit.Test;
import static org.junit.Assert.*;

public class SignalDisplayTargetsTest {
    public static class Cellular {
        boolean carrierNetworkChange, showExclamationMark;
        int level, numberOfLevels;
    }
    public static class Continuation { }
    public static class Pair { Object second; Pair(Object value) { second = value; } }
    public static class ViewModel { public int getSubscriptionId() { return 12; } }
    public static class Binder {
        int $r8$classId;
        Object $mobileDrawable;
        ViewModel $viewModel = new ViewModel();
        public Object emit(Object value, Continuation continuation) { return null; }
    }
    public static class WrongViewModel { public String getSubscriptionId() { return "12"; } }
    public static class WrongBinder {
        int $r8$classId;
        Object $mobileDrawable;
        WrongViewModel $viewModel;
        public Object emit(Object value, Continuation continuation) { return null; }
    }

    private SignalDisplayTargets targets(boolean paired) throws Exception {
        return new SignalDisplayTargets(Cellular.class, Binder.class, Continuation.class, paired ? Pair.class : null);
    }

    @Test public void api35AcceptsOnlyDirectCellularModels() throws Exception {
        SignalDisplayTargets target = targets(false);
        Cellular cellular = new Cellular();
        assertSame(cellular, target.model(cellular));
        assertNull(target.model(new Pair(cellular)));
        assertNull(target.model(new Object())); // Satellite or another binder branch.
        assertNull(target.model(null));
    }

    @Test public void api37StillUnwrapsOnlyItsPairPayload() throws Exception {
        SignalDisplayTargets target = targets(true);
        Cellular cellular = new Cellular();
        assertSame(cellular, target.model(new Pair(cellular)));
        assertNull(target.model(cellular));
        assertNull(target.model(new Pair(new Object())));
        assertNull(target.model(new Pair(null)));
    }

    @Test public void carrierChangeAnimationRemainsNativeForBothPayloadShapes() throws Exception {
        Cellular cellular = new Cellular(); cellular.carrierNetworkChange = true;
        assertNull(targets(false).model(cellular));
        assertNull(targets(true).model(new Pair(cellular)));
        assertTrue(cellular.carrierNetworkChange);
    }

    @Test public void subscriberIsReadFromTheEmittingViewModelAndWrongTypesAreRejected() throws Exception {
        SignalDisplayTargets target = targets(false);
        Binder binder = new Binder();
        assertEquals(12, target.subscription.invoke(target.viewModel.get(binder)));
        assertThrows(NoSuchMethodException.class, () -> new SignalDisplayTargets(Cellular.class,
                WrongBinder.class, Continuation.class, null));
    }
}
