package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import net.minecraft.world.phys.Vec3;

/** Schwimmen wie ein Delfin: schneller schwimmen und automatisch auftauchen (Schleichen = tauchen). */
public final class DolphinMode extends Module {
    private final Setting speed = add(Setting.number("tempo", "Schwimm-Tempo (1 = schnell, 2 = wie mit Delfin-Gunst)", 1.5, 0.3, 6));
    private final Setting surface = add(Setting.bool("auftauchen", "Automatisch an die Oberfläche schwimmen", true));
    private final Setting jump = add(Setting.bool("springen", "Wie ein Delfin aus dem Wasser springen", false));

    public DolphinMode() {
        super("DolphinMode", Category.BEWEGUNG, "Schnell schwimmen und automatisch auftauchen.");
    }

    @Override
    public void onTick() {
        if (!inGame() || !mc.player.isInWater() || mc.player.getAbilities().flying || mc.player.isPassenger()) return;
        Vec3 v = mc.player.getDeltaMovement();
        double f = (mc.options.keyUp.isDown() ? 1 : 0) - (mc.options.keyDown.isDown() ? 1 : 0);
        double s = (mc.options.keyLeft.isDown() ? 1 : 0) - (mc.options.keyRight.isDown() ? 1 : 0);
        double sp = 0.25 * speed.num();
        if (f != 0 || s != 0) {
            double yaw = Math.toRadians(mc.player.getYRot());
            Vec3 fwd = mc.player.isSwimming() ? mc.player.getLookAngle() : new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
            Vec3 left = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
            Vec3 dir = fwd.scale(f).add(left.scale(s)).normalize().scale(sp);
            v = mc.player.isSwimming() ? dir : new Vec3(dir.x, v.y, dir.z);
        }
        boolean sneak = mc.options.keyShift.isDown();
        if (surface.on() && !sneak && !mc.player.isSwimming()) {
            if (mc.player.isUnderWater()) v = new Vec3(v.x, Math.max(v.y, 0.12), v.z);
            else if (jump.on() && (f != 0 || s != 0)) v = new Vec3(v.x, Math.max(v.y, 0.42), v.z);
            else v = new Vec3(v.x, Math.max(v.y, 0.0), v.z);
        }
        mc.player.setDeltaMovement(v);
    }

    @Override
    public String info() { return Setting.fmt(speed.num()); }
}
