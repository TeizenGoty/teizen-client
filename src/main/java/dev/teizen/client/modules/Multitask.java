package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/** Multitask: gleichzeitig essen/trinken/Schild halten UND schlagen oder abbauen (normal geht das nicht). */
public final class Multitask extends Module {
    private boolean wasAttack;
    public int hits, mineTicks;

    public Multitask() {
        super("Multitask", Category.SONSTIGES, "Beim Essen oder Schild-Halten trotzdem schlagen und abbauen.");
    }

    @Override
    public void onTick() {
        if (!inGame() || mc.gui.screen() != null) { wasAttack = false; return; }
        boolean attack = mc.options.keyAttack.isDown();
        // Minecraft macht das selbst, solange man nichts benutzt
        if (!mc.player.isUsingItem() || !attack) { wasAttack = attack; return; }
        HitResult hr = mc.hitResult;
        if (hr instanceof EntityHitResult eh && hr.getType() == HitResult.Type.ENTITY) {
            if (!wasAttack || mc.player.getAttackStrengthScale(0.5f) >= 1f) {
                mc.gameMode.attack(mc.player, eh.getEntity());
                mc.player.swing(InteractionHand.MAIN_HAND);
                hits++;
            }
        } else if (hr instanceof BlockHitResult bh && hr.getType() == HitResult.Type.BLOCK && !mc.level.getBlockState(bh.getBlockPos()).isAir()) {
            if (!wasAttack) mc.gameMode.startDestroyBlock(bh.getBlockPos(), bh.getDirection());
            else mc.gameMode.continueDestroyBlock(bh.getBlockPos(), bh.getDirection());
            mc.player.swing(InteractionHand.MAIN_HAND);
            mineTicks++;
        }
        wasAttack = attack;
    }
}
