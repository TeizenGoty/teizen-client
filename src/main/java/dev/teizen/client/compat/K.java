package dev.teizen.client.compat;

/** Tasten-Codes – kommen beim Start aus der passenden Version (26.2: GLFW, 26.3: SDL). */
public final class K {
    private K() {}
    private static final Impl I = V.impl();
    public static final int MOD_SHIFT = I.key("MOD_SHIFT");
    public static final int ESCAPE = I.key("ESCAPE");
    public static final int ENTER = I.key("ENTER");
    public static final int KP_ENTER = I.key("KP_ENTER");
    public static final int TAB = I.key("TAB");
    public static final int BACKSPACE = I.key("BACKSPACE");
    public static final int DELETE = I.key("DELETE");
    public static final int INSERT = I.key("INSERT");
    public static final int HOME = I.key("HOME");
    public static final int END = I.key("END");
    public static final int PAGE_UP = I.key("PAGE_UP");
    public static final int PAGE_DOWN = I.key("PAGE_DOWN");
    public static final int UP = I.key("UP");
    public static final int DOWN = I.key("DOWN");
    public static final int LEFT = I.key("LEFT");
    public static final int RIGHT = I.key("RIGHT");
    public static final int SPACE = I.key("SPACE");
    public static final int LEFT_SHIFT = I.key("LEFT_SHIFT");
    public static final int RIGHT_SHIFT = I.key("RIGHT_SHIFT");
    public static final int LEFT_CONTROL = I.key("LEFT_CONTROL");
    public static final int RIGHT_CONTROL = I.key("RIGHT_CONTROL");
    public static final int LEFT_ALT = I.key("LEFT_ALT");
    public static final int RIGHT_ALT = I.key("RIGHT_ALT");
    public static final int LEFT_SUPER = I.key("LEFT_SUPER");
    public static final int RIGHT_SUPER = I.key("RIGHT_SUPER");
    public static final int CAPS_LOCK = I.key("CAPS_LOCK");
    public static final int COMMA = I.key("COMMA");
    public static final int PERIOD = I.key("PERIOD");
    public static final int MINUS = I.key("MINUS");
    public static final int A = I.key("A");
    public static final int G = I.key("G");
    public static final int H = I.key("H");
    public static final int J = I.key("J");
    public static final int K = I.key("K");
    public static final int M = I.key("M");
    public static final int O = I.key("O");
    public static final int F1 = I.key("F1");

    public static int letter(char c) { return I.letter(c); }
    public static int digit(int d) { return I.digit(d); }
    public static int f(int i) { return I.f(i); }
    public static int numpad(int d) { return I.numpad(d); }

    /** Für die Config: Codes werden immer als GLFW-Code gespeichert (gleiche Datei für 26.2 und 26.3). */
    public static int toGlfw(int code) { return I.toGlfw(code); }
    public static int fromGlfw(int glfw) { return I.fromGlfw(glfw); }
}
