package dev.teizen.client.compat;

import com.mojang.blaze3d.platform.InputConstants;

/** Tasten-Codes für Minecraft 26.3 (SDL3, nicht mehr GLFW). Gleiche Namen wie in der 26.2-Version. */
public final class K {
    private K() {}
    public static final int MOD_SHIFT = InputConstants.MOD_SHIFT;
    public static final int ESCAPE = InputConstants.KEY_ESCAPE;
    public static final int ENTER = InputConstants.KEY_RETURN;
    public static final int KP_ENTER = InputConstants.KEY_NUMPADENTER;
    public static final int TAB = InputConstants.KEY_TAB;
    public static final int BACKSPACE = InputConstants.KEY_BACKSPACE;
    public static final int DELETE = InputConstants.KEY_DELETE;
    public static final int INSERT = InputConstants.KEY_INSERT;
    public static final int HOME = InputConstants.KEY_HOME;
    public static final int END = InputConstants.KEY_END;
    public static final int PAGE_UP = InputConstants.KEY_PAGEUP;
    public static final int PAGE_DOWN = InputConstants.KEY_PAGEDOWN;
    public static final int UP = InputConstants.KEY_UP;
    public static final int DOWN = InputConstants.KEY_DOWN;
    public static final int LEFT = InputConstants.KEY_LEFT;
    public static final int RIGHT = InputConstants.KEY_RIGHT;
    public static final int SPACE = InputConstants.KEY_SPACE;
    public static final int LEFT_SHIFT = InputConstants.KEY_LSHIFT;
    public static final int RIGHT_SHIFT = InputConstants.KEY_RSHIFT;
    public static final int LEFT_CONTROL = InputConstants.KEY_LCONTROL;
    public static final int RIGHT_CONTROL = InputConstants.KEY_RCONTROL;
    public static final int LEFT_ALT = InputConstants.KEY_LALT;
    public static final int RIGHT_ALT = InputConstants.KEY_RALT;
    public static final int LEFT_SUPER = InputConstants.KEY_LGUI;
    public static final int RIGHT_SUPER = InputConstants.KEY_RGUI;
    public static final int CAPS_LOCK = InputConstants.KEY_CAPSLOCK;
    public static final int COMMA = InputConstants.KEY_COMMA;
    public static final int PERIOD = InputConstants.KEY_PERIOD;
    public static final int MINUS = InputConstants.KEY_MINUS;
    public static final int A = InputConstants.KEY_A;
    public static final int G = InputConstants.KEY_G;
    public static final int H = InputConstants.KEY_H;
    public static final int J = InputConstants.KEY_J;
    public static final int K = InputConstants.KEY_K;
    public static final int M = InputConstants.KEY_M;
    public static final int O = InputConstants.KEY_O;
    public static final int F1 = InputConstants.KEY_F1;

