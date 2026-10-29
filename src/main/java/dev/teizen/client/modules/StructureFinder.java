package dev.teizen.client.modules;

import dev.teizen.client.core.Locator;
import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.util.Arrows;
import dev.teizen.client.util.Chat;
import dev.teizen.client.util.Draw;
import dev.teizen.client.util.Projector;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Findet jede Struktur (Dorf, Festung, Antike Stadt, Bastion, ...): im Menü einfach anhaken.
 * In der eigenen Welt direkt, auf Servern per /locate (OP) – ohne OP über den Seed (SeedCracker oder selbst eingegeben,
 * ausgerechnet von SeedMapper). Zeigt Pfeil, Richtung, Entfernung und eine Linie.
 */
public final class StructureFinder extends Module {
    public final Setting structures = add(Setting.multi("strukturen",
            "Welche Strukturen gesucht werden (anhaken; Rechtsklick = Namen eintippen)", "village",
            Locator.names().toArray(new String[0])).labels(Locator::label));
    private final Setting arrows = add(Setting.bool("pfeile", "Pfeile um das Fadenkreuz, die zur Struktur zeigen", true));
    private final Setting tracers = add(Setting.bool("linien", "Linie zur Struktur", true));
    private final Setting compass = add(Setting.bool("kompass", "Richtung und Entfernung oben in der Mitte", true));
    private final Setting autoSeed = add(Setting.bool("autoseed", "Seed automatisch holen und an SeedMapper / Seed Atlas geben (Server: OP)", true));
    private final Setting mapMode = add(Setting.choice("seedmap", "Welche Karte: auto = SeedMapper, sonst Seed Atlas (Xaero), sonst Chunkbase im Browser",
            "auto", "auto", "seedmapper", "seedatlas", "chunkbase"));
    /** Eigener Seed (Zahl oder Text wie bei Minecraft) – gilt für die Welt, in der du ihn eingibst. */
    public final Setting ownSeed = add(Setting.text("seed", "Eigenen Seed eingeben (Zahl oder Text wie bei Minecraft) – gilt für diese Welt/diesen Server; leer = wieder entfernen", ""));
    private final Setting openMap = add(Setting.action("karte", "Seed holen und die Seed-Karte mit allen Strukturen öffnen", "Seedmap öffnen",
            this::openSeedMap));

    public record Found(BlockPos pos, String label, String key) {
        public Found(BlockPos pos, String label) { this(pos, label, ""); }
    }

    public final List<Found> found = new ArrayList<>();
    /** Strukturen, die schon gesucht werden (Hauptname). */
    private final Set<String> searched = new LinkedHashSet<>();
    private String lastQuery = "";
    private ResourceKey<Level> lastDim;
    private int session;

    public StructureFinder() {
        super("StructureFinder", Category.SICHT, "Findet Dörfer, Festungen und jede andere Struktur (mit OP per /locate, ohne OP über den Seed).");
    }

    @Override
    protected void onEnable() {
        found.clear();
        searched.clear();
        session++;
        lastQuery = "";
        lastDim = null;
        if (inGame()) {
            lastWorld = dev.teizen.client.core.Seed.worldKey();
            sync();
            if (autoSeed.on()) {
                dev.teizen.client.core.Seed.fetch(sd -> {
                    if (sd == null) return;
                    dev.teizen.client.compat.SeedMapCompat.giveSeed(sd, false, structures.selected());
                    if (dev.teizen.client.compat.SeedMapCompat.seedMapper() || dev.teizen.client.compat.SeedMapCompat.seedAtlas())
                        Chat.info("Seed §f" + sd + "§7 an die Seed-Karte gegeben. §f///seedmap§7 öffnet sie.");
                });
            }
        }
    }

    private String appliedSeed = "";
    private String seedWorld;

