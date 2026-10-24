package dev.teizen.client.modules;

import dev.teizen.client.TeizenClient;
import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.util.Inv;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** Hält ein Schild in der linken Hand und blockt automatisch bei Pfeilen oder nahen Gegnern. */
public final class AutoShield extends Module {
    private final Setting mode = add(Setting.choice("modus", "Wann geblockt wird: pfeile / gegner / aus (nur Schild halten)", "pfeile", "pfeile", "gegner", "aus"));
    private final Setting range = add(Setting.number("reichweite", "Gegner/Geschosse in dieser Entfernung (Blöcke)", 4, 1, 16));
    private final Setting totemFirst = add(Setting.bool("totemzuerst", "Wenn AutoTotem an ist: Totem hat Vorrang vor dem Schild", true));

    public boolean blockedOnce;
    private boolean blocking;
    private int pause;

    public AutoShield() {
        super("AutoShield", Category.KAMPF, "Schild immer links und automatisch blocken.");
    }

    public static boolean isShield(ItemStack s) {
        return !s.isEmpty() && s.has(DataComponents.BLOCKS_ATTACKS);
    }

    @Override
    protected void onDisable() { stopBlocking(); }

    @Override
    public void onTick() {
        if (!inGame()) return;
        ItemStack off = mc.player.getOffhandItem();
        // 1) Schild in die linke Hand
        AutoTotem at = TeizenClient.modules().get(AutoTotem.class);
        boolean totemWins = totemFirst.on() && at != null && at.isEnabled()
                && (off.is(Items.TOTEM_OF_UNDYING) || Inv.findAny(s -> s.is(Items.TOTEM_OF_UNDYING)) >= 0);
        boolean screenOk = mc.gui.screen() == null || mc.gui.screen() instanceof InventoryScreen;
        if (!totemWins && !isShield(off) && screenOk && mc.player.containerMenu == mc.player.inventoryMenu) {
            int slot = Inv.findAny(AutoShield::isShield);
            if (slot >= 0) Inv.swapToOffhand(slot);
            return;
        }
        // 2) Blocken
        boolean want = false;
        if (isShield(off) && !mode.is("aus") && mc.gui.screen() == null) {
            boolean busy = mc.player.isUsingItem() && mc.player.getUsedItemHand() == InteractionHand.MAIN_HAND;
            if (!busy) {
                if (mode.is("gegner")) want = Targets.nearest(range.num(), true, true, false, false) != null;
                if (!want) want = projectileIncoming();
            }
            if (mc.options.keyAttack.isDown()) pause = 6; // selbst schlagen geht vor
            if (pause > 0) { pause--; want = false; }
        }
        if (want) {
            if (!mc.player.isUsingItem()) mc.gameMode.useItem(mc.player, InteractionHand.OFF_HAND);
            mc.options.keyUse.setDown(true);
            blocking = true;
            if (mc.player.isUsingItem() && isShield(mc.player.getUseItem())) blockedOnce = true;
        } else if (blocking) {
            stopBlocking();
        }
    }

    private void stopBlocking() {
        if (!blocking) return;
        blocking = false;
        mc.options.keyUse.setDown(false);
        if (mc.player != null && mc.player.isUsingItem() && mc.player.getUsedItemHand() == InteractionHand.OFF_HAND)
            mc.gameMode.releaseUsingItem(mc.player);
    }

    /** Fliegt ein Pfeil/Feuerball usw. auf uns zu? */
    private boolean projectileIncoming() {
        double r = range.num() + 4;
        Vec3 me = mc.player.getBoundingBox().getCenter();
        for (Projectile p : mc.level.getEntitiesOfClass(Projectile.class, mc.player.getBoundingBox().inflate(r))) {
            if (p.getOwner() == mc.player) continue;
            Vec3 v = p.getDeltaMovement();
            if (v.lengthSqr() < 0.04) continue; // liegt still (steckt z.B. im Boden)
            Vec3 to = me.subtract(p.position());
            if (to.length() > r) continue;
            if (v.normalize().dot(to.normalize()) > 0.8) return true;
        }
        return false;
    }

    @Override
    public String info() { return mode.str(); }
}
