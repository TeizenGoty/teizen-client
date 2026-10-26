package dev.teizen.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.ShulkerBoxBlock;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Wie wertvoll ist ein Item? Für ChestStealer und Shulker-Ausräumen:
 * beste Sachen zuerst nehmen, Müll (Liste "nie") liegen lassen.
 */
public final class Loot {
    private static final Minecraft mc = Minecraft.getInstance();

    /** Standard-Liste: das wird nie genommen. */
    public static final String DEFAULT_NEVER = "dirt,coarse_dirt,rooted_dirt,cobblestone,cobbled_deepslate,gravel,sand,red_sand,"
            + "netherrack,diorite,andesite,granite,tuff,rotten_flesh,poisonous_potato,wheat_seeds,dead_bush";

    /** Was zuerst, was nie, sortieren an/aus, Liste "nie" an/aus. */
    public record Prefs(List<String> first, List<String> never, boolean sort, boolean filter) {}

    private record Rule(int score, String... patterns) {}

    /** Rangliste: die erste passende Zeile zählt. * = beliebige Buchstaben. */
    private static final Rule[] RULES = {
            new Rule(1000, "enchanted_golden_apple", "totem_of_undying", "elytra", "nether_star", "dragon_egg", "beacon", "mace", "heavy_core"),
            new Rule(950, "netherite_*", "ancient_debris"),
            new Rule(900, "*shulker_box", "trident", "enchanted_book"),
            new Rule(800, "diamond*", "golden_apple", "ender_chest", "end_crystal"),
            new Rule(750, "*_smithing_template", "*trial_key", "music_disc_*", "experience_bottle", "emerald_block"),
            new Rule(700, "ender_pearl", "firework_rocket", "*potion", "tipped_arrow", "wind_charge", "breeze_rod", "blaze_rod", "shulker_shell", "echo_shard"),
            new Rule(600, "emerald", "gold_block", "iron_block", "lapis_block", "redstone_block", "*obsidian", "golden_carrot",
                    "bow", "crossbow", "shield", "saddle", "name_tag", "*_horse_armor"),
            new Rule(500, "iron_*", "gold_*", "golden_*", "raw_iron", "raw_gold", "lapis_lazuli", "redstone", "amethyst_shard", "quartz",
                    "cooked_*", "bread", "baked_potato", "*arrow", "gunpowder", "glowstone_dust", "book"),
            new Rule(300, "coal", "charcoal", "*copper*", "*_log", "*_planks", "torch", "slime_ball", "leather", "bone", "feather", "flint",
                    "paper", "string", "sugar_cane", "apple", "carrot", "potato", "beef", "porkchop", "chicken", "mutton", "cod", "salmon"),
    };

    /** Alles, was in keiner Zeile steht. */
    public static final int DEFAULT_SCORE = 200;

    private static final Map<String, Pattern> PATTERNS = new HashMap<>();

    private Loot() {}

    // ------------------------------------------------------------------ Namen

    /** "minecraft:diamond" -> "diamond", andere Mods behalten ihren Namensraum. */
    public static String id(ItemStack s) {
        Identifier k = BuiltInRegistries.ITEM.getKey(s.getItem());
        return "minecraft".equals(k.getNamespace()) ? k.getPath() : k.toString();
    }

    /** Gibt es dieses Item (oder ist es ein Muster mit *)? */
    public static boolean known(String name) {
        String n = clean(name);
        if (n.isEmpty()) return false;
        if (n.contains("*")) return true;
        Identifier k = Identifier.tryParse(n.contains(":") ? n : "minecraft:" + n);
        return k != null && BuiltInRegistries.ITEM.containsKey(k);
    }

    public static String clean(String name) {
        String n = name.trim().toLowerCase(Locale.ROOT);
        return n.startsWith("minecraft:") ? n.substring("minecraft:".length()) : n;
    }

    /** Passt das Item zu diesem Namen bzw. Muster ("dirt", "*_seeds", "minecraft:dirt")? */
    public static boolean matches(String pattern, ItemStack s) {
        String p = clean(pattern);
        if (p.isEmpty()) return false;
        String id = id(s);
        if (!p.contains("*")) return p.equals(id);
        return PATTERNS.computeIfAbsent(p, Loot::compile).matcher(id).matches();
    }

    /** "*_seeds" -> beliebiger Anfang, dann "_seeds". */
    private static Pattern compile(String glob) {
        String[] parts = glob.split("\\*", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append(".*");
            if (!parts[i].isEmpty()) sb.append(Pattern.quote(parts[i]));
        }
        return Pattern.compile(sb.toString());
    }

    private static boolean matchesAny(List<String> patterns, ItemStack s) {
        for (String p : patterns) if (matches(p, s)) return true;
        return false;
    }

    // ------------------------------------------------------------------ Wert

    /** Soll das Item liegen bleiben (Liste "nie")? Einträge aus "zuerst" werden immer genommen. */
    public static boolean never(ItemStack s, Prefs p) {
        return p.filter() && !matchesAny(p.first(), s) && matchesAny(p.never(), s);
    }

