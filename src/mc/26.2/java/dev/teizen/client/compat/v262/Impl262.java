package dev.teizen.client.compat.v262;

import com.mojang.blaze3d.platform.InputConstants;
import dev.teizen.client.compat.Impl;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.PiercingWeapon;
import org.lwjgl.glfw.GLFW;

/** Versions-Code für Minecraft 26.2. */
public final class Impl262 implements Impl {
    public Impl262() {}

    @Override public String mc() { return "26.2"; }
    @Override public boolean multiMovePackets() { return true; }
    @Override public String fn() { return "function"; }
    @Override public InputConstants.Type keyboard() { return InputConstants.Type.KEYSYM; }

    @Override public void swing(InteractionHand hand) {
        Minecraft.getInstance().player.swing(hand);
    }

    @Override public boolean isKeyDown(int code) {
        if (code < 0) return false;
        return InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), code);
    }

    @Override public void piercingAttack(ItemStack weapon, PiercingWeapon pw) {
        Minecraft.getInstance().gameMode.piercingAttack(pw);
    }

    @Override public float attackAnim(LivingEntity e, float partial) {
        return e.getAttackAnim(partial);
    }

    @Override public String layoutName(int code) {
        try {
            return org.lwjgl.glfw.GLFW.glfwGetKeyName(code, 0);
        } catch (Throwable t) {
            return null;
        }
    }

    @Override public int key(String name) {
        return switch (name) {
            case "MOD_SHIFT" -> GLFW.GLFW_MOD_SHIFT;
            case "ESCAPE" -> GLFW.GLFW_KEY_ESCAPE;
            case "ENTER" -> GLFW.GLFW_KEY_ENTER;
            case "KP_ENTER" -> GLFW.GLFW_KEY_KP_ENTER;
            case "TAB" -> GLFW.GLFW_KEY_TAB;
            case "BACKSPACE" -> GLFW.GLFW_KEY_BACKSPACE;
            case "DELETE" -> GLFW.GLFW_KEY_DELETE;
            case "INSERT" -> GLFW.GLFW_KEY_INSERT;
            case "HOME" -> GLFW.GLFW_KEY_HOME;
            case "END" -> GLFW.GLFW_KEY_END;
            case "PAGE_UP" -> GLFW.GLFW_KEY_PAGE_UP;
            case "PAGE_DOWN" -> GLFW.GLFW_KEY_PAGE_DOWN;
            case "UP" -> GLFW.GLFW_KEY_UP;
            case "DOWN" -> GLFW.GLFW_KEY_DOWN;
            case "LEFT" -> GLFW.GLFW_KEY_LEFT;
            case "RIGHT" -> GLFW.GLFW_KEY_RIGHT;
            case "SPACE" -> GLFW.GLFW_KEY_SPACE;
            case "LEFT_SHIFT" -> GLFW.GLFW_KEY_LEFT_SHIFT;
            case "RIGHT_SHIFT" -> GLFW.GLFW_KEY_RIGHT_SHIFT;
            case "LEFT_CONTROL" -> GLFW.GLFW_KEY_LEFT_CONTROL;
            case "RIGHT_CONTROL" -> GLFW.GLFW_KEY_RIGHT_CONTROL;
            case "LEFT_ALT" -> GLFW.GLFW_KEY_LEFT_ALT;
            case "RIGHT_ALT" -> GLFW.GLFW_KEY_RIGHT_ALT;
            case "LEFT_SUPER" -> GLFW.GLFW_KEY_LEFT_SUPER;
            case "RIGHT_SUPER" -> GLFW.GLFW_KEY_RIGHT_SUPER;
            case "CAPS_LOCK" -> GLFW.GLFW_KEY_CAPS_LOCK;
            case "COMMA" -> GLFW.GLFW_KEY_COMMA;
            case "PERIOD" -> GLFW.GLFW_KEY_PERIOD;
            case "MINUS" -> GLFW.GLFW_KEY_MINUS;
            case "A" -> GLFW.GLFW_KEY_A;
            case "G" -> GLFW.GLFW_KEY_G;
            case "H" -> GLFW.GLFW_KEY_H;
            case "J" -> GLFW.GLFW_KEY_J;
            case "K" -> GLFW.GLFW_KEY_K;
            case "M" -> GLFW.GLFW_KEY_M;
            case "O" -> GLFW.GLFW_KEY_O;
            case "F1" -> GLFW.GLFW_KEY_F1;
            default -> throw new IllegalArgumentException("Taste " + name);
        };
    }


    @Override public int letter(char c) { return GLFW.GLFW_KEY_A + (Character.toLowerCase(c) - 'a'); }
    @Override public int digit(int d) { return GLFW.GLFW_KEY_0 + d; }
    @Override public int f(int i) { return GLFW.GLFW_KEY_F1 + i - 1; }
    @Override public int numpad(int d) { return GLFW.GLFW_KEY_KP_0 + d; }

    /** Für die Config: Codes werden immer als GLFW-Code gespeichert (gleiche Datei für 26.2 und 26.3). */
    @Override public int toGlfw(int code) { return code; }
    @Override public int fromGlfw(int glfw) { return glfw; }
}
