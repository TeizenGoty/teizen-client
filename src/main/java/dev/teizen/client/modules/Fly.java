package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import net.minecraft.world.entity.player.Abilities;

/** Fliegen wie im Kreativmodus. */
public final class Fly extends Module {
    private final Setting speed = add(Setting.number("tempo", "Flug-Tempo (1 = wie Kreativ, 0.5 = halb so schnell)", 2, 0.1, 10));
    private final Setting antiKick = add(Setting.bool("antikick", "Kleine Abwärtsbewegung gegen Fly-Kick", true));
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
        if (antiKick.on() && ++ticks % 40 == 0 && !mc.player.onGround()) {
            var v = mc.player.getDeltaMovement();
            mc.player.setDeltaMovement(v.x, v.y - 0.04, v.z);
        }
    }

    @Override
    public String info() { return Setting.fmt(speed.num()); }
}
