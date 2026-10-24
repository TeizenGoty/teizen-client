package dev.teizen.client.compat;

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

    public static boolean hasSchematic() {
        return SchematicWorldHandler.getSchematicWorld() != null;
    }
}
