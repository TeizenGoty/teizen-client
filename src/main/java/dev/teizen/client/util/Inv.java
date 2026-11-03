package dev.teizen.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Predicate;

/** Inventar-Helfer. Slots 0-8 = Hotbar, 9-35 = Inventar. */
public final class Inv {
    private static final Minecraft mc = Minecraft.getInstance();

    private Inv() {}

    public static Inventory inv() { return mc.player.getInventory(); }

    public static int findHotbar(Predicate<ItemStack> p) {
        for (int i = 0; i < 9; i++) if (p.test(inv().getItem(i))) return i;
        return -1;
    }

    public static int findAny(Predicate<ItemStack> p) {
        int h = findHotbar(p);
        if (h >= 0) return h;
        for (int i = 9; i < 36; i++) if (p.test(inv().getItem(i))) return i;
        return -1;
    }

    public static int selected() { return inv().getSelectedSlot(); }

    public static void select(int hotbarSlot) {
        if (hotbarSlot >= 0 && hotbarSlot < 9) inv().setSelectedSlot(hotbarSlot);
    }

    /** Inventar-Slot -> Slot-Nummer im Spieler-Inventar-Menü. */
    public static int menuSlot(int invSlot) {
        return invSlot < 9 ? 36 + invSlot : invSlot;
    }

    /** Tauscht einen Inventar-Slot mit einem Hotbar-Slot (wie Zahlentaste im Inventar). */
    public static void swapToHotbar(int invSlot, int hotbarSlot) {
        LocalPlayer p = mc.player;
        mc.gameMode.handleContainerInput(p.inventoryMenu.containerId, menuSlot(invSlot), hotbarSlot, ContainerInput.SWAP, p);
    }

    /** Tauscht einen Inventar-Slot mit der Zweithand (Taste F im Inventar). */
    public static void swapToOffhand(int invSlot) {
        LocalPlayer p = mc.player;
        mc.gameMode.handleContainerInput(p.inventoryMenu.containerId, menuSlot(invSlot), 40, ContainerInput.SWAP, p);
    }

    /**
     * Sorgt dafür, dass ein passendes Item in der Hand ist.
     * @return true wenn danach ein passendes Item ausgewählt ist
     */
    public static boolean ensureInHand(Predicate<ItemStack> p) {
        if (p.test(inv().getSelectedItem())) return true;
        int h = findHotbar(p);
        if (h >= 0) { select(h); return true; }
        int any = findAny(p);
        if (any < 0) return false;
        int target = selected();
        swapToHotbar(any, target);
        return p.test(inv().getItem(target));
    }

    /** Bestes Werkzeug für einen Block in der Hotbar (oder -1). */
    public static int bestTool(BlockState state) {
        int best = -1;
        float bestSpeed = 1.0f;
        for (int i = 0; i < 9; i++) {
            ItemStack s = inv().getItem(i);
            if (s.isEmpty()) continue;
            float sp = s.getDestroySpeed(state);
            if (sp > bestSpeed) { bestSpeed = sp; best = i; }
        }
        return best;
    }

    /**
     * Bestes Werkzeug auch aus dem Inventar holen: ist im Rucksack ein besseres als in der Hotbar (z.B. weil Essen die
     * Spitzhacke aus der Hotbar geschoben hat), wird es in die Hotbar getauscht – in einen freien Platz, sonst in den
     * aktuellen. @return Hotbar-Slot oder -1
     */
    public static int bestToolAnywhere(BlockState state) {
        int hot = bestTool(state);
        float hotSpeed = hot >= 0 ? inv().getItem(hot).getDestroySpeed(state) : 1.0f;
        int best = -1;
        float bestSpeed = hotSpeed;
        for (int i = 9; i < 36; i++) {
            ItemStack s = inv().getItem(i);
            if (s.isEmpty()) continue;
            float sp = s.getDestroySpeed(state);
            if (sp > bestSpeed + 0.01f) { bestSpeed = sp; best = i; }
        }
        if (best < 0 || mc.gui.screen() != null) return hot;
        int target = -1;
        for (int h = 0; h < 9; h++) if (inv().getItem(h).isEmpty()) { target = h; break; }
        if (target < 0) {
            // kein Platz frei: nur den aktuellen Slot nehmen, wenn dort kein Baublock liegt (AutoBuild & Co. nichts wegnehmen)
            if (inv().getSelectedItem().getItem() instanceof net.minecraft.world.item.BlockItem) return hot;
            target = selected();
        }
        swapToHotbar(best, target);
        return target;
    }
}
