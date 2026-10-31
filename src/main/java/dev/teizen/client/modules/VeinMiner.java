package dev.teizen.client.modules;

import dev.teizen.client.TeizenClient;
import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.util.Breaker;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** VeinMiner: baust du einen Block einer Ader ab (Erz, Holz ...), werden alle verbundenen gleichen Blöcke mit abgebaut. */
public final class VeinMiner extends Module {
    private final Setting max = add(Setting.number("max", "Höchstens so viele Blöcke auf einmal", 64, 2, 256));
    private final Setting what = add(Setting.choice("was", "Welche Blöcke", "erzeholz", "erzeholz", "erze", "alles"));
    private final ArrayDeque<BlockPos> queue = new ArrayDeque<>();
    private Block vein;
    private BlockPos current;
    private int stuck;
    public int mined;
    private static boolean own;

    public VeinMiner() {
        super("VeinMiner", Category.ABBAU, "Baut die ganze Ader mit ab (Erze, Baumstamm …).");
    }

    @Override
    protected void onDisable() { queue.clear(); if (current != null) Breaker.stop(); current = null; }

    private boolean fits(Block b) {
        String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(b).getPath();
        boolean ore = id.endsWith("_ore") || id.equals("ancient_debris");
        boolean wood = id.endsWith("_log") || id.endsWith("_stem") || id.endsWith("_wood") || id.endsWith("_hyphae");
        return switch (what.str()) {
            case "erze" -> ore;
            case "alles" -> !b.defaultBlockState().isAir();
            default -> ore || wood;
        };
    }

    /** Mixin (MultiPlayerGameMode.destroyBlock, bevor der Block weg ist). */
    public static void onBreak(BlockPos pos) {
        if (own || TeizenClient.modules() == null || mc.level == null) return;
        VeinMiner v = TeizenClient.modules().get(VeinMiner.class);
        if (v == null || !v.isEnabled()) return;
        Block b = mc.level.getBlockState(pos).getBlock();
        if (!v.fits(b)) return;
        // Ader suchen (auch schräg verbunden)
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> open = new ArrayDeque<>();
        List<BlockPos> found = new ArrayList<>();
        open.add(pos.immutable());
        seen.add(pos.immutable());
        while (!open.isEmpty() && found.size() < v.max.integer()) {
            BlockPos p = open.poll();
            for (BlockPos q : BlockPos.betweenClosed(p.offset(-1, -1, -1), p.offset(1, 1, 1))) {
                BlockPos qi = q.immutable();
                if (seen.contains(qi) || qi.distSqr(pos) > 16 * 16) continue;
                seen.add(qi);
                if (mc.level.getBlockState(qi).getBlock() != b) continue;
                found.add(qi);
                open.add(qi);
            }
        }
        found.sort((x, y) -> Double.compare(x.distSqr(pos), y.distSqr(pos)));
        v.vein = b;
        v.queue.clear();
        v.queue.addAll(found);
    }

    @Override
    public void onTick() {
        if (!inGame()) return;
        if (current == null) {
            while (!queue.isEmpty()) {
                BlockPos p = queue.poll();
                if (mc.level.getBlockState(p).getBlock() == vein && Breaker.inReach(p)) { current = p; stuck = 0; break; }
            }
            if (current == null) return;
        }
        own = true;
        try {
            if (Breaker.tick(current, true, true) || ++stuck > 300 || mc.level.getBlockState(current).getBlock() != vein) {
                if (mc.level.getBlockState(current).isAir()) mined++;
                current = null;
            }
        } finally {
            own = false;
        }
    }

    @Override
    public String info() { return queue.isEmpty() && current == null ? "" : String.valueOf(queue.size() + 1); }
}
