package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.util.Draw;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/** LightOverlay: zeigt, wo Monster spawnen können (rot = immer, gelb = nur nachts). */
public final class LightOverlay extends Module {
    private final Setting radius = add(Setting.number("radius", "So weit um dich herum (Blöcke)", 16, 4, 32));
    private final Setting night = add(Setting.bool("nachts", "Auch Stellen zeigen, wo nur nachts etwas spawnt (gelb)", true));
    public final List<long[]> spots = new ArrayList<>(); // x, y, z, 1 = immer / 0 = nachts
    private int scan;

    public LightOverlay() {
        super("LightOverlay", Category.SICHT, "Zeigt, wo Monster spawnen können (Licht 0).");
    }

    @Override
    public void onTick() {
        if (!inGame() || ++scan % 10 != 0) return;
        spots.clear();
        int r = radius.integer();
        BlockPos me = mc.player.blockPosition();
        for (BlockPos p : BlockPos.betweenClosed(me.offset(-r, -8, -r), me.offset(r, 8, r))) {
            if (spots.size() > 1500) break;
            var s = mc.level.getBlockState(p);
            if (!s.getCollisionShape(mc.level, p).isEmpty() || !s.getFluidState().isEmpty()) continue;
            if (!mc.level.getBlockState(p.above()).getCollisionShape(mc.level, p.above()).isEmpty()) continue;
            BlockPos below = p.below();
            var b = mc.level.getBlockState(below);
            if (!b.isFaceSturdy(mc.level, below, Direction.UP) || b.getBlock() instanceof net.minecraft.world.level.block.SlabBlock
                    || b.is(net.minecraft.world.level.block.Blocks.BEDROCK) || b.is(net.minecraft.world.level.block.Blocks.GLASS)) continue;
            if (mc.level.getBrightness(LightLayer.BLOCK, p) > 0) continue;
            boolean always = mc.level.getBrightness(LightLayer.SKY, p) == 0;
            if (!always && !night.on()) continue;
            spots.add(new long[]{p.getX(), p.getY(), p.getZ(), always ? 1 : 0});
        }
    }

    @Override
    public void onHud(GuiGraphicsExtractor g, float partial) {
        for (long[] s : spots) {
            int c = s[3] == 1 ? 0xC0FF3030 : 0xA0FFD030;
            Draw.worldBox(g, new AABB(s[0] + 0.3, s[1], s[2] + 0.3, s[0] + 0.7, s[1] + 0.02, s[2] + 0.7), c, true);
        }
    }

    @Override
    public String info() { return String.valueOf(spots.size()); }
}
