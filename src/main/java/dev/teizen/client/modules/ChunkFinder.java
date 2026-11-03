package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.util.Arrows;
import dev.teizen.client.util.Chat;
import dev.teizen.client.util.Draw;
import dev.teizen.client.util.Projector;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ChunkFinder: findet Chunks, in denen Spieler waren – z.B. Basen. Gezählt werden Dinge, die es so in der Natur (fast)
 * nicht gibt: Shulkerkisten, Endertruhen, Leuchtfeuer, Trichter, Schilder, Banner, Bienenstöcke, Rahmen,
 * Rüstungsständer usw. Verdächtige Chunks bekommen einen Rahmen und eine Meldung im Chat.
 * Natürliche Bauwerke (Trial Chambers, Ancient Cities, Dungeons, Ruinen) werden erkannt: dort zählen nur noch Dinge,
 * die nie natürlich spawnen (Shulker, Leuchtfeuer, Rüstungsständer ...). Kisten, Öfen, Betten usw. zählen nur halb.
 */
public final class ChunkFinder extends Module {
    private final Setting minScore = add(Setting.number("empfindlich", "Ab wie vielen Punkten ein Chunk verdächtig ist (weniger = mehr Treffer)", 6, 1, 40));
    private final Setting chat = add(Setting.bool("chat", "Neue Funde im Chat melden", true));
    private final Setting arrows = add(Setting.bool("pfeile", "Pfeil zum nächsten verdächtigen Chunk", true));

    public record Found(int cx, int cz, double score, int minY, int maxY, String what) {}

