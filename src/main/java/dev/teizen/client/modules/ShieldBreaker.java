package dev.teizen.client.modules;

import dev.teizen.client.TeizenClient;
import dev.teizen.client.core.Friends;
import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.util.Inv;
import dev.teizen.client.util.WeaponSwap;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.HashSet;
import java.util.Set;

/**
 * ShieldBreaker: blockt ein Gegner mit dem Schild, wird für den Schlag kurz zur Axt gewechselt – das Schild ist dann
 * ein paar Sekunden kaputt. Klappt bei jedem Schlag (auch KillAura/TriggerBot/GodPvP). Mit "automatisch" schlägt es
 * selbst zu, sobald du einen blockenden Gegner ansiehst. Du brauchst eine Axt in der Hotbar.
 */
public final class ShieldBreaker extends Module {
    private final Setting auto = add(Setting.bool("automatisch", "Selbst zuschlagen, wenn ein Gegner vor dir blockt", true));
    private final Setting range = add(Setting.number("reichweite", "Bis zu welcher Entfernung automatisch (Blöcke)", 3.5, 1, 6));

    public int swaps;
    /** Nur Selbsttest: diese Wesen gelten als "blockt gerade". */
    public static final Set<Integer> testBlocking = new HashSet<>();

    public ShieldBreaker() {
        super("ShieldBreaker", Category.KAMPF, "Blockt ein Gegner, wird kurz zur Axt gewechselt und sein Schild zerschlagen.");
    }

    public static boolean blocking(Entity e) {
        return e instanceof LivingEntity le && (le.isBlocking() || testBlocking.contains(e.getId()));
    }

    @Override
    public void onTick() {
        if (!auto.on() || !inGame() || mc.gui.screen() != null) return;
        // KillAura/GodPvP schlagen sowieso – dort wird beim Schlag automatisch zur Axt gewechselt
        KillAura ka = TeizenClient.modules().get(KillAura.class);
        if (ka != null && ka.isEnabled()) return;
        Entity t = mc.crosshairPickEntity;
        if (t == null || !blocking(t) || t == mc.player) return;
        if (t instanceof Player p && Friends.is(p.getGameProfile().name())) return;
        if (Targets.distanceToBox(t) > range.num()) return;
        if (mc.player.getAttackStrengthScale(0.5f) < 0.9f) return;
        if (Inv.findHotbar(WeaponSwap::isAxe) < 0) return;
        mc.gameMode.attack(mc.player, t);
        dev.teizen.client.compat.V.swing(InteractionHand.MAIN_HAND);
    }

    @Override
    public String info() { return mc.player != null && Inv.findHotbar(WeaponSwap::isAxe) < 0 ? "§ckeine Axt" : ""; }
}
