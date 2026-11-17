package dev.local.sunwindowdemo.features.callblocking;

import java.lang.reflect.*;
import java.util.*;

/** Preserve every native API35 filtering fact, including DND and contact state. */
final class CallFilteringCopy {
    private static final String[][] FIELDS = {
        {"shouldAllowCall", "mShouldAllowCall"}, {"shouldReject", "mShouldReject"},
        {"shouldSilence", "mShouldSilence"}, {"shouldScreenViaAudio", "mShouldScreenViaAudio"},
        {"shouldAddToCallLog", "mShouldAddToCallLog"}, {"shouldShowNotification", "mShouldShowNotification"},
        {"mCallBlockReason", "mCallBlockReason"}, {"mCallScreeningAppName", "mCallScreeningAppName"},
        {"mCallScreeningComponentName", "mCallScreeningComponentName"}, {"mCallScreeningResponse", "mCallScreeningResponse"},
        {"mIsResponseFromSystemDialer", "mIsResponseFromSystemDialer"}, {"contactExists", "mContactExists"},
        {"shouldSuppressCallDueToDndStatus", "mDndSuppressed"}
    };
    private final Method from;
    private final Constructor<?> create;
    private final List<Field[]> fields = new ArrayList<>();

    CallFilteringCopy(Class<?> result, Class<?> builder) throws ReflectiveOperationException {
        Method factory;
        try { factory = builder.getDeclaredMethod("from", result); }
        catch (NoSuchMethodException legacy) { factory = null; }
        from = factory;
        if (from != null) {
            if (!Modifier.isStatic(from.getModifiers()) || from.getReturnType() != builder)
                throw new NoSuchMethodException("filter result copy factory");
            from.setAccessible(true); create = null;
            return;
        }
        create = builder.getDeclaredConstructor(); create.setAccessible(true);
        Set<String> mapped = new HashSet<>();
        for (String[] pair : FIELDS) {
            Field source = result.getDeclaredField(pair[0]), target = builder.getDeclaredField(pair[1]);
            if (source.getType() != target.getType() || Modifier.isStatic(source.getModifiers())
                    || Modifier.isStatic(target.getModifiers()) || Modifier.isFinal(target.getModifiers()))
                throw new NoSuchFieldException("native filter copy " + pair[0]);
            source.setAccessible(true); target.setAccessible(true);
            fields.add(new Field[]{source, target}); mapped.add(pair[0]);
        }
        for (Field field : result.getDeclaredFields()) if (!Modifier.isStatic(field.getModifiers()) && !mapped.contains(field.getName()))
            throw new NoSuchFieldException("unmapped native filtering fact " + field.getName());
    }

    Object copy(Object result) throws ReflectiveOperationException {
        if (from != null) return from.invoke(null, result);
        Object builder = create.newInstance();
        for (Field[] pair : fields) pair[1].set(builder, pair[0].get(result));
        return builder;
    }

    static Method response(Class<?> builder) throws NoSuchMethodException {
        Method selected = null;
        for (Method method : builder.getDeclaredMethods()) {
            Class<?>[] args = method.getParameterTypes();
            if (!method.getName().equals("setCallScreeningResponse") || args.length != 2 || args[1] != boolean.class
                    || method.getReturnType() != builder || Modifier.isStatic(method.getModifiers())) continue;
            String type = args[0].getName();
            if (!type.equals("android.telecom.ParcelableCallResponse")
                    && !type.equals("android.telecom.CallScreeningService$ParcelableCallResponse")) continue;
            if (selected != null) throw new NoSuchMethodException("ambiguous screening response");
            selected = method;
        }
        if (selected == null) throw new NoSuchMethodException("native screening response");
        selected.setAccessible(true); return selected;
    }
}
