package dev.teizen.client.compat;

import org.lwjgl.glfw.GLFW;

/** Tasten-Codes für Minecraft 26.2 (GLFW). Die gleiche Klasse gibt es für 26.3 (SDL). */
public final class K {
    private K() {}
    public static final int MOD_SHIFT = GLFW.GLFW_MOD_SHIFT;
    public static final int ESCAPE = GLFW.GLFW_KEY_ESCAPE;
    public static final int ENTER = GLFW.GLFW_KEY_ENTER;
    public static final int KP_ENTER = GLFW.GLFW_KEY_KP_ENTER;
    public static final int TAB = GLFW.GLFW_KEY_TAB;
    public static final int BACKSPACE = GLFW.GLFW_KEY_BACKSPACE;
    public static final int DELETE = GLFW.GLFW_KEY_DELETE;
    public static final int INSERT = GLFW.GLFW_KEY_INSERT;
    public static final int HOME = GLFW.GLFW_KEY_HOME;
    public static final int END = GLFW.GLFW_KEY_END;
    public static final int PAGE_UP = GLFW.GLFW_KEY_PAGE_UP;
    public static final int PAGE_DOWN = GLFW.GLFW_KEY_PAGE_DOWN;
    public static final int UP = GLFW.GLFW_KEY_UP;
    public static final int DOWN = GLFW.GLFW_KEY_DOWN;
    public static final int LEFT = GLFW.GLFW_KEY_LEFT;
    public static final int RIGHT = GLFW.GLFW_KEY_RIGHT;
    public static final int SPACE = GLFW.GLFW_KEY_SPACE;
    public static final int LEFT_SHIFT = GLFW.GLFW_KEY_LEFT_SHIFT;
    public static final int RIGHT_SHIFT = GLFW.GLFW_KEY_RIGHT_SHIFT;
    public static final int LEFT_CONTROL = GLFW.GLFW_KEY_LEFT_CONTROL;
    public static final int RIGHT_CONTROL = GLFW.GLFW_KEY_RIGHT_CONTROL;
    public static final int LEFT_ALT = GLFW.GLFW_KEY_LEFT_ALT;
    public static final int RIGHT_ALT = GLFW.GLFW_KEY_RIGHT_ALT;
    public static final int LEFT_SUPER = GLFW.GLFW_KEY_LEFT_SUPER;
    public static final int RIGHT_SUPER = GLFW.GLFW_KEY_RIGHT_SUPER;
    public static final int CAPS_LOCK = GLFW.GLFW_KEY_CAPS_LOCK;
    public static final int COMMA = GLFW.GLFW_KEY_COMMA;
    public static final int PERIOD = GLFW.GLFW_KEY_PERIOD;
    public static final int MINUS = GLFW.GLFW_KEY_MINUS;
    public static final int A = GLFW.GLFW_KEY_A;
    public static final int G = GLFW.GLFW_KEY_G;
    public static final int H = GLFW.GLFW_KEY_H;
    public static final int J = GLFW.GLFW_KEY_J;
    public static final int K = GLFW.GLFW_KEY_K;
    public static final int M = GLFW.GLFW_KEY_M;
    public static final int O = GLFW.GLFW_KEY_O;
    public static final int F1 = GLFW.GLFW_KEY_F1;

    public static int letter(char c) { return GLFW.GLFW_KEY_A + (Character.toLowerCase(c) - 'a'); }
    public static int digit(int d) { return GLFW.GLFW_KEY_0 + d; }
    public static int f(int i) { return GLFW.GLFW_KEY_F1 + i - 1; }
    public static int numpad(int d) { return GLFW.GLFW_KEY_KP_0 + d; }

    /** Für die Config: Codes werden immer als GLFW-Code gespeichert (gleiche Datei für 26.2 und 26.3). */
    public static int toGlfw(int code) { return code; }
    public static int fromGlfw(int glfw) { return glfw; }
}
