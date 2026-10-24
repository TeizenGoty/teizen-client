package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.util.Inv;
import net.minecraft.world.item.Items;

/** Legt automatisch ein Totem in die Zweithand. */
public final class AutoTotem extends Module {
    private final Setting delay = add(Setting.number("verzoegerung", "Ticks zwischen zwei Tauschvorgängen", 2, 0, 20));
    private int wait;

    public AutoTotem() {
        super("AutoTotem", Category.KAMPF, "Hält immer ein Totem in der linken Hand bereit.");
    }

    @Override
    public void onTick() {
        if (!inGame()) return;
        if (wait > 0) { wait--; return; }
        if (mc.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) return;
        if (mc.gui.screen() != null && !(mc.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen)) return;
        int slot = Inv.findAny(s -> s.is(Items.TOTEM_OF_UNDYING));
        if (slot < 0) return;
        Inv.swapToOffhand(slot);
        wait = delay.integer();
    }
}
