package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.mixin.MultiPlayerGameModeAccessor;
import dev.teizen.client.util.Chat;
import dev.teizen.client.util.Inv;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * AutoMend: Taste drücken (Standard K) – wirft XP-Flaschen unter dich, bis alle Rüstungsteile (und was du in den Händen
 * hast) mit Reparatur wieder ganz sind. Dann geht es von selbst aus. Braucht Reparatur (Mending) und XP-Flaschen.
 */
public final class AutoMend extends Module {
    private final Setting pause = add(Setting.number("pause", "Ticks zwischen zwei Flaschen", 1, 0, 10));
    private final Setting hands = add(Setting.bool("haende", "Auch Items in den Händen reparieren", true));

    private int wait;
    private boolean stop;
    public int thrown;

    public AutoMend() {
        super("AutoMend", Category.SONSTIGES, "Taste K: XP-Flaschen werfen, bis die Rüstung ganz ist.");
        key = org.lwjgl.glfw.GLFW.GLFW_KEY_K;
    }

    private boolean mending(ItemStack s) {
        var reg = mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        var m = reg.get(Enchantments.MENDING);
        return m.isPresent() && EnchantmentHelper.getItemEnchantmentLevel(m.get(), s) > 0;
    }

    /** Wie viele Teile noch kaputt sind (nur mit Reparatur). */
    public int damagedCount() {
        int n = 0;
        EquipmentSlot[] slots = hands.on()
                ? new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}
                : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        for (EquipmentSlot sl : slots) {
            ItemStack s = mc.player.getItemBySlot(sl);
            if (!s.isEmpty() && s.isDamaged() && mending(s)) n++;
        }
        return n;
    }

    @Override
    protected void onEnable() {
        wait = 0;
        stop = false;
        thrown = 0;
        if (!inGame()) { stop = true; return; }
        int d = damagedCount();
        if (d == 0) { Chat.info("AutoMend: nichts zu reparieren (nur Teile mit Reparatur/Mending)."); stop = true; return; }
        Chat.ok("AutoMend: repariere " + d + " Teil(e) ...");
    }

    @Override
    public void onTick() {
        if (!inGame()) return;
        if (stop) { setEnabled(false); return; }
        if (mc.gui.screen() != null) return;
        if (damagedCount() == 0) {
            Chat.ok("AutoMend: alles repariert (" + thrown + " Flaschen).");
            setEnabled(false);
            return;
        }
        if (wait > 0) { wait--; return; }
        int slot = Inv.findHotbar(s -> s.is(Items.EXPERIENCE_BOTTLE));
        if (slot < 0) {
            int inv = Inv.findAny(s -> s.is(Items.EXPERIENCE_BOTTLE));
            if (inv < 0) { Chat.error("AutoMend: keine XP-Flaschen mehr."); setEnabled(false); return; }
            Inv.swapToHotbar(inv, 8);
            wait = 1;
            return;
        }
        int prev = Inv.selected();
        Inv.select(slot);
        // nach unten werfen: der Benutzen-Befehl schickt die Blickrichtung mit -> kurz nach unten schauen
        float pitch = mc.player.getXRot();
        mc.player.setXRot(90f);
        ((MultiPlayerGameModeAccessor) mc.gameMode).teizen$sendCarriedItem();
        mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        mc.player.setXRot(pitch);
        Inv.select(prev);
        thrown++;
        wait = pause.integer();
    }

    @Override
    public String info() { return mc.player == null ? "" : damagedCount() + " kaputt"; }
}
