package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.util.Inv;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Isst automatisch, wenn du Hunger hast. */
public final class AutoEat extends Module {
    private final Setting hunger = add(Setting.number("hunger", "Essen ab so wenig Hunger (20 = voll)", 14, 1, 19));
    private final Setting gapple = add(Setting.bool("goldaepfel", "Auch Goldäpfel essen", false));

    private boolean eating;
    private int prevSlot = -1;

    public AutoEat() {
        super("AutoEat", Category.SONSTIGES, "Isst automatisch, wenn du Hunger hast.");
    }

    private boolean edible(ItemStack s) {
        if (s.isEmpty() || !s.has(DataComponents.FOOD)) return false;
        if (s.is(Items.ROTTEN_FLESH) || s.is(Items.SPIDER_EYE) || s.is(Items.POISONOUS_POTATO) || s.is(Items.PUFFERFISH)
                || s.is(Items.CHORUS_FRUIT) || s.is(Items.SUSPICIOUS_STEW)) return false;
        if (!gapple.on() && (s.is(Items.GOLDEN_APPLE) || s.is(Items.ENCHANTED_GOLDEN_APPLE))) return false;
        return true;
    }

    @Override
    protected void onDisable() { stop(); }

    private void stop() {
        if (eating) {
            mc.options.keyUse.setDown(false);
            if (prevSlot >= 0) Inv.select(prevSlot);
        }
        eating = false;
        prevSlot = -1;
    }

    @Override
    public void onTick() { eatIfHungry(); }

    /** Isst gerade? */
    public boolean isEating() { return eating; }

    /** Für andere Funktionen (z.B. AutoMine): Essen beenden, falls AutoEat selbst aus ist. */
    public void stopEating() { if (!isEnabled()) stop(); }

    /**
     * Essen, wenn Hunger da ist – auch Essen aus dem Inventar (kommt dann in die Hand).
     * @return true solange gegessen wird (dann nichts anderes machen)
     */
    public boolean eatIfHungry() {
        if (!inGame() || mc.player.isCreative()) return false;
        int food = mc.player.getFoodData().getFoodLevel();
        if (eating) {
            if (food >= 20 || !edible(mc.player.getMainHandItem())) { stop(); return false; }
            mc.options.keyUse.setDown(true);
            if (!mc.player.isUsingItem()) mc.gameMode.useItem(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND);
            return true;
        }
        if (food > hunger.num() || mc.gui.screen() != null) return false;
        int slot = Inv.findHotbar(this::edible);
        prevSlot = Inv.selected();
        if (slot >= 0) Inv.select(slot);
        else if (!Inv.ensureInHand(this::edible)) { prevSlot = -1; return false; } // nichts zu essen dabei
        eating = true;
        mc.options.keyUse.setDown(true);
        return true;
    }
}
