package dev.teizen.client.modules;

import dev.teizen.client.TeizenClient;
import dev.teizen.client.core.Module;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** GhostHand: Kisten, Fässer usw. durch Wände öffnen (Rechtsklick auf die Wand davor). */
public final class GhostHand extends Module {
    public int opened;

    public GhostHand() {
        super("GhostHand", Category.SONSTIGES, "Kisten und Fässer durch Wände öffnen.");
    }

    private static boolean container(BlockPos p) {
        var be = mc.level.getBlockEntity(p);
        return be instanceof net.minecraft.world.Container || be instanceof net.minecraft.world.level.block.entity.EnderChestBlockEntity;
    }

    /** Mixin (Minecraft.startUseItem): true = erledigt. */
    public static boolean onUse() {
        if (TeizenClient.modules() == null || mc.player == null || mc.level == null) return false;
        GhostHand g = TeizenClient.modules().get(GhostHand.class);
        if (g == null || !g.isEnabled() || mc.player.isShiftKeyDown()) return false;
        HitResult hr = mc.hitResult;
        if (hr instanceof BlockHitResult bh && hr.getType() == HitResult.Type.BLOCK && container(bh.getBlockPos())) return false; // normal
        double reach = mc.player.blockInteractionRange();
        Vec3 eye = mc.player.getEyePosition(), dir = mc.player.getViewVector(1f);
        BlockPos last = null;
        for (double d = 0; d <= reach; d += 0.1) {
            Vec3 at = eye.add(dir.scale(d));
            BlockPos p = BlockPos.containing(at);
            if (p.equals(last)) continue;
            last = p;
            if (container(p)) {
                BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(p), net.minecraft.core.Direction.getApproximateNearest(-dir.x, -dir.y, -dir.z), p, false);
                mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
                dev.teizen.client.compat.V.swing(InteractionHand.MAIN_HAND);
                g.opened++;
                return true;
            }
        }
        return false;
    }
}
