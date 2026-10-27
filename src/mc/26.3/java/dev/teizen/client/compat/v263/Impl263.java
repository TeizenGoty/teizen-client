package dev.teizen.client.compat.v263;

import com.mojang.blaze3d.platform.InputConstants;
import dev.teizen.client.compat.Impl;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.PiercingWeapon;
import net.minecraft.world.item.component.SwingAnimation;

/** Versions-Code für Minecraft 26.3. */
public final class Impl263 implements Impl {
    public Impl263() {}

    @Override public String mc() { return "26.3"; }
    @Override public boolean multiMovePackets() { return false; }
    @Override public String fn() { return "type"; }
    @Override public InputConstants.Type keyboard() { return InputConstants.Type.KEYBOARD; }

    @Override public void swing(InteractionHand hand) {
        Minecraft mc = Minecraft.getInstance();
        ItemStack s = mc.player.getItemInHand(hand);
        mc.player.swing(hand, s.getOrDefault(DataComponents.ATTACK_ANIMATION, SwingAnimation.DEFAULT), false);
    }

    @Override public boolean isKeyDown(int code) {
        if (code < 0) return false;
        return InputConstants.isKeyDown(code);
    }

    @Override public void piercingAttack(ItemStack weapon, PiercingWeapon pw) {
        Minecraft.getInstance().gameMode.piercingAttack(weapon.getOrDefault(DataComponents.ATTACK_ANIMATION, SwingAnimation.DEFAULT), pw);
    }

    @Override public float attackAnim(LivingEntity e, float partial) {
        return e.getSwingAnimation(partial);
    }

    @Override public String layoutName(int code) {
        try {
            String n = InputConstants.Type.KEYBOARD.getOrCreate(code).getDisplayName().getString();
            return n == null || n.isBlank() || n.startsWith("key.") ? null : n;
        } catch (Throwable t) {
            return null;
        }
    }

    @Override public int key(String name) {
        return switch (name) {
            case "MOD_SHIFT" -> InputConstants.MOD_SHIFT;
            case "ESCAPE" -> InputConstants.KEY_ESCAPE;
            case "ENTER" -> InputConstants.KEY_RETURN;
            case "KP_ENTER" -> InputConstants.KEY_NUMPADENTER;
            case "TAB" -> InputConstants.KEY_TAB;
            case "BACKSPACE" -> InputConstants.KEY_BACKSPACE;
            case "DELETE" -> InputConstants.KEY_DELETE;
            case "INSERT" -> InputConstants.KEY_INSERT;
            case "HOME" -> InputConstants.KEY_HOME;
            case "END" -> InputConstants.KEY_END;
            case "PAGE_UP" -> InputConstants.KEY_PAGEUP;
            case "PAGE_DOWN" -> InputConstants.KEY_PAGEDOWN;
            case "UP" -> InputConstants.KEY_UP;
            case "DOWN" -> InputConstants.KEY_DOWN;
            case "LEFT" -> InputConstants.KEY_LEFT;
            case "RIGHT" -> InputConstants.KEY_RIGHT;
            case "SPACE" -> InputConstants.KEY_SPACE;
            case "LEFT_SHIFT" -> InputConstants.KEY_LSHIFT;
            case "RIGHT_SHIFT" -> InputConstants.KEY_RSHIFT;
            case "LEFT_CONTROL" -> InputConstants.KEY_LCONTROL;
            case "RIGHT_CONTROL" -> InputConstants.KEY_RCONTROL;
            case "LEFT_ALT" -> InputConstants.KEY_LALT;
            case "RIGHT_ALT" -> InputConstants.KEY_RALT;
            case "LEFT_SUPER" -> InputConstants.KEY_LGUI;
            case "RIGHT_SUPER" -> InputConstants.KEY_RGUI;
            case "CAPS_LOCK" -> InputConstants.KEY_CAPSLOCK;
            case "COMMA" -> InputConstants.KEY_COMMA;
            case "PERIOD" -> InputConstants.KEY_PERIOD;
            case "MINUS" -> InputConstants.KEY_MINUS;
            case "A" -> InputConstants.KEY_A;
            case "G" -> InputConstants.KEY_G;
            case "H" -> InputConstants.KEY_H;
            case "J" -> InputConstants.KEY_J;
            case "K" -> InputConstants.KEY_K;
            case "M" -> InputConstants.KEY_M;
            case "O" -> InputConstants.KEY_O;
            case "F1" -> InputConstants.KEY_F1;
            default -> throw new IllegalArgumentException("Taste " + name);
        };
    }


    private static final int[] LETTERS = {InputConstants.KEY_A,InputConstants.KEY_B,InputConstants.KEY_C,InputConstants.KEY_D,InputConstants.KEY_E,InputConstants.KEY_F,InputConstants.KEY_G,InputConstants.KEY_H,InputConstants.KEY_I,InputConstants.KEY_J,InputConstants.KEY_K,InputConstants.KEY_L,InputConstants.KEY_M,InputConstants.KEY_N,InputConstants.KEY_O,InputConstants.KEY_P,InputConstants.KEY_Q,InputConstants.KEY_R,InputConstants.KEY_S,InputConstants.KEY_T,InputConstants.KEY_U,InputConstants.KEY_V,InputConstants.KEY_W,InputConstants.KEY_X,InputConstants.KEY_Y,InputConstants.KEY_Z};
    private static final int[] DIGITS = {InputConstants.KEY_0,InputConstants.KEY_1,InputConstants.KEY_2,InputConstants.KEY_3,InputConstants.KEY_4,InputConstants.KEY_5,InputConstants.KEY_6,InputConstants.KEY_7,InputConstants.KEY_8,InputConstants.KEY_9};
    private static final int[] FS = {InputConstants.KEY_F1,InputConstants.KEY_F2,InputConstants.KEY_F3,InputConstants.KEY_F4,InputConstants.KEY_F5,InputConstants.KEY_F6,InputConstants.KEY_F7,InputConstants.KEY_F8,InputConstants.KEY_F9,InputConstants.KEY_F10,InputConstants.KEY_F11,InputConstants.KEY_F12,InputConstants.KEY_F13,InputConstants.KEY_F14,InputConstants.KEY_F15,InputConstants.KEY_F16,InputConstants.KEY_F17,InputConstants.KEY_F18,InputConstants.KEY_F19,InputConstants.KEY_F20,InputConstants.KEY_F21,InputConstants.KEY_F22,InputConstants.KEY_F23,InputConstants.KEY_F24};
    private static final int[] NUMPAD = {InputConstants.KEY_NUMPAD0,InputConstants.KEY_NUMPAD1,InputConstants.KEY_NUMPAD2,InputConstants.KEY_NUMPAD3,InputConstants.KEY_NUMPAD4,InputConstants.KEY_NUMPAD5,InputConstants.KEY_NUMPAD6,InputConstants.KEY_NUMPAD7,InputConstants.KEY_NUMPAD8,InputConstants.KEY_NUMPAD9};

    @Override public int letter(char c) { return LETTERS[Character.toLowerCase(c) - 'a']; }
    @Override public int digit(int d) { return DIGITS[d]; }
    @Override public int f(int i) { return FS[i - 1]; }
    @Override public int numpad(int d) { return NUMPAD[d]; }

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
    @Override public int toGlfw(int code) {
        if (code < 0) return code;
        for (int[] p : GLFW) if (p[1] == code) return p[0];
        return -1;
    }

    @Override public int fromGlfw(int glfw) {
        if (glfw < 0) return glfw;
        for (int[] p : GLFW) if (p[0] == glfw) return p[1];
        return -1;
    }
}