    private static final int[] LETTERS = {InputConstants.KEY_A,InputConstants.KEY_B,InputConstants.KEY_C,InputConstants.KEY_D,InputConstants.KEY_E,InputConstants.KEY_F,InputConstants.KEY_G,InputConstants.KEY_H,InputConstants.KEY_I,InputConstants.KEY_J,InputConstants.KEY_K,InputConstants.KEY_L,InputConstants.KEY_M,InputConstants.KEY_N,InputConstants.KEY_O,InputConstants.KEY_P,InputConstants.KEY_Q,InputConstants.KEY_R,InputConstants.KEY_S,InputConstants.KEY_T,InputConstants.KEY_U,InputConstants.KEY_V,InputConstants.KEY_W,InputConstants.KEY_X,InputConstants.KEY_Y,InputConstants.KEY_Z};
    private static final int[] DIGITS = {InputConstants.KEY_0,InputConstants.KEY_1,InputConstants.KEY_2,InputConstants.KEY_3,InputConstants.KEY_4,InputConstants.KEY_5,InputConstants.KEY_6,InputConstants.KEY_7,InputConstants.KEY_8,InputConstants.KEY_9};
    private static final int[] FS = {InputConstants.KEY_F1,InputConstants.KEY_F2,InputConstants.KEY_F3,InputConstants.KEY_F4,InputConstants.KEY_F5,InputConstants.KEY_F6,InputConstants.KEY_F7,InputConstants.KEY_F8,InputConstants.KEY_F9,InputConstants.KEY_F10,InputConstants.KEY_F11,InputConstants.KEY_F12,InputConstants.KEY_F13,InputConstants.KEY_F14,InputConstants.KEY_F15,InputConstants.KEY_F16,InputConstants.KEY_F17,InputConstants.KEY_F18,InputConstants.KEY_F19,InputConstants.KEY_F20,InputConstants.KEY_F21,InputConstants.KEY_F22,InputConstants.KEY_F23,InputConstants.KEY_F24};
    private static final int[] NUMPAD = {InputConstants.KEY_NUMPAD0,InputConstants.KEY_NUMPAD1,InputConstants.KEY_NUMPAD2,InputConstants.KEY_NUMPAD3,InputConstants.KEY_NUMPAD4,InputConstants.KEY_NUMPAD5,InputConstants.KEY_NUMPAD6,InputConstants.KEY_NUMPAD7,InputConstants.KEY_NUMPAD8,InputConstants.KEY_NUMPAD9};

    public static int letter(char c) { return LETTERS[Character.toLowerCase(c) - 'a']; }
    public static int digit(int d) { return DIGITS[d]; }
    public static int f(int i) { return FS[i - 1]; }
    public static int numpad(int d) { return NUMPAD[d]; }

