package dev.teizen.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Blöcke abbauen (über die normalen Spiel-Funktionen, inkl. Werkzeugwahl). */
public final class Breaker {
    private static final Minecraft mc = Minecraft.getInstance();
    private static BlockPos current;

    private Breaker() {}

    public static boolean canBreak(BlockPos pos) {
        BlockState s = mc.level.getBlockState(pos);
        if (s.isAir() || !s.getFluidState().isEmpty() && s.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock) return false;
        float h = s.getDestroySpeed(mc.level, pos);
        return h >= 0; // -1 = unzerstörbar (Grundgestein)
    }

    public static boolean inReach(BlockPos pos) {
        double r = mc.player.blockInteractionRange();
        return mc.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) <= r * r;
    }

    public static Direction face(BlockPos pos) {
        Vec3 eye = mc.player.getEyePosition();
        Vec3 c = Vec3.atCenterOf(pos);
        return Direction.getApproximateNearest(eye.x - c.x, eye.y - c.y, eye.z - c.z);
    }

    /**
     * Einen Tick lang an einem Block abbauen.
     * @param rotate Server-Blickrichtung setzen
     * @return true wenn der Block weg ist
     */
    public static boolean tick(BlockPos pos, boolean rotate, boolean autoTool) {
        BlockState s = mc.level.getBlockState(pos);
        if (s.isAir()) { current = null; return true; }
        if (autoTool) {
            int t = Inv.bestTool(s);
            if (t >= 0) Inv.select(t);
        }
        Direction dir = face(pos);
        if (rotate) {
            float[] r = Rot.to(Vec3.atCenterOf(pos));
            Rot.sendSilent(r[0], r[1]);
        }
        if (!pos.equals(current) || !mc.gameMode.isDestroying()) {
            mc.gameMode.startDestroyBlock(pos, dir);
            current = pos.immutable();
        } else {
            mc.gameMode.continueDestroyBlock(pos, dir);
        }
        mc.player.swing(InteractionHand.MAIN_HAND);
        return mc.level.getBlockState(pos).isAir();
    }

    public static void stop() {
        if (current != null && mc.gameMode != null) mc.gameMode.stopDestroyBlock();
        current = null;
    }
}