    private static long key(int cx, int cz) { return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL); }

    public final Map<Long, Found> found = new LinkedHashMap<>();
    private final Set<Long> reported = new HashSet<>();
    private static final Map<String, Double> WEIGHTS = new HashMap<>();
    static {
        // Blöcke (Block-Entities), die fast nur Spieler bauen
        WEIGHTS.put("shulker_box", 10.0);
        WEIGHTS.put("beacon", 10.0);
        WEIGHTS.put("ender_chest", 6.0);
        WEIGHTS.put("conduit", 6.0);
        WEIGHTS.put("crafter", 4.0);
        WEIGHTS.put("hopper", 3.0);
        WEIGHTS.put("sign", 3.0);
        WEIGHTS.put("hanging_sign", 3.0);
        WEIGHTS.put("beehive", 0.0); // wächst natürlich an Bäumen
        WEIGHTS.put("jukebox", 3.0);
        WEIGHTS.put("enchanting_table", 5.0);
        WEIGHTS.put("comparator", 3.0);
        WEIGHTS.put("daylight_detector", 3.0);
        WEIGHTS.put("banner", 2.0);
        WEIGHTS.put("brewing_stand", 2.0);
        WEIGHTS.put("skull", 2.0);
        WEIGHTS.put("trapped_chest", 1.0);
        WEIGHTS.put("dispenser", 1.0);
        WEIGHTS.put("dropper", 2.0);
        WEIGHTS.put("furnace", 1.0);
        WEIGHTS.put("smoker", 0.5);
        WEIGHTS.put("blast_furnace", 0.5);
        WEIGHTS.put("barrel", 0.5);
        WEIGHTS.put("bed", 0.5);
        WEIGHTS.put("lectern", 0.5);
        WEIGHTS.put("chest", 0.5);
        WEIGHTS.put("campfire", 0.5);
        WEIGHTS.put("chiseled_bookshelf", 1.0);
        // Wesen
        WEIGHTS.put("e:item_frame", 2.0);
        WEIGHTS.put("e:glow_item_frame", 3.0);
        WEIGHTS.put("e:armor_stand", 3.0);
        WEIGHTS.put("e:painting", 2.0);
        // normale Blöcke ohne Block-Entity (werden extra gesucht)
        WEIGHTS.put("b:anvil", 5.0);
    }

    /**
     * Zeichen für natürliche Bauwerke (Trial Chambers, Ancient Cities, Dungeons, Ruinen, Wüstentempel ...).
     * Liegt so etwas im Chunk oder direkt daneben, zählen nur noch Dinge, die NIE natürlich entstehen.
     */
    private static final Set<String> STRUCTURE_MARKERS = Set.of(
            "trial_spawner", "vault", "mob_spawner", "sculk_shrieker", "sculk_catalyst", "brushable_block");
    /** Dinge, die es in keinem natürlichen Bauwerk gibt – die zählen auch neben Bauwerken voll. */
    private static final Set<String> NEVER_NATURAL = Set.of(
            "shulker_box", "beacon", "conduit", "jukebox", "daylight_detector", "hanging_sign", "e:armor_stand", "e:glow_item_frame",
            "enchanting_table", "b:anvil", "cluster");
    /** Gegenstände/Blöcke, die in natürlichen Bauwerken oft vorkommen (Dörfer, Iglus, Villen, Endstädte ...): nur halb zählen. */
    private static final Set<String> OFTEN_NATURAL = Set.of(
            "chest", "barrel", "furnace", "smoker", "blast_furnace", "bed", "lectern", "campfire", "brewing_stand", "banner",
            "dispenser", "dropper", "hopper", "crafter", "skull", "trapped_chest", "chiseled_bookshelf", "e:item_frame");
    /** Lager-Blöcke: viele davon auf wenigen Metern = Lager einer Basis. */
    private static final Set<String> STORAGE = Set.of("chest", "trapped_chest", "barrel", "shulker_box");
    private static final double CLUSTER_RADIUS = 5.0;

    private int cursor;

    public ChunkFinder() {
        super("ChunkFinder", Category.SICHT, "Findet Chunks mit Spieler-Spuren (Basen).");
    }

    @Override
    protected void onEnable() {
        found.clear();
        reported.clear();
        markers.clear();
        cursor = 0;
        primed = false;
    }

    private static String label(String key) {
        return switch (key) {
            case "shulker_box" -> "Shulker";
            case "beacon" -> "Leuchtfeuer";
            case "ender_chest" -> "Endertruhe";
            case "hopper" -> "Trichter";
            case "sign", "hanging_sign" -> "Schild";
            case "banner" -> "Banner";
            case "beehive" -> "Bienenstock";
            case "e:item_frame", "e:glow_item_frame" -> "Rahmen";
            case "e:armor_stand" -> "Rüstungsständer";
            case "e:painting" -> "Bild";
            case "enchanting_table" -> "Zaubertisch";
            case "brewing_stand" -> "Braustand";
            case "chest" -> "Kiste";
            case "b:anvil" -> "Amboss";
            case "cluster" -> "Kisten/Fässer dicht beisammen";
            case "furnace" -> "Ofen";
            default -> key.replace("e:", "").replace("b:", "").replace('_', ' ');
        };
    }

    /** Einen Chunk bewerten. */
    private static boolean isAnvil(net.minecraft.world.level.block.state.BlockState s) {
        return s.getBlock() instanceof net.minecraft.world.level.block.AnvilBlock;
    }

    private static boolean hasMarker(LevelChunk chunk) {
        for (BlockEntity be : chunk.getBlockEntities().values()) {
            var id = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType());
            if (id != null && STRUCTURE_MARKERS.contains(id.getPath())) return true;
        }
        return false;
    }

    /** Liegt im Chunk oder in einem Nachbar-Chunk ein natürliches Bauwerk? (Bauwerke sind oft größer als ein Chunk) */
    private boolean nearStructure(int cx, int cz) {
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++)
            if (markers.getOrDefault(key(cx + dx, cz + dz), false)) return true;
        return false;
    }

    /** Wie viel ein Ding zählt (neben Bauwerken nur noch die nie-natürlichen). */
    private static double weight(String k, boolean structure) {
        Double w = WEIGHTS.get(k);
        if (w == null || w <= 0) return 0;
        if (NEVER_NATURAL.contains(k)) return w;
        if (structure) return 0;
        return OFTEN_NATURAL.contains(k) ? w * 0.5 : w;
    }

    /** Für den Selbsttest: Chunks, die wegen eines Bauwerks ignoriert wurden. */
    public int skippedStructures;
    private int skippedRound;
    private boolean primed;

    private Found rate(LevelChunk chunk, int cx, int cz, Map<Long, List<Entity>> entities, boolean structure) {
        double score = 0;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        Map<String, Integer> count = new LinkedHashMap<>();
        for (BlockEntity be : chunk.getBlockEntities().values()) {
            var id = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType());
            if (id == null) continue;
            double w = weight(id.getPath(), structure);
            if (w <= 0) continue;
            score += w;
            count.merge(id.getPath(), 1, Integer::sum);
            BlockPos p = be.getBlockPos();
            minY = Math.min(minY, p.getY());
            maxY = Math.max(maxY, p.getY());
        }
        // Ambosse (kein Block-Entity): nur Abschnitte durchsuchen, die überhaupt einen Amboss enthalten können
        var sections = chunk.getSections();
        for (int i = 0; i < sections.length; i++) {
            var sec = sections[i];
            if (sec == null || sec.hasOnlyAir() || !sec.maybeHas(ChunkFinder::isAnvil)) continue;
            int baseY = mc.level.getMinY() + i * 16;
            for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                if (!isAnvil(sec.getBlockState(x, y, z))) continue;
                double w = weight("b:anvil", structure);
                if (w <= 0) continue;
                score += w;
                count.merge("b:anvil", 1, Integer::sum);
                minY = Math.min(minY, baseY + y);
                maxY = Math.max(maxY, baseY + y);
            }
        }
        // viele Kisten/Fässer auf wenigen Metern (größte Häufung im Chunk)
        List<BlockPos> storage = new ArrayList<>();
        for (BlockEntity be : chunk.getBlockEntities().values()) {
            var id = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType());
            if (id != null && STORAGE.contains(id.getPath())) storage.add(be.getBlockPos());
        }
        int cluster = 0;
        if (storage.size() >= 3 && storage.size() < 400) {
            for (BlockPos a : storage) {
                int n = 0;
                for (BlockPos b : storage) if (a.distSqr(b) <= CLUSTER_RADIUS * CLUSTER_RADIUS) n++;
                cluster = Math.max(cluster, n);
            }
        } else if (storage.size() >= 400) cluster = storage.size();
        // ab 3 dicht beisammen verdächtig, je mehr desto mehr (Doppelkiste = 2)
        if (cluster >= (structure ? 5 : 3)) { // neben Bauwerken (Trial Chamber ...) erst ab 5
            score += (cluster - 2) * 3.0;
            count.put("cluster", cluster);
        }
        for (Entity e : entities.getOrDefault(key(cx, cz), List.of())) {
            var id = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType());
            double w = weight("e:" + id.getPath(), structure);
            if (w <= 0) continue;
            score += w;
            count.merge("e:" + id.getPath(), 1, Integer::sum);
            minY = Math.min(minY, e.blockPosition().getY());
            maxY = Math.max(maxY, e.blockPosition().getY());
        }
        if (score < minScore.num()) return null;
        StringBuilder what = new StringBuilder();
        count.entrySet().stream().sorted((a, b) -> Double.compare(WEIGHTS.getOrDefault(b.getKey(), 3.0) * b.getValue(), WEIGHTS.getOrDefault(a.getKey(), 3.0) * a.getValue()))
                .limit(4).forEach(en -> what.append(what.isEmpty() ? "" : ", ").append(en.getValue()).append("x ").append(label(en.getKey())));
        return new Found(cx, cz, score, minY, maxY, what.toString() + (structure ? " (neben Bauwerk)" : ""));
    }

    /** Welche Chunks ein natürliches Bauwerk enthalten (wird nach und nach aufgefrischt). */
    private final Map<Long, Boolean> markers = new HashMap<>();
    private Map<Long, List<Entity>> entities = new HashMap<>();

    @Override
    public void onTick() {
        if (!inGame()) return;
        // Arbeit auf 20 Ticks verteilt: pro Tick nur jeder 20. Chunk (vorher alle Chunks auf einmal = Ruckler)
        int r = Math.max(2, Math.min(16, mc.options.renderDistance().get()));
        int mx = mc.player.blockPosition().getX() >> 4, mz = mc.player.blockPosition().getZ() >> 4;
        if (cursor == 0) {
            // Wesen nach Chunk sortieren (1x pro Runde)
            entities = new HashMap<>();
            for (Entity e : mc.level.entitiesForRendering()) entities.computeIfAbsent(key(e.blockPosition().getX() >> 4, e.blockPosition().getZ() >> 4), k -> new ArrayList<>()).add(e);
            markers.keySet().removeIf(k -> Math.abs((int) (k >> 32) - mx) > r + 4 || Math.abs((int) (long) k - mz) > r + 4);
        }
        // natürliche Bauwerke (Trial Chamber, Ancient City, Dungeon ...) merken
        for (int dx = -r - 2; dx <= r + 2; dx++)
            for (int dz = -r - 2; dz <= r + 2; dz++) {
                int cx = mx + dx, cz = mz + dz;
                if (Math.floorMod(cx * 31 + cz * 17, 20) != cursor || !mc.level.hasChunk(cx, cz)) continue;
                markers.put(key(cx, cz), hasMarker(mc.level.getChunk(cx, cz)));
            }
        if (!primed) {
            // erste Runde: nur Bauwerke einsammeln, noch nichts melden (sonst Fehlalarm bei Trial Chambers)
            cursor = (cursor + 1) % 20;
            if (cursor == 0) primed = true;
            return;
        }
        int skipped = skippedRound;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                int cx = mx + dx, cz = mz + dz;
                if (!mc.level.hasChunk(cx, cz)) continue;
                LevelChunk lc = mc.level.getChunk(cx, cz);
                long key = key(cx, cz);
                // jeder Chunk nur alle ~5 s neu bewerten, verteilt auf mehrere Ticks (weniger Lag)
                if (Math.floorMod(cx * 31 + cz * 17, 20) != cursor) continue;
                boolean structure = nearStructure(cx, cz);
                Found f = rate(lc, cx, cz, entities, structure);
                if (f == null && structure) skipped++;
                if (f == null) { found.remove(key); continue; }
                found.put(key, f);
                if (reported.add(key) && chat.on())
                    Chat.info("§6ChunkFinder: §fChunk bei " + (cx * 16 + 8) + " " + (cz * 16 + 8) + " §7– " + f.what());
            }
        }
        skippedRound = skipped;
        cursor = (cursor + 1) % 20;
        if (cursor == 0) { skippedStructures = skippedRound; skippedRound = 0; }
        // weit weg -> vergessen
        found.values().removeIf(f -> Math.abs(f.cx() - mx) > r + 2 || Math.abs(f.cz() - mz) > r + 2);
    }

    @Override
    public void onHud(GuiGraphicsExtractor g, float partial) {
        if (!inGame() || found.isEmpty()) return;
        Found nearest = null;
        double best = Double.MAX_VALUE;
        for (Found f : found.values()) {
            double x0 = f.cx() * 16, z0 = f.cz() * 16;
            int col = f.score() >= 20 ? 0xFFFF3030 : f.score() >= 10 ? 0xFFFF9020 : 0xFFFFDD33;
            AABB box = new AABB(x0, f.minY(), z0, x0 + 16, f.maxY() + 1, z0 + 16);
            Draw.worldBox(g, box, col, false);
            double[] s = Projector.project(x0 + 8, f.maxY() + 2, z0 + 8);
            if (s != null) {
                String t = "§6Spieler-Spuren §f" + Math.round(f.score());
                g.text(mc.font, t, (int) s[0] - mc.font.width(t) / 2, (int) s[1], 0xFFFFFFFF, true);
            }
            double d = mc.player.distanceToSqr(x0 + 8, mc.player.getY(), z0 + 8);
            if (d < best) { best = d; nearest = f; }
        }
        if (arrows.on() && nearest != null) {
            double x = nearest.cx() * 16 + 8, z = nearest.cz() * 16 + 8;
            Arrows.draw(g, x, mc.player.getEyeY(), z, 0xFFFF9020, null, 64);
        }
    }

    @Override
    public String info() { return String.valueOf(found.size()); }
}
