package dev.teizen.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;

/**
 * Schematic sofort in die Welt kopieren (mit OP / in der eigenen Welt) – setzt die Blöcke mit /setblock,
 * pro Tick nur ein paar, damit der Server einen nicht rauswirft.
 */
public final class SchematicPaste {
    private static final Minecraft mc = Minecraft.getInstance();
    private static final Deque<BlockPos> queue = new ArrayDeque<>();
    private static int total, doneCount;

    private SchematicPaste() {}

    public static boolean running() { return !queue.isEmpty(); }
    public static int remaining() { return queue.size(); }

    /** Alle Blöcke der geladenen Schematic einsammeln und los. @return Anzahl oder -1 wenn nichts da. */
    public static int start() {
        if (!Op.available()) { Chat.error("Sofort einfügen braucht OP (oder deine eigene Welt). Ohne OP: ///autobuild (baut Block für Block)."); return -1; }
        queue.clear();
        for (int[] b : dev.teizen.client.compat.LitematicaCompat.regionBoxes()) {
            for (BlockPos p : BlockPos.betweenClosed(b[0], b[1], b[2], b[3], b[4], b[5])) {
                BlockState want = dev.teizen.client.compat.LitematicaCompat.schematicState(p);
                if (want == null || want.isAir()) continue;
                if (mc.level.getBlockState(p) == want) continue;
                queue.add(p.immutable());
            }
        }
        total = queue.size();
        doneCount = 0;
        if (total == 0) { Chat.info("Nichts einzufügen (oder keine Schematic geladen). Erst im Fenster eine auswählen/laden."); return 0; }
        Chat.ok("Füge " + total + " Blöcke ein ...");
        return total;
    }

    public static void cancel() { queue.clear(); }

    /** Jeden Tick aufrufen. */
    public static void tick() {
        if (queue.isEmpty() || mc.level == null) return;
        int batch = mc.getSingleplayerServer() != null ? 4000 : 48; // eigene Welt: schnell; Server: vorsichtig
        for (int i = 0; i < batch && !queue.isEmpty(); i++) {
            BlockPos p = queue.poll();
            BlockState want = dev.teizen.client.compat.LitematicaCompat.schematicState(p);
            if (want == null || want.isAir()) continue;
            Op.run("setblock " + p.getX() + " " + p.getY() + " " + p.getZ() + " " + stateString(want));
            doneCount++;
        }
        if (queue.isEmpty()) Chat.ok("Fertig eingefügt (" + doneCount + " Blöcke).");
    }

    /** BlockState als Befehls-Text, z.B. "minecraft:oak_stairs[facing=north,half=top]". */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static String stateString(BlockState s) {
        StringBuilder sb = new StringBuilder(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString());
        var props = s.getProperties();
        if (!props.isEmpty()) {
            sb.append('[');
            boolean first = true;
            for (Property prop : props) {
                if (!first) sb.append(',');
                first = false;
                sb.append(prop.getName()).append('=').append(prop.getName(s.getValue(prop)).toLowerCase(Locale.ROOT));
            }
            sb.append(']');
        }
        return sb.toString();
    }
}
