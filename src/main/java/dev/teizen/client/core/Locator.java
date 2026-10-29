package dev.teizen.client.core;

import com.mojang.datafixers.util.Pair;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import dev.teizen.client.util.Chat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Strukturen finden.
 * Eigene Welt / LAN: direkt über den eingebauten Server.
 * Auf Servern: per /locate (braucht OP) – die Antwort wird aus dem Chat gelesen.
 */
public final class Locator {
    private static final Minecraft mc = Minecraft.getInstance();
    private static final Pattern COORDS = Pattern.compile("\\[\\s*(-?\\d+)\\s*,\\s*(~|-?\\d+)\\s*,\\s*(-?\\d+)\\s*]");

    public interface Callback {
        void found(BlockPos pos, String label);
        default void failed(String why) { Chat.error(why); }
        /** Suche wurde abgebrochen (Verbindung weg, Welt lädt neu) – darf später neu versucht werden. */
        default void aborted() {}
    }

    private record Request(String arg, String label, Callback cb) {}

    private static final Deque<Request> queue = new ArrayDeque<>();
    private static Request pending;
    private static int pendingTicks;

    /** Name (auch deutsch) -> Argument für /locate structure. */
    private static final Map<String, String> ARG = new LinkedHashMap<>();
    /** Argument -> deutscher Anzeigename. */
    private static final Map<String, String> LABEL = new LinkedHashMap<>();
    /** Argument -> in welchen Dimensionen es die Struktur gibt (o = Oberwelt, n = Nether, e = End). */
    private static final Map<String, String> DIM = new LinkedHashMap<>();

    /** Erster (englischer) Name je Struktur, für Vorschläge. */
    private static final List<String> PRIMARY = new ArrayList<>();

    static {
        s("#minecraft:village", "Dorf", "o", "village", "dorf", "doerfer");
        s("minecraft:stronghold", "Festung", "o", "stronghold", "festung", "endportal");
        s("minecraft:fortress", "Netherfestung", "n", "fortress", "netherfestung", "nether_fortress");
        s("minecraft:bastion_remnant", "Bastion", "n", "bastion", "bastion_remnant", "bastionsruine");
        s("minecraft:monument", "Ozeanmonument", "o", "monument", "ozeanmonument", "ocean_monument");
        s("minecraft:mansion", "Waldanwesen", "o", "mansion", "anwesen", "waldanwesen", "woodland_mansion");
        s("minecraft:ancient_city", "Antike Stadt", "o", "ancient_city", "antike_stadt", "city");
        s("minecraft:end_city", "Endsiedlung", "e", "end_city", "endstadt", "endsiedlung");
        s("minecraft:trial_chambers", "Prüfungskammer", "o", "trial_chambers", "trial", "pruefungskammer");
        s("minecraft:desert_pyramid", "Wüstentempel", "o", "desert_pyramid", "pyramide", "wuestentempel");
        s("minecraft:jungle_pyramid", "Dschungeltempel", "o", "jungle_pyramid", "dschungeltempel", "jungle_temple");
        s("minecraft:igloo", "Iglu", "o", "igloo", "iglu");
        s("minecraft:swamp_hut", "Hexenhütte", "o", "swamp_hut", "hexenhuette", "witch_hut");
        s("minecraft:pillager_outpost", "Plünderer-Außenposten", "o", "pillager_outpost", "outpost", "aussenposten");
        s("#minecraft:ruined_portal", "Portalruine", "on", "ruined_portal", "portalruine");
        s("#minecraft:shipwreck", "Schiffswrack", "o", "shipwreck", "schiffswrack");
        s("#minecraft:ocean_ruin", "Ozeanruine", "o", "ocean_ruin", "ozeanruine");
        s("#minecraft:mineshaft", "Mine", "o", "mineshaft", "mine", "verlassene_mine");
        s("minecraft:buried_treasure", "Vergrabener Schatz", "o", "buried_treasure", "schatz");
        s("minecraft:trail_ruins", "Pfadruine", "o", "trail_ruins", "pfadruine");
        s("minecraft:nether_fossil", "Netherfossil", "n", "nether_fossil", "fossil");
    }

    private static void s(String arg, String label, String dims, String... names) {
        for (String n : names) ARG.put(n, arg);
        LABEL.put(arg, label);
        DIM.put(arg, dims);
        PRIMARY.add(names[0]);
    }

    private Locator() {}

    /** Alle bekannten Namen (für Vorschläge im Chat). */
    public static List<String> names() { return PRIMARY; }

