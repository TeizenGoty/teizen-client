package dev.teizen.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Durchsucht geladene Chunks um den Spieler nach Blöcken – verteilt auf mehrere Ticks,
 * damit das Spiel nicht ruckelt. Ergebnisse werden laufend aktualisiert.
 */
public final class BlockScanner {
    private static final Minecraft mc = Minecraft.getInstance();

    /** Zusätzliche Prüfung mit Position (z.B. "unter der Oberfläche"). */
    public interface PosFilter {
        boolean test(LevelChunk chunk, int x, int y, int z, BlockState state);
    }

    private Predicate<BlockState> filter;
    private int radiusChunks;
    private final int sectionsPerTick;
    private PosFilter posFilter;
    /** Nur jeden n-ten Block prüfen (1 = alle). */
    private int step = 1;
    /** Höchstens so viele Treffer sammeln. */
    private int maxResults = Integer.MAX_VALUE;

    private final List<long[]> queue = new ArrayList<>(); // {chunkX, chunkZ}
    private int queueIndex;
    private Set<BlockPos> building = new HashSet<>();
    private volatile List<BlockPos> results = Collections.emptyList();
    private int centerX = Integer.MIN_VALUE, centerZ = Integer.MIN_VALUE;
    private boolean finishedOnce;

    public BlockScanner(Predicate<BlockState> filter, int radiusChunks, int sectionsPerTick) {
        this.filter = filter;
        this.radiusChunks = radiusChunks;
        this.sectionsPerTick = sectionsPerTick;
    }

    public void setFilter(Predicate<BlockState> f) { this.filter = f; restart(); }

    public BlockScanner posFilter(PosFilter f) { this.posFilter = f; return this; }

    public BlockScanner step(int s) { this.step = Math.max(1, s); return this; }

    public BlockScanner maxResults(int m) { this.maxResults = m; return this; }

    public void setRadius(int r) {
        if (r != radiusChunks) { radiusChunks = r; restart(); }
    }

    public void restart() {
        queue.clear();
        queueIndex = 0;
        building = new HashSet<>();
        centerX = Integer.MIN_VALUE;
        finishedOnce = false;
    }

    public void clear() { restart(); results = Collections.emptyList(); }

    public List<BlockPos> results() { return results; }

    public boolean finishedOnce() { return finishedOnce; }

    private void buildQueue(int pcx, int pcz) {
        queue.clear();
        queueIndex = 0;
        centerX = pcx;
        centerZ = pcz;
        for (int r = 0; r <= radiusChunks; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    queue.add(new long[]{pcx + dx, pcz + dz});
                }
            }
        }
    }

    /** Einmal pro Tick aufrufen. */
    public void tick() {
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || filter == null) return;
        int pcx = mc.player.blockPosition().getX() >> 4, pcz = mc.player.blockPosition().getZ() >> 4;
        if (centerX == Integer.MIN_VALUE) buildQueue(pcx, pcz);

        int budget = sectionsPerTick;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        while (budget > 0 && queueIndex < queue.size()) {
            long[] c = queue.get(queueIndex++);
            int cx = (int) c[0], cz = (int) c[1];
            if (!level.hasChunk(cx, cz)) continue;
            LevelChunk chunk = level.getChunk(cx, cz);
            LevelChunkSection[] sections = chunk.getSections();
            for (int i = 0; i < sections.length; i++) {
                LevelChunkSection s = sections[i];
                if (s == null || s.hasOnlyAir()) continue;
                budget--;
                if (!s.maybeHas(filter)) continue;
                int baseY = chunk.getSectionYFromSectionIndex(i) << 4;
                if (building.size() >= maxResults) continue;
                for (int y = 0; y < 16; y += step) {
                    for (int z = 0; z < 16; z += step) {
                        for (int x = 0; x < 16; x += step) {
                            BlockState st = s.getBlockState(x, y, z);
                            if (!filter.test(st)) continue;
                            int wx = (cx << 4) + x, wy = baseY + y, wz = (cz << 4) + z;
                            if (posFilter != null && !posFilter.test(chunk, wx, wy, wz, st)) continue;
                            building.add(new BlockPos(wx, wy, wz));
                        }
                    }
                }
            }
        }
        if (queueIndex >= queue.size()) {
            results = new ArrayList<>(building);
            building = new HashSet<>();
            finishedOnce = true;
            buildQueue(pcx, pcz);
        }
    }

    /** Entfernt Treffer, die nicht mehr passen (z.B. abgebaut). */
    public void validate() {
        ClientLevel level = mc.level;
        if (level == null) return;
        List<BlockPos> cur = results;
        if (cur.isEmpty()) return;
        List<BlockPos> keep = new ArrayList<>(cur.size());
        for (BlockPos p : cur) {
            if (!level.hasChunk(p.getX() >> 4, p.getZ() >> 4) || filter.test(level.getBlockState(p))) keep.add(p);
        }
        if (keep.size() != cur.size()) results = keep;
    }
}
