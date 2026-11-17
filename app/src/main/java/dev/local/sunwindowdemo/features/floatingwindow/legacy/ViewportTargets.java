package dev.local.sunwindowdemo;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

/** Exact Binder out-parameter layouts. Other native out objects are never remapped. */
final class ViewportTargets {
    record Output(boolean initial, int result, int insets) { }
    private static final String SESSION = "com.android.server.wm.Session";
    private static final String CLIENT = "android.view.IWindow";
    private static final String PARAMS = "android.view.WindowManager$LayoutParams";
    private static final String RESULT = "android.view.WindowRelayoutResult";
    private ViewportTargets() { }

    static Output describe(Method method) {
        return describe(method.getName(), method.getReturnType(),
                Arrays.stream(method.getParameterTypes()).map(Class::getName).toArray(String[]::new));
    }

    static Output describe(String name, Class<?> result, String... parameters) {
        if (result != int.class) return null;
        List<String> args = Arrays.asList(parameters);
        if (name.equals("addWindow")) {
            if (args.equals(List.of(SESSION, CLIENT, PARAMS, "int", "int", "int", "int",
                    "android.view.InputChannel", RESULT))) return new Output(true, 8, -1);
            if (args.equals(List.of(SESSION, CLIENT, PARAMS, "int", "int", "int", "int",
                    "android.view.InputChannel", "android.view.InsetsState", "android.view.InsetsSourceControl$Array",
                    "android.graphics.Rect", "[F"))) return new Output(true, -1, 8);
        } else if (name.equals("relayoutWindow")) {
            List<String> base = List.of(SESSION, CLIENT, PARAMS, "int", "int", "int", "int", "int", "int", RESULT);
            if (args.equals(base) || args.size() == 11 && args.subList(0, 10).equals(base)
                    && args.get(10).equals("android.view.SurfaceControl")) return new Output(false, 9, -1);
        }
        return null;
    }
}
