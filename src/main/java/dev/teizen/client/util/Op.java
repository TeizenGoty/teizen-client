package dev.teizen.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Befehle mit OP-Rechten ausführen: in der eigenen Welt direkt über den eingebauten Server (geht auch ohne
 * "Cheats erlaubt"), auf Servern als normaler Befehl (braucht OP).
 */
public final class Op {
    private static final Minecraft mc = Minecraft.getInstance();

    private Op() {}

    /** Kann man Befehle wie /effect, /attribute benutzen? */
    public static boolean available() {
        if (mc.getSingleplayerServer() != null) return true;
        return has("attribute");
    }

    /** Hat der Server uns diesen Befehl erlaubt? (Der Server schickt nur Befehle, die man benutzen darf.) */
    public static boolean has(String command) {
        var c = mc.getConnection();
        if (c == null) return false;
        try {
            return c.getCommands().getRoot().getChild(command) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Befehl ohne "/" ausführen, z.B. "effect give @s minecraft:speed infinite 1 true". */
    public static void run(String command) {
        if (mc.player == null) return;
        IntegratedServer server = mc.getSingleplayerServer();
        if (server != null) {
            UUID id = mc.player.getUUID();
            server.execute(() -> {
                ServerPlayer sp = server.getPlayerList().getPlayer(id);
                if (sp == null) return;
                try {
                    var src = server.createCommandSourceStack().withEntity(sp).withPosition(sp.position())
                            .withLevel(sp.level()).withSuppressedOutput();
                    server.getCommands().performPrefixedCommand(src, command);
                } catch (Throwable t) {
                    dev.teizen.client.TeizenClient.LOG.warn("Befehl fehlgeschlagen: " + command, t);
                }
            });
            return;
        }
        mc.player.connection.sendCommand(command);
    }
}
