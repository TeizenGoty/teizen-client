package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.util.Breaker;
import dev.teizen.client.util.Ids;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.Set;

/** Baut automatisch alle Blöcke um dich herum ab. */
public final class Nuker extends Module {
    private final Setting radius = add(Setting.number("radius", "Radius in Blöcken", 4, 1, 6));
    private final Setting mode = add(Setting.choice("modus", "alles / erze / liste", "alles", "alles", "erze", "liste"));
    private final Setting list = add(Setting.text("liste", "Blöcke für Modus 'liste'", "stone,deepslate,dirt"));
    private final Setting below = add(Setting.bool("drunter", "Auch Blöcke unter dir abbauen", false));
    private final Setting perTick = add(Setting.number("protick", "Blöcke pro Tick (nur Kreativ)", 4, 1, 40));

    private BlockPos current;
    private Set<Block> listBlocks = new HashSet<>();
    private String lastList = "";

    public Nuker() {
        super("Nuker", Category.ABBAU, "Baut alles um dich herum automatisch ab.");
    }

    private boolean wanted(BlockPos p, BlockState s) {
        if (s.isAir() || !s.getFluidState().isEmpty() || s.getDestroySpeed(mc.level, p) < 0) return false;
        if (!below.on() && p.getY() < mc.player.getBlockY()) return false;
        if (mode.is("erze")) return Ids.isOre(s);
        if (mode.is("liste")) return listBlocks.contains(s.getBlock());
        return true;
    }

    @Override
    protected void onDisable() {
        Breaker.stop();
        current = null;
    }

    @Override
    public void onTick() {
        if (!inGame() || mc.gui.screen() != null) return;
        if (!list.str().equals(lastList)) {
            Set<Block> s = new HashSet<>();
            for (String p : list.str().split(",")) s.addAll(Ids.resolve(p.trim()));
            listBlocks = s;
            lastList = list.str();
        }
        boolean creative = mc.player.isCreative();
        if (current != null && (!wanted(current, mc.level.getBlockState(current)) || !Breaker.inReach(current))) {
            Breaker.stop();
            current = null;
        }
        int r = radius.integer();
        BlockPos me = mc.player.blockPosition();
        int done = 0;
        if (current == null || creative) {
            BlockPos best = null;
            double bestD = Double.MAX_VALUE;
            for (BlockPos p : BlockPos.betweenClosed(me.offset(-r, -r, -r), me.offset(r, r + 1, r))) {
                BlockState s = mc.level.getBlockState(p);
                if (!wanted(p, s) || !Breaker.inReach(p)) continue;
                if (creative) {
                    Breaker.tick(p.immutable(), true, false);
                    if (++done >= perTick.integer()) return;
                    continue;
                }
                double d = p.distSqr(me);
                if (d < bestD) { bestD = d; best = p.immutable(); }
            }
            if (creative) return;
            current = best;
        }
        if (current != null && Breaker.tick(current, true, true)) current = null;
    }
}
