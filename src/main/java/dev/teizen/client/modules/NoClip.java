package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.util.Breaker;
import dev.teizen.client.util.Chat;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * NoClip: durch Wände.
 * - Modus "abbauen" (ohne OP): du fliegst (Fly) und alles, was dir im Weg ist, wird automatisch abgebaut – du gräbst dich
 *   in jede Richtung durch, in die du fliegst (auch hoch/runter mit Leertaste/Shift).
 * - Modus "zuschauer" (braucht OP): echter Zuschauer-Modus, durch alles hindurch.
 */
public final class NoClip extends Module {
    private final Setting mode = add(Setting.choice("modus", "abbauen = ohne OP: alles im Weg wird abgebaut, zuschauer = echter Zuschauer-Modus (OP)",
            "abbauen", "abbauen", "zuschauer"));
    private final Setting speed = add(Setting.number("tempo", "Flug-Tempo im Zuschauer-Modus (1 = normal)", 1, 0.1, 10));
    private GameType previous;
    private int enabledTick;
    private boolean warned, flyByUs;
    private BlockPos digging;
    private int digTicks;
    /** Für den Selbsttest: so viele Blöcke im Weg abgebaut. */
    public int dug;

    public NoClip() {
        super("NoClip", Category.BEWEGUNG, "Durch Wände: ohne OP gräbt es sich durch (alles im Weg wird abgebaut), mit OP Zuschauer-Modus.");
    }

    private boolean spectatorMode() { return mode.is("zuschauer"); }

    @Override
    protected void onEnable() {
        warned = false;
        previous = null;
        digging = null;
        flyByUs = false;
        if (!inGame()) return;
        if (spectatorMode()) startSpectator();
        else startDig();
    }

    private void startSpectator() {
        previous = mc.gameMode.getPlayerMode();
        enabledTick = mc.player.tickCount;
        if (previous != GameType.SPECTATOR) mc.player.connection.sendCommand("gamemode spectator");
    }

    private void startDig() {
        Fly fly = dev.teizen.client.TeizenClient.modules().get(Fly.class);
        if (fly != null && !fly.isEnabled()) { fly.setEnabled(true); flyByUs = true; }
    }

    @Override
    protected void onDisable() {
        if (digging != null) Breaker.stop();
        digging = null;
        if (!inGame()) return;
        if (flyByUs) {
            Fly fly = dev.teizen.client.TeizenClient.modules().get(Fly.class);
            if (fly != null && fly.isEnabled()) fly.setEnabled(false);
            flyByUs = false;
        }
        if (previous != null) {
            mc.player.getAbilities().setFlyingSpeed(0.05f);
            if (previous != GameType.SPECTATOR && mc.gameMode.getPlayerMode() == GameType.SPECTATOR)
                mc.player.connection.sendCommand("gamemode " + previous.getName());
        }
        previous = null;
    }

    /** Richtung, in die man gerade fliegen will (aus den Tasten + Blickrichtung). */
    private Vec3 wish() {
        var o = mc.options;
        double f = (o.keyUp.isDown() ? 1 : 0) - (o.keyDown.isDown() ? 1 : 0);
        double s = (o.keyLeft.isDown() ? 1 : 0) - (o.keyRight.isDown() ? 1 : 0);
        double u = (o.keyJump.isDown() ? 1 : 0) - (o.keyShift.isDown() ? 1 : 0);
        double yaw = Math.toRadians(mc.player.getYRot());
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);
        double sx = fz, sz = -fx; // links
        Vec3 v = new Vec3(fx * f + sx * s, u, fz * f + sz * s);
        // vorwärts + nach oben/unten schauen = auch schräg graben
        if (f != 0 && u == 0) {
            double pitch = mc.player.getXRot();
            if (pitch > 45) v = v.add(0, -1, 0);
            else if (pitch < -45) v = v.add(0, 1, 0);
        }
        return v.lengthSqr() < 1e-6 ? Vec3.ZERO : v.normalize();
    }

    @Override
    public void onTick() {
        if (!inGame()) return;
        if (spectatorMode()) {
            if (previous == null) { startSpectator(); return; }
            if (mc.gameMode.getPlayerMode() == GameType.SPECTATOR) {
                mc.player.getAbilities().setFlyingSpeed((float) (0.05 * speed.num()));
            } else if (!warned && mc.player.tickCount - enabledTick > 60) {
                warned = true;
                Chat.error("NoClip Zuschauer-Modus braucht OP. Ohne OP: ///set noclip modus abbauen");
            }
            return;
        }
        if (mc.gui.screen() != null) return;
        Vec3 dir = wish();
        if (digging != null) {
            if (!Breaker.canBreak(digging) || !Breaker.inReach(digging) || ++digTicks > 400) { Breaker.stop(); digging = null; }
        }
        if (digging == null) {
            if (dir == Vec3.ZERO) return;
            // Blöcke, die die Hitbox in Bewegungsrichtung berühren würde (ein Stück voraus)
            AABB box = mc.player.getBoundingBox();
            AABB ahead = box.move(dir.scale(0.6)).inflate(-0.05);
            BlockPos best = null;
            double bd = Double.MAX_VALUE;
            Vec3 eye = mc.player.getEyePosition();
            for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(ahead.minX, ahead.minY, ahead.minZ), BlockPos.containing(ahead.maxX, ahead.maxY, ahead.maxZ))) {
                var s = mc.level.getBlockState(p);
                if (s.getCollisionShape(mc.level, p).isEmpty() || !Breaker.canBreak(p)) continue;
                // nie die Flüssigkeit daneben freilegen? -> Lava ist gefährlich: nur graben, wenn keine Lava direkt dahinter
                if (lavaNext(p)) continue;
                double d = eye.distanceToSqr(Vec3.atCenterOf(p));
                if (d < bd) { bd = d; best = p.immutable(); }
            }
            if (best == null) return;
            digging = best;
            digTicks = 0;
        }
        if (Breaker.tick(digging, true, true)) { dug++; digging = null; }
    }

    private boolean lavaNext(BlockPos p) {
        for (var d : net.minecraft.core.Direction.values())
            if (mc.level.getFluidState(p.relative(d)).is(net.minecraft.tags.FluidTags.LAVA)) return true;
        return false;
    }

    @Override
    public String info() { return spectatorMode() ? "Zuschauer" : "gräbt"; }
}