    /** Je höher, desto früher wird das Item genommen. */
    public static int score(ItemStack s, Prefs p) {
        for (int i = 0; i < p.first().size(); i++) if (matches(p.first().get(i), s)) return 10000 - i;
        if (matchesAny(p.never(), s)) return 0;
        return baseScore(s);
    }

    /** Wert nur aus der eingebauten Rangliste (+ Bonus für verzauberte Sachen). */
    public static int baseScore(ItemStack s) {
        String id = id(s);
        int base = DEFAULT_SCORE;
        outer:
        for (Rule r : RULES) {
            for (String pat : r.patterns()) {
                if (pat.contains("*") ? PATTERNS.computeIfAbsent(pat, Loot::compile).matcher(id).matches() : pat.equals(id)) {
                    base = r.score();
                    break outer;
                }
            }
        }
        if (s.isEnchanted()) base += 200;
        return base;
    }

    // ------------------------------------------------------------------ Ausräumen

    /**
     * Nimmt Items aus einem offenen Behälter (Kiste, Shulker …) ins Inventar, die wertvollsten zuerst.
     * @param max      höchstens so viele Stapel (0 = alle)
     * @param keepFree so viele Inventar-Plätze bleiben frei (für den Shulker, der danach wieder aufgehoben wird)
     * @return wie viele Stapel wirklich (ganz oder teilweise) genommen wurden
     */
    public static int steal(AbstractContainerMenu menu, Prefs p, int max, int keepFree) {
        int n = menu.slots.size() - 36;
        if (n <= 0) return 0;
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            ItemStack s = menu.slots.get(i).getItem();
            if (!s.isEmpty() && !never(s, p)) order.add(i);
        }
        if (p.sort()) {
            Map<Integer, Integer> score = new HashMap<>();
            for (int i : order) score.put(i, score(menu.slots.get(i).getItem(), p));
            // stabil: bei gleichem Wert bleibt die Reihenfolge in der Kiste
            order.sort(Comparator.comparingInt((Integer i) -> -score.get(i)));
        }
        int moved = 0;
        for (int i : order) {
            if (max > 0 && moved >= max) break;
            ItemStack s = menu.slots.get(i).getItem();
            if (s.isEmpty()) continue;
            if (keepFree > 0 && !fits(s, keepFree)) continue;
            int before = s.getCount();
            mc.gameMode.handleContainerInput(menu.containerId, i, 0, ContainerInput.QUICK_MOVE, mc.player);
            ItemStack after = menu.slots.get(i).getItem();
            if (after.isEmpty() || after.getCount() < before) moved++;
        }
        return moved;
    }

    /** Freie Plätze im Inventar (Hotbar + 27 Plätze). */
    public static int freeSlots() {
        int free = 0;
        for (int i = 0; i < 36; i++) if (mc.player.getInventory().getItem(i).isEmpty()) free++;
        return free;
    }

    /** Wie viele davon passen noch auf schon vorhandene Stapel? */
    private static int stackRoom(ItemStack s) {
        int room = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack t = mc.player.getInventory().getItem(i);
            if (!t.isEmpty() && ItemStack.isSameItemSameComponents(t, s)) room += Math.max(0, t.getMaxStackSize() - t.getCount());
        }
        return room;
    }

    /** Passt der ganze Stapel ins Inventar, ohne die letzten {@code keepFree} freien Plätze zu belegen? */
    public static boolean fits(ItemStack s, int keepFree) {
        return freeSlots() > keepFree || stackRoom(s) >= s.getCount();
    }

    // ------------------------------------------------------------------ Shulker

    public static boolean isShulker(ItemStack s) {
        return !s.isEmpty() && s.getItem() instanceof BlockItem bi && bi.getBlock() instanceof ShulkerBoxBlock;
    }

    /** Inhalt eines Shulkers als Item (den sieht auch der Client). */
    public static List<ItemStack> contents(ItemStack shulker) {
        ItemContainerContents c = shulker.get(DataComponents.CONTAINER);
        return c == null ? List.of() : c.nonEmptyItemCopyStream().toList();
    }

    /** Liegt im Shulker etwas, das genommen werden soll? */
    public static boolean hasWanted(ItemStack shulker, Prefs p) {
        for (ItemStack s : contents(shulker)) if (!never(s, p)) return true;
        return false;
    }

    /**
     * Ist nach dem Hinstellen des Shulkers Platz für mindestens einen guten Stapel
     * (ein Platz bleibt immer frei, damit der Shulker wieder aufgehoben werden kann)?
     */
    public static boolean roomFor(ItemStack shulker, Prefs p) {
        // Hinstellen macht den Platz des Shulkers frei -> mit keepFree=1 darf dann ein neuer Platz belegt werden, wenn jetzt schon einer frei ist
        if (freeSlots() >= 1) return true;
        for (ItemStack s : contents(shulker)) if (!never(s, p) && stackRoom(s) >= s.getCount()) return true;
        return false;
    }
}
