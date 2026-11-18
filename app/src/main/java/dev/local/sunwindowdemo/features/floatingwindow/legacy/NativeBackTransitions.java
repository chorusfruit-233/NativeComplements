package dev.local.sunwindowdemo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Resolve the Shell instance that owns predictive Back transitions in this SystemUI. */
final class NativeBackTransitions {
    final Class<?> type;
    private final Field handler;
    private final Field transitions;
    private final Method idle;

    NativeBackTransitions(Class<?> controller, Class<?> type) throws ReflectiveOperationException {
        this.type = type;
        Field parent = null;
        Field member;
        try {
            member = controller.getDeclaredField("mTransitions");
        } catch (NoSuchFieldException nestedOwner) {
            parent = controller.getDeclaredField("mBackTransitionHandler");
            if (Modifier.isStatic(parent.getModifiers())) throw new NoSuchFieldException(parent.toString());
            parent.setAccessible(true);
            member = parent.getType().getDeclaredField("mTransitions");
        }
        if (member.getType() != type || Modifier.isStatic(member.getModifiers()))
            throw new NoSuchFieldException(member.toString());
        member.setAccessible(true);
        handler = parent;
        transitions = member;
        idle = type.getDeclaredMethod("runOnIdle", Runnable.class);
        if (idle.getReturnType() != void.class || Modifier.isStatic(idle.getModifiers()))
            throw new NoSuchMethodException(idle.toString());
        idle.setAccessible(true);
    }

    boolean nestedOwner() { return handler != null; }

    Object owner(Object controller) throws IllegalAccessException {
        return transitions.get(handler == null ? controller : handler.get(controller));
    }

    void runOnIdle(Object controller, Runnable callback) throws ReflectiveOperationException {
        idle.invoke(owner(controller), callback);
    }
}
