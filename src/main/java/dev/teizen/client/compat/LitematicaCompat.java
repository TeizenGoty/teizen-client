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

    /** Block aus dem Schematic-Hologramm an dieser Stelle (oder null). */
    public static BlockState schematicState(BlockPos pos) {
        WorldSchematic w = SchematicWorldHandler.getSchematicWorld();
        if (w == null) return null;
        return w.getBlockState(pos);
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

    public static int placementCount() {
        return DataManager.getSchematicPlacementManager().getAllSchematicsPlacements().size();
    }

    public static boolean hasSchematic() {
        return SchematicWorldHandler.getSchematicWorld() != null;
    }

    /** Alle Bereiche (min/max x,y,z) der eingeschalteten Platzierungen – einmal pro Tick holen. */
    public static java.util.List<int[]> regionBoxes() {
        java.util.List<int[]> out = new java.util.ArrayList<>();
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
