package dev.local.sunwindowdemo;

import org.junit.Test;
import static org.junit.Assert.*;

public class ViewportTargetsTest {
    private static final String S="com.android.server.wm.Session",C="android.view.IWindow",P="android.view.WindowManager$LayoutParams";
    private static final String R="android.view.WindowRelayoutResult";
    @Test public void api35AddUsesOnlyTheDirectInsetsOutParameter(){
        var output=ViewportTargets.describe("addWindow",int.class,S,C,P,"int","int","int","int",
                "android.view.InputChannel","android.view.InsetsState","android.view.InsetsSourceControl$Array","android.graphics.Rect","[F");
        assertNotNull(output);assertTrue(output.initial());assertEquals(-1,output.result());assertEquals(8,output.insets());
    }
    @Test public void api37AddAndBothRelayoutShapesUseTheNativeResultContainer(){
        var add=ViewportTargets.describe("addWindow",int.class,S,C,P,"int","int","int","int","android.view.InputChannel",R);
        assertTrue(add.initial());assertEquals(8,add.result());assertEquals(-1,add.insets());
        var legacy=ViewportTargets.describe("relayoutWindow",int.class,S,C,P,"int","int","int","int","int","int",R);
        var current=ViewportTargets.describe("relayoutWindow",int.class,S,C,P,"int","int","int","int","int","int",R,"android.view.SurfaceControl");
        assertEquals(legacy,current);assertFalse(legacy.initial());assertEquals(9,legacy.result());assertEquals(-1,legacy.insets());
    }
    @Test public void similarOrUnknownBinderSignaturesAreNotAccepted(){
        assertNull(ViewportTargets.describe("addWindow",boolean.class,S,C,P,"int","int","int","int","android.view.InputChannel",R));
        assertNull(ViewportTargets.describe("addWindow",int.class,S,C,P,"int","int","int","int","android.view.InsetsState",R));
        assertNull(ViewportTargets.describe("relayoutWindow",int.class,S,C,P,"int","int","int","int","int","int",R,"java.lang.Object"));
    }
}
