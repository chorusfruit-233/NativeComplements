package dev.local.sunwindowdemo;

import java.lang.reflect.Method;
import org.junit.Test;
import static org.junit.Assert.*;

public class FrameworkVariantsTest {
    public static class LegacyTask { }
    public static class ModernTask {
        public boolean isOverrideBoundsAllowed(){return true;}
        public boolean isOverrideWindowingModeAllowed(){return true;}
        public boolean isForceOpaque(){return false;}
    }
    public static class PartialTask { public boolean isForceOpaque(){return false;} }
    public static class InvalidTask extends PartialTask {
        public int isOverrideBoundsAllowed(){return 1;}
        public boolean isOverrideWindowingModeAllowed(){return true;}
    }
    public static class Task {
        Task leaf;
        int reads;
        public Task getTopMostTask(){reads++;return leaf;}
    }
    public static class Root {
        Task selected;
        public Task focused(){return selected;}
    }

    @Test public void absentLegacyPoliciesDoNotHidePartialOrWrongNewerInterfaces()throws Exception{
        assertTrue(FrameworkVariants.nativeOverrideValidation(LegacyTask.class));
        assertFalse(FrameworkVariants.nativeOverrideValidation(ModernTask.class));
        assertThrows(NoSuchMethodException.class,()->FrameworkVariants.nativeOverrideValidation(PartialTask.class));
        assertThrows(IllegalStateException.class,()->FrameworkVariants.nativeOverrideValidation(InvalidTask.class));
    }
    @Test public void legacyFocusedRootResolvesItsLeafAndNullFocusIsPreserved()throws Exception{
        Root root=new Root();Task parent=new Task(),leaf=new Task();parent.leaf=leaf;root.selected=parent;
        Method focus=Root.class.getMethod("focused"),top=Task.class.getMethod("getTopMostTask");
        assertSame(leaf,FrameworkVariants.focusedTask(root,focus,top));assertEquals(1,parent.reads);
        assertSame(parent,FrameworkVariants.focusedTask(root,focus,null));assertEquals(1,parent.reads);
        root.selected=null;assertNull(FrameworkVariants.focusedTask(root,focus,top));
        assertSame(leaf,parent.leaf);
    }
    @Test public void legacyBarsRetainSplitScreenAndCurrentBarsRetainNativeIntegerFacts(){
        Object focus=new Object();Object[] legacy={focus,true,true},current={focus,17,29,true};
        assertArrayEquals(new Object[]{focus,true,false},FrameworkVariants.withoutFreeform(legacy));
        assertArrayEquals(new Object[]{focus,17,29,false},FrameworkVariants.withoutFreeform(current));
        assertArrayEquals(new Object[]{focus,true,true},legacy);
        assertArrayEquals(new Object[]{focus,17,29,true},current);
        assertThrows(IllegalArgumentException.class,()->FrameworkVariants.withoutFreeform(new Object[]{focus,true}));
    }
}
