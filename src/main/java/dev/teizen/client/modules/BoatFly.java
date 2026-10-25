package dev.teizen.client.modules;

import dev.teizen.client.TeizenClient;
import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;

/**
 * BoatFly: mit dem Boot fliegen. Das Boot fährt dahin, wo du hinschaust (W/S/A/D), Leertaste = hoch, Sprint-Taste
 * (Strg) = runter. Ohne Taste bleibt es in der Luft stehen. "antikick" verhindert den "Flying is not enabled"-Kick.
 */
public final class BoatFly extends Module {
    private final Setting speed = add(Setting.number("tempo", "Tempo (Blöcke pro Tick, 1 = 20 Blöcke/s)", 1.0, 0.1, 5));
    private final Setting up = add(Setting.number("hoch", "Tempo nach oben/unten (Blöcke pro Tick)", 0.5, 0.05, 3));
    private final Setting antiKick = add(Setting.bool("antikick", "Kurz absinken, damit der Server nicht wegen Fliegen kickt", true));

    private int ticks;

    public BoatFly() {
        super("BoatFly", Category.BEWEGUNG, "Mit dem Boot fliegen (Leertaste hoch, Strg runter).");
    }

    /** Steuert gerade BoatFly oder BoatNoclip dieses Boot? (Mixin: dann keine Boot-Physik) */
    public static boolean controls(Entity boat) {
        if (TeizenClient.modules() == null || mc.player == null || boat.getControllingPassenger() != mc.player) return false;
        BoatFly bf = TeizenClient.modules().get(BoatFly.class);
        BoatNoclip bn = TeizenClient.modules().get(BoatNoclip.class);
        return (bf != null && bf.isEnabled()) || (bn != null && bn.isEnabled());
    }

    /** Das Boot, das du gerade lenkst, oder null. */
    static AbstractBoat myBoat() {
        if (mc.player != null && mc.player.getVehicle() instanceof AbstractBoat b && b.getControllingPassenger() == mc.player) return b;
        return null;
    }

    /** Boot frei steuern (für BoatFly und BoatNoclip). */
    static void drive(AbstractBoat b, double speed, double vspeed, boolean antiKick, int tick) {
        float yaw = mc.player.getYRot();
        b.setYRot(yaw);
        b.yRotO = yaw;
        double fwd = (mc.options.keyUp.isDown() ? 1 : 0) - (mc.options.keyDown.isDown() ? 1 : 0);
        double side = (mc.options.keyLeft.isDown() ? 1 : 0) - (mc.options.keyRight.isDown() ? 1 : 0);
        double rad = Math.toRadians(yaw);
        double x = -Math.sin(rad) * fwd + Math.cos(rad) * side;
        double z = Math.cos(rad) * fwd + Math.sin(rad) * side;
        double len = Math.sqrt(x * x + z * z);
        if (len > 1e-6) { x = x / len * speed; z = z / len * speed; }
        double y = 0;
        if (mc.options.keyJump.isDown()) y = vspeed;
        else if (mc.options.keySprint.isDown()) y = -vspeed;
        else if (antiKick && tick % 40 == 0) y = -0.05;
        b.setDeltaMovement(x, y, z);
    }

    @Override
    public void onTick() {
        AbstractBoat b = myBoat();
        if (b == null) return;
        BoatNoclip bn = TeizenClient.modules().get(BoatNoclip.class);
        if (bn != null && bn.isEnabled()) return; // BoatNoclip steuert selbst
        drive(b, speed.num(), up.num(), antiKick.on(), ++ticks);
    }

    @Override
    public String info() { return myBoat() == null ? "§8(ins Boot)" : dev.teizen.client.core.Setting.fmt(speed.num()); }
}