    /** Läuft jede Sekunde (auch wenn StructureFinder aus ist): eingetippten Seed übernehmen. */
    public void applyOwnSeed() {
        if (!inGame()) return;
        String w = dev.teizen.client.core.Seed.worldKey();
        if (!w.equals(seedWorld)) {
            // andere Welt: Feld zeigt den eigenen Seed DIESER Welt (oder leer)
            seedWorld = w;
            Long m = dev.teizen.client.core.Seed.manual();
            appliedSeed = m == null ? "" : String.valueOf(m);
            ownSeed.setText(appliedSeed);
            return;
        }
        String t = ownSeed.str() == null ? "" : ownSeed.str().trim();
        if (t.equals(appliedSeed)) return;
        appliedSeed = t;
        if (t.isEmpty()) { dev.teizen.client.core.Seed.clearManual(); Chat.info("Eigener Seed für diese Welt entfernt."); return; }
        long s = dev.teizen.client.core.Seed.parse(t);
        dev.teizen.client.core.Seed.setManual(s);
        dev.teizen.client.compat.SeedMapCompat.giveSeed(s, true, structures.selected());
        Chat.ok("Eigener Seed: §f" + s + "§a – gilt für diese Welt, für Strukturen und die Seed-Karte (///seedmap).");
    }

    /** Seed von außen (///seed, ///structurefinder seed) auch im Menü-Feld zeigen. */
    public void rememberSeed(long s) {
        seedWorld = dev.teizen.client.core.Seed.worldKey();
        appliedSeed = String.valueOf(s);
        ownSeed.setText(appliedSeed);
    }

    /** Seed holen und die Seed-Karte öffnen (Knopf im Menü, ///seedmap). */
    public void openSeedMap() {
        if (!inGame()) return;
        dev.teizen.client.core.Seed.fetch(sd -> {
            if (sd == null) {
                Chat.error("Seed unbekannt. " + dev.teizen.client.core.Seed.whyUnknown());
                return;
            }
            String what = dev.teizen.client.compat.SeedMapCompat.open(sd, mapMode.str(), structures.selected());
            Chat.ok("Seed §f" + sd + "§a → " + what);
            if (what.startsWith("Chunkbase")) Chat.raw("§7" + dev.teizen.client.compat.SeedMapCompat.lastUrl);
        });
    }

    @Override
    protected void onDisable() {
        found.clear();
        searched.clear();
        searchedAt.clear();
        session++;
    }

    /** Ab so vielen Blöcken Abstand vom Such-Ort wird neu gesucht (die nächste Struktur kann jetzt eine andere sein). */
    private static final int RESEARCH_DIST = 512;
    /** Wo zuletzt nach welcher Struktur gesucht wurde. */
    private final java.util.Map<String, BlockPos> searchedAt = new java.util.HashMap<>();
    private String lastWorld;
    private int tick;
    /** Wie oft schon gesucht wurde (Selbsttest). */
    public int searches;

    @Override
    public void onTick() {
        if (!inGame()) return;
        String world = dev.teizen.client.core.Seed.worldKey();
        if (lastDim != mc.level.dimension() || !world.equals(lastWorld)) {
            // andere Dimension oder andere Welt/Server: alles neu suchen
            lastWorld = world;
            found.clear();
            searched.clear();
            searchedAt.clear();
            session++;
            sync();
            return;
        }
        if (!structures.str().equals(lastQuery)) { sync(); return; }
        if (++tick % 40 != 0) return;
        // weit gelaufen -> nach dieser Struktur neu suchen (alte Markierung bleibt, bis die neue da ist)
        BlockPos me = mc.player.blockPosition();
        boolean again = false;
        for (var e : new java.util.ArrayList<>(searchedAt.entrySet())) {
            if (!searched.contains(e.getKey()) || dev.teizen.client.core.Locator.busy()) continue;
            if (Math.abs(e.getValue().getX() - me.getX()) + Math.abs(e.getValue().getZ() - me.getZ()) > RESEARCH_DIST) {
                searched.remove(e.getKey());
                again = true;
            }
        }
        // auch abgebrochene Suchen (Verbindung kurz weg) landen hier wieder
        if (again || searched.size() < wanted().size()) sync();
    }

    private List<String> wanted() {
        List<String> want = new ArrayList<>();
        for (String n : structures.selected()) {
            String p = Locator.primary(n);
            if (!want.contains(p)) want.add(p);
        }
        return want;
    }

