package dev.local.sunwindowdemo;

import android.view.InputEvent;
import java.lang.reflect.*;
import java.util.Map;

/** Exact native input boundaries: API35 listener or the verified per-display Function1. */
final class NativeBackInput {
    private static final String BASE = "com.android.systemui.navigationbar.gestural.";
    final Method invoke, receiver, pilfer;
    final Field owner;
    final Object consumed;
    private final Field displays, displayId, inputMonitor, monitor;

    static NativeBackInput resolve(ClassLoader loader) throws Exception {
        Class<?> handler = Class.forName(BASE + "EdgeBackGestureHandler", false, loader);
        Class<?> compat = Class.forName("com.android.systemui.shared.system.InputMonitorCompat", false, loader);
        Class<?> monitor = Class.forName("android.view.InputMonitor", false, loader);
        Class<?> display;
        try { display = Class.forName(BASE + "DisplayBackGestureHandlerImpl", false, loader); }
        catch (ClassNotFoundException singleDisplay) {
            return new NativeBackInput(handler,
                    Class.forName(BASE + "EdgeBackGestureHandler$$ExternalSyntheticLambda11", false, loader),
                    null, compat, monitor, null,
                    Class.forName("com.android.systemui.shared.system.InputChannelCompat$InputEventReceiver$1", false, loader));
        }
        Class<?> adapter = display.getDeclaredField("onInputEvent").getType();
        if (adapter.isInterface()) adapter = Class.forName(BASE + "EdgeBackGestureHandler$$ExternalSyntheticLambda20", false, loader);
        return new NativeBackInput(handler, adapter, display, compat, monitor,
                Class.forName("kotlin.Unit", false, loader), null);
    }

    NativeBackInput(Class<?> handler, Class<?> adapter, Class<?> display, Class<?> compat,
                    Class<?> monitorType, Class<?> unit, Class<?> receiverType) throws Exception {
        owner = field(adapter, "f$0", handler);
        monitor = field(compat, "mInputMonitor", monitorType);
        pilfer = method(monitorType, "pilferPointers", void.class);
        if (display == null) {
            invoke = method(adapter, "onInputEvent", void.class, InputEvent.class);
            receiver = method(receiverType, "onInputEvent", void.class, InputEvent.class);
            Class<?> listener = receiverType.getDeclaredField("val$listener").getType();
            if (!listener.isInterface() || !listener.isAssignableFrom(adapter))
                throw new NoSuchFieldException("native input receiver listener");
            displayId = field(handler, "mDisplayId", int.class);
            inputMonitor = field(handler, "mInputMonitor", compat);
            displays = null;
            consumed = null; // Native InputEventListener returns void, not kotlin.Unit.
        } else {
            invoke = method(adapter, "invoke", Object.class, Object.class);
            receiver = null;
            displays = field(handler, "mDisplayBackGestureHandlers", Map.class);
            inputMonitor = field(display, "inputMonitorCompat", compat);
            displayId = null;
            Field instance = unit.getField("INSTANCE");
            if (instance.getType() != unit || !Modifier.isStatic(instance.getModifiers()))
                throw new NoSuchFieldException("Unit.INSTANCE");
            consumed = instance.get(null);
            if (consumed == null) throw new IllegalStateException("missing Unit.INSTANCE");
        }
    }

    Object display(Object callback, int id) throws IllegalAccessException {
        Object handler = owner.get(callback);
        if (displays == null) return displayId.getInt(handler) == id ? handler : null;
        Object value = displays.get(handler);
        return value instanceof Map<?, ?> map ? map.get(id) : null;
    }

    void pilfer(Object display) throws ReflectiveOperationException {
        pilfer.invoke(monitor.get(inputMonitor.get(display)));
    }

    private static Field field(Class<?> type, String name, Class<?> expected) throws Exception {
        Field field = type.getDeclaredField(name);
        if (field.getType() != expected || Modifier.isStatic(field.getModifiers()))
            throw new NoSuchFieldException(type.getName() + "." + name);
        field.setAccessible(true);
        return field;
    }

    private static Method method(Class<?> type, String name, Class<?> result, Class<?>... args) throws Exception {
        Method method = type.getDeclaredMethod(name, args);
        if (method.getReturnType() != result || Modifier.isStatic(method.getModifiers()))
            throw new NoSuchMethodException(type.getName() + "#" + name);
        method.setAccessible(true);
        return method;
    }
}
