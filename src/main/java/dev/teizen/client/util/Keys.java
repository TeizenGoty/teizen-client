package dev.teizen.client.util;

import dev.teizen.client.compat.K;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Tastennamen <-> Tasten-Codes (26.2: GLFW, 26.3: SDL). */
public final class Keys {
    public static final int INVALID = Integer.MIN_VALUE;
    private static final Map<String, Integer> BY_NAME = new HashMap<>();
    private static final Map<Integer, String> BY_CODE = new HashMap<>();

    static {
        for (char c = 'a'; c <= 'z'; c++) put(String.valueOf(c), K.letter(c));
        for (char c = '0'; c <= '9'; c++) put(String.valueOf(c), K.digit(c - '0'));
        for (int i = 1; i <= 12; i++) put("f" + i, K.f(i));
        put("rshift", K.RIGHT_SHIFT);
        put("lshift", K.LEFT_SHIFT);
        put("rctrl", K.RIGHT_CONTROL);
        put("lctrl", K.LEFT_CONTROL);
        put("ralt", K.RIGHT_ALT);
        put("lalt", K.LEFT_ALT);
        put("tab", K.TAB);
        put("capslock", K.CAPS_LOCK);
        put("insert", K.INSERT);
        put("delete", K.DELETE);
        put("home", K.HOME);
        put("end", K.END);
        put("pageup", K.PAGE_UP);
        put("pagedown", K.PAGE_DOWN);
        put("up", K.UP);
        put("down", K.DOWN);
        put("left", K.LEFT);
        put("right", K.RIGHT);
        put("comma", K.COMMA);
        put("period", K.PERIOD);
        put("minus", K.MINUS);
        for (int i = 0; i <= 9; i++) put("num" + i, K.numpad(i));
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

    /** Alle bekannten Tastennamen (für Vorschläge im Chat). */
    public static java.util.List<String> names() {
        java.util.List<String> out = new java.util.ArrayList<>(BY_NAME.keySet());
        out.sort((x, y) -> x.length() != y.length() ? Integer.compare(x.length(), y.length()) : x.compareTo(y));
        return out;
    }

    public static String name(int code) {
        if (code < 0) return "-";
        return BY_CODE.getOrDefault(code, "#" + code);
    }
}