    /** Neu angehakte Strukturen suchen, abgehakte entfernen. */
    private void sync() {
        lastQuery = structures.str();
        lastDim = mc.level.dimension();
        List<String> want = wanted();
        searched.removeIf(n -> {
            if (want.contains(n)) return false;
            found.removeIf(f -> f.key().equals(n));
            return true;
        });
        final int my = session;
        for (String name : want) {
            if (searched.contains(name)) continue;
            searched.add(name);
            searchedAt.put(name, mc.player.blockPosition());
            searches++;
            if (!Locator.inDimension(name, lastDim)) {
                Chat.info(Locator.label(name) + " §7gibt es nur im §f" + Locator.dimensionText(name) + "§7.");
                continue;
            }
            boolean refresh = found.stream().anyMatch(f -> f.key().equals(name));
            if (!refresh) Chat.info("Suche §f" + Locator.label(name) + "§7...");
            Locator.locate(name, new Locator.Callback() {
                @Override
                public void found(BlockPos pos, String label) {
                    if (!isEnabled() || my != session || !searched.contains(name)) return;
                    boolean same = found.stream().anyMatch(f -> f.key().equals(name) && f.pos().equals(pos));
                    found.removeIf(f -> f.key().equals(name));
                    found.add(new Found(pos, label, name));
                    if (same) return; // gleiche Struktur wie vorher – nicht nochmal melden
                    BlockPos me = mc.player.blockPosition();
                    long d = Math.round(Math.sqrt(Math.pow(pos.getX() - me.getX(), 2) + Math.pow(pos.getZ() - me.getZ(), 2)));
                    Chat.ok(label + " §7bei §f" + pos.getX() + " " + pos.getZ() + " §7(" + d + " Blöcke)");
                }

                @Override
                public void failed(String why) {
                    if (isEnabled() && my == session) Chat.error(why);
                }

                @Override
                public void aborted() {
                    if (my == session) searched.remove(name); // später neu versuchen
                }
            });
        }
    }

    private static double flat(Found f, net.minecraft.world.phys.Vec3 eye) {
        double dx = f.pos().getX() + 0.5 - eye.x, dz = f.pos().getZ() + 0.5 - eye.z;
        return dx * dx + dz * dz;
    }

    static String direction(double dx, double dz) {
        double ang = Math.toDegrees(Math.atan2(dz, dx)); // 0 = Osten, 90 = Süden
        String[] dirs = {"O", "SO", "S", "SW", "W", "NW", "N", "NO"};
        int i = (int) Math.floorMod(Math.round(ang / 45.0), 8);
        return dirs[i];
    }

    @Override
    public void onHud(GuiGraphicsExtractor g, float partial) {
        if (!inGame() || found.isEmpty()) return;
        var eye = mc.player.getEyePosition();
        int line = 0;
        Found nearestF = null;
        for (Found f : found)
            if (nearestF == null || flat(f, eye) < flat(nearestF, eye)) nearestF = f;
        for (Found f : found) {
            double x = f.pos().getX() + 0.5, z = f.pos().getZ() + 0.5;
            double dx = x - eye.x, dz = z - eye.z;
            long dist = Math.round(Math.sqrt(dx * dx + dz * dz));
            // Markierung immer auf Augenhöhe, damit sie am Horizont zu sehen ist
            double y = eye.y;
            if (tracers.on()) {
                if (f == nearestF) Draw.tracerNearest(g, x, y, z, 0xFFFFAA00, f.label() + " " + dist + "m");
                else Draw.tracer(g, x, y, z, 0xFFFFAA00);
            }
            if (arrows.on()) Arrows.draw(g, x, y, z, 0xFFFFAA00, f.label() + " " + dist + "m", 70);
            double[] s = Projector.project(x, y, z);
            if (s != null) {
                String t = "§6" + f.label() + " §f" + dist + "m";
                g.text(mc.font, t, (int) s[0] - mc.font.width(t) / 2, (int) s[1] - 4, 0xFFFFFFFF, true);
            }
            if (compass.on()) {
                String t = "§6" + f.label() + "§7: §f" + dist + " m §7Richtung §f" + direction(dx, dz);
                g.text(mc.font, t, g.guiWidth() / 2 - mc.font.width(t) / 2, 16 + line * 10, 0xFFFFFFFF, true);
                line++;
            }
        }
    }

    @Override
    public String info() {
        List<String> sel = structures.selected();
        if (sel.size() == 1) return Locator.label(sel.get(0));
        return found.size() + "/" + sel.size();
    }
}
