package dev.teizen.client.modules;

import dev.teizen.client.TeizenClient;
import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** Hitboxes: Gegner-Hitboxen größer = leichter treffen. */
public final class Hitboxes extends Module {
    private final Setting size = add(Setting.number("groesse", "So viel größer (Blöcke pro Seite)", 0.3, 0.05, 1.5));
    private final Setting players = add(Setting.bool("spieler", "Spieler", true));
    private final Setting mobs = add(Setting.bool("mobs", "Monster und Tiere", false));

    public Hitboxes() {
        super("Hitboxes", Category.KAMPF, "Gegner-Hitboxen größer, damit du leichter triffst.");
    }

    /** Mixin (Entity.getPickRadius): so viel kommt dazu. */
    public static float extra(Entity e) {
        if (TeizenClient.modules() == null || e == mc.player) return 0;
        Hitboxes h = TeizenClient.modules().get(Hitboxes.class);
        if (h == null || !h.isEnabled()) return 0;
        if (e instanceof Player p) {
            if (!h.players.on() || dev.teizen.client.core.Friends.is(p.getGameProfile().name())) return 0;
            return (float) h.size.num();
        }
        return e instanceof LivingEntity && h.mobs.on() ? (float) h.size.num() : 0;
    }

    @Override
    public String info() { return dev.teizen.client.core.Setting.fmt(size.num()); }
}
