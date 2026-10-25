package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import net.minecraft.world.entity.player.Abilities;

/** Fliegen wie im Kreativmodus. */
public final class Fly extends Module {
    private final Setting speed = add(Setting.number("tempo", "Flug-Tempo (1 = wie Kreativ, 0.5 = halb so schnell)", 2, 0.1, 10));
    private final Setting antiKick = add(Setting.bool("antikick", "Kleine Abwärtsbewegung gegen Fly-Kick", true));
    private final Setting noFall = add(Setting.bool("fallschutz", "Kein Fallschaden beim Landen und nach dem Ausschalten in der Luft", true));
    /** Nach dem Ausschalten in der Luft: bis zur Landung vor Fallschaden schützen. */
    private boolean guardUntilGround;
    private double lastY = Double.NaN;
    public int protectedPackets;
    private int ticks;
    private boolean hadMayfly;

    public Fly() {
        super("Fly", Category.BEWEGUNG, "Du kannst fliegen wie im Kreativmodus.");
    }

    @Override
    protected void onEnable() {
        if (!inGame()) return;
        hadMayfly = mc.player.getAbilities().mayfly;
    }

    @Override
    protected void onDisable() {
        if (!inGame()) return;
        guardUntilGround = noFall.on() && !mc.player.onGround();
        Abilities a = mc.player.getAbilities();
        if (!mc.player.isCreative() && !mc.player.isSpectator()) {
            a.flying = false;
            a.mayfly = hadMayfly;
        }
        a.setFlyingSpeed(0.05f);
    }

    @Override
    public void onTick() {
        if (!inGame()) return;
        Abilities a = mc.player.getAbilities();
        a.mayfly = true;
        a.flying = true;
        a.setFlyingSpeed((float) (0.05 * speed.num()));
        // Beim Runterfliegen dem Server sagen, dass wir stehen: so sammelt sich keine Fallhöhe an -> kein Fallschaden beim Landen
        double y = mc.player.getY();
        if (noFall.on() && !Double.isNaN(lastY) && y < lastY - 0.001 && !mc.player.isCreative()) groundPacket();
        lastY = y;
        if (antiKick.on() && ++ticks % 40 == 0 && !mc.player.onGround()) {
            var v = mc.player.getDeltaMovement();
            mc.player.setDeltaMovement(v.x, v.y - 0.04, v.z);
        }
    }

    @Override
    public String info() { return Setting.fmt(speed.num()); }

    private void groundPacket() {
        mc.player.connection.send(new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.StatusOnly(true, mc.player.horizontalCollision));
        protectedPackets++;
    }

    /** Läuft jeden Tick (auch wenn Fly aus ist): Schutz nach dem Ausschalten in der Luft. */
    public void fallGuard() {
        if (!guardUntilGround || isEnabled() || !inGame()) return;
        if (mc.player.onGround() || mc.player.isInWater() || mc.player.getAbilities().flying) { guardUntilGround = false; return; }
        if (mc.player.fallDistance > 1.5) {
            groundPacket();
            mc.player.fallDistance = 0;
        }
    }
}
