package dev.teizen.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Liest die Wegpunkte von Xaero's Minimap / World Map aus deren Dateien (xaero/minimap/…/waypoints.txt).
 * So klappt es mit jeder Xaero-Version, ohne in den Mod hineinzugreifen.
 */
public final class Waypoints {
    private Waypoints() {}

    public record W(String name, int x, int z) {}

    /** Welt-Ordner eines Wegpunkt-Pfads: der Ordner direkt unter "minimap"/"XaeroWaypoints" (eine Welt bzw. ein Server). */
    private static String worldKey(Path p) {
        String s = p.toString().replace('\\', '/');
        for (String root : new String[]{"/minimap/", "/XaeroWaypoints/"}) {
            int i = s.indexOf(root);
            if (i < 0) continue;
            int start = i + root.length();
            int end = s.indexOf('/', start);
            if (end < 0) end = s.length();
            return s.substring(0, end); // bis einschließlich Welt-Ordnername
        }
        return s;
    }

    private static List<Path> files() {
        List<Path> out = new ArrayList<>();
        Path game = Minecraft.getInstance().gameDirectory.toPath();
        for (String d : new String[]{"xaero/minimap", "XaeroWaypoints"}) {
            Path root = game.resolve(d);
            if (!Files.isDirectory(root)) continue;
            try (Stream<Path> s = Files.walk(root, 6)) {
                s.filter(p -> p.getFileName().toString().endsWith(".txt") && Files.isRegularFile(p)).forEach(out::add);
            } catch (Throwable ignored) {}
        }
        if (out.isEmpty()) return out;
        out.sort(Comparator.comparingLong((Path p) -> p.toFile().lastModified()).reversed());
        // Nur die AKTUELLE Welt/Server: das ist der Welt-Ordner der zuletzt benutzten (neuesten) Datei.
        // Xaero schreibt beim Betreten der Welt in genau diesen Ordner, also ist er der neueste.
        String world = worldKey(out.get(0));
        List<Path> here = new ArrayList<>();
        for (Path p : out) if (worldKey(p).equals(world)) here.add(p);
        // innerhalb der Welt: passend zur Dimension
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            String dim = mc.level.dimension() == Level.NETHER ? "dim%-1" : mc.level.dimension() == Level.END ? "dim%1" : "dim%0";
            List<Path> match = new ArrayList<>();
            for (Path p : here) if (p.toString().replace('\\', '/').contains("/" + dim + "/")) match.add(p);
            if (!match.isEmpty()) return match;
        }
        return here;
    }

    private static List<W> read(Path f) {
        List<W> out = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(f)) {
                if (!line.startsWith("waypoint:")) continue;
                String[] a = line.split(":");
                if (a.length < 6) continue;
                try {
                    out.add(new W(a[1].replace("§§", ":"), Integer.parseInt(a[3].trim()), Integer.parseInt(a[5].trim())));
                } catch (NumberFormatException ignored) {}
            }
        } catch (Throwable ignored) {}
        return out;
    }

    private static List<String> nameCache = new ArrayList<>();
    private static long nameCacheAt;

    /** Namen aller Wegpunkte, "neuester" vorn – höchstens alle 2 Sekunden neu von der Platte gelesen. */
    public static List<String> names() {
        long now = System.currentTimeMillis();
        if (now - nameCacheAt < 2000 && !nameCache.isEmpty()) return nameCache;
        List<String> out = new ArrayList<>();
        out.add("neuester");
        java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();
        for (W w : all()) if (!w.name().isBlank()) seen.add(w.name());
        out.addAll(seen);
        nameCache = out;
        nameCacheAt = now;
        return out;
    }

    /** Für Selbsttest/Fehlersuche: wie die Wegpunkte zuletzt gelesen wurden. */
    public static String source = "noch nicht gelesen";

    private static Object call(Object o, String... names) throws Exception {
        if (o == null) return null;
        for (String n : names) {
            try {
                var m = o.getClass().getMethod(n);
                m.setAccessible(true);
                return m.invoke(o);
            } catch (NoSuchMethodException ignored) {}
        }
        throw new NoSuchMethodException(o.getClass().getName() + "." + String.join("/", names));
    }

    /**
     * Wegpunkte direkt aus Xaero's Minimap (der gerade geladenen Welt) – damit auch die temporären, die Xaero
     * nicht in die Datei schreibt. Klappt das nicht (andere Xaero-Version), wird null zurückgegeben.
     */
    private static List<W> live() {
        try {
            Class<?> mods = Class.forName("xaero.hud.minimap.BuiltInHudModules");
            Object module = mods.getField("MINIMAP").get(null);
            Object session = call(module, "getCurrentSession");
            if (session == null) { source = "Xaero: keine Sitzung"; return null; }
            Object world = call(call(session, "getWorldManager"), "getCurrentWorld");
            if (world == null) { source = "Xaero: keine Welt"; return null; }
            List<Object> sets = new ArrayList<>();
            try {
                Object cur = call(world, "getCurrentWaypointSet", "getCurrentSet");
                if (cur != null) sets.add(cur); // die gewählte Liste zuerst
            } catch (Exception ignored) {}
            try {
                Object it = call(world, "getIterableWaypointSets", "getWaypointSets");
                if (it instanceof Iterable<?> i) for (Object o : i) if (o != null && !sets.contains(o)) sets.add(o);
            } catch (Exception ignored) {}
            List<W> out = new ArrayList<>();
            int temp = 0;
            for (Object set : sets) {
                Object wps = call(set, "getWaypoints", "getList");
                if (!(wps instanceof Iterable<?> i)) continue;
                for (Object w : i) {
                    if (w == null) continue;
                    String name = String.valueOf(call(w, "getName", "getNameSafe"));
                    int x = ((Number) call(w, "getX")).intValue(), z = ((Number) call(w, "getZ")).intValue();
                    boolean t = false;
                    try { t = Boolean.TRUE.equals(call(w, "isTemporary")); } catch (Exception ignored) {}
                    if (t) temp++;
                    out.add(new W(name, x, z));
                }
            }
            source = "Xaero direkt: " + out.size() + " Wegpunkte (" + temp + " temporär) in " + sets.size() + " Listen";
            return out;
        } catch (ClassNotFoundException e) {
            source = "Xaero nicht da";
            return null;
        } catch (Throwable t) {
            source = "Xaero direkt ging nicht: " + t;
            return null;
        }
    }

    /** Alle Wegpunkte der aktuellen Welt – direkt aus Xaero (mit temporären), sonst aus Xaeros Dateien. */
    public static List<W> all() {
        List<W> live = live();
        if (live != null && !live.isEmpty()) return live;
        String why = source;
        List<W> out = new ArrayList<>();
        for (Path f : files()) out.addAll(read(f));
        source = why + " -> Dateien: " + out.size();
        return out;
    }

    /** Wegpunkt mit diesem Namen; leer = der oberste der zuletzt geänderten Datei (bei Xaero der neueste). */
    public static W find(String name) {
        List<W> l = all();
        if (l.isEmpty()) return null;
        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) return l.get(0);
        for (W w : l) if (w.name().equalsIgnoreCase(n)) return w;
        for (W w : l) if (w.name().toLowerCase(java.util.Locale.ROOT).contains(n.toLowerCase(java.util.Locale.ROOT))) return w;
        return null;
    }
}
