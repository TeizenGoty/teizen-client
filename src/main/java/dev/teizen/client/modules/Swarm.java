package dev.teizen.client.modules;

import dev.teizen.client.TeizenClient;
import dev.teizen.client.core.Commands;
import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.util.Chat;
import net.minecraft.world.phys.Vec3;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Swarm: mehrere eigene Minecraft-Fenster (Accounts) gleichzeitig steuern.
 * Ein Fenster ist "chef" (Host), die anderen "helfer" (verbinden sich mit IP + Port des Chefs, gleicher PC = 127.0.0.1).
 * Beim Chef: ///swarm <Befehl> -> alle Helfer führen ihn aus (z.B. ///swarm ///killaura oder ///swarm Hallo).
 * ///swarm komm -> alle Helfer laufen zu dir.
 */
public final class Swarm extends Module {
    private final Setting mode = add(Setting.choice("modus", "chef = steuert, helfer = gehorcht", "chef", "chef", "helfer"));
    private final Setting ip = add(Setting.text("ip", "Helfer: IP des Chefs (gleicher PC = 127.0.0.1)", "127.0.0.1"));
    private final Setting port = add(Setting.number("port", "Port", 6969, 1024, 65535));

    private ServerSocket server;
    private final List<PrintWriter> workers = new CopyOnWriteArrayList<>();
    private Socket conn;
    private Thread thread;
    private volatile Vec3 comeTo;
    public volatile int received, sent;

    public Swarm() {
        super("Swarm", Category.SONSTIGES, "Mehrere eigene Minecraft-Fenster gleichzeitig steuern (Chef + Helfer).");
    }

    private boolean host() { return mode.is("chef"); }

    @Override
    protected void onEnable() {
        int p = port.integer();
        if (host()) {
            thread = new Thread(() -> {
                try (ServerSocket ss = new ServerSocket()) {
                    ss.setReuseAddress(true);
                    ss.bind(new InetSocketAddress(p));
                    server = ss;
                    mc.execute(() -> Chat.ok("Swarm-Chef läuft auf Port " + p + ". Helfer: ///set swarm modus helfer, dann Swarm an."));
                    while (!ss.isClosed()) {
                        Socket s = ss.accept();
                        PrintWriter w = new PrintWriter(new java.io.OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8), true);
                        workers.add(w);
                        mc.execute(() -> Chat.info("Swarm: Helfer verbunden (" + workers.size() + ")."));
                    }
                } catch (Exception e) {
                    if (isEnabled()) mc.execute(() -> Chat.error("Swarm-Chef: " + e.getMessage()));
                }
            }, "Teizen-Swarm-Chef");
        } else {
            thread = new Thread(() -> {
                try (Socket s = new Socket()) {
                    s.connect(new InetSocketAddress(ip.str().trim(), p), 5000);
                    conn = s;
                    mc.execute(() -> Chat.ok("Swarm: mit dem Chef verbunden."));
                    BufferedReader r = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                    String line;
                    while ((line = r.readLine()) != null) {
                        String l = line;
                        received++;
                        mc.execute(() -> run(l));
                    }
                } catch (Exception e) {
                    if (isEnabled()) mc.execute(() -> Chat.error("Swarm-Helfer: keine Verbindung zum Chef (" + e.getMessage() + ")."));
                }
            }, "Teizen-Swarm-Helfer");
        }
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    protected void onDisable() {
        try { if (server != null) server.close(); } catch (Exception ignored) {}
        try { if (conn != null) conn.close(); } catch (Exception ignored) {}
        for (PrintWriter w : workers) w.close();
        workers.clear();
        server = null;
        conn = null;
        comeTo = null;
        if (mc.options != null) mc.options.keyUp.setDown(false);
    }

    /** Helfer: Befehl vom Chef ausführen. */
    private void run(String line) {
        if (mc.player == null) return;
        if (line.startsWith("COME ")) {
            String[] p = line.substring(5).split(" ");
            comeTo = new Vec3(Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2]));
            return;
        }
        if (line.startsWith("///")) Commands.handle(line);
        else if (line.startsWith("/")) mc.player.connection.sendCommand(line.substring(1));
        else mc.player.connection.sendChat(line);
    }

    /** Chef: ///swarm <Text>. @return Meldung */
    public static String send(String text) {
        if (TeizenClient.modules() == null) return "Swarm fehlt.";
        Swarm s = TeizenClient.modules().get(Swarm.class);
        if (s == null || !s.isEnabled() || !s.host()) return "Erst Swarm als Chef einschalten (///set swarm modus chef, dann ///swarm).";
        String line = text;
        if (text.equalsIgnoreCase("komm") && mc.player != null)
            line = String.format(java.util.Locale.ROOT, "COME %.2f %.2f %.2f", mc.player.getX(), mc.player.getY(), mc.player.getZ());
        for (PrintWriter w : s.workers) w.println(line);
        s.sent++;
        return "An " + s.workers.size() + " Helfer geschickt.";
    }

    @Override
    public void onTick() {
        if (!inGame() || comeTo == null) return;
        double dx = comeTo.x - mc.player.getX(), dz = comeTo.z - mc.player.getZ();
        if (dx * dx + dz * dz < 4) { comeTo = null; mc.options.keyUp.setDown(false); return; }
        mc.player.setYRot((float) Math.toDegrees(Math.atan2(dz, dx)) - 90f);
        mc.options.keyUp.setDown(true);
        mc.options.keyJump.setDown(mc.player.horizontalCollision && mc.player.onGround());
    }

    @Override
    public String info() { return host() ? "Chef " + workers.size() : (conn != null && conn.isConnected() ? "Helfer" : "…"); }
}
