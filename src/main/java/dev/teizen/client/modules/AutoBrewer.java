package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.util.Chat;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * AutoBrewer: Braustand öffnen – braut automatisch den gewählten Trank aus Wasserflaschen.
 * Du brauchst im Inventar: Wasserflaschen, Netherwarzen, die Zutat (z.B. Lohenstaub für Stärke), Lohenstaub als Brennstoff,
 * und für "lang" Redstone. Fertige Tränke kommen ins Inventar, dann geht es mit neuen Flaschen weiter.
 */
public final class AutoBrewer extends Module {
    private final Setting potion = add(Setting.choice("trank", "Welcher Trank", "staerke",
            "staerke", "tempo", "heilung", "feuerschutz", "nachtsicht", "unsichtbar", "regeneration", "wasseratmung", "sprung", "langsamfallen"));
    private final Setting extend = add(Setting.bool("lang", "Mit Redstone verlängern (wo es geht)", true));
    private int wait;
    public int brewed;
    private boolean warned;

    /** Ein Brau-Schritt: aus diesem Trank wird mit dieser Zutat der nächste. */
    private record Step(String from, String ingredient) {}

    public AutoBrewer() {
        super("AutoBrewer", Category.SONSTIGES, "Braut im Braustand automatisch den gewählten Trank.");
    }

    private List<Step> chain() {
        String main = switch (potion.str()) {
            case "tempo" -> "sugar";
            case "heilung" -> "glistering_melon_slice";
            case "feuerschutz" -> "magma_cream";
            case "nachtsicht", "unsichtbar" -> "golden_carrot";
            case "regeneration" -> "ghast_tear";
            case "wasseratmung" -> "pufferfish";
            case "sprung" -> "rabbit_foot";
            case "langsamfallen" -> "phantom_membrane";
            default -> "blaze_powder";
        };
        String base = result(potion.str().equals("unsichtbar") ? "nachtsicht" : potion.str());
        java.util.ArrayList<Step> l = new java.util.ArrayList<>();
        l.add(new Step("water", "nether_wart"));
        l.add(new Step("awkward", main));
        if (potion.is("unsichtbar")) { l.add(new Step("night_vision", "fermented_spider_eye")); base = "invisibility"; }
        if (extend.on() && !potion.is("heilung")) l.add(new Step(base, "redstone"));
        return l;
    }

    private static String result(String n) {
        return switch (n) {
            case "tempo" -> "swiftness";
            case "heilung" -> "healing";
            case "feuerschutz" -> "fire_resistance";
            case "nachtsicht" -> "night_vision";
            case "regeneration" -> "regeneration";
            case "wasseratmung" -> "water_breathing";
            case "sprung" -> "leaping";
            case "langsamfallen" -> "slow_falling";
            default -> "strength";
        };
    }

    private static String potionName(ItemStack s) {
        if (s.isEmpty()) return "";
        var pc = s.get(DataComponents.POTION_CONTENTS);
        if (pc == null || pc.potion().isEmpty()) return "?";
        String n = pc.potion().get().getRegisteredName();
        return n.contains(":") ? n.substring(n.indexOf(':') + 1) : n;
    }

    private static String id(ItemStack s) { return s.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(s.getItem()).getPath(); }

    private void quick(BrewingStandMenu m, int slot) { mc.gameMode.handleContainerInput(m.containerId, slot, 0, ContainerInput.QUICK_MOVE, mc.player); }

    /** Inventar-Slot (im Braustand-Menü) mit diesem Item, oder -1. */
    private int find(BrewingStandMenu m, java.util.function.Predicate<ItemStack> p) {
        for (int i = 5; i < m.slots.size(); i++) if (p.test(m.getSlot(i).getItem())) return i;
        return -1;
    }

    @Override
    public void onTick() {
        if (!inGame() || !(mc.player.containerMenu instanceof BrewingStandMenu m)) { warned = false; return; }
        if (wait > 0) { wait--; return; }
        wait = 4;
        // Brennstoff
        if (m.getFuel() <= 0 && m.getSlot(4).getItem().isEmpty()) {
            int b = find(m, s -> id(s).equals("blaze_powder"));
            if (b >= 0) { quick(m, b); return; }
            if (!warned) { warned = true; Chat.error("AutoBrewer: kein Lohenstaub als Brennstoff."); }
            return;
        }
        if (m.getBrewingTicks() > 0) return;
        // Flaschen
        ItemStack b0 = m.getSlot(0).getItem(), b1 = m.getSlot(1).getItem(), b2 = m.getSlot(2).getItem();
        if (b0.isEmpty() && b1.isEmpty() && b2.isEmpty()) {
            int w = find(m, s -> id(s).equals("potion") && potionName(s).equals("water"));
            if (w < 0) { if (!warned) { warned = true; Chat.info("AutoBrewer fertig: keine Wasserflaschen mehr (" + brewed + " Tränke gebraut)."); } return; }
            quick(m, w);
            return;
        }
        String now = potionName(!b0.isEmpty() ? b0 : !b1.isEmpty() ? b1 : b2);
        Step next = null;
        for (Step s : chain()) if (s.from().equals(now)) { next = s; break; }
        ItemStack ing = m.getSlot(3).getItem();
        if (next == null) {
            // fertig (oder ein fremder Trank): Zutat raus, Tränke ins Inventar
            if (!ing.isEmpty()) { quick(m, 3); return; }
            for (int i = 0; i < 3; i++) if (!m.getSlot(i).getItem().isEmpty()) { quick(m, i); brewed++; return; }
            return;
        }
        String want = next.ingredient();
        if (!ing.isEmpty() && !id(ing).equals(want)) { quick(m, 3); return; } // falsche Zutat raus
        if (ing.isEmpty()) {
            int i = find(m, s -> id(s).equals(want));
            if (i < 0) { if (!warned) { warned = true; Chat.error("AutoBrewer: es fehlt §f" + want); } return; }
            // nur eine Zutat reinlegen: Stapel nehmen, 1 rein (Rechtsklick), Rest zurück
            mc.gameMode.handleContainerInput(m.containerId, i, 0, ContainerInput.PICKUP, mc.player);
            mc.gameMode.handleContainerInput(m.containerId, 3, 1, ContainerInput.PICKUP, mc.player);
            mc.gameMode.handleContainerInput(m.containerId, i, 0, ContainerInput.PICKUP, mc.player);
        }
    }

    @Override
    public String info() { return String.valueOf(brewed); }
}
