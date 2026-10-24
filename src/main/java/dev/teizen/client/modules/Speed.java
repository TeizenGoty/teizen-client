package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import net.minecraft.world.phys.Vec3;

/** Schneller laufen. */
public final class Speed extends Module {
    private final Setting factor = add(Setting.number("tempo", "Lauf-Tempo (1 = normal, 0.5 = halb so schnell, 2 = doppelt)", 1.8, 0.1, 5));
    private final Setting hop = add(Setting.bool("springen", "Automatisch hüpfen (noch schneller)", false));
    private final Setting elytra = add(Setting.bool("elytra", "Elytra-Boost: beim Gleiten nach vorne beschleunigen", true));
    private final Setting elytraSpeed = add(Setting.number("elytratempo", "Elytra-Höchsttempo in Blöcken/Sekunde", 40, 5, 100));
    private final Setting elytraBoost = add(Setting.number("elytraschub", "Wie stark der Elytra-Boost beschleunigt", 1, 0.1, 5));

    public Speed() {
        super("Speed", Category.BEWEGUNG, "Lauf-Tempo einstellen (schneller oder langsamer).");
    }

    @Override
    public void onTick() {
        if (!inGame() || mc.player.getAbilities().flying || mc.player.isPassenger()) return;
        if (mc.player.isFallFlying()) { elytraTick(); return; }
        var in = mc.player.input;
        boolean moving = mc.options.keyUp.isDown() || mc.options.keyDown.isDown() || mc.options.keyLeft.isDown() || mc.options.keyRight.isDown();
        if (!moving) return;
        if (mc.player.isInWater() || mc.player.isInLava()) return;
        if (hop.on() && mc.player.onGround()) mc.player.jumpFromGround();

        float yaw = mc.player.getYRot();
        double forward = (mc.options.keyUp.isDown() ? 1 : 0) - (mc.options.keyDown.isDown() ? 1 : 0);
        double strafe = (mc.options.keyLeft.isDown() ? 1 : 0) - (mc.options.keyRight.isDown() ? 1 : 0);
        double len = Math.sqrt(forward * forward + strafe * strafe);
        if (len == 0) return;
        forward /= len;
        strafe /= len;
        double base = mc.player.isSprinting() ? 0.2806 : 0.2158;
        double sp = base * factor.num() * (mc.player.onGround() ? 1.0 : 0.95);
        double rad = Math.toRadians(yaw);
        double mx = (-Math.sin(rad) * forward + Math.cos(rad) * strafe) * sp;
        double mz = (Math.cos(rad) * forward + Math.sin(rad) * strafe) * sp;
        Vec3 v = mc.player.getDeltaMovement();
        mc.player.setDeltaMovement(mx, v.y, mz);
    }

    /** Elytra-Boost: in Blickrichtung beschleunigen, solange "vorwärts" gedrückt ist (bis zum Höchsttempo). */
    private void elytraTick() {
        if (!elytra.on() || !mc.options.keyUp.isDown()) return;
        Vec3 look = mc.player.getLookAngle();
        Vec3 v = mc.player.getDeltaMovement();
        double max = elytraSpeed.num() / 20.0;
        Vec3 nv = v.add(look.scale(0.05 * elytraBoost.num()));
        if (nv.length() > max) nv = nv.normalize().scale(Math.max(max, v.length() * 0.99));
        mc.player.setDeltaMovement(nv);
    }

    @Override
    public String info() { return Setting.fmt(factor.num()) + "x"; }
}
