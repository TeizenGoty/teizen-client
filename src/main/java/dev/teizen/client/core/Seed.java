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
 * Der Seed der Welt: eigene Welt / LAN direkt vom eingebauten Server, auf Servern per /seed (braucht OP).
 * Ohne OP: wenn die Mod SeedCrackerX installiert ist, wird der Seed übernommen, sobald sie ihn beim Herumlaufen
 * geknackt hat. Sonst von Hand mit ///seed <Zahl>.
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
            // ohne OP: vielleicht hat SeedCrackerX ihn schon geknackt
            java.util.Set<Long> cracked = crackerSeeds();
            done(cracked.size() == 1 ? cracked.iterator().next() : null);
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

    // ------------------------------------------------------------ SeedCrackerX (findet den Seed ohne OP)

    public static boolean crackerInstalled() {
        return net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("seedcrackerx");
    }

    private static Object timeMachine() throws Exception {
        Class<?> sc = Class.forName("kaptainwutax.seedcrackerX.SeedCracker");
        Object inst = sc.getMethod("get").invoke(null);
        Object ds = sc.getMethod("getDataStorage").invoke(inst);
        return ds.getClass().getMethod("getTimeMachine").invoke(ds);
    }

    /** Welt-Seeds, die SeedCrackerX gefunden hat (leer = noch keiner). */
    @SuppressWarnings("unchecked")
    public static java.util.Set<Long> crackerSeeds() {
        if (!crackerInstalled()) return java.util.Set.of();
        try {
            Object tm = timeMachine();
            Object ws = tm.getClass().getField("worldSeeds").get(tm);
            if (ws instanceof java.util.Collection<?> c) {
                java.util.Set<Long> out = new java.util.LinkedHashSet<>();
                for (Object o : new ArrayList<>(c)) if (o instanceof Long l) out.add(l);
                return out;
            }
        } catch (Throwable t) {
            // SeedCrackerX noch nicht bereit / andere Version
        }
        return java.util.Set.of();
    }

    private static final List<Long> testBackup = new ArrayList<>();

    /** Nur für den Selbsttest: so tun, als hätte SeedCrackerX genau diesen Seed geknackt (add=false stellt alles wieder her). */
    @SuppressWarnings("unchecked")
    public static boolean crackerTestSeed(long s, boolean add) {
        try {
            Object tm = timeMachine();
            java.util.Collection<Long> ws = (java.util.Collection<Long>) tm.getClass().getField("worldSeeds").get(tm);
            if (add) {
                testBackup.clear();
                testBackup.addAll(ws);
                ws.clear();
                return ws.add(s);
            }
            boolean r = ws.remove(s);
            ws.addAll(testBackup);
            testBackup.clear();
            return r;
        } catch (Throwable t) {
            return false;
        }
    }

    /** {gesammelte Bits, benötigte Bits} von SeedCrackerX, oder null. */
    public static double[] crackerBits() {
        if (!crackerInstalled()) return null;
        try {
            Class<?> sc = Class.forName("kaptainwutax.seedcrackerX.SeedCracker");
            Object inst = sc.getMethod("get").invoke(null);
            Object ds = sc.getMethod("getDataStorage").invoke(inst);
            double have = (Double) ds.getClass().getMethod("getBaseBits").invoke(ds);
            double want = (Double) ds.getClass().getMethod("getWantedBits").invoke(ds);
            return new double[]{have, want};
        } catch (Throwable t) {
            return null;
        }
    }

    /** Beim Beenden: die Thread-Pools von SeedCrackerX schließen, sonst bleibt Minecraft hängen (Crash-Report beim Schließen). */
    public static void stopCracker() {
        if (!crackerInstalled()) return;
        for (String c : new String[]{"kaptainwutax.seedcrackerX.finder.FinderQueue", "kaptainwutax.seedcrackerX.cracker.storage.TimeMachine"}) {
            try {
                Object s = Class.forName(c).getField("SERVICE").get(null);
                if (s instanceof java.util.concurrent.ExecutorService es) es.shutdownNow();
            } catch (Throwable ignored) {
                // andere Version
            }
        }
    }

    /** Warum der Seed (noch) unbekannt ist – als Hilfe-Text. */
    public static String whyUnknown() {
        if (crackerInstalled()) {
            double[] b = crackerBits();
            String prog = b == null ? "" : " (" + Math.round(b[0]) + " von " + Math.round(b[1]) + " Bits)";
            return "SeedCrackerX sucht den Seed noch" + prog + ". Lauf herum und finde Iglus, Wüsten- oder Dschungeltempel, "
                    + "Hexenhütten, Schiffswracks, Außenposten oder Ozeanmonumente – sobald er gefunden ist, wird er automatisch benutzt.";
        }
        return "Auf diesem Server braucht /seed OP. Ohne OP: die Mod SeedCrackerX installieren (knackt den Seed beim Herumlaufen) "
                + "oder den Seed von Hand eingeben: ///seed <Zahl>";
    }

    private static int ticks;

    /** Jeden Tick: neuen Seed von SeedCrackerX übernehmen. */
    public static void tick() {
        if (++ticks % 20 != 0 || mc.player == null || !crackerInstalled()) return;
        java.util.Set<Long> cracked = crackerSeeds();
        if (cracked.size() != 1) return;
        long s = cracked.iterator().next();
        Long k = known();
        if (k != null && k == s) return;
        set(s);
        Chat.ok("SeedCrackerX hat den Seed gefunden: §f" + s + "§a – wird jetzt für die Seed-Karte benutzt (///seedmap).");
        var sf = dev.teizen.client.TeizenClient.modules().get(dev.teizen.client.modules.StructureFinder.class);
        dev.teizen.client.compat.SeedMapCompat.giveSeed(s, false, sf == null ? List.of() : sf.structures.selected());
        crackerTaken++;
    }

    /** Wie oft ein Seed von SeedCrackerX übernommen wurde (Selbsttest). */
    public static int crackerTaken;

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
