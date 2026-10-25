package dev.teizen.client.core;

import com.mojang.datafixers.util.Pair;
import dev.teizen.client.modules.StorageESP;
import dev.teizen.client.util.BlockScanner;
import dev.teizen.client.util.Chat;
import dev.teizen.client.util.Draw;
import dev.teizen.client.util.Ids;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** ///find – Blöcke, Kisten, Biome und Strukturen finden und markieren. */
public final class Finder {
    private static final Minecraft mc = Minecraft.getInstance();

    public record Marker(BlockPos pos, String label, int color) {}

    private static final List<Marker> markers = new ArrayList<>();
    private static BlockScanner scanner;
    private static String scanLabel;
    private static int scanColor;

    private Finder() {}

    public static void clear() {
        markers.clear();
        scanner = null;
    }

    public static List<Marker> markers() { return markers; }

    // ---------------------------------------------------------------- Blöcke

    public static void findBlocks(String name, int radius) {
        Set<Block> blocks = Ids.resolve(name);
        if (blocks.isEmpty()) {
            Chat.error("Unbekannter Block: " + name);
            return;
        }
        clear();
        scanner = new BlockScanner(s -> blocks.contains(s.getBlock()), radius, 400);
        scanLabel = name;
        scanColor = blocks.size() == 1 ? Ids.oreColor(blocks.iterator().next()) : 0xFFC08CFF;
        Chat.info("Suche §f" + name + "§7 im Umkreis von §f" + radius + "§7 Chunks (nur geladene Chunks)...");
    }

    public static void tick() {
        if (scanner == null || mc.level == null) return;
        for (int i = 0; i < 4 && scanner != null && !scanner.finishedOnce(); i++) scanner.tick();
        if (scanner != null && scanner.finishedOnce()) {
            List<BlockPos> found = new ArrayList<>(scanner.results());
            scanner = null;
            BlockPos me = mc.player.blockPosition();
            found.sort(Comparator.comparingDouble(p -> p.distSqr(me)));
            if (found.isEmpty()) {
                Chat.info("Nichts gefunden: §f" + scanLabel + "§7. Lauf weiter, dann werden mehr Chunks geladen.");
                return;
            }
            for (int i = 0; i < Math.min(found.size(), 500); i++) markers.add(new Marker(found.get(i), scanLabel, scanColor));
            Chat.ok(found.size() + "x " + scanLabel + " gefunden. Die nächsten:");
            for (int i = 0; i < Math.min(8, found.size()); i++) {
                BlockPos p = found.get(i);
                Chat.raw("  §f" + p.getX() + " " + p.getY() + " " + p.getZ() + " §7(" + Math.round(Math.sqrt(p.distSqr(me))) + "m)");
            }
        }
    }

    // ---------------------------------------------------------------- Kisten

