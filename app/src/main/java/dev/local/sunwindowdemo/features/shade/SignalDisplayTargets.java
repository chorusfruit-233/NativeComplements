package dev.local.sunwindowdemo.features.shade;

import java.lang.reflect.*;

/** Keep API35's direct Cellular emission separate from API37's Pair emission. */
final class SignalDisplayTargets {
    private static final String BASE = "com.android.systemui.statusbar.pipeline.mobile.";
    final Class<?> cellular;
    final Method emit, subscription;
    final Field id, drawable, viewModel, changing;
    private final Class<?> pair;
    private final Field second;

    static SignalDisplayTargets resolve(ClassLoader loader) throws Exception {
        Class<?> cellular;
        try { cellular = Class.forName(BASE + "domain.model.SignalIconModel$CellularTypeIconModel$Cellular", false, loader); }
        catch (ClassNotFoundException api35) {
            return new SignalDisplayTargets(
                    Class.forName(BASE + "domain.model.SignalIconModel$Cellular", false, loader),
                    Class.forName(BASE + "ui.binder.MobileIconBinder$bind$1$2$1$2$1", false, loader),
                    Class.forName("kotlin.coroutines.Continuation", false, loader), null);
        }
        return new SignalDisplayTargets(cellular,
                Class.forName(BASE + "ui.binder.MobileIconBinder$bind$1$2$1$2$2", false, loader),
                Class.forName("kotlin.coroutines.Continuation", false, loader),
                Class.forName("kotlin.Pair", false, loader));
    }

    SignalDisplayTargets(Class<?> cellular, Class<?> binder, Class<?> continuation, Class<?> pair) throws Exception {
        this.cellular = cellular;
        this.pair = pair;
        second = pair == null ? null : field(pair, "second", Object.class);
        emit = binder.getDeclaredMethod("emit", Object.class, continuation);
        if (emit.getReturnType() != Object.class || Modifier.isStatic(emit.getModifiers()))
            throw new NoSuchMethodException("mobile binder emit");
        id = field(binder, "$r8$classId", int.class);
        drawable = field(binder, "$mobileDrawable", Object.class);
        viewModel = binder.getDeclaredField("$viewModel");
        if (Modifier.isStatic(viewModel.getModifiers())) throw new NoSuchFieldException("mobile view model");
        viewModel.setAccessible(true);
        subscription = viewModel.getType().getMethod("getSubscriptionId");
        if (subscription.getReturnType() != int.class || Modifier.isStatic(subscription.getModifiers()))
            throw new NoSuchMethodException("mobile subscription");
        changing = field(cellular, "carrierNetworkChange", boolean.class);
        field(cellular, "level", int.class);
        field(cellular, "numberOfLevels", int.class);
        field(cellular, "showExclamationMark", boolean.class);
    }

    Object model(Object value) throws IllegalAccessException {
        Object model = pair == null ? value : pair.isInstance(value) ? second.get(value) : null;
        return cellular.isInstance(model) && !changing.getBoolean(model) ? model : null;
    }

    private static Field field(Class<?> owner, String name, Class<?> type) throws Exception {
        Field field = owner.getDeclaredField(name);
        if (field.getType() != type || Modifier.isStatic(field.getModifiers()))
            throw new NoSuchFieldException(owner.getName() + "." + name);
        field.setAccessible(true);
        return field;
    }
}
