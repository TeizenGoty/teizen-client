package dev.teizen.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Blöcke setzen: sucht einen Nachbarblock, an den geklickt werden kann. */
public final class Placer {
    private static final Minecraft mc = Minecraft.getInstance();

    private Placer() {}

    public static boolean replaceable(BlockPos pos) {
        BlockState s = mc.level.getBlockState(pos);
        return s.canBeReplaced();
    }

    /** Ein Nachbar, an dessen Seite man klicken kann, um {@code pos} zu füllen. */
    public static Direction findSupport(BlockPos pos) {
        for (Direction d : new Direction[]{Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP}) {
            BlockPos n = pos.relative(d);
            BlockState s = mc.level.getBlockState(n);
            if (!s.canBeReplaced() && !s.isAir()) return d;
        }
        return null;
    }

    /**
     * Klickt mit dem aktuellen Item an die Seite des Nachbarn {@code pos.relative(supportDir)}.
     * @param hitYOffset 0..1 Höhe des Klickpunkts (wichtig für obere/untere Stufen)
     */
    public static boolean placeAgainst(BlockPos pos, Direction supportDir, double hitYOffset, boolean rotate) {
        BlockPos neighbor = pos.relative(supportDir);
        Direction face = supportDir.getOpposite();
        Vec3 hit = Vec3.atCenterOf(neighbor).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
        if (face.getAxis() != Direction.Axis.Y) hit = new Vec3(hit.x, neighbor.getY() + hitYOffset, hit.z);
        if (rotate) {
            float[] r = Rot.to(hit);
            Rot.sendSilent(r[0], r[1]);
        }
        BlockHitResult bhr = new BlockHitResult(hit, face, neighbor, false);
        InteractionResult res = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, bhr);
        if (res.consumesAction()) {
            mc.player.swing(InteractionHand.MAIN_HAND);
            return true;
        }
        return false;
    }

    public static boolean place(BlockPos pos, boolean rotate) {
        if (!replaceable(pos)) return false;
        Direction d = findSupport(pos);
        if (d == null) return false;
        return placeAgainst(pos, d, 0.5, rotate);
    }
}
