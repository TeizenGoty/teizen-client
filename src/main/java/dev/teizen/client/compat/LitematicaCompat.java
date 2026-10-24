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
}
