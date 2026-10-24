package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.util.Inv;
import dev.teizen.client.util.Placer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

/** Setzt beim Laufen automatisch Blöcke unter deine Füße (Brücken bauen, Türme beim Springen). */
public final class Scaffold extends Module {
    private final Setting tower = add(Setting.bool("turm", "Beim Springen nach oben bauen", true));
    private final Setting keepSlot = add(Setting.bool("slotzurueck", "Danach wieder den alten Slot nehmen", true));

    public Scaffold() {
        super("Scaffold", Category.BAUEN, "Baut automatisch Blöcke unter dich.");
    }

    private static boolean isBuildBlock(ItemStack s) {
        if (!(s.getItem() instanceof BlockItem bi)) return false;
        var st = bi.getBlock().defaultBlockState();
        return st.isCollisionShapeFullBlock(mc.level, BlockPos.ZERO) && !(bi.getBlock() instanceof net.minecraft.world.level.block.FallingBlock);
    }

    @Override
    public void onTick() {
        if (!inGame() || mc.gui.screen() != null) return;
        BlockPos below = BlockPos.containing(mc.player.getX(), mc.player.getY() - 0.5, mc.player.getZ());
        if (!Placer.replaceable(below)) return;
        int old = Inv.selected();
        int slot = Inv.findHotbar(Scaffold::isBuildBlock);
        if (slot < 0) return;
        Inv.select(slot);
        boolean placed = Placer.place(below, true);
        if (!placed) {
            // Ecke: erst einen Stützblock neben dem Ziel setzen
            for (var d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                BlockPos side = below.relative(d);
                if (Placer.replaceable(side) && Placer.place(side, true)) break;
            }
        }
        if (placed && tower.on() && mc.options.keyJump.isDown() && !mc.options.keyUp.isDown()) {
            var v = mc.player.getDeltaMovement();
            mc.player.setDeltaMovement(v.x * 0.3, 0.42, v.z * 0.3);
        }
        if (keepSlot.on()) Inv.select(old);
    }
}