    public static String arg(String name) {
        String n = name.toLowerCase(Locale.ROOT).trim();
        String a = ARG.get(n);
        if (a != null) return a;
        if (n.startsWith("#") || n.contains(":")) return n;
        return "minecraft:" + n;
    }

    /** Englischer Hauptname (z.B. "dorf" -> "village"), oder der Name selbst, wenn unbekannt. */
    public static String primary(String name) {
        String a = arg(name);
        for (String p : PRIMARY) if (arg(p).equals(a)) return p;
        return name.toLowerCase(Locale.ROOT).trim();
    }

    /** Gibt es die Struktur in dieser Dimension? Unbekannte Strukturen: ja (einfach suchen). */
    public static boolean inDimension(String name, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dim) {
        String d = DIM.get(arg(name));
        if (d == null) return true;
        if (dim == net.minecraft.world.level.Level.NETHER) return d.contains("n");
        if (dim == net.minecraft.world.level.Level.END) return d.contains("e");
        if (dim == net.minecraft.world.level.Level.OVERWORLD) return d.contains("o");
        return true;
    }

    /** Wo es die Struktur gibt, als Text ("Oberwelt", "Nether", "End"). */
    public static String dimensionText(String name) {
        String d = DIM.getOrDefault(arg(name), "o");
        List<String> out = new ArrayList<>();
        if (d.contains("o")) out.add("Oberwelt");
        if (d.contains("n")) out.add("Nether");
        if (d.contains("e")) out.add("End");
        return String.join("/", out);
    }

    public static String label(String name) {
        String a = arg(name);
        String l = LABEL.get(a);
        if (l != null) return l;
        return a.replace("#", "").replace("minecraft:", "");
    }

