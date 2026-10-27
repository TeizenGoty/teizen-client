package dev.teizen.client.compat;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.PiercingWeapon;
import net.minecraft.world.item.component.SwingAnimation;

/** Was sich zwischen Minecraft 26.2 und 26.3 geändert hat – hier die 26.3-Version. */
public final class V {
    private V() {}

    public static final String MC = "26.3";
    public static final InputConstants.Type KEYBOARD = InputConstants.Type.KEYBOARD;

    /** Arm schwingen. In 26.3 schwingt der Server beim Schlagen selbst. */
    public static void swing(InteractionHand hand) {
        Minecraft mc = Minecraft.getInstance();
        ItemStack s = mc.player.getItemInHand(hand);
        mc.player.swing(hand, s.getOrDefault(DataComponents.ATTACK_ANIMATION, SwingAnimation.DEFAULT), false);
    }

    public static boolean isKeyDown(int code) {
        return InputConstants.isKeyDown(code);
    }

    public static void piercingAttack(ItemStack weapon, PiercingWeapon pw) {
        Minecraft.getInstance().gameMode.piercingAttack(weapon.getOrDefault(DataComponents.ATTACK_ANIMATION, SwingAnimation.DEFAULT), pw);
    }

    /** Wie weit der Schlag-Arm gerade ist (0 = kein Schlag). */
    public static float attackAnim(LivingEntity e, float partial) {
        return e.getSwingAnimation(partial);
    }

    /** Name der Taste in der Tastatur-Belegung, oder null. */
    public static String layoutName(int code) {
        try {
            String n = InputConstants.Type.KEYBOARD.getOrCreate(code).getDisplayName().getString();
            return n == null || n.isBlank() || n.startsWith("key.") ? null : n;
        } catch (Throwable t) {
            return null;
        }
    }
}
