package dev.teizen.client.modules;

import dev.teizen.client.TeizenClient;
import dev.teizen.client.core.Module;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

/** Jeder Schlag wird ein kritischer Treffer (mini-Sprung, den nur der Server sieht). */
public final class Criticals extends Module {
    public Criticals() {
        super("Criticals", Category.KAMPF, "Jeder Schlag ist ein Krit-Treffer (mehr Schaden).");
    }

    /** Wird direkt vor jedem Angriff aufgerufen (auch bei normalen Klicks über ein Mixin). */
    public static void beforeAttack() {
        Criticals c = TeizenClient.modules().get(Criticals.class);
        if (c == null || !c.isEnabled() || !c.inGame()) return;
        LocalPlayer p = mc.player;
        if (!p.onGround() || p.isInWater() || p.isInLava() || p.onClimbable() || p.isPassenger()) return;
        double x = p.getX(), y = p.getY(), z = p.getZ();
        p.connection.send(new ServerboundMovePlayerPacket.Pos(x, y + 0.0625, z, false, p.horizontalCollision));
        p.connection.send(new ServerboundMovePlayerPacket.Pos(x, y, z, false, p.horizontalCollision));
    }
}
