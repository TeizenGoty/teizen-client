package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import net.minecraft.world.phys.Vec3;

/** Schneller laufen. */
public final class Speed extends Module {
    private final Setting factor = add(Setting.number("tempo", "Wie viel schneller (1 = normal)", 1.8, 1, 5));
    private final Setting hop = add(Setting.bool("springen", "Automatisch hüpfen (noch schneller)", false));

    public Speed() {
        super("Speed", Category.BEWEGUNG, "Du läufst viel schneller.");
    }

    @Override
    public void onTick() {
        if (!inGame() || mc.player.getAbilities().flying || mc.player.isPassenger()) return;
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

    @Override
    public String info() { return Setting.fmt(factor.num()) + "x"; }
}
