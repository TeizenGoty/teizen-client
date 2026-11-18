package dev.teizen.client.util;

import dev.teizen.client.compat.LitematicaCompat;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Vergleicht die Welt mit dem Litematica-Hologramm und merkt sich die Fehler (für XRay, durch Wände sichtbar).
 * Arbeitet in Häppchen über mehrere Ticks, damit es auch bei großen Bauten nicht ruckelt.
 */
public final class SchematicCheck {
    public enum Kind { FALSCH, FEHLT, ZUSTAND, ZUVIEL }

    private static final Minecraft mc = Minecraft.getInstance();
    /** So viele Blöcke pro Tick prüfen. */
    private static final int BUDGET = 40000;

    private Map<BlockPos, Kind> shown = new HashMap<>();
    private Map<BlockPos, Kind> building = new HashMap<>();
    private List<int[]> boxes = new ArrayList<>();
    private int boxIdx;
    private int x, y, z;
    private boolean started;

    public static boolean available() {
        return FabricLoader.getInstance().isModLoaded("litematica");
    }

    public void clear() {
        shown = new HashMap<>();
        building = new HashMap<>();
        started = false;
    }

    /** Was an dieser Stelle falsch ist, oder null wenn alles passt. */
    public static Kind check(BlockPos p) {
        BlockState want = LitematicaCompat.schematicState(p);
        if (want == null) return null;
        BlockState have = mc.level.getBlockState(p);
        boolean wantAir = want.isAir(), haveAir = have.isAir() || have.canBeReplaced();
        if (wantAir) return have.isAir() ? null : (have.canBeReplaced() ? null : Kind.ZUVIEL);
        if (haveAir) return have.getBlock() == want.getBlock() ? null : Kind.FEHLT;
        if (have.getBlock() != want.getBlock()) return Kind.FALSCH;
        return have.equals(want) ? null : Kind.ZUSTAND;
    }

    /** Neuen Durchlauf beginnen: Bereiche der Platzierungen, abgeschnitten auf den Umkreis. */
    private void restart(int radius) {
        boxes = new ArrayList<>();
        BlockPos me = mc.player.blockPosition();
        int minY = mc.level.getMinY(), maxY = mc.level.getMinY() + mc.level.getHeight() - 1;
        for (int[] b : LitematicaCompat.regionBoxes()) {
            int[] c = {Math.max(b[0], me.getX() - radius), Math.max(Math.max(b[1], minY), me.getY() - radius), Math.max(b[2], me.getZ() - radius),
                    Math.min(b[3], me.getX() + radius), Math.min(Math.min(b[4], maxY), me.getY() + radius), Math.min(b[5], me.getZ() + radius)};
            if (c[0] <= c[3] && c[1] <= c[4] && c[2] <= c[5]) boxes.add(c);
        }
        boxIdx = 0;
        if (!boxes.isEmpty()) { int[] c = boxes.get(0); x = c[0]; y = c[1]; z = c[2]; }
        building = new HashMap<>();
        started = true;
    }

    /** Einen Teil prüfen. Gefundene Fehler, die inzwischen richtig sind, verschwinden sofort. */
    public void tick(int radius) {
        if (mc.level == null || mc.player == null || !available()) return;
        // schon gezeigte Fehler sofort neu prüfen (richtig gesetzt -> weg, ohne auf den Durchlauf zu warten)
        if (!shown.isEmpty()) {
            Map<BlockPos, Kind> next = new HashMap<>();
            for (BlockPos p : shown.keySet()) {
                Kind k = check(p);
                if (k != null) next.put(p, k);
            }
            shown = next;
        }
        if (!started || boxIdx >= boxes.size()) {
            if (started) shown = building; // Durchlauf fertig -> neue Liste zeigen
            restart(radius);
            if (boxes.isEmpty()) { shown = new HashMap<>(); return; }
        }
        int budget = BUDGET;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        while (budget-- > 0 && boxIdx < boxes.size()) {
            int[] c = boxes.get(boxIdx);
            m.set(x, y, z);
            if (mc.level.isLoaded(m)) {
                Kind k = check(m);
                if (k != null) {
                    BlockPos p = m.immutable();
                    building.put(p, k);
                    shown.putIfAbsent(p, k);
                }
            }
            if (++x > c[3]) {
                x = c[0];
                if (++z > c[5]) {
                    z = c[2];
                    if (++y > c[4]) {
                        if (++boxIdx < boxes.size()) { int[] n = boxes.get(boxIdx); x = n[0]; y = n[1]; z = n[2]; }
                    }
                }
            }
        }
    }

    public Map<BlockPos, Kind> results() { return shown; }
}
