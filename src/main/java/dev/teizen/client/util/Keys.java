package dev.teizen.client.util;

import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Tastennamen <-> GLFW-Codes. */
public final class Keys {
    public static final int INVALID = Integer.MIN_VALUE;
    private static final Map<String, Integer> BY_NAME = new HashMap<>();
    private static final Map<Integer, String> BY_CODE = new HashMap<>();

    static {
        for (char c = 'a'; c <= 'z'; c++) put(String.valueOf(c), GLFW.GLFW_KEY_A + (c - 'a'));
        for (char c = '0'; c <= '9'; c++) put(String.valueOf(c), GLFW.GLFW_KEY_0 + (c - '0'));
        for (int i = 1; i <= 12; i++) put("f" + i, GLFW.GLFW_KEY_F1 + i - 1);
        put("rshift", GLFW.GLFW_KEY_RIGHT_SHIFT);
        put("lshift", GLFW.GLFW_KEY_LEFT_SHIFT);
        put("rctrl", GLFW.GLFW_KEY_RIGHT_CONTROL);
        put("lctrl", GLFW.GLFW_KEY_LEFT_CONTROL);
        put("ralt", GLFW.GLFW_KEY_RIGHT_ALT);
        put("lalt", GLFW.GLFW_KEY_LEFT_ALT);
        put("tab", GLFW.GLFW_KEY_TAB);
        put("capslock", GLFW.GLFW_KEY_CAPS_LOCK);
        put("insert", GLFW.GLFW_KEY_INSERT);
        put("delete", GLFW.GLFW_KEY_DELETE);
        put("home", GLFW.GLFW_KEY_HOME);
        put("end", GLFW.GLFW_KEY_END);
        put("pageup", GLFW.GLFW_KEY_PAGE_UP);
        put("pagedown", GLFW.GLFW_KEY_PAGE_DOWN);
        put("up", GLFW.GLFW_KEY_UP);
        put("down", GLFW.GLFW_KEY_DOWN);
        put("left", GLFW.GLFW_KEY_LEFT);
        put("right", GLFW.GLFW_KEY_RIGHT);
        put("comma", GLFW.GLFW_KEY_COMMA);
        put("period", GLFW.GLFW_KEY_PERIOD);
        put("minus", GLFW.GLFW_KEY_MINUS);
        for (int i = 0; i <= 9; i++) put("num" + i, GLFW.GLFW_KEY_KP_0 + i);
    }

    private static void put(String n, int code) {
        BY_NAME.put(n, code);
        BY_CODE.putIfAbsent(code, n.toUpperCase(Locale.ROOT));
    }

    private Keys() {}

    /** @return Code, -1 für "keine", oder INVALID. */
    public static int parse(String s) {
        String n = s.toLowerCase(Locale.ROOT).trim();
        if (n.equals("none") || n.equals("keine") || n.equals("aus") || n.equals("-")) return -1;
        Integer c = BY_NAME.get(n);
        return c == null ? INVALID : c;
    }

    public static String name(int code) {
        if (code < 0) return "-";
        return BY_CODE.getOrDefault(code, "#" + code);
    }
}
