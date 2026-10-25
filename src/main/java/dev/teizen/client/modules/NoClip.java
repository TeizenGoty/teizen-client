package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.util.Chat;
import net.minecraft.world.level.GameType;

/**
 * NoClip: durch Wände fliegen. Auf Servern geht das nur über den Zuschauer-Modus,
 * darum schaltet NoClip per Befehl in den Zuschauer-Modus und beim Ausschalten zurück (braucht OP).
 */
public final class NoClip extends Module {
    private final Setting speed = add(Setting.number("tempo", "Flug-Tempo (1 = normal)", 1, 0.1, 10));
    private GameType previous;
    private int enabledTick;
    private boolean warned;

    public NoClip() {
        super("NoClip", Category.BEWEGUNG, "Durch Wände fliegen (Zuschauer-Modus, braucht OP).");
        opOnly();
    }

    @Override
    protected void onEnable() {
        warned = false;
        previous = null;
        if (!inGame()) return;
        start();
    }

    private void start() {
        previous = mc.gameMode.getPlayerMode();
        enabledTick = mc.player.tickCount;
        if (previous != GameType.SPECTATOR) mc.player.connection.sendCommand("gamemode spectator");
    }

    @Override
    protected void onDisable() {
        if (!inGame()) return;
        mc.player.getAbilities().setFlyingSpeed(0.05f);
        if (previous != null && previous != GameType.SPECTATOR && mc.gameMode.getPlayerMode() == GameType.SPECTATOR)
            mc.player.connection.sendCommand("gamemode " + previous.getName());
        previous = null;
    }

    @Override
    public void onTick() {
        if (!inGame()) return;
        if (previous == null) { start(); return; }
        if (mc.gameMode.getPlayerMode() == GameType.SPECTATOR) {
            mc.player.getAbilities().setFlyingSpeed((float) (0.05 * speed.num()));
        } else if (!warned && mc.player.tickCount - enabledTick > 60) {
            warned = true;
            Chat.error("NoClip geht nicht: du brauchst OP-Rechte für den Zuschauer-Modus.");
        }
    }

    @Override
    public String info() { return Setting.fmt(speed.num()); }
}
