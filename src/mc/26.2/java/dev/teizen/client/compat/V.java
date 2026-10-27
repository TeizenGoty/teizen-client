package dev.teizen.client.compat;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.PiercingWeapon;

/** Was sich zwischen Minecraft 26.2 und 26.3 geändert hat – hier die 26.2-Version. */
public final class V {
    private V() {}

    public static final String MC = "26.2";
    public static final InputConstants.Type KEYBOARD = InputConstants.Type.KEYSYM;

    /** Arm schwingen (auch für den Server). */
    public static void swing(InteractionHand hand) {
        Minecraft.getInstance().player.swing(hand);
    }

    public static boolean isKeyDown(int code) {
        return InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), code);
    }

    public static void piercingAttack(ItemStack weapon, PiercingWeapon pw) {
        Minecraft.getInstance().gameMode.piercingAttack(pw);
    }

    /** Wie weit der Schlag-Arm gerade ist (0 = kein Schlag). */
    public static float attackAnim(LivingEntity e, float partial) {
        return e.getAttackAnim(partial);
    }

    /** Name der Taste in der Tastatur-Belegung (z.B. "Z" auf deutscher Tastatur), oder null. */
    public static String layoutName(int code) {
        try {
            return org.lwjgl.glfw.GLFW.glfwGetKeyName(code, 0);
        } catch (Throwable t) {
            return null;
        }
    }
}