    public static void findStorage(int radius) {
        markers.removeIf(m -> m.color() != scanColor);
        int pcx = mc.player.blockPosition().getX() >> 4, pcz = mc.player.blockPosition().getZ() >> 4;
        List<BlockPos> found = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (!mc.level.hasChunk(pcx + dx, pcz + dz)) continue;
                LevelChunk ch = mc.level.getChunk(pcx + dx, pcz + dz);
                for (BlockEntity be : ch.getBlockEntities().values()) {
                    int c = StorageESP.color(be);
                    if (c != 0) {
                        markers.add(new Marker(be.getBlockPos(), Ids.path(be.getBlockState().getBlock()), c));
                        found.add(be.getBlockPos());
                    }
                }
            }
        }
        BlockPos me = mc.player.blockPosition();
        found.sort(Comparator.comparingDouble(p -> p.distSqr(me)));
        Chat.ok(found.size() + " Kisten/Shulker/Spawner gefunden.");
        for (int i = 0; i < Math.min(8, found.size()); i++) {
            BlockPos p = found.get(i);
            Chat.raw("  §f" + Ids.path(mc.level.getBlockState(p).getBlock()) + " §7bei §f" + p.toShortString() + " §7(" + Math.round(Math.sqrt(p.distSqr(me))) + "m)");
        }
    }

    // ---------------------------------------------------------------- Biome

    public static void findBiome(String name, int radiusBlocks) {
        String q = name.toLowerCase(Locale.ROOT);
        IntegratedServer server = mc.getSingleplayerServer();
        BlockPos origin = mc.player.blockPosition();
        if (server != null) {
            var dim = mc.level.dimension();
            Chat.info("Suche Biom §f" + name + "§7 in der ganzen Welt...");
            server.execute(() -> {
                ServerLevel sl = server.getLevel(dim);
                if (sl == null) return;
                Pair<BlockPos, Holder<Biome>> res = sl.findClosestBiome3d(h -> matches(h, q), origin, radiusBlocks, 32, 64);
                mc.execute(() -> {
                    if (res == null) { Chat.info("Kein Biom §f" + name + "§7 im Umkreis von " + radiusBlocks + " Blöcken."); return; }
                    report(res.getFirst(), "Biom " + biomeName(res.getSecond()), 0xFF55FFAA);
                });
            });
            return;
        }
        // Server: nur geladene Chunks absuchen
        int r = mc.options.renderDistance().get();
        BlockPos best = null;
        Holder<Biome> bestH = null;
        double bestD = Double.MAX_VALUE;
        for (int dx = -r * 16; dx <= r * 16; dx += 8) {
            for (int dz = -r * 16; dz <= r * 16; dz += 8) {
                int x = origin.getX() + dx, z = origin.getZ() + dz;
                if (!mc.level.hasChunk(x >> 4, z >> 4)) continue;
                for (int y : new int[]{origin.getY(), 64, 0, -40}) {
                    BlockPos p = new BlockPos(x, y, z);
                    Holder<Biome> h = mc.level.getBiome(p);
                    if (matches(h, q)) {
                        double d = p.distSqr(origin);
                        if (d < bestD) { bestD = d; best = p; bestH = h; }
                        break;
                    }
                }
            }
        }
        if (best == null) Chat.info("Kein Biom §f" + name + "§7 in den geladenen Chunks. (Auf Servern geht nur die Sichtweite.)");
        else report(best, "Biom " + biomeName(bestH), 0xFF55FFAA);
    }

    private static boolean matches(Holder<Biome> h, String q) {
        return h.unwrapKey().map(k -> k.identifier().getPath().contains(q)).orElse(false);
    }

    private static String biomeName(Holder<Biome> h) {
        return h.unwrapKey().map(k -> k.identifier().getPath()).orElse("?");
    }

    // ---------------------------------------------------------------- Strukturen

    public static void findStructure(String name) {
        Chat.info("Suche §f" + Locator.label(name) + "§7" + (mc.getSingleplayerServer() == null ? " (per /locate, braucht OP)" : "") + "...");
        Locator.locate(name, (pos, label) -> report(pos, label, 0xFFFFAA00));
    }

    private static void report(BlockPos p, String label, int color) {
        markers.add(new Marker(p, label, color));
        BlockPos me = mc.player.blockPosition();
        double d = Math.sqrt(Math.pow(p.getX() - me.getX(), 2) + Math.pow(p.getZ() - me.getZ(), 2));
        Chat.ok(label + " §7bei §f" + p.getX() + " " + p.getY() + " " + p.getZ() + " §7(" + Math.round(d) + " Blöcke)");
    }

    // ---------------------------------------------------------------- Anzeige

    public static void hud(GuiGraphicsExtractor g) {
        if (markers.isEmpty() || mc.player == null) return;
        Vec3 eye = mc.player.getEyePosition();
        int shown = 0;
        Marker nearest = null;
        double nd = Double.MAX_VALUE;
        for (Marker m : markers) {
            double d = eye.distanceTo(Vec3.atCenterOf(m.pos()));
            if (d < nd) { nd = d; nearest = m; }
        }
        for (Marker m : markers) {
            if (shown++ > 400) break;
            double[] r = Draw.worldBox(g, new AABB(m.pos()), m.color(), true);
            if (shown <= 5 && m != nearest) Draw.tracer(g, m.pos().getX() + 0.5, m.pos().getY() + 0.5, m.pos().getZ() + 0.5, m.color());
            if (r != null && shown <= 30) {
                String t = m.label() + " " + Math.round(eye.distanceTo(Vec3.atCenterOf(m.pos()))) + "m";
                g.text(mc.font, t, (int) r[0], (int) r[1] - 10, m.color(), true);
            }
        }
        if (nearest != null) Draw.tracerNearest(g, nearest.pos().getX() + 0.5, nearest.pos().getY() + 0.5, nearest.pos().getZ() + 0.5,
                nearest.color(), nearest.label() + " " + Math.round(nd) + "m");
    }
}
