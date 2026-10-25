package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.util.Draw;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * HoleESP: zeigt sichere Löcher gegen End-Kristalle und Anker – 1 Block groß, rundherum und unten aus Grundgestein
 * oder Obsidian. Grün = nur Grundgestein (ganz sicher), gelb = mit Obsidian.
 */
public final class HoleESP extends Module {
    private final Setting radius = add(Setting.number("radius", "Wie weit gesucht wird (Blöcke)", 10, 3, 24));
    private final Setting fill = add(Setting.bool("fuellen", "Löcher farbig füllen", true));

    public record Hole(BlockPos pos, boolean bedrock) {}

    public final List<Hole> holes = new ArrayList<>();

    public HoleESP() {
        super("HoleESP", Category.SICHT, "Zeigt sichere Löcher gegen Kristalle (Grundgestein grün, Obsidian gelb).");
    }

    private static int kind(BlockState s) {
        if (s.is(Blocks.BEDROCK)) return 2;
        if (s.is(Blocks.OBSIDIAN) || s.is(Blocks.CRYING_OBSIDIAN) || s.is(Blocks.NETHERITE_BLOCK) || s.is(Blocks.RESPAWN_ANCHOR)
                || s.is(Blocks.ENDER_CHEST) || s.is(Blocks.ANCIENT_DEBRIS) || s.is(Blocks.ENCHANTING_TABLE) || s.is(Blocks.ANVIL)) return 1;
        return 0;
    }

    private boolean air(BlockPos p) {
        return mc.level.getBlockState(p).getCollisionShape(mc.level, p).isEmpty();
    }

    @Override
    public void onTick() {
        if (!inGame() || mc.player.tickCount % 10 != 0) return;
        holes.clear();
        int r = radius.integer();
        BlockPos c = mc.player.blockPosition();
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-r, -r / 2 - 2, -r), c.offset(r, r / 2, r))) {
            if (!air(p) || !air(p.above()) || !air(p.above(2))) continue;
            int below = kind(mc.level.getBlockState(p.below()));
            if (below == 0) continue;
            boolean allBedrock = below == 2;
            boolean ok = true;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                int k = kind(mc.level.getBlockState(p.relative(d)));
                if (k == 0) { ok = false; break; }
                if (k != 2) allBedrock = false;
            }
            if (ok) holes.add(new Hole(p.immutable(), allBedrock));
        }
    }

    @Override
    public void onHud(GuiGraphicsExtractor g, float partial) {
        if (!inGame()) return;
        for (Hole h : holes) {
            int col = h.bedrock() ? 0xFF33FF55 : 0xFFFFDD33;
            BlockPos p = h.pos();
            AABB box = new AABB(p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 0.1, p.getZ() + 1);
            if (fill.on()) Draw.worldBox(g, box, Draw.alpha(col, 0.35), true);
            Draw.worldBox(g, box, col, false);
        }
    }

    @Override
    public String info() { return String.valueOf(holes.size()); }
}
