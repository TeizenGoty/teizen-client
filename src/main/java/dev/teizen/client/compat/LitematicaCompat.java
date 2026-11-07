package dev.teizen.client.compat;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.world.SchematicWorldHandler;
import fi.dy.masa.litematica.world.WorldSchematic;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Wird nur geladen, wenn Litematica installiert ist. */
public final class LitematicaCompat {
    private LitematicaCompat() {}

    /** Nur Selbsttest: statt Litematica diese Blöcke als Schematic nehmen (Luft-Stellen als AIR eintragen). */
    public static java.util.Map<BlockPos, BlockState> testOverride;

    /** Block aus dem Schematic-Hologramm an dieser Stelle (oder null). */
    public static BlockState schematicState(BlockPos pos) {
        if (testOverride != null) return testOverride.get(pos);
        WorldSchematic w = SchematicWorldHandler.getSchematicWorld();
        if (w == null) return null;
        return w.getBlockState(pos);
    }

    /** Schild-Text (vorne, 4 Zeilen) aus der Schematic, oder null. */
    public static String[] signLines(BlockPos pos) {
        try {
            if (testOverride != null) return null;
            WorldSchematic w = SchematicWorldHandler.getSchematicWorld();
            if (w == null) return null;
            if (!(w.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.SignBlockEntity s)) return null;
            String[] l = new String[4];
            boolean any = false;
            for (int i = 0; i < 4; i++) {
                l[i] = s.getFrontText().getMessage(i, false).getString();
                if (!l[i].isEmpty()) any = true;
            }
            return any ? l : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Ordner, in dem Litematica seine Schematics sucht (.minecraft/schematics). */
    public static java.nio.file.Path schematicsDir() {
        return DataManager.getSchematicsBaseDirectory();
    }

    /** Lädt eine .litematic-Datei und platziert sie an {@code origin}. @return Fehlertext oder null. */
    public static String loadAndPlace(String fileName, BlockPos origin) {
        java.nio.file.Path dir = schematicsDir();
        String name = fileName.endsWith(".litematic") ? fileName : fileName + ".litematic";
        java.nio.file.Path file = dir.resolve(name);
        if (!java.nio.file.Files.isRegularFile(file)) {
            // auch in Unterordnern suchen
            try (var stream = java.nio.file.Files.walk(dir, 4)) {
                file = stream.filter(f -> f.getFileName().toString().equalsIgnoreCase(name)).findFirst().orElse(null);
            } catch (Exception e) {
                file = null;
            }
            if (file == null) return "Datei nicht gefunden: " + name + " (in " + dir + ")";
        }
        LitematicaSchematic schem = LitematicaSchematic.createFromFile(file.getParent(), file.getFileName().toString());
        if (schem == null) return "Konnte " + name + " nicht lesen.";
        String base = name.substring(0, name.length() - ".litematic".length());
        SchematicPlacement pl = SchematicPlacement.createFor(schem, origin, base, true, true);
        DataManager.getSchematicPlacementManager().addSchematicPlacement(pl, true);
        return null;
    }

    /** Gibt es schon eine Platzierung mit diesem Namen? */
    public static boolean hasPlacement(String name) {
        for (SchematicPlacement pl : DataManager.getSchematicPlacementManager().getAllSchematicsPlacements())
            if (pl.getName().equalsIgnoreCase(name)) return true;
        return false;
    }

    /**
     * Platzierung(en) entfernen (Name ohne Endung, null = alle).
     * @param blocks gefüllt mit allen Positionen, an denen schon echt der Schematic-Block steht (oder null)
     * @return Anzahl entfernter Platzierungen
     */
    public static int removePlacement(String name, java.util.List<BlockPos> blocks) {
        var mgr = DataManager.getSchematicPlacementManager();
        java.util.List<SchematicPlacement> hit = new java.util.ArrayList<>();
        for (SchematicPlacement pl : mgr.getAllSchematicsPlacements())
            if (name == null || pl.getName().equalsIgnoreCase(name)) hit.add(pl);
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (blocks != null && mc.level != null) {
            for (SchematicPlacement pl : hit)
                for (fi.dy.masa.litematica.selection.Box b : pl.getSubRegionBoxes(fi.dy.masa.litematica.schematic.placement.SubRegionPlacement.RequiredEnabled.PLACEMENT_ENABLED).values()) {
                    BlockPos a = b.getPos1(), c = b.getPos2();
                    if (a == null || c == null) continue;
                    for (BlockPos p : BlockPos.betweenClosed(a, c)) {
                        BlockState want = schematicState(p);
                        if (want == null || want.isAir()) continue;
                        if (mc.level.getBlockState(p) == want) blocks.add(p.immutable());
                    }
                }
        }
        int n = 0;
        for (SchematicPlacement pl : hit) {
            try {
                boolean done = false;
                for (java.lang.reflect.Method m : mgr.getClass().getMethods()) {
                    if (!m.getName().equals("removeSchematicPlacement")) continue;
                    Class<?>[] t = m.getParameterTypes();
                    if (t.length == 1 && t[0].isInstance(pl)) { m.invoke(mgr, pl); done = true; break; }
                    if (t.length == 2 && t[0].isInstance(pl) && t[1] == boolean.class) { m.invoke(mgr, pl, true); done = true; break; }
                }
                if (done) n++;
            } catch (Throwable e) {
                dev.teizen.client.TeizenClient.LOG.warn("Platzierung entfernen: " + e);
            }
        }
        return n;
    }

    public static int placementCount() {
        return DataManager.getSchematicPlacementManager().getAllSchematicsPlacements().size();
    }

    public static boolean hasSchematic() {
        if (testOverride != null) return true;
        return SchematicWorldHandler.getSchematicWorld() != null;
    }

    /** Alle Bereiche (min/max x,y,z) der eingeschalteten Platzierungen – einmal pro Tick holen. */
    public static java.util.List<int[]> regionBoxes() {
        java.util.List<int[]> out = new java.util.ArrayList<>();
        if (testOverride != null && !testOverride.isEmpty()) {
            int[] b = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
            for (BlockPos p : testOverride.keySet()) {
                b[0] = Math.min(b[0], p.getX()); b[1] = Math.min(b[1], p.getY()); b[2] = Math.min(b[2], p.getZ());
                b[3] = Math.max(b[3], p.getX()); b[4] = Math.max(b[4], p.getY()); b[5] = Math.max(b[5], p.getZ());
            }
            out.add(b);
            return out;
        }
        for (SchematicPlacement pl : DataManager.getSchematicPlacementManager().getAllSchematicsPlacements()) {
            if (!pl.isEnabled() || !pl.isRenderingEnabled()) continue;
            for (fi.dy.masa.litematica.selection.Box b : pl.getSubRegionBoxes(fi.dy.masa.litematica.schematic.placement.SubRegionPlacement.RequiredEnabled.PLACEMENT_ENABLED).values()) {
                BlockPos a = b.getPos1(), c = b.getPos2();
                if (a == null || c == null) continue;
                out.add(new int[]{Math.min(a.getX(), c.getX()), Math.min(a.getY(), c.getY()), Math.min(a.getZ(), c.getZ()),
                        Math.max(a.getX(), c.getX()), Math.max(a.getY(), c.getY()), Math.max(a.getZ(), c.getZ())});
            }
        }
        return out;
    }

    /** Alle Schematic-Dateien im Ordner (auch Unterordner), relativer Name. */
    public static java.util.List<String> listSchematics() {
        java.util.List<String> out = new java.util.ArrayList<>();
        java.nio.file.Path dir = schematicsDir();
        if (dir == null || !java.nio.file.Files.isDirectory(dir)) return out;
        try (var stream = java.nio.file.Files.walk(dir, 6)) {
            stream.filter(java.nio.file.Files::isRegularFile).forEach(f -> {
                String n = f.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
                if (n.endsWith(".litematic") || n.endsWith(".schem") || n.endsWith(".schematic") || n.endsWith(".nbt"))
                    out.add(dir.relativize(f).toString().replace('\\', '/'));
            });
        } catch (Exception e) {
            dev.teizen.client.TeizenClient.LOG.warn("Schematics lesen ging nicht", e);
        }
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return out;
    }
}
