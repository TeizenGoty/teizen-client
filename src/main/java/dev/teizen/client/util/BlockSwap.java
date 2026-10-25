package dev.teizen.client.util;

import dev.teizen.client.mixin.MultiPlayerGameModeAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import java.util.function.Predicate;

/** Einen Block mit einem Item aus der Hotbar setzen und sofort wieder die alte Hand nehmen (Surround, AutoTrap). */
public final class BlockSwap {
    private static final Minecraft mc = Minecraft.getInstance();

    private BlockSwap() {}

    /** Steht an dieser Stelle ein Wesen (dann geht kein Block hin)? */
    public static boolean blockedByEntity(BlockPos p) {
        for (Entity e : mc.level.getEntities((Entity) null, new AABB(p), e -> e.isAlive() && e.blocksBuilding)) return true;
        return false;
    }

    /** @return true wenn gesetzt */
    public static boolean place(BlockPos pos, Predicate<ItemStack> item) {
        if (!Placer.replaceable(pos) || blockedByEntity(pos)) return false;
        if (Placer.findSupport(pos) == null) return false;
        int slot = Inv.findHotbar(item);
        if (slot < 0) return false;
        int prev = Inv.selected();
        Inv.select(slot);
        ((MultiPlayerGameModeAccessor) mc.gameMode).teizen$sendCarriedItem();
        boolean ok = Placer.place(pos, false);
        Inv.select(prev);
        return ok;
    }
}
