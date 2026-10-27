package dev.teizen.client.compat;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.PiercingWeapon;

/** Alles, was zwischen Minecraft 26.2 und 26.3 anders ist. Umsetzung: compat.v262.Impl262 / compat.v263.Impl263. */
public interface Impl {
    String mc();
    /** Server erlaubt mehrere Positions-Pakete pro Tick (26.2 ja, 26.3 nein). */
    boolean multiMovePackets();
    /** Schlüssel für Item-Modifier in /item modify (26.2: function, 26.3: type). */
    String fn();
    InputConstants.Type keyboard();
    void swing(InteractionHand hand);
    boolean isKeyDown(int code);
    void piercingAttack(ItemStack weapon, PiercingWeapon pw);
    float attackAnim(LivingEntity e, float partial);
    String layoutName(int code);
    /** Tasten-Code nach Name (gleiche Namen wie GLFW_KEY_..., z.B. "ESCAPE", "RIGHT_SHIFT", "MOD_SHIFT"). */
    int key(String name);
    int letter(char c);
    int digit(int d);
    int f(int i);
    int numpad(int d);
    int toGlfw(int code);
    int fromGlfw(int glfw);
}
