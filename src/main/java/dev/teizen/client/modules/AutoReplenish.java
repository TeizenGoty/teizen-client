package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.util.Inv;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

/**
 * AutoReplenish: wird ein Stapel in der Hotbar klein (oder leer), wird er automatisch aus dem Inventar mit dem gleichen
 * Item aufgefüllt – z.B. Blöcke beim Bauen, Essen, Perlen, Pfeile.
 */
public final class AutoReplenish extends Module {
    private final Setting threshold = add(Setting.number("ab", "Auffüllen, wenn nur noch so viele im Stapel sind", 8, 1, 63));
    private final Setting pause = add(Setting.number("pause", "Ticks zwischen zwei Auffüll-Aktionen", 2, 0, 20));

    private final ItemStack[] last = new ItemStack[9];
    private int wait;
    public int refills;

    public AutoReplenish() {
        super("AutoReplenish", Category.SONSTIGES, "Füllt Hotbar-Stapel automatisch aus dem Inventar nach.");
    }

    @Override
    protected void onEnable() {
        for (int i = 0; i < 9; i++) last[i] = null;
        wait = 0;
    }

    /** Inventar-Slot (9-35) mit dem gleichen Item, am liebsten der größte Stapel; -1 = keiner. */
    private int source(ItemStack like) {
        int best = -1, bestCount = 0;
        for (int i = 9; i < 36; i++) {
            ItemStack s = Inv.inv().getItem(i);
            if (s.isEmpty() || !ItemStack.isSameItemSameComponents(s, like)) continue;
            if (s.getCount() > bestCount) { best = i; bestCount = s.getCount(); }
        }
        return best;
    }

    @Override
    public void onTick() {
        if (!inGame()) return;
        if (mc.gui.screen() != null || mc.player.isUsingItem() || !mc.player.containerMenu.getCarried().isEmpty()) return;
        if (wait > 0) { wait--; return; }
        for (int h = 0; h < 9; h++) {
            ItemStack s = Inv.inv().getItem(h);
            if (!s.isEmpty()) {
                last[h] = s.copyWithCount(1);
                if (s.getMaxStackSize() <= 1 || s.getCount() > threshold.num() || s.getCount() >= s.getMaxStackSize()) continue;
            } else if (last[h] == null) {
                continue;
            }
            ItemStack like = s.isEmpty() ? last[h] : s;
            int src = source(like);
            if (src < 0) {
                if (s.isEmpty()) last[h] = null; // nichts mehr da
                continue;
            }
            refill(src, h);
            refills++;
            wait = pause.integer();
            return; // eine Aktion pro Tick
        }
    }

    /** Stapel aus dem Inventar in die Hotbar legen/auffüllen: aufnehmen, ablegen, Rest zurück. */
    private void refill(int invSlot, int hotbar) {
        var p = mc.player;
        int id = p.inventoryMenu.containerId;
        int from = Inv.menuSlot(invSlot), to = Inv.menuSlot(hotbar);
        mc.gameMode.handleContainerInput(id, from, 0, ContainerInput.PICKUP, p);
        mc.gameMode.handleContainerInput(id, to, 0, ContainerInput.PICKUP, p);
        if (!p.containerMenu.getCarried().isEmpty()) mc.gameMode.handleContainerInput(id, from, 0, ContainerInput.PICKUP, p);
    }

    @Override
    public String info() { return String.valueOf(refills); }
}
