package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.util.Draw;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/** Kisten, Fässer, Shulker, Endertruhen und Spawner durch Wände sehen. */
public final class StorageESP extends Module {
    private final Setting radius = add(Setting.number("radius", "Radius in Chunks", 6, 1, 16));
    private final List<Object[]> found = new ArrayList<>();

    public StorageESP() {
        super("StorageESP", Category.SICHT, "Zeigt Kisten, Shulker und Spawner durch Wände.");
    }

    public static int color(BlockEntity be) {
        if (be instanceof EnderChestBlockEntity) return 0xFFB44CFF;
        if (be instanceof ShulkerBoxBlockEntity) return 0xFFFF6EC7;
        if (be instanceof BarrelBlockEntity) return 0xFFC08A4A;
        if (be instanceof SpawnerBlockEntity) return 0xFFFF3030;
        if (be instanceof ChestBlockEntity) return 0xFFFFB22E;
        return 0;
    }

    @Override
    public void onTick() {
        if (!inGame() || mc.player.tickCount % 20 != 0) return;
        found.clear();
        int r = radius.integer();
        int pcx = mc.player.blockPosition().getX() >> 4, pcz = mc.player.blockPosition().getZ() >> 4;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (!mc.level.hasChunk(pcx + dx, pcz + dz)) continue;
                LevelChunk ch = mc.level.getChunk(pcx + dx, pcz + dz);
                for (BlockEntity be : ch.getBlockEntities().values()) {
                    int c = color(be);
                    if (c != 0) found.add(new Object[]{be.getBlockPos(), c});
                }
            }
        }
    }

    @Override
    public void onHud(GuiGraphicsExtractor g, float partial) {
        for (Object[] o : found) Draw.worldBox(g, new AABB((BlockPos) o[0]), (Integer) o[1], true);
    }

    @Override
    public String info() { return String.valueOf(found.size()); }
}
