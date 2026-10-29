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
    public static String worldKey() {
        if (mc.getSingleplayerServer() != null) return "sp:" + mc.getSingleplayerServer().getWorldData().getLevelName();
        var s = mc.getCurrentServer();
        return s != null ? "mp:" + s.ip : "?";
    }

    /** Bekannter Seed dieser Welt, oder null. */
    public static Long known() {
        if (!worldKey().equals(world)) { world = worldKey(); seed = MANUAL.get(world); }
        return seed;
    }

    /** Seed von Hand setzen (///seed 12345). */
    public static void set(long s) {
        world = worldKey();
        seed = s;
    }

    /** Von Hand eingegebene Seeds pro Welt/Server (wird gespeichert; hat Vorrang vor SeedCrackerX). */
    public static final java.util.Map<String, Long> MANUAL = new java.util.LinkedHashMap<>();

    /** Seed von Hand (Menü / ///seed): gilt für diese Welt und wird nicht vom SeedCracker ersetzt. */
    public static void setManual(long s) {
        VERIFIED.remove(worldKey()); // neue Eingabe -> neu prüfen
        set(s);
        MANUAL.put(worldKey(), s);
        Config.save();
    }

    /** Eigenen Seed dieser Welt wieder entfernen. */
    public static void clearManual() {
        VERIFIED.remove(worldKey());
        if (MANUAL.remove(worldKey()) != null) { seed = null; Config.save(); }
    }

    public static Long manual() { return MANUAL.get(worldKey()); }

    /** Aus Text wie bei Minecraft: Zahl = Zahl, sonst der Text-Hash. */
    public static long parse(String t) {
        t = t.trim();
        try { return Long.parseLong(t); } catch (NumberFormatException e) { return t.hashCode(); }
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

    /** Den ECHTEN Seed fragen (eigene Welt / OP), egal was eingegeben ist. */
    private static void realSeed(Consumer<Long> cb) {
        IntegratedServer server = mc.getSingleplayerServer();
        if (server != null) {
            server.execute(() -> { long s = server.overworld().getSeed(); mc.execute(() -> cb.accept(s)); });
            return;
        }
        ServerQuery.command("seed", "commands.seed.success", msg -> {
            Matcher m = NUM.matcher(msg.getString());
            Long found = null;
            while (m.find()) {
                try { found = Long.parseLong(m.group()); } catch (NumberFormatException ignored) { }
            }
            cb.accept(found);
        }, () -> cb.accept(null));
    }

    // ------------------------------------------------------------ eigener Seed: prüfen und verbessern
    /** Welten, deren eingegebener Seed bestätigt ist (Server oder SeedCracker). */
    private static final java.util.Set<String> VERIFIED = new java.util.HashSet<>();
    private static String serverChecked;
    /** Selbsttest: so tun, als gäbe es kein /seed (Prüfung nur über den SeedCracker). */
    public static boolean forceNoServerSeed;
    /** Wie oft ein falscher Seed verbessert/entfernt wurde (Selbsttest). */
    public static int corrections;

    public static boolean manualVerified() { return VERIFIED.contains(worldKey()); }

    /** Eingegebenen Seed mit einem sicheren Seed vergleichen: gleich -> bestätigt, sonst ersetzen. */
    private static void checkAgainst(long real, String from) {
        Long man = manual();
        if (man == null) return;
        String w = worldKey();
        if (man == real) {
            if (VERIFIED.add(w)) Chat.ok("Dein Seed stimmt ✔ (" + from + ").");
            return;
        }
        setManual(real);
        VERIFIED.add(w);
        corrections++;
        var sf = dev.teizen.client.TeizenClient.modules().get(dev.teizen.client.modules.StructureFinder.class);
        if (sf != null) sf.rememberSeed(real);
        dev.teizen.client.compat.SeedMapCompat.giveSeed(real, true, sf == null ? List.of() : sf.structures.selected());
        Chat.ok("Dein Seed §f" + man + "§a war falsch – automatisch verbessert auf §f" + real + "§a (" + from + ").");
    }

    private static void verifyManual() {
        Long man = manual();
        String w = worldKey();
        if (man == null || VERIFIED.contains(w)) return;
        // 1) eigene Welt / OP: den echten Seed fragen
        String key = w + ":" + man;
        if (!forceNoServerSeed && !key.equals(serverChecked) && (mc.getSingleplayerServer() != null || ServerQuery.allowed("seed"))) {
            serverChecked = key;
            realSeed(real -> { if (real != null) checkAgainst(real, "vom Server"); });
            return;
        }
        // 2) SeedCracker: ganzer Seed gefunden -> vergleichen; sonst Struktur-Seed (untere 48 Bit) prüfen
        if (!crackerInstalled()) return;
        java.util.Set<Long> ws = crackerSeeds();
        if (ws.size() == 1) { checkAgainst(ws.iterator().next(), "vom SeedCracker"); return; }
        java.util.Set<Long> ss = crackerStructureSeeds();
        if (!ss.isEmpty() && !ss.contains(man & ((1L << 48) - 1))) {
            clearManual();
            corrections++;
            var sf = dev.teizen.client.TeizenClient.modules().get(dev.teizen.client.modules.StructureFinder.class);
            if (sf != null) sf.forgetSeed();
            Chat.error("Dein Seed §f" + man + "§c passt nicht zu den Strukturen hier – entfernt. Der SeedCracker sucht weiter und trägt den richtigen automatisch ein.");
        }
    }

    /** Struktur-Seeds (untere 48 Bit), die SeedCrackerX schon gefunden hat. */
    @SuppressWarnings("unchecked")
    public static java.util.Set<Long> crackerStructureSeeds() {
        if (!crackerInstalled()) return java.util.Set.of();
        try {
            Object tm = timeMachine();
            Object ss = tm.getClass().getField("structureSeeds").get(tm);
            if (ss instanceof java.util.Collection<?> c) {
                java.util.Set<Long> out = new java.util.HashSet<>();
                for (Object o : new ArrayList<>(c)) if (o instanceof Long l) out.add(l);
                return out;
            }
        } catch (Throwable ignored) { }
        return java.util.Set.of();
    }

    // ------------------------------------------------------------ SeedCrackerX (findet den Seed ohne OP)

    private static String autoFetched;
    private static Boolean crackerWasActive;
    private static Boolean crackerSet;

    /** SeedCrackerX an/aus (ohne seine Config-Datei zu ändern). */
    static void crackerActive(boolean on) {
        if (!crackerInstalled() || (crackerSet != null && crackerSet == on)) return;
        try {
            Class<?> c = Class.forName("kaptainwutax.seedcrackerX.config.Config");
            Object cfg = c.getMethod("get").invoke(null);
            java.lang.reflect.Field f = c.getField("active");
            if (crackerWasActive == null) crackerWasActive = f.getBoolean(cfg);
            f.setBoolean(cfg, on && crackerWasActive);
            crackerSet = on;
            dev.teizen.client.TeizenClient.LOG.info("SeedCrackerX " + (on && crackerWasActive ? "an" : "pausiert (Seed bekannt)"));
        } catch (Throwable t) {
            crackerSet = on;
            dev.teizen.client.TeizenClient.LOG.warn("SeedCrackerX an/aus ging nicht: " + t);
        }
    }

    /** Für den Selbsttest: läuft SeedCrackerX gerade? */
    public static Boolean crackerRunning() {
        try {
            Class<?> c = Class.forName("kaptainwutax.seedcrackerX.config.Config");
            return c.getField("active").getBoolean(c.getMethod("get").invoke(null));
        } catch (Throwable t) {
            return null;
        }
    }

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
        // SeedCrackerX: FinderQueue.SERVICE + TimeMachine.SERVICE; SeedMapper: LocatorThreadHelper.locatingExecutor
        String[][] pools = {
                {"seedcrackerx", "kaptainwutax.seedcrackerX.finder.FinderQueue", "SERVICE"},
                {"seedcrackerx", "kaptainwutax.seedcrackerX.cracker.storage.TimeMachine", "SERVICE"},
                {"seedmapper", "dev.xpple.seedmapper.thread.LocatorThreadHelper", "locatingExecutor"}};
        for (String[] p : pools) {
            if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded(p[0])) continue;
            try {
                java.lang.reflect.Field f = Class.forName(p[1]).getDeclaredField(p[2]);
                f.setAccessible(true);
                if (f.get(null) instanceof java.util.concurrent.ExecutorService es) es.shutdownNow();
            } catch (Throwable ignored) {
                // andere Version
            }
        }
        if (dev.teizen.client.SelfTest.ACTIVE) {
            // Selbsttest: nach 3 s zeigen, welche Threads das Beenden noch aufhalten
            Thread t = new Thread(() -> {
                try { Thread.sleep(3000); } catch (InterruptedException ignored) { }
                StringBuilder sb = new StringBuilder();
                for (Thread th : Thread.getAllStackTraces().keySet())
                    if (!th.isDaemon() && th.isAlive() && th != Thread.currentThread()) sb.append(th.getName()).append(", ");
                dev.teizen.client.TeizenClient.LOG.info("Beenden: offene Threads: " + sb);
            }, "teizen-shutdown-check");
            t.setDaemon(true);
            t.start();
        }
    }

    /** Warum der Seed (noch) unbekannt ist – als Hilfe-Text. */
    public static String whyUnknown() {
        if (crackerInstalled()) {
            double[] b = crackerBits();
            String prog = b == null || b[1] <= 0 ? "" : " (" + Math.round(b[0]) + " von " + Math.round(b[1]) + " Bits, "
                    + (int) Math.round(100.0 * Math.min(b[0], b[1]) / b[1]) + "%)";
            return "SeedCrackerX sucht den Seed noch" + prog + ". Lauf herum und finde Iglus, Wüsten- oder Dschungeltempel, "
                    + "Hexenhütten, Schiffswracks, Außenposten oder Ozeanmonumente – sobald er gefunden ist, wird er automatisch benutzt.";
        }
        return "Auf diesem Server braucht /seed OP. Ohne OP: die Mod SeedCrackerX installieren (knackt den Seed beim Herumlaufen) "
                + "oder den Seed von Hand eingeben: ///seed <Zahl>";
    }

    private static int ticks;
    /** Fortschritts-Text (z.B. "23/48 Bits – 48%"), oder null wenn gerade nichts zu knacken ist. Wird im HUD gezeigt. */
    public static volatile String progressText;
    /** HUD-Anzeige des Seed-Fortschritts an/aus (///seed hud). */
    public static boolean showProgress = true;

    /** Für die Restzeit: ab wann und mit wie vielen Bits in dieser Welt gezählt wird. */
    private static String rateWorld;
    private static double firstHave = -1;
    private static long firstTime;

    private static void updateProgress() {
        if (mc.player == null) { progressText = null; return; }
        Long k = known();
        if (k != null) {
            boolean man = manual() != null;
            progressText = man && !manualVerified() ? "§e" + k + " §7(eingegeben, wird geprüft)"
                    : "§a" + k + " §7(" + (man ? "eingegeben, geprüft ✔" : "bekannt") + ")";
            return;
        }
        if (!crackerInstalled()) { progressText = "§cunbekannt §7– ///seed <Zahl> oder StructureFinder → seed"; return; }
        String wk = worldKey();
        if (!wk.equals(rateWorld)) { rateWorld = wk; firstHave = -1; }
        double[] b = crackerBits();
        if (b == null || b[1] <= 0) { progressText = "§esucht… §7lauf zu Tempeln, Iglus, Hexenhütten, Schiffswracks"; return; }
        int have = (int) Math.round(b[0]), want = (int) Math.round(b[1]);
        int pct = (int) Math.round(100.0 * Math.min(have, want) / want);
        long now = System.currentTimeMillis();
        if (firstHave < 0) { firstHave = have; firstTime = now; }
        String eta;
        if (have >= want) eta = "§arechnet gleich fertig…";
        else if (have > firstHave && now - firstTime > 5000) {
            double perMs = (have - firstHave) / (double) (now - firstTime);
            eta = "§7noch ca. §f" + duration((long) ((want - have) / perMs));
        } else eta = "§7Restzeit: sobald du Strukturen findest";
        progressText = "§f" + have + "/" + want + " Bits §7(" + pct + "%) · " + eta;
    }

    /** "45 Sek", "12 Min", "1 Std 5 Min". */
    static String duration(long ms) {
        long sec = Math.max(1, ms / 1000);
        if (sec < 60) return sec + " Sek";
        long min = (sec + 30) / 60;
        if (min < 60) return min + " Min";
        return (min / 60) + " Std " + (min % 60) + " Min";
    }

    public static String durationText(long ms) { return duration(ms); }

    /** Für Selbsttest/Befehl: aktueller Fortschritt sofort ausrechnen. */
    public static String progressNow() { updateProgress(); return progressText; }

    /** Jeden Tick: neuen Seed von SeedCrackerX übernehmen. */
    public static void tick() {
        if (++ticks % 20 != 0 || mc.player == null) return;
        // eigene Welt / OP: Seed direkt holen (still) – dann braucht man den SeedCracker gar nicht
        if (known() == null && !fetching && !worldKey().equals(autoFetched)
                && (mc.getSingleplayerServer() != null || ServerQuery.allowed("seed"))) {
            autoFetched = worldKey();
            fetch(sd -> {});
        }
        var sfOwn = dev.teizen.client.TeizenClient.modules().get(dev.teizen.client.modules.StructureFinder.class);
        if (sfOwn != null) sfOwn.applyOwnSeed();
        verifyManual();
        // Seed sicher bekannt -> SeedCrackerX pausieren; unbekannt oder eingegeben-aber-ungeprüft -> weiter knacken
        crackerActive(known() == null || (manual() != null && !manualVerified()));
        updateProgress();
        if (!crackerInstalled()) return;
        if (MANUAL.containsKey(worldKey())) return; // eigener Seed hat Vorrang
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
