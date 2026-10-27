package dev.teizen.client.modules;

import dev.teizen.client.TeizenClient;
import dev.teizen.client.compat.V;
import dev.teizen.client.core.Module;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Jeder Schlag wird ein kritischer Treffer – unsichtbar (nur der Server "sieht" einen Mini-Sprung).
 * 26.2: zwei Positions-Pakete direkt vor dem Schlag.
 * 26.3: der Server erlaubt nur 1 Positions-Paket pro Tick. Darum wird der Schlag 2 Ticks verzögert:
 *   Tick 1: "hoch" (+0.0625), Tick 2: "etwas runter" (fällt = Krit möglich), dann Schlag, Tick 3: wieder am Boden.
 *   Die Pakete ersetzen die normalen Bewegungs-Pakete dieser Ticks, also nie zwei in einem Tick.
 */
public final class Criticals extends Module {
    public Criticals() {
        super("Criticals", Category.KAMPF, "Jeder Schlag ist ein Krit-Treffer (mehr Schaden).");
    }

    private static Entity pending;
    private static int stage, waited;
    private static boolean bypass;
    /** Für den Selbsttest: wie viele Krit-Schläge über den 26.3-Weg gemacht wurden. */
    public static int delayedHits;

    private static boolean blocked(LocalPlayer p) {
        return p.isInWater() || p.isInLava() || p.onClimbable() || p.isPassenger() || p.getAbilities().flying;
    }

    private static Criticals active() {
        Criticals c = TeizenClient.modules().get(Criticals.class);
        return c != null && c.isEnabled() && c.inGame() ? c : null;
    }

    /**
     * Wird direkt vor jedem Angriff aufgerufen (auch bei normalen Klicks über ein Mixin).
     * @return true = diesen Schlag jetzt abbrechen (er kommt in 2 Ticks als Krit nach)
     */
    public static boolean beforeAttack(Entity target) {
        if (active() == null || bypass) return false;
        LocalPlayer p = mc.player;
        if (V.MULTI_MOVE_PACKETS) {
            if (!p.onGround() || blocked(p)) return false;
            double x = p.getX(), y = p.getY(), z = p.getZ();
            p.connection.send(new ServerboundMovePlayerPacket.Pos(x, y + 0.0625, z, false, p.horizontalCollision));
            p.connection.send(new ServerboundMovePlayerPacket.Pos(x, y, z, false, p.horizontalCollision));
            return false;
        }
        if (stage != 0) return true; // Krit wird gerade vorbereitet: weitere Schläge schlucken
        if (!(target instanceof LivingEntity) || !p.onGround() || blocked(p)) return false;
        pending = target;
        stage = 1;
        waited = 0;
        return true;
    }

    /** Aus LocalPlayer.sendPosition (Mixin). @return true = normales Bewegungs-Paket in diesem Tick nicht schicken */
    public static boolean onSendPosition() {
        if (stage == 0 || mc.player == null) return false;
        LocalPlayer p = mc.player;
        double x = p.getX(), y = p.getY(), z = p.getZ();
        switch (stage) {
            case 1 -> { p.connection.send(new ServerboundMovePlayerPacket.Pos(x, y + 0.0625, z, false, p.horizontalCollision)); stage = 2; }
            case 2 -> { p.connection.send(new ServerboundMovePlayerPacket.Pos(x, y + 0.03125, z, false, p.horizontalCollision)); stage = 3; }
            case 4 -> { p.connection.send(new ServerboundMovePlayerPacket.Pos(x, y, z, p.onGround(), p.horizontalCollision)); stage = 0; pending = null; }
            default -> { return false; }
        }
        return true;
    }

    /** Ende jedes Ticks: wenn die zwei Pakete raus sind, jetzt zuschlagen. */
    @Override
    public void onTick() {
        if (stage == 0) return;
        if (++waited > 10) { reset(); return; }
        if (stage != 3) return;
        LocalPlayer p = mc.player;
        if (pending != null && pending.isAlive() && p.distanceTo(pending) < 7) {
            bypass = true;
            try {
                mc.gameMode.attack(p, pending);
                V.swing(InteractionHand.MAIN_HAND);
                delayedHits++;
            } finally {
                bypass = false;
            }
        }
        stage = 4;
    }

    private static void reset() { stage = 0; pending = null; waited = 0; }

    @Override
    protected void onDisable() {
        // falls gerade "in der Luft": Server wieder auf den Boden setzen (nächstes normales Paket)
        if (stage == 2 || stage == 3) stage = 4; else reset();
    }
}
