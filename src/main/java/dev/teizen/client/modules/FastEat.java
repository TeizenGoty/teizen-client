package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.util.Chat;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * FastEat: Essen geht viel schneller.
 * Die Ess-Dauer bestimmt der Server, darum stellt FastEat dein Essen per Befehl auf eine kurze Ess-Zeit um
 * (braucht OP bzw. "Cheats erlaubt"). Beim Ausschalten wird das Essen wieder normal.
 * Essen mit Spezial-Effekten (z.B. Goldäpfel) wird nicht verändert, damit die Effekte bleiben.
 */
public final class FastEat extends Module {
    private final Setting seconds = add(Setting.number("sekunden", "Ess-Dauer in Sekunden (normal 1.6)", 0.2, 0.05, 1.5));

    /** Slot -> Tick, an dem zuletzt ein Befehl dafür geschickt wurde (nicht doppelt schicken). */
    private final Map<Integer, Integer> sent = new HashMap<>();
    private final Map<Integer, Integer> tries = new HashMap<>();
    private int lastCommandTick = -100;
    private boolean warned;

    public FastEat() {
        super("FastEat", Category.SONSTIGES, "Essen geht viel schneller (braucht OP / Cheats an).");
    }

    @Override
    protected void onEnable() {
        sent.clear();
        tries.clear();
        warned = false;
    }

    @Override
    protected void onDisable() {
        if (!inGame()) return;
        // Essen wieder auf normale Ess-Dauer zurückstellen
        for (int slot = 0; slot <= 36; slot++) {
            ItemStack s = stack(slot);
            if (!changeable(s)) continue;
            float normal = normalSeconds(s);
            if (Math.abs(current(s) - normal) > 0.01f) command(slot, normal);
        }
        sent.clear();
        tries.clear();
    }

    @Override
    public void onTick() {
        if (!inGame()) return;
        int now = mc.player.tickCount;
        if (now - lastCommandTick < 3) return; // höchstens ein Befehl alle 3 Ticks
        float want = (float) seconds.num();
        for (int slot = 0; slot <= 36; slot++) {
            ItemStack s = stack(slot);
            if (!changeable(s)) { tries.remove(slot); continue; }
            if (Math.abs(current(s) - want) <= 0.01f) { tries.remove(slot); continue; }
            Integer last = sent.get(slot);
            if (last != null && now - last < 40) continue; // Server hat noch nicht geantwortet
            int t = tries.merge(slot, 1, Integer::sum);
            if (t > 3) {
                if (!warned) {
                    warned = true;
                    Chat.error("FastEat geht nicht: du brauchst OP-Rechte (bzw. in der eigenen Welt \"Cheats erlaubt\").");
                }
                continue;
            }
            command(slot, want);
            sent.put(slot, now);
            lastCommandTick = now;
            return;
        }
    }

    // ------------------------------------------------------------------

    /** 0-35 = Inventar, 36 = linke Hand. */
    private ItemStack stack(int slot) {
        return slot == 36 ? mc.player.getOffhandItem() : mc.player.getInventory().getItem(slot);
    }

    /** Nur normales Essen ohne Spezial-Effekte. */
    private static boolean changeable(ItemStack s) {
        if (s.isEmpty() || !s.has(DataComponents.FOOD)) return false;
        Consumable c = s.get(DataComponents.CONSUMABLE);
        Consumable normal = s.getItem().components().get(DataComponents.CONSUMABLE);
        if (c == null || normal == null) return false;
        return c.onConsumeEffects().isEmpty() && normal.onConsumeEffects().isEmpty();
    }

    private static float current(ItemStack s) {
        Consumable c = s.get(DataComponents.CONSUMABLE);
        return c == null ? 0 : c.consumeSeconds();
    }

    private static float normalSeconds(ItemStack s) {
        Consumable c = s.getItem().components().get(DataComponents.CONSUMABLE);
        return c == null ? 1.6f : c.consumeSeconds();
    }

    private void command(int slot, float secs) {
        String target = slot == 36 ? "weapon.offhand" : "container." + slot;
        String value = String.format(Locale.ROOT, "%.3f", secs);
        mc.player.connection.sendCommand("item modify entity @s " + target
                + " {function:\"minecraft:set_components\",components:{\"minecraft:consumable\":{consume_seconds:" + value + "f}}}");
    }

    @Override
    public String info() { return String.format(Locale.ROOT, "%.2fs", seconds.num()); }
}
