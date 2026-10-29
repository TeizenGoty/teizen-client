package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;

/**
 * Kein Fallschaden: beim Fallen sagt das normale Bewegungs-Paket "am Boden" (Fly.beforeSendPosition) – so zählt der
 * Server keine Fallhöhe. Vorher wurde erst ab 2 Blöcken ein Extra-Paket geschickt; bei schnellem Fall hatte der Server
 * bis dahin schon über 3 Blöcke gezählt -> zufälliger Schaden mitten in der Luft. Beim Elytra-Gleiten aus.
 */
public final class NoFall extends Module {
    private final Setting from = add(Setting.number("ab", "Ab welcher Fallhöhe geschützt wird (Blöcke, höchstens 3 = kein Schaden)", 1, 0, 3));
    /** Wie oft ein Paket als "am Boden" gemeldet wurde (Selbsttest). */
    public int saved;

    public NoFall() {
        super("NoFall", Category.BEWEGUNG, "Kein Fallschaden mehr.");
    }

    public double from() { return from.num(); }

    @Override
    public void onTick() {
        if (!inGame() || mc.player.isCreative() || mc.player.isSpectator() || mc.player.isFallFlying()) return;
        if (mc.player.fallDistance > 0) mc.player.fallDistance = 0; // auch im Client keine Fallhöhe sammeln
    }
}
