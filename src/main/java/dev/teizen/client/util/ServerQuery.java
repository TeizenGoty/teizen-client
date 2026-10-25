package dev.teizen.client.util;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;

/**
 * Fragt den Server per "/data get entity ..." (braucht OP) und liest die Antwort aus dem Chat –
 * die Antworten werden versteckt, damit der Chat sauber bleibt.
 */
public final class ServerQuery {
    private static final Minecraft mc = Minecraft.getInstance();

    private record Req(String command, Consumer<String> ok, Runnable failed) {}

    private static final Deque<Req> queue = new ArrayDeque<>();
    private static final Deque<Req> pending = new ArrayDeque<>();
    private static int pendingSince;
    private static int ticks;

    private ServerQuery() {}

    /** Darf man /data benutzen? */
    public static boolean available() {
        return mc.getSingleplayerServer() == null && Op.has("data");
    }

    /** Wie viele Anfragen noch offen sind. */
    public static int busy() { return queue.size() + pending.size(); }

    /** "/data get entity <ziel> <pfad>" – die Antwort (als Text) kommt an {@code ok}. */
    public static void data(String target, String path, Consumer<String> ok, Runnable failed) {
        if (busy() > 40) return;
        queue.add(new Req("data get entity " + target + (path == null || path.isEmpty() ? "" : " " + path), ok, failed));
    }

    public static void tick() {
        ticks++;
        if (mc.player == null) { queue.clear(); pending.clear(); return; }
        if (!pending.isEmpty() && ticks - pendingSince > 80) {
            // keine Antwort -> aufgeben
            for (Req r : pending) if (r.failed != null) r.failed.run();
            pending.clear();
        }
        // höchstens 3 Befehle pro Tick, damit der Server nicht überlastet wird
        for (int i = 0; i < 3 && !queue.isEmpty() && pending.size() < 12; i++) {
            Req r = queue.poll();
            if (pending.isEmpty()) pendingSince = ticks;
            pending.add(r);
            mc.player.connection.sendCommand(r.command);
        }
    }

    private static String key(Component c) {
        return c.getContents() instanceof TranslatableContents tc ? tc.getKey() : "";
    }

    public static void register() {
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            if (overlay || pending.isEmpty()) return true;
            String k = key(message);
            if (k.equals("commands.data.entity.query") && message.getContents() instanceof TranslatableContents tc && tc.getArgs().length >= 2) {
                Object v = tc.getArgs()[tc.getArgs().length - 1];
                String txt = v instanceof Component c ? c.getString() : String.valueOf(v);
                Req r = pending.poll();
                pendingSince = ticks;
                try { r.ok.accept(txt); } catch (Throwable ignored) {}
                return false;
            }
            if (k.startsWith("arguments.nbtpath") || k.startsWith("argument.entity") || k.startsWith("commands.data")
                    || k.startsWith("command.unknown") || k.equals("command.context.here") || k.startsWith("permissions.")) {
                Req r = pending.poll();
                pendingSince = ticks;
                if (r != null && r.failed != null) try { r.failed.run(); } catch (Throwable ignored) {}
                return false;
            }
            return true;
        });
    }
}
