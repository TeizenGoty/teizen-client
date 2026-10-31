package dev.teizen.client.modules;

import dev.teizen.client.TeizenClient;
import dev.teizen.client.core.Module;
import dev.teizen.client.util.Draw;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.Map;

/** BreakIndicators: zeigt, welche Blöcke andere Spieler gerade abbauen (mit Fortschritt in %). */
public final class BreakIndicators extends Module {
    public record Breaking(BlockPos pos, int progress, long at) {}
    public final Map<Integer, Breaking> breaking = new HashMap<>();
    public int seen;

    public BreakIndicators() {
        super("BreakIndicators", Category.SICHT, "Zeigt, was andere Spieler gerade abbauen (mit Fortschritt).");
    }

    /** Aus ClientPacketListener.handleBlockDestruction (Mixin). progress 0-9, sonst = fertig/abgebrochen. */
    public static void onDestruction(int breakerId, BlockPos pos, int progress) {
        if (TeizenClient.modules() == null) return;
        BreakIndicators b = TeizenClient.modules().get(BreakIndicators.class);
        if (b == null || !b.isEnabled()) return;
        if (mc.player != null && breakerId == mc.player.getId()) return;
        if (progress < 0 || progress > 9) { b.breaking.remove(breakerId); return; }
        b.breaking.put(breakerId, new Breaking(pos.immutable(), progress, System.currentTimeMillis()));
        b.seen++;
    }

    @Override
    public void onHud(GuiGraphicsExtractor g, float partial) {
        long now = System.currentTimeMillis();
        breaking.values().removeIf(x -> now - x.at() > 3000 || mc.level.getBlockState(x.pos()).isAir());
        for (Breaking x : breaking.values()) {
            double f = (x.progress() + 1) / 10.0;
            int c = f < 0.5 ? 0xFFFFD040 : f < 0.8 ? 0xFFFF9030 : 0xFFFF3030;
            double s = 0.5 - f / 2;
            AABB box = new AABB(x.pos()).deflate(s);
            double[] r = Draw.worldBox(g, box, Draw.alpha(c, 0.55), true);
            Draw.worldBox(g, new AABB(x.pos()), c, false);
            if (r != null) g.text(mc.font, Math.round(f * 100) + "%", (int) ((r[0] + r[2]) / 2) - 8, (int) ((r[1] + r[3]) / 2) - 4, 0xFFFFFFFF, true);
        }
    }

    @Override
    public String info() { return breaking.isEmpty() ? "" : String.valueOf(breaking.size()); }
}
