package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * EntityControl: Reittiere und Boote besser steuern – schneller, immer voller Pferde-Sprung, in Blickrichtung lenken.
 * Hinweis: auf normalen Servern braucht ein Pferd trotzdem einen Sattel (sonst bewegt es der Server nicht).
 */
public final class EntityControl extends Module {
    private final Setting speed = add(Setting.number("tempo", "Tempo (Blöcke pro Tick, 0 = normal lassen)", 0.6, 0, 2));
    private final Setting jump = add(Setting.number("sprung", "Pferde-Sprung (0 = normal, 1 = sehr hoch)", 0.9, 0, 2));
    private final Setting look = add(Setting.bool("blick", "Immer in Blickrichtung lenken", true));
    public int boosted;

    public EntityControl() {
        super("EntityControl", Category.BEWEGUNG, "Reittiere/Boote schneller, voller Pferde-Sprung, in Blickrichtung lenken.");
    }

    @Override
    public void onTick() {
        if (!inGame()) return;
        Entity v = mc.player.getVehicle();
        if (v == null || v.getControllingPassenger() != mc.player) return;
        if (look.on()) { v.setYRot(mc.player.getYRot()); }
        Vec3 d = v.getDeltaMovement();
        if (speed.num() > 0 && mc.options.keyUp.isDown()) {
            double yaw = Math.toRadians(mc.player.getYRot());
            double s = speed.num();
            d = new Vec3(-Math.sin(yaw) * s, d.y, Math.cos(yaw) * s);
            boosted++;
        }
        if (jump.num() > 0 && mc.options.keyJump.isDown() && v.onGround() && !(v instanceof net.minecraft.world.entity.vehicle.boat.AbstractBoat)) {
            d = new Vec3(d.x, jump.num(), d.z);
        }
        v.setDeltaMovement(d);
    }
}
