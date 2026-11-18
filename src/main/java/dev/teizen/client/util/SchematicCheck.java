package dev.teizen.client.util;

import dev.teizen.client.compat.LitematicaCompat;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Vergleicht die Welt mit dem Litematica-Hologramm und merkt sich die Fehler (für XRay, durch Wände sichtbar).
 * Sparsam: prüft pro Tick nur ein kleines Stück, merkt sich höchstens MAX Fehler und sortiert nur alle halbe
 * Sekunde – sonst laggt es bei einem großen, fast leeren Bau (da ist fast jeder Block "fehlt").
 */
public final class SchematicCheck {
    public enum Kind { FALSCH, FEHLT, ZUSTAND, ZUVIEL }

    private static final Minecraft mc = Minecraft.getInstance();
    /** So viele Blöcke pro Tick prüfen. */
    private static final int BUDGET = 15000;
    /** Höchstens so viele FEHLENDE Blöcke merken (bei einem leeren Bau ist fast alles "fehlt"). Falsche immer. */
    private static final int MAX = 20000;
    /** Erst dieser Umkreis um dich (damit das Nahe sofort da ist), dann der ganze. */
    private static final int NEAR = 16;
    /** So viele der nächsten Fehler pro Tick neu prüfen (richtig gesetzt -> sofort weg). */
    private static final int RECHECK = 400;

    private Map<BlockPos, Kind> shown = new HashMap<>();
    private Map<BlockPos, Kind> building = new HashMap<>();
    private List<BlockPos> nearest = new ArrayList<>();
    private int sortIn;
    private List<int[]> boxes = new ArrayList<>();
    private int boxIdx;
    private int x, y, z;
    private boolean started;
    private int fehlt;

    public static boolean available() {
        return FabricLoader.getInstance().isModLoaded("litematica");
    }

    public void clear() {
        shown = new HashMap<>();
        building = new HashMap<>();
        nearest = new ArrayList<>();
        started = false;
    }

    /** Was an dieser Stelle falsch ist, oder null wenn alles passt. */
    public static Kind check(BlockPos p) {
        BlockState want = LitematicaCompat.schematicState(p);
        if (want == null) return null;
        BlockState have = mc.level.getBlockState(p);
        if (want.isAir()) return have.isAir() || have.canBeReplaced() ? null : Kind.ZUVIEL;
        if (have.isAir() || have.canBeReplaced()) return have.getBlock() == want.getBlock() ? null : Kind.FEHLT;
        if (have.getBlock() != want.getBlock()) return Kind.FALSCH;
        return have.equals(want) ? null : Kind.ZUSTAND;
    }

    /** Neuen Durchlauf beginnen: Bereiche der Platzierungen, abgeschnitten auf den Umkreis. */
    private void restart(int radius) {
        boxes = new ArrayList<>();
        BlockPos me = mc.player.blockPosition();
        int minY = mc.level.getMinY(), maxY = mc.level.getMinY() + mc.level.getHeight() - 1;
        List<int[]> regions = LitematicaCompat.regionBoxes();
        for (int r : radius > NEAR ? new int[]{NEAR, radius} : new int[]{radius}) {
            for (int[] b : regions) {
                int[] c = {Math.max(b[0], me.getX() - r), Math.max(Math.max(b[1], minY), me.getY() - r), Math.max(b[2], me.getZ() - r),
                        Math.min(b[3], me.getX() + r), Math.min(Math.min(b[4], maxY), me.getY() + r), Math.min(b[5], me.getZ() + r)};
                if (c[0] <= c[3] && c[1] <= c[4] && c[2] <= c[5]) boxes.add(c);
            }
        }
        fehlt = 0;
        boxIdx = 0;
        if (!boxes.isEmpty()) { int[] c = boxes.get(0); x = c[0]; y = c[1]; z = c[2]; }
        building = new HashMap<>();
        started = true;
    }

    public void tick(int radius) {
        if (mc.level == null || mc.player == null || !available()) return;
        // die nächsten gezeigten Fehler neu prüfen: richtig gesetzt -> sofort weg
        int n = Math.min(RECHECK, nearest.size());
        for (int i = 0; i < n; i++) {
            BlockPos p = nearest.get(i);
            if (!shown.containsKey(p)) continue;
            Kind k = check(p);
            if (k == null) { shown.remove(p); building.remove(p); }
            else shown.put(p, k);
        }
        if (!started || boxIdx >= boxes.size()) {
            if (started) { shown = building; sortIn = 0; } // Durchlauf fertig -> neue Liste zeigen
            restart(radius);
            if (boxes.isEmpty()) { shown = new HashMap<>(); nearest = new ArrayList<>(); return; }
        }
        int budget = BUDGET;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        while (budget-- > 0 && boxIdx < boxes.size()) {
            int[] c = boxes.get(boxIdx);
            m.set(x, y, z);
            if (mc.level.isLoaded(m)) {
                Kind k = check(m);
                if (k != null && (k != Kind.FEHLT || fehlt < MAX) && building.size() < MAX * 3) {
                    BlockPos p = m.immutable();
                    if (building.put(p, k) == null && k == Kind.FEHLT) fehlt++;
                    shown.putIfAbsent(p, k);
                }
            }
            if (++x > c[3]) {
                x = c[0];
                if (++z > c[5]) {
                    z = c[2];
                    if (++y > c[4]) {
                        if (++boxIdx < boxes.size()) { int[] nx = boxes.get(boxIdx); x = nx[0]; y = nx[1]; z = nx[2]; }
                    }
                }
            }
        }
        // nur alle 10 Ticks nach Entfernung sortieren (nicht jedes Bild)
        if (--sortIn <= 0) {
            sortIn = 10;
            BlockPos me = mc.player.blockPosition();
            List<BlockPos> l = new ArrayList<>(shown.keySet());
            l.sort(Comparator.comparingDouble(p -> p.distSqr(me)));
            nearest = l;
        }
    }

    public Map<BlockPos, Kind> results() { return shown; }

    /** Fehler nach Entfernung sortiert (nächste zuerst, alle halbe Sekunde neu). */
    public List<BlockPos> nearest() { return nearest; }
}
