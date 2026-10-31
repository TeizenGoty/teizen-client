package dev.teizen.client.modules;

import dev.teizen.client.TeizenClient;
import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * BetterTab: in der Spielerliste (Tab) steht hinter jedem Spieler, wie weit er weg ist und in welcher Richtung.
 * Ohne OP sieht man nur Spieler in Sichtweite – bei den anderen steht nichts.
 */
public final class BetterTab extends Module {
    private final Setting dir = add(Setting.bool("richtung", "Auch die Richtung (N, NO, …) anzeigen", true));
    private final Setting friends = add(Setting.bool("freunde", "Freunde grün markieren", true));
    public int labelled;

    public BetterTab() {
        super("BetterTab", Category.SICHT, "Spielerliste (Tab) zeigt, wie viele Meter jeder weg ist.");
    }

    /** Mixin (PlayerTabOverlay.getNameForDisplay). */
    public static Component decorate(PlayerInfo info, Component name) {
        if (TeizenClient.modules() == null || mc.level == null || mc.player == null) return name;
        BetterTab b = TeizenClient.modules().get(BetterTab.class);
        if (b == null || !b.isEnabled()) return name;
        var out = Component.empty();
        String pn = info.getProfile().name();
        if (b.friends.on() && dev.teizen.client.core.Friends.is(pn)) out.append(Component.literal("§a★ "));
        out.append(name);
        Player p = mc.level.getPlayerByUUID(info.getProfile().id());
        if (p != null && p != mc.player) {
            double dx = p.getX() - mc.player.getX(), dz = p.getZ() - mc.player.getZ();
            long d = Math.round(Math.sqrt(dx * dx + dz * dz + Math.pow(p.getY() - mc.player.getY(), 2)));
            out.append(Component.literal(" §7" + d + " m" + (b.dir.on() ? " " + StructureFinder.direction(dx, dz) : "")));
            b.labelled++;
        }
        return out;
    }
}
