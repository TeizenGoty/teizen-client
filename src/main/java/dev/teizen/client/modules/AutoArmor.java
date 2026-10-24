package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.util.Inv;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.Equippable;

/** Zieht automatisch die beste Rüstung aus dem Inventar an. */
public final class AutoArmor extends Module {
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private final Setting delay = add(Setting.number("verzoegerung", "Ticks zwischen zwei Rüstungsteilen", 2, 0, 20));
    private final Setting keepElytra = add(Setting.bool("elytra", "Angezogene Elytra nicht gegen Brustpanzer tauschen", true));
    public int equipped;
    private int wait;

    public AutoArmor() {
        super("AutoArmor", Category.KAMPF, "Zieht automatisch die beste Rüstung an.");
    }

    /** Wie gut ein Teil für diesen Slot ist (-1 = leer / passt nicht). */
    public static double score(ItemStack s, EquipmentSlot slot) {
        if (s.isEmpty()) return -1;
        Equippable eq = s.get(DataComponents.EQUIPPABLE);
        if (eq == null || eq.slot() != slot) return -1;
        ItemAttributeModifiers mods = s.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        double armor = mods.compute(Attributes.ARMOR, 0, slot);
        double tough = mods.compute(Attributes.ARMOR_TOUGHNESS, 0, slot);
        double score = armor * 10 + tough * 4 + (s.isEnchanted() ? 2 : 0);
        if (s.isDamageableItem() && s.getMaxDamage() > 0) score += 1.0 - (double) s.getDamageValue() / s.getMaxDamage();
        return score;
    }

    @Override
    public void onTick() {
        if (!inGame() || mc.player.isCreative()) return;
        if (wait > 0) { wait--; return; }
        if (mc.gui.screen() != null && !(mc.gui.screen() instanceof InventoryScreen)) return;
        InventoryMenu menu = mc.player.inventoryMenu;
        if (mc.player.containerMenu != menu || !menu.getCarried().isEmpty()) return;
        for (EquipmentSlot slot : SLOTS) {
            ItemStack cur = mc.player.getItemBySlot(slot);
            if (keepElytra.on() && slot == EquipmentSlot.CHEST && cur.has(DataComponents.GLIDER)) continue;
            double curScore = cur.isEmpty() ? -1 : Math.max(0, score(cur, slot));
            int best = -1;
            double bestScore = curScore;
            for (int i = 0; i < 36; i++) {
                double sc = score(Inv.inv().getItem(i), slot);
                if (sc > bestScore + 0.01) { bestScore = sc; best = i; }
            }
            if (best < 0) continue;
            equip(menu, best, slot);
            equipped++;
            wait = delay.integer();
            return;
        }
    }

    /** Teil aufnehmen, in den Rüstungs-Slot legen, altes Teil zurück an seinen Platz. */
    private void equip(InventoryMenu menu, int invSlot, EquipmentSlot slot) {
        int from = Inv.menuSlot(invSlot);
        int armorSlot = 8 - slot.getIndex(); // Kopf 5, Brust 6, Beine 7, Füße 8
        mc.gameMode.handleContainerInput(menu.containerId, from, 0, ContainerInput.PICKUP, mc.player);
        mc.gameMode.handleContainerInput(menu.containerId, armorSlot, 0, ContainerInput.PICKUP, mc.player);
        if (!menu.getCarried().isEmpty()) mc.gameMode.handleContainerInput(menu.containerId, from, 0, ContainerInput.PICKUP, mc.player);
    }
}
