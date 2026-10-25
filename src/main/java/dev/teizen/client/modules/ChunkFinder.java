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
        WEIGHTS.put("beehive", 3.0);
        WEIGHTS.put("jukebox", 3.0);
        WEIGHTS.put("enchanting_table", 3.0);
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
    }

    private int cursor;

    public ChunkFinder() {
        super("ChunkFinder", Category.SICHT, "Findet Chunks mit Spieler-Spuren (Basen).");
    }

    @Override
    protected void onEnable() {
        found.clear();
        reported.clear();
        cursor = 0;
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
            case "furnace" -> "Ofen";
            default -> key.replace("e:", "").replace('_', ' ');
        };
    }

    /** Einen Chunk bewerten. */
    private Found rate(LevelChunk chunk, int cx, int cz, Map<Long, List<Entity>> entities) {
        double score = 0;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        Map<String, Integer> count = new LinkedHashMap<>();
        for (BlockEntity be : chunk.getBlockEntities().values()) {
            var id = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType());
            if (id == null) continue;
            Double w = WEIGHTS.get(id.getPath());
            if (w == null) continue;
            score += w;
            count.merge(id.getPath(), 1, Integer::sum);
            BlockPos p = be.getBlockPos();
            minY = Math.min(minY, p.getY());
            maxY = Math.max(maxY, p.getY());
        }
        for (Entity e : entities.getOrDefault(key(cx, cz), List.of())) {
            var id = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType());
            Double w = WEIGHTS.get("e:" + id.getPath());
            if (w == null) continue;
            score += w;
            count.merge("e:" + id.getPath(), 1, Integer::sum);
            minY = Math.min(minY, e.blockPosition().getY());
            maxY = Math.max(maxY, e.blockPosition().getY());
        }
        if (score < minScore.num()) return null;
        StringBuilder what = new StringBuilder();
        count.entrySet().stream().sorted((a, b) -> Double.compare(WEIGHTS.get(b.getKey()) * b.getValue(), WEIGHTS.get(a.getKey()) * a.getValue()))
                .limit(4).forEach(en -> what.append(what.isEmpty() ? "" : ", ").append(en.getValue()).append("x ").append(label(en.getKey())));
        return new Found(cx, cz, score, minY, maxY, what.toString());
    }

    @Override
    public void onTick() {
        if (!inGame() || mc.player.tickCount % 20 != 0) return;
        int r = Math.max(2, Math.min(16, mc.options.renderDistance().get()));
        int mx = mc.player.blockPosition().getX() >> 4, mz = mc.player.blockPosition().getZ() >> 4;
        // Wesen nach Chunk sortieren
        Map<Long, List<Entity>> entities = new HashMap<>();
        for (Entity e : mc.level.entitiesForRendering()) entities.computeIfAbsent(key(e.blockPosition().getX() >> 4, e.blockPosition().getZ() >> 4), k -> new ArrayList<>()).add(e);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                int cx = mx + dx, cz = mz + dz;
                if (!mc.level.hasChunk(cx, cz)) continue;
                LevelChunk lc = mc.level.getChunk(cx, cz);
                long key = key(cx, cz);
                Found f = rate(lc, cx, cz, entities);
                if (f == null) { found.remove(key); continue; }
                found.put(key, f);
                if (reported.add(key) && chat.on())
                    Chat.info("§6ChunkFinder: §fChunk bei " + (cx * 16 + 8) + " " + (cz * 16 + 8) + " §7– " + f.what());
            }
        }
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
