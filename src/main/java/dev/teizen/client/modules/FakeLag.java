package dev.teizen.client.modules;

import dev.teizen.client.TeizenClient;
import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * FakeLag: simuliert eine schlechte Verbindung. Was du machst, kommt verzögert beim Server an
 * (für andere springst du dann hin und her). Modus "stoesse": alles sammeln und auf einmal schicken.
 */
public final class FakeLag extends Module {
    private final Setting delay = add(Setting.number("verzoegerung", "Verzögerung in Millisekunden", 400, 50, 3000));
    private final Setting mode = add(Setting.choice("modus", "verzoegert = alles kommt später an, stoesse = gesammelt und auf einmal", "verzoegert", "verzoegert", "stoesse"));

    private record Held(Packet<?> packet, ChannelFutureListener listener, boolean flush, long time) {}

    private static final Deque<Held> held = new ArrayDeque<>();
    private static Connection heldConn;
    private static boolean releasing;
    private static volatile boolean active;
    private long lastPulse;
    /** Zähler (Anzeige + Selbsttest). */
    public static int heldCount, releasedCount;
    public static long maxHeldMs;

    public FakeLag() {
        super("FakeLag", Category.SONSTIGES, "Simuliert Lag: deine Pakete kommen verzögert beim Server an.");
    }

    @Override
    protected void onEnable() {
        lastPulse = System.currentTimeMillis();
        active = true;
    }

    @Override
    protected void onDisable() {
        active = false;
        releaseAll();
    }

    /** Aus dem Connection-Mixin: Paket festhalten? */
    public static boolean hold(Connection conn, Packet<?> packet, ChannelFutureListener listener, boolean flush) {
        if (!active || releasing) return false;
        Minecraft mc = Minecraft.getInstance();
        var cl = mc.getConnection();
        if (cl == null || cl.getConnection() != conn || mc.player == null) return false;
        synchronized (held) {
            if (heldConn != conn) { held.clear(); heldConn = conn; }
            held.add(new Held(packet, listener, flush, System.currentTimeMillis()));
            heldCount++;
        }
        return true;
    }

    private static void send(Held h) {
        releasing = true;
        try {
            heldConn.send(h.packet, h.listener, h.flush);
            releasedCount++;
            maxHeldMs = Math.max(maxHeldMs, System.currentTimeMillis() - h.time);
        } catch (Throwable t) {
            TeizenClient.LOG.warn("FakeLag: Paket konnte nicht gesendet werden", t);
        } finally {
            releasing = false;
        }
    }

    public static void releaseAll() {
        synchronized (held) {
            while (!held.isEmpty()) send(held.poll());
        }
    }

    @Override
    public void onTick() {
        if (!inGame()) { synchronized (held) { held.clear(); } return; }
        flushDue();
    }

    /** Fällige Pakete schicken (auch jedes Bild aufgerufen, damit die Zeit genau stimmt). */
    public void flushDue() {
        long now = System.currentTimeMillis();
        long d = delay.integer();
        synchronized (held) {
            if (mode.is("stoesse")) {
                if (now - lastPulse >= d) {
                    lastPulse = now;
                    while (!held.isEmpty()) send(held.poll());
                }
                return;
            }
            while (!held.isEmpty() && now - held.peek().time >= d) send(held.poll());
        }
    }

    @Override
    public void onHud(net.minecraft.client.gui.GuiGraphicsExtractor g, float partial) { flushDue(); }

    @Override
    public String info() {
        synchronized (held) { return delay.integer() + "ms" + (held.isEmpty() ? "" : " (" + held.size() + ")"); }
    }
}
