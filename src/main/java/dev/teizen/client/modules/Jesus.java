package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** Über Wasser und Lava laufen. */
public final class Jesus extends Module {
    public Jesus() {
        super("Jesus", Category.BEWEGUNG, "Du läufst über Wasser und Lava (Schleichen = untertauchen).");
    }

    @Override
    public void onTick() {
        if (!inGame() || mc.player.isShiftKeyDown() || mc.player.getAbilities().flying) return;
        boolean inFluid = mc.player.isInWater() || mc.player.isInLava();
        Vec3 v = mc.player.getDeltaMovement();
        if (inFluid) {
            // nach oben drücken, bis wir auf der Oberfläche sind
            mc.player.setDeltaMovement(v.x, Math.max(v.y, 0.11), v.z);
            return;
        }
        BlockPos below = BlockPos.containing(mc.player.getX(), mc.player.getY() - 0.05, mc.player.getZ());
        boolean fluidBelow = !mc.level.getFluidState(below).isEmpty();
        if (fluidBelow && v.y < 0) {
            mc.player.setDeltaMovement(v.x, 0, v.z);
            mc.player.setOnGround(true);
            mc.player.fallDistance = 0;
        }
    }
}
