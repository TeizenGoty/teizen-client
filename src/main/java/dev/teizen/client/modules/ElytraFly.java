package dev.teizen.client.modules;

import dev.teizen.client.TeizenClient;
import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * ElytraFly: mit der Elytra fliegen wie mit Fly – ohne Raketen. Sobald die Elytra offen ist: W/A/S/D = in Blickrichtung,
 * Leertaste = hoch, Schleichen = runter. Ohne Taste bleibst du in der Luft stehen.
 */
public final class ElytraFly extends Module {
    private final Setting speed = add(Setting.number("tempo", "Tempo (Blöcke pro Tick, 1 = 20 Blöcke/s)", 1.5, 0.1, 5));
    private final Setting up = add(Setting.number("hoch", "Tempo nach oben/unten (Blöcke pro Tick)", 0.8, 0.05, 3));
    private final Setting hover = add(Setting.bool("schweben", "Ohne Taste in der Luft stehen bleiben", true));
    private final Setting pitch = add(Setting.bool("blickhoehe", "Beim Vorwärtsfliegen auch der Blick-Höhe folgen (hoch/runter schauen)", false));
    private final Setting auto = add(Setting.bool("auto", "Elytra in der Luft von selbst öffnen (dann kann man sofort schweben)", true));

    public ElytraFly() {
        super("ElytraFly", Category.BEWEGUNG, "Elytra-Flug ohne Raketen: fliegen wie mit Fly.");
    }

    @Override
    public void onTick() {
        if (!inGame() || !auto.on()) return;
        var p = mc.player;
        boolean hasElytra = p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).is(net.minecraft.world.item.Items.ELYTRA);
        if (hasElytra && !p.onGround() && !p.isFallFlying() && !p.isInWater() && !p.isInLava() && !p.getAbilities().flying) {
            p.connection.send(new net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket(
                    p, net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
        }
    }

    /** Mixin (Elytra-Physik): unsere Geschwindigkeit statt der normalen, oder null. */
    public static Vec3 velocity(LivingEntity e) {
        if (TeizenClient.modules() == null || e != mc.player) return null;
        ElytraFly m = TeizenClient.modules().get(ElytraFly.class);
        if (m == null || !m.isEnabled()) return null;
        return m.wanted();
    }

    private Vec3 wanted() {
        float yaw = mc.player.getYRot();
        double fwd = (mc.options.keyUp.isDown() ? 1 : 0) - (mc.options.keyDown.isDown() ? 1 : 0);
        double side = (mc.options.keyLeft.isDown() ? 1 : 0) - (mc.options.keyRight.isDown() ? 1 : 0);
        double rad = Math.toRadians(yaw);
        double x = -Math.sin(rad) * fwd + Math.cos(rad) * side;
        double z = Math.cos(rad) * fwd + Math.sin(rad) * side;
        double y = 0;
        if (pitch.on() && fwd != 0) y = -Math.sin(Math.toRadians(mc.player.getXRot())) * fwd;
        double len = Math.sqrt(x * x + z * z + y * y);
        if (len > 1e-6) { double s = speed.num() / len; x *= s; y *= s; z *= s; }
        if (mc.options.keyJump.isDown()) y = up.num();
        else if (mc.options.keyShift.isDown()) y = -up.num();
        if (len < 1e-6 && y == 0 && !hover.on()) return mc.player.getDeltaMovement().multiply(0.98, 1, 0.98).add(0, -0.04, 0);
        return new Vec3(x, y, z);
    }

    @Override
    public String info() { return mc.player != null && mc.player.isFallFlying() ? Setting.fmt(speed.num()) : "§8(Elytra auf)"; }
}
