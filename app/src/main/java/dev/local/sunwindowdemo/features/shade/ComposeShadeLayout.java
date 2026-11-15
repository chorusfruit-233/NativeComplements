package dev.local.sunwindowdemo.features.shade;

import android.content.res.Configuration;
import android.content.res.Resources;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Feeds the native Compose shade-mode flow; never rewrites its readonly StateFlow. */
final class ComposeShadeLayout {
    private static final ThreadLocal<Boolean> readingShadeConfig = new ThreadLocal<>();
    private static final List<WeakReference<Object>> callbacks = new ArrayList<>();
    private static Method onConfigChanged;
    private static volatile int splitId, fullWidthId;
    private ComposeShadeLayout() {}

    static void install(XposedModule module, ClassLoader loader, List<XposedInterface.HookHandle> hooks,
                        Predicate<Resources> force) throws Exception {
        Class<?> repository;
        try { repository = Class.forName("com.android.systemui.shade.data.repository.ShadeConfigRepository", false, loader); }
        catch (ClassNotFoundException api35) {
            // API35 hydrates ShadeRepository through the same legacy split getter.
            // It has no ShadeConfigRepository or Compose boolean resource scope.
            Class<?> startable = Class.forName("com.android.systemui.shade.domain.startable.ShadeStartable", false, loader);
            Class<?> emitter = Class.forName("com.android.systemui.shade.domain.startable.ShadeStartable$hydrateShadeLayoutWidth$1$2", false, loader);
            if (emitter.getDeclaredField("this$0").getType() != startable
                    || !startable.getDeclaredField("splitShadeStateController").getType().getName()
                        .equals("com.android.systemui.statusbar.policy.SplitShadeStateControllerImpl"))
                throw new NoSuchFieldException("legacy shade hydration owner");
            Method emit = emitter.getDeclaredMethod("emit", Object.class, Class.forName("kotlin.coroutines.Continuation", false, loader));
            if (emit.getReturnType() != Object.class) throw new NoSuchMethodException("legacy shade hydration emit");
            trackConfiguration(module, loader, hooks);
            if (!module.deoptimize(emit)) throw new IllegalStateException("legacy shade hydration deoptimize");
            return;
        }
        Class<?> interactor = Class.forName("com.android.systemui.shade.domain.interactor.ShadeModeInteractorImpl", false, loader);
        Class<?> emitter;
        boolean hydrated;
        try {
            repository.getDeclaredField("legacyUseSplitShade");
            emitter=Class.forName("com.android.systemui.shade.data.repository.ShadeConfigRepository$special$$inlined$map$1$2",false,loader);
            hydrated=false;
        } catch (NoSuchFieldException newer) {
            repository.getDeclaredField("legacyUseSplitShade$delegate");
            emitter=Class.forName("com.android.systemui.shade.data.repository.ShadeConfigRepository$onActivated$2$1$1",false,loader);
            hydrated=true;
        }
        Method emit = emitter.getDeclaredMethod("emit", Object.class, Class.forName("kotlin.coroutines.Continuation", false, loader));
        hooks.add(module.hook(Resources.class.getDeclaredMethod("getBoolean", int.class))
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    Object original = chain.proceed();
                    int id = (Integer) chain.getArg(0);
                    if (!Boolean.TRUE.equals(readingShadeConfig.get())) return original;
                    Resources resources = (Resources) chain.getThisObject();
                    resolveResourceIds(resources);
                    if (id != splitId && id != fullWidthId) return original;
                    return overrideBoolean(original, id, true,
                            force.test(resources), splitId, fullWidthId);
                }));
        hooks.add(module.hook(emit).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
            Boolean before = readingShadeConfig.get();
            readingShadeConfig.set(true);
            try { return chain.proceed(); }
            finally { restoreScope(before); }
        }));
        for (Constructor<?> constructor : (hydrated?repository:interactor).getDeclaredConstructors()) {
            hooks.add(module.hook(constructor).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                Boolean before = readingShadeConfig.get();
                readingShadeConfig.set(true);
                try { return chain.proceed(); }
                finally { restoreScope(before); }
            }));
            if (!module.deoptimize(constructor)) throw new IllegalStateException("compose shade initial state deoptimize");
        }
        trackConfiguration(module, loader, hooks);
        if (!module.deoptimize(emit)) throw new IllegalStateException("compose shade emitter deoptimize");
    }

    private static void trackConfiguration(XposedModule module, ClassLoader loader,
                                           List<XposedInterface.HookHandle> hooks) throws Exception {
        Class<?> callback = Class.forName("com.android.systemui.common.ui.data.repository.ConfigurationRepositoryImpl$onConfigurationChange$1$callback$1", false, loader);
        onConfigChanged = callback.getDeclaredMethod("onConfigChanged", Configuration.class);
        if (onConfigChanged.getReturnType() != void.class) throw new NoSuchMethodException("shade configuration callback");
        onConfigChanged.setAccessible(true);
        for (Constructor<?> constructor : callback.getDeclaredConstructors()) {
            hooks.add(module.hook(constructor).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                Object result = chain.proceed();
                synchronized (callbacks) { callbacks.add(new WeakReference<>(chain.getThisObject())); }
                return result;
            }));
        }
    }

    // R classes can be removed by R8 even when JADX prints symbolic R.bool references.
    // Resolve from the actual host resource table, never the module's resource namespace.
    static synchronized void resolveResourceIds(Resources resources) {
        if (splitId != 0 && fullWidthId != 0) return;
        int split = resources.getIdentifier("config_use_split_notification_shade", "bool", "com.android.systemui");
        int fullWidth = resources.getIdentifier("config_isFullWidthShade", "bool", "com.android.systemui");
        if (split == 0 || fullWidth == 0) throw new Resources.NotFoundException("SystemUI shade booleans");
        splitId = split;
        fullWidthId = fullWidth;
    }

    private static void restoreScope(Boolean before) {
        if (before == null) readingShadeConfig.remove(); else readingShadeConfig.set(before);
    }

    static Object overrideBoolean(Object original, int id, boolean scoped, boolean force, int split, int fullWidth) {
        if (!scoped || !force) return original;
        if (id == split) return true;
        if (id == fullWidth) return false;
        return original;
    }

    static void refresh(Configuration current) throws Exception {
        if (onConfigChanged == null) return;
        List<Object> live = new ArrayList<>();
        synchronized (callbacks) {
            callbacks.removeIf(ref -> ref.get() == null);
            for (WeakReference<Object> ref : callbacks) { Object value = ref.get(); if (value != null) live.add(value); }
        }
        // Only notify this repository's callback: no global fake configuration or rotation.
        for (Object callback : live) onConfigChanged.invoke(callback, new Configuration(current));
    }
}
