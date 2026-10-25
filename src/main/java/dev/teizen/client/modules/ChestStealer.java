package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;

/** Leert geöffnete Kisten automatisch in dein Inventar – standardmäßig alles sofort im selben Moment. */
public final class ChestStealer extends Module {
    private final Setting delay = add(Setting.number("pause", "Ticks zwischen zwei Items (0 = alles sofort auf einmal)", 0, 0, 10));
    private final Setting close = add(Setting.bool("schliessen", "Kiste danach schließen", true));
    private int wait;
    public int taken;

    public ChestStealer() {
        super("ChestStealer", Category.SONSTIGES, "Räumt Kisten automatisch aus.");
    }

    @Override
    public void onTick() {
        if (!inGame()) return;
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> scr) || scr instanceof InventoryScreen) return;
        if (wait-- > 0) return;
        AbstractContainerMenu menu = mc.player.containerMenu;
        int containerSlots = menu.slots.size() - 36;
        if (containerSlots <= 0) return;
        boolean moved = false;
        for (int i = 0; i < containerSlots; i++) {
            Slot s = menu.slots.get(i);
            if (s.hasItem()) {
                mc.gameMode.handleContainerInput(menu.containerId, i, 0, ContainerInput.QUICK_MOVE, mc.player);
                taken++;
                moved = true;
                if (delay.integer() > 0) {
                    wait = delay.integer();
                    return;
                }
            }
        }
        // alles genommen (oder Inventar voll) -> schließen
        if (close.on() && (!moved || delay.integer() == 0)) mc.player.closeContainer();
    }
}