    /** GLFW-Code (26.2) <-> SDL-Code (26.3), damit die Config für beide Versionen gleich bleibt. */
    private static final int[][] GLFW = {
        {65, InputConstants.KEY_A},
        {66, InputConstants.KEY_B},
        {67, InputConstants.KEY_C},
        {68, InputConstants.KEY_D},
        {69, InputConstants.KEY_E},
        {70, InputConstants.KEY_F},
        {71, InputConstants.KEY_G},
        {72, InputConstants.KEY_H},
        {73, InputConstants.KEY_I},
        {74, InputConstants.KEY_J},
        {75, InputConstants.KEY_K},
        {76, InputConstants.KEY_L},
        {77, InputConstants.KEY_M},
        {78, InputConstants.KEY_N},
        {79, InputConstants.KEY_O},
        {80, InputConstants.KEY_P},
        {81, InputConstants.KEY_Q},
        {82, InputConstants.KEY_R},
        {83, InputConstants.KEY_S},
        {84, InputConstants.KEY_T},
        {85, InputConstants.KEY_U},
        {86, InputConstants.KEY_V},
        {87, InputConstants.KEY_W},
        {88, InputConstants.KEY_X},
        {89, InputConstants.KEY_Y},
        {90, InputConstants.KEY_Z},
        {48, InputConstants.KEY_0},
        {49, InputConstants.KEY_1},
        {50, InputConstants.KEY_2},
        {51, InputConstants.KEY_3},
        {52, InputConstants.KEY_4},
        {53, InputConstants.KEY_5},
        {54, InputConstants.KEY_6},
        {55, InputConstants.KEY_7},
        {56, InputConstants.KEY_8},
        {57, InputConstants.KEY_9},
        {290, InputConstants.KEY_F1},
        {291, InputConstants.KEY_F2},
        {292, InputConstants.KEY_F3},
        {293, InputConstants.KEY_F4},
        {294, InputConstants.KEY_F5},
        {295, InputConstants.KEY_F6},
        {296, InputConstants.KEY_F7},
        {297, InputConstants.KEY_F8},
        {298, InputConstants.KEY_F9},
        {299, InputConstants.KEY_F10},
        {300, InputConstants.KEY_F11},
        {301, InputConstants.KEY_F12},
        {302, InputConstants.KEY_F13},
        {303, InputConstants.KEY_F14},
        {304, InputConstants.KEY_F15},
        {305, InputConstants.KEY_F16},
        {306, InputConstants.KEY_F17},
        {307, InputConstants.KEY_F18},
        {308, InputConstants.KEY_F19},
        {309, InputConstants.KEY_F20},
        {310, InputConstants.KEY_F21},
        {311, InputConstants.KEY_F22},
        {312, InputConstants.KEY_F23},
        {313, InputConstants.KEY_F24},
        {320, InputConstants.KEY_NUMPAD0},
        {321, InputConstants.KEY_NUMPAD1},
        {322, InputConstants.KEY_NUMPAD2},
        {323, InputConstants.KEY_NUMPAD3},
        {324, InputConstants.KEY_NUMPAD4},
        {325, InputConstants.KEY_NUMPAD5},
        {326, InputConstants.KEY_NUMPAD6},
        {327, InputConstants.KEY_NUMPAD7},
        {328, InputConstants.KEY_NUMPAD8},
        {329, InputConstants.KEY_NUMPAD9},
        {32, InputConstants.KEY_SPACE},
        {39, InputConstants.KEY_APOSTROPHE},
        {44, InputConstants.KEY_COMMA},
        {45, InputConstants.KEY_MINUS},
        {46, InputConstants.KEY_PERIOD},
        {47, InputConstants.KEY_SLASH},
        {59, InputConstants.KEY_SEMICOLON},
        {61, InputConstants.KEY_EQUALS},
        {91, InputConstants.KEY_LBRACKET},
        {92, InputConstants.KEY_BACKSLASH},
        {93, InputConstants.KEY_RBRACKET},
        {96, InputConstants.KEY_GRAVE},
        {256, InputConstants.KEY_ESCAPE},
        {257, InputConstants.KEY_RETURN},
        {258, InputConstants.KEY_TAB},
        {259, InputConstants.KEY_BACKSPACE},
        {260, InputConstants.KEY_INSERT},
        {261, InputConstants.KEY_DELETE},
        {262, InputConstants.KEY_RIGHT},
        {263, InputConstants.KEY_LEFT},
        {264, InputConstants.KEY_DOWN},
        {265, InputConstants.KEY_UP},
        {266, InputConstants.KEY_PAGEUP},
        {267, InputConstants.KEY_PAGEDOWN},
        {268, InputConstants.KEY_HOME},
        {269, InputConstants.KEY_END},
        {280, InputConstants.KEY_CAPSLOCK},
        {281, InputConstants.KEY_SCROLLLOCK},
        {282, InputConstants.KEY_NUMLOCK},
        {283, InputConstants.KEY_PRINTSCREEN},
        {284, InputConstants.KEY_PAUSE},
        {330, InputConstants.KEY_NUMPADCOMMA},
        {332, InputConstants.KEY_MULTIPLY},
        {334, InputConstants.KEY_ADD},
        {335, InputConstants.KEY_NUMPADENTER},
        {336, InputConstants.KEY_NUMPADEQUALS},
        {340, InputConstants.KEY_LSHIFT},
        {341, InputConstants.KEY_LCONTROL},
        {342, InputConstants.KEY_LALT},
        {343, InputConstants.KEY_LGUI},
        {344, InputConstants.KEY_RSHIFT},
        {345, InputConstants.KEY_RCONTROL},
        {346, InputConstants.KEY_RALT},
        {347, InputConstants.KEY_RGUI}
    };

    /** Für die Config: Codes werden immer als GLFW-Code gespeichert (gleiche Datei für 26.2 und 26.3). */
    public static int toGlfw(int code) {
        if (code < 0) return code;
        for (int[] p : GLFW) if (p[1] == code) return p[0];
        return -1;
    }

    public static int fromGlfw(int glfw) {
        if (glfw < 0) return glfw;
        for (int[] p : GLFW) if (p[0] == glfw) return p[1];
        return -1;
    }
}
