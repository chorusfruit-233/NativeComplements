package dev.local.sunwindowdemo;

import java.lang.reflect.*;

/** Bounded native ABI choices; missing one member never silently weakens a newer policy. */
final class FrameworkVariants {
    private FrameworkVariants() { }

    static Method method(Class<?> owner, String name, Class<?>... args) throws NoSuchMethodException {
        for (Class<?> type = owner; type != null; type = type.getSuperclass()) try {
            Method method = type.getDeclaredMethod(name, args);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException absent) { }
        throw new NoSuchMethodException(owner.getName() + "#" + name);
    }

    static boolean nativeOverrideValidation(Class<?> task) throws NoSuchMethodException {
        int present = 0;
        for (String name : new String[]{"isOverrideBoundsAllowed", "isOverrideWindowingModeAllowed", "isForceOpaque"}) try {
            Method method = method(task, name);
            if (method.getReturnType() != boolean.class || Modifier.isStatic(method.getModifiers()))
                throw new IllegalStateException("native task policy " + name);
            present++;
        } catch (NoSuchMethodException absent) { }
        if (present != 0 && present != 3) throw new NoSuchMethodException("incomplete native task override policy");
        // API35 has no separate override/force-opaque policy. Its native Freeform,
        // TaskDisplayArea and scoped resize checks still run before task acquisition.
        return present == 0;
    }

    static Object focusedTask(Object root, Method focused, Method leaf) throws ReflectiveOperationException {
        Object task = focused.invoke(root);
        return task == null || leaf == null ? task : leaf.invoke(task);
    }

    static Object[] withoutFreeform(Object[] original) {
        if (original.length != 3 && original.length != 4 || !(original[original.length - 1] instanceof Boolean))
            throw new IllegalArgumentException("native system bars arguments");
        Object[] args = original.clone();
        args[args.length - 1] = false;
        return args;
    }
}
