package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.mixin.MinecraftAccessor;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

/**
 * FastUse: Items ohne Wartezeit benutzen – XP-Flaschen, Schneebälle, Eier, Wurftränke, Feuerwerk usw. Rechtsklick
 * gedrückt halten = mehrere pro Tick. (Für Blöcke gibt es FastPlace; Essen/Bogen bleiben normal.)
 */
public final class FastUse extends Module {
    private final Setting perTick = add(Setting.number("protick", "Wie oft pro Tick benutzt wird (gedrückt halten)", 2, 1, 5));

    public int extraUses;

    public FastUse() {
        super("FastUse", Category.SONSTIGES, "Wurf-Items und XP-Flaschen ohne Wartezeit benutzen.");
    }

    private static boolean fits(ItemStack s) {
        return !s.isEmpty() && !(s.getItem() instanceof BlockItem) && s.getUseDuration(mc.player) <= 0;
    }

    @Override
    public void onTick() {
        if (!inGame() || mc.gui.screen() != null) return;
        ItemStack s = mc.player.getMainHandItem();
        if (!fits(s)) return;
        ((MinecraftAccessor) mc).teizen$setRightClickDelay(0);
        if (!mc.options.keyUse.isDown() || mc.player.isUsingItem()) return;
        for (int i = 1; i < perTick.integer(); i++) {
            if (!fits(mc.player.getMainHandItem()) || mc.player.getCooldowns().isOnCooldown(mc.player.getMainHandItem())) break;
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            extraUses++;
        }
    }

    @Override
    public String info() { return perTick.integer() + "x"; }
}
