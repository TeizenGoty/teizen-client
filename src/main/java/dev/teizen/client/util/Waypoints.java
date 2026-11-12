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
        out.sort(Comparator.comparingLong((Path p) -> p.toFile().lastModified()).reversed());
        // passend zur Dimension, wenn es solche Dateien gibt
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            String dim = mc.level.dimension() == Level.NETHER ? "dim%-1" : mc.level.dimension() == Level.END ? "dim%1" : "dim%0";
            List<Path> match = new ArrayList<>();
            for (Path p : out) if (p.toString().replace('\\', '/').contains("/" + dim + "/")) match.add(p);
            if (!match.isEmpty()) return match;
        }
        return out;
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

    /** Alle Wegpunkte (zuletzt geänderte Datei zuerst). */
    public static List<W> all() {
        List<W> out = new ArrayList<>();
        for (Path f : files()) out.addAll(read(f));
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
