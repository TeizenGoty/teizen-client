package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.util.Chat;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Mehr Haltbarkeit für Werkzeuge, Waffen und Rüstung (über /item modify, braucht OP).
 * mehr = Haltbarkeit × Faktor · unzerstoerbar = geht nie kaputt · reparieren = wird automatisch wieder ganz.
 * Beim Ausschalten wird alles wieder normal (die Abnutzung bleibt anteilig gleich).
 */
public final class MoreDurability extends Module {
    private final Setting mode = add(Setting.choice("modus", "mehr = Haltbarkeit × Faktor, unzerstoerbar, reparieren", "mehr", "mehr", "unzerstoerbar", "reparieren"));
    private final Setting factor = add(Setting.number("faktor", "Wie viel mal mehr Haltbarkeit (Modus mehr)", 10, 2, 100));
    private final Setting repairAt = add(Setting.number("ab", "Reparieren ab so viel % Abnutzung (Modus reparieren)", 50, 1, 99));

    private static final String[] ARMOR = {"armor.feet", "armor.legs", "armor.chest", "armor.head"};
    private final Map<Integer, Integer> sent = new HashMap<>();
    private final Map<Integer, Integer> tries = new HashMap<>();
    private String lastMode = "";
    private int lastFactor;
    private int lastCommand = -100;
    private boolean warned;

    public MoreDurability() {
        super("MoreDurability", Category.SONSTIGES, "Werkzeuge und Rüstung halten länger (braucht OP).");
        opOnly();
    }

    // 0-35 Inventar, 36 linke Hand, 37-40 Rüstung (Füße, Beine, Brust, Kopf)
    private ItemStack stack(int slot) {
        if (slot < 36) return mc.player.getInventory().getItem(slot);
        if (slot == 36) return mc.player.getOffhandItem();
        EquipmentSlot[] a = {EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};
        return mc.player.getItemBySlot(a[slot - 37]);
    }

    private static String target(int slot) {
        if (slot < 36) return "container." + slot;
        if (slot == 36) return "weapon.offhand";
        return ARMOR[slot - 37];
    }

    private static int defaultMax(ItemStack s) {
        Integer m = s.getItem().components().get(DataComponents.MAX_DAMAGE);
        return m == null ? 0 : m;
    }

    private static int currentMax(ItemStack s) {
        Integer m = s.get(DataComponents.MAX_DAMAGE);
        return m == null ? 0 : m;
    }

    /** Was für dieses Item noch zu tun ist (Befehls-Teil) – oder null. */
    private String wanted(ItemStack s) {
        int def = defaultMax(s);
        if (s.isEmpty() || def <= 0) return null;
        switch (mode.str()) {
            case "mehr" -> {
                int want = def * factor.integer();
                if (currentMax(s) != want) return "{function:\"minecraft:set_components\",components:{\"minecraft:max_damage\":" + want + "}}";
            }
            case "unzerstoerbar" -> {
                if (!s.has(DataComponents.UNBREAKABLE)) return "{function:\"minecraft:set_components\",components:{\"minecraft:unbreakable\":{}}}";
            }
            default -> {
                int max = currentMax(s);
                if (max > 0 && s.getDamageValue() >= max * repairAt.num() / 100.0) return "{function:\"minecraft:set_damage\",damage:1.0}";
            }
        }
        return null;
    }

    /** Alles zurück auf normal. */
    private String revert(ItemStack s) {
        int def = defaultMax(s);
        if (s.isEmpty() || def <= 0) return null;
        StringBuilder sb = new StringBuilder();
        int cur = currentMax(s);
        if (cur != def && cur > 0) {
            float left = Math.max(0.01f, 1f - (float) s.getDamageValue() / cur);
            sb.append("{function:\"minecraft:set_components\",components:{\"minecraft:max_damage\":").append(def).append("}},")
              .append("{function:\"minecraft:set_damage\",damage:").append(String.format(Locale.ROOT, "%.3f", left)).append("},");
        }
        if (s.has(DataComponents.UNBREAKABLE)) sb.append("{function:\"minecraft:set_components\",components:{\"!minecraft:unbreakable\":{}}},");
        if (sb.isEmpty()) return null;
        return "[" + sb.substring(0, sb.length() - 1) + "]";
    }

    private void command(int slot, String modifier) {
        mc.player.connection.sendCommand("item modify entity @s " + target(slot) + " " + modifier);
    }

    private void revertAll() {
        for (int slot = 0; slot <= 40; slot++) {
            String r = revert(stack(slot));
            if (r != null) command(slot, r);
        }
    }

    @Override
    protected void onEnable() {
        sent.clear();
        tries.clear();
        warned = false;
        lastMode = mode.str();
        lastFactor = factor.integer();
    }

    @Override
    protected void onDisable() {
        if (inGame()) revertAll();
        sent.clear();
        tries.clear();
    }

    @Override
    public void onTick() {
        if (!inGame()) return;
        int now = mc.player.tickCount;
        if (!mode.str().equals(lastMode) || (mode.is("mehr") && factor.integer() != lastFactor)) {
            // Modus gewechselt: erst alles zurücksetzen
            lastMode = mode.str();
            lastFactor = factor.integer();
            revertAll();
            sent.clear();
            tries.clear();
            lastCommand = now;
            return;
        }
        if (now - lastCommand < 3) return;
        for (int slot = 0; slot <= 40; slot++) {
            ItemStack s = stack(slot);
            String w = wanted(s);
            if (w == null) { tries.remove(slot); continue; }
            Integer last = sent.get(slot);
            if (last != null && now - last < 40) continue;
            if (tries.merge(slot, 1, Integer::sum) > 3) {
                if (!warned) {
                    warned = true;
                    Chat.error("MoreDurability geht nicht: du brauchst OP-Rechte (bzw. \"Cheats erlaubt\").");
                }
                continue;
            }
            command(slot, w);
            sent.put(slot, now);
            lastCommand = now;
            return;
        }
    }

    @Override
    public String info() {
        return mode.is("mehr") ? "×" + factor.integer() : mode.str();
    }
}