    public static void register() {
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) onMessage(message);
        });
    }

    /** Sucht die nächste Struktur; die Antwort kommt später über den Callback. */
    public static void locate(String name, Callback cb) {
        String a = arg(name);
        String l = label(name);
        IntegratedServer server = mc.getSingleplayerServer();
        if (server != null) {
            singleplayer(server, a.replace("#", "").replace("minecraft:", ""), l, cb);
            return;
        }
        queue.add(new Request(a, l, cb));
    }

    public static boolean busy() { return pending != null || !queue.isEmpty(); }

    public static void tick() {
        if (mc.player == null) {
            // keine Welt (Verbindung weg / lädt neu): offene Suchen abbrechen und Bescheid geben, damit sie neu gestartet werden
            if (pending != null) pending.cb.aborted();
            for (Request q : queue) q.cb.aborted();
            queue.clear();
            pending = null;
            return;
        }
        if (pending != null) {
            if (++pendingTicks > 900) {
                Request r = pending;
                pending = null;
                r.cb.failed(viaSeed ? "Seed-Suche ohne Ergebnis (" + r.label + ")." : "Keine Antwort vom Server auf /locate (" + r.label + ").");
                viaSeed = false;
            }
            return;
        }
        Request r = queue.poll();
        if (r == null) return;
        // ohne OP (kein /locate): mit dem Seed über SeedMapper ausrechnen
        if (forceSeedMode || !dev.teizen.client.util.ServerQuery.allowed("locate")) { seedLocate(r); return; }
        pending = r;
        pendingTicks = 0;
        viaSeed = false;
        mc.player.connection.sendCommand("locate structure " + r.arg);
    }

    /** Läuft die aktuelle Suche über den Seed (SeedMapper) statt /locate? */
    private static boolean viaSeed;
    /** Selbsttest: so tun, als hätte man kein OP. */
    public static boolean forceSeedMode;
    /** Wie oft schon über den Seed gesucht wurde (Selbsttest). */
    public static int seedSearches;

    private static void seedLocate(Request r) {
        if (!dev.teizen.client.compat.SeedMapCompat.seedMapper()) {
            r.cb.failed("Ohne OP findet der StructureFinder Strukturen über den Seed – dafür brauchst du die Mod SeedMapper.");
            return;
        }
        Long seed = Seed.known();
        if (seed == null) {
            r.cb.failed("Ohne OP braucht der StructureFinder den Seed: " + Seed.whyUnknown()
                    + " Oder selbst eingeben: StructureFinder -> seed.");
            return;
        }
        dev.teizen.client.compat.SeedMapCompat.giveSeed(seed, false);
        String name = r.arg.replace("#", "").replace("minecraft:", "");
        if (name.equals("ruined_portal") && mc.level.dimension() == net.minecraft.world.level.Level.NETHER) name = "ruined_portal_nether";
        String cmd = "sm:locate feature " + name; // SeedMapper: "sm:locate feature village" / "feature stronghold"
        pending = r;
        pendingTicks = 0;
        viaSeed = true;
        seedSearches++;
        String err = dev.teizen.client.compat.SeedMapCompat.runClientCommand(cmd);
        if (err != null && pending == r) {
            pending = null;
            r.cb.failed(err.toLowerCase(Locale.ROOT).contains("couldn't locate")
                    ? "Keine " + r.label + " in der Nähe gefunden (Seed " + seed + ")." : "Seed-Suche (" + r.label + "): " + err);
        }
    }

    /** Antwort von SeedMapper (Client-Meldung). */
    public static void onClientMessage(Component msg) {
        if (pending == null || !viaSeed) return;
        Set<String> keys = new HashSet<>();
        collectKeys(msg, keys, 0);
        boolean found = false;
        for (String k : keys) if (k.startsWith("command.locate.feature") && k.endsWith("foundAt")) found = true;
        if (!found) return;
        Matcher m = NUMS.matcher(msg.getString());
        List<Integer> n = new ArrayList<>();
        while (m.find() && n.size() < 3) n.add(Integer.parseInt(m.group()));
        if (n.size() < 2) return;
        int x = n.get(0), y = n.size() == 3 ? n.get(1) : mc.player.blockPosition().getY(), z = n.size() == 3 ? n.get(2) : n.get(1);
        Request r = pending;
        pending = null;
        viaSeed = false;
        r.cb.found(new BlockPos(x, y, z), r.label);
    }

    private static final Pattern NUMS = Pattern.compile("-?\\d+");

    private static void collectKeys(Component c, Set<String> keys, int depth) {
        if (c == null || depth > 8) return;
        if (c.getContents() instanceof TranslatableContents tc) {
            keys.add(tc.getKey());
            for (Object o : tc.getArgs()) if (o instanceof Component sub) collectKeys(sub, keys, depth + 1);
        }
        for (Component sib : c.getSiblings()) collectKeys(sib, keys, depth + 1);
    }

    private static void onMessage(Component msg) {
        if (pending == null || viaSeed) return;
        Set<String> keys = new HashSet<>();
        collectKeys(msg, keys, 0);
        String text = msg.getString();
        if (keys.contains("commands.locate.structure.success")) {
            Matcher m = COORDS.matcher(text);
            if (m.find()) {
                int x = Integer.parseInt(m.group(1));
                int z = Integer.parseInt(m.group(3));
                int y = m.group(2).equals("~") ? mc.player.blockPosition().getY() : Integer.parseInt(m.group(2));
                Request r = pending;
                pending = null;
                r.cb.found(new BlockPos(x, y, z), r.label);
            }
            return;
        }
        if (keys.contains("commands.locate.structure.not_found")) {
            Request r = pending;
            pending = null;
            r.cb.failed("Keine " + r.label + " in der Nähe gefunden (in dieser Dimension?).");
            return;
        }
        if (keys.contains("commands.locate.structure.invalid")) {
            Request r = pending;
            pending = null;
            r.cb.failed("Unbekannte Struktur: " + r.label);
            return;
        }
        if (keys.contains("command.unknown.command") || keys.contains("command.unknown.argument") || keys.contains("commands.help.failed")) {
            Request r = pending;
            pending = null;
            r.cb.failed("Strukturen finden auf Servern geht nur mit OP-Rechten (Befehl /locate).");
        }
    }

    // ------------------------------------------------------------------ eigene Welt

    private static void singleplayer(IntegratedServer server, String query, String label, Callback cb) {
        BlockPos origin = mc.player.blockPosition();
        var dim = mc.level.dimension();
        server.execute(() -> {
            ServerLevel sl = server.getLevel(dim);
            if (sl == null) return;
            Registry<Structure> reg = sl.registryAccess().lookupOrThrow(Registries.STRUCTURE);
            List<Holder<Structure>> holders = new ArrayList<>();
            for (Identifier id : reg.keySet()) {
                if (id.getPath().contains(query)) reg.get(id).ifPresent(holders::add);
            }
            if (holders.isEmpty()) {
                mc.execute(() -> cb.failed("Unbekannte Struktur: " + label));
                return;
            }
            Pair<BlockPos, Holder<Structure>> res = sl.getChunkSource().getGenerator()
                    .findNearestMapStructure(sl, HolderSet.direct(holders), origin, 100, false);
            mc.execute(() -> {
                if (res == null) cb.failed("Keine " + label + " gefunden (in dieser Dimension?).");
                else cb.found(res.getFirst(), label);
            });
        });
    }
}
