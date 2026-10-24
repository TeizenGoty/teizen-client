package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.util.Inv;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** Erntet reife Pflanzen und pflanzt sofort wieder an (Weizen, Karotten, Kartoffeln, Rote Bete, Netherwarzen). */
public final class AutoReplant extends Module {
    private final Setting range = add(Setting.number("reichweite", "Wie weit um dich herum (Blöcke)", 4.5, 1, 6));
    private final Setting harvest = add(Setting.bool("ernten", "Reife Pflanzen automatisch ernten", true));
    private final Setting perTick = add(Setting.number("protick", "Aktionen pro Tick", 2, 1, 10));

    /** Boden-Position -> was dort wachsen soll. */
    private final Map<BlockPos, Item> toPlant = new HashMap<>();
    /** Gesehene Pflanzen (auch selbst geerntete werden so wieder angepflanzt). */
    private final Map<BlockPos, Item> crops = new HashMap<>();
    public int harvested, planted;

    public AutoReplant() {
        super("AutoReplant", Category.SONSTIGES, "Erntet reife Pflanzen und pflanzt automatisch neu.");
    }

    @Override
    protected void onDisable() {
        toPlant.clear();
        crops.clear();
    }

    private static boolean isCrop(BlockState s) {
        return s.getBlock() instanceof CropBlock || s.is(Blocks.NETHER_WART);
    }

    private static boolean mature(BlockState s) {
        if (s.getBlock() instanceof CropBlock c) return c.isMaxAge(s);
        if (s.is(Blocks.NETHER_WART)) return s.getValue(NetherWartBlock.AGE) >= 3;
        return false;
    }

    private static boolean soil(BlockState s, Item seed) {
        return seed == Blocks.NETHER_WART.asItem() ? s.is(Blocks.SOUL_SAND) : s.is(Blocks.FARMLAND);
    }

    @Override
    public void onTick() {
        if (!inGame() || mc.gui.screen() != null) return;
        double r = range.num();
        int ri = (int) Math.ceil(r);
        Vec3 eye = mc.player.getEyePosition();
        BlockPos c = mc.player.blockPosition();
        int actions = perTick.integer();

        // Pflanzen merken / verschwundene zum Nachpflanzen vormerken
        if (mc.player.tickCount % 5 == 0) {
            for (BlockPos p : BlockPos.betweenClosed(c.offset(-ri, -2, -ri), c.offset(ri, 2, ri))) {
                BlockState s = mc.level.getBlockState(p);
                if (isCrop(s)) crops.put(p.immutable(), s.getBlock().asItem());
            }
        }
        for (Iterator<Map.Entry<BlockPos, Item>> it = crops.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<BlockPos, Item> e = it.next();
            BlockState s = mc.level.getBlockState(e.getKey());
            if (isCrop(s)) continue;
            it.remove();
            if (s.isAir()) toPlant.put(e.getKey().below(), e.getValue());
        }

        // 1) nachpflanzen
        for (Iterator<Map.Entry<BlockPos, Item>> it = toPlant.entrySet().iterator(); it.hasNext() && actions > 0; ) {
            Map.Entry<BlockPos, Item> e = it.next();
            BlockPos ground = e.getKey();
            Item seed = e.getValue();
            if (!soil(mc.level.getBlockState(ground), seed) || !mc.level.getBlockState(ground.above()).isAir()) { it.remove(); continue; }
            if (eye.distanceToSqr(Vec3.atCenterOf(ground)) > r * r) continue;
            if (!Inv.ensureInHand(s -> s.is(seed))) continue;
            Vec3 hit = new Vec3(ground.getX() + 0.5, ground.getY() + 1.0, ground.getZ() + 0.5);
            if (mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(hit, Direction.UP, ground, false)).consumesAction()) {
                mc.player.swing(InteractionHand.MAIN_HAND);
                planted++;
                it.remove();
            }
            actions--;
        }

        // 2) reife Pflanzen ernten
        if (!harvest.on()) return;
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-ri, -2, -ri), c.offset(ri, 2, ri))) {
            if (actions <= 0) break;
            BlockState s = mc.level.getBlockState(p);
            if (!mature(s) || eye.distanceToSqr(Vec3.atCenterOf(p)) > r * r) continue;
            BlockPos pos = p.immutable();
            toPlant.put(pos.below(), s.getBlock().asItem());
            crops.remove(pos);
            mc.gameMode.startDestroyBlock(pos, Direction.UP);
            mc.player.swing(InteractionHand.MAIN_HAND);
            harvested++;
            actions--;
        }
    }

    @Override
    public String info() { return String.valueOf(planted); }
}
