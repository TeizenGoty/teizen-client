package dev.teizen.client.core;

import dev.teizen.client.util.Chat;
import dev.teizen.client.util.ServerQuery;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Der Seed der Welt: eigene Welt / LAN direkt vom eingebauten Server, auf Servern per /seed (braucht OP),
 * sonst von Hand mit ///seed <Zahl>.
 */
public final class Seed {
    private static final Minecraft mc = Minecraft.getInstance();
    private static final Pattern NUM = Pattern.compile("-?\\d+");

    private static Long seed;
    private static String world = "";
    private static boolean fetching;
    private static final List<Consumer<Long>> waiting = new ArrayList<>();

    private Seed() {}

    /** Welche Welt / welcher Server gerade offen ist (damit ein alter Seed nicht für eine andere Welt gilt). */
    private static String worldKey() {
        if (mc.getSingleplayerServer() != null) return "sp:" + mc.getSingleplayerServer().getWorldData().getLevelName();
        var s = mc.getCurrentServer();
        return s != null ? "mp:" + s.ip : "?";
    }

    /** Bekannter Seed dieser Welt, oder null. */
    public static Long known() {
        if (!worldKey().equals(world)) { seed = null; world = worldKey(); }
        return seed;
    }

    /** Seed von Hand setzen (///seed 12345). */
    public static void set(long s) {
        world = worldKey();
        seed = s;
    }

    /** Seed holen (falls noch unbekannt); die Antwort kommt an {@code cb} (null = ging nicht). */
    public static void fetch(Consumer<Long> cb) {
        Long k = known();
        if (k != null) { cb.accept(k); return; }
        waiting.add(cb);
        if (fetching) return;
        fetching = true;
        IntegratedServer server = mc.getSingleplayerServer();
        if (server != null) {
            server.execute(() -> {
                long s = server.overworld().getSeed();
                mc.execute(() -> done(s));
            });
            return;
        }
        if (!ServerQuery.allowed("seed")) {
            done(null);
            return;
        }
        ServerQuery.command("seed", "commands.seed.success", msg -> {
            Matcher m = NUM.matcher(msg.getString());
            Long found = null;
            while (m.find()) {
                try { found = Long.parseLong(m.group()); } catch (NumberFormatException ignored) { }
            }
            done(found);
        }, () -> done(null));
    }

    private static void done(Long s) {
        fetching = false;
        if (s != null) set(s);
        List<Consumer<Long>> cbs = new ArrayList<>(waiting);
        waiting.clear();
        for (Consumer<Long> cb : cbs) {
            try { cb.accept(s); } catch (Throwable t) { Chat.error("Seed: " + t); }
        }
    }
}
