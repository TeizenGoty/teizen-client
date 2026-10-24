package dev.teizen.client.modules;

import dev.teizen.client.core.Friends;
import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.util.Draw;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/** Spieler leuchten durch Wände + farbiger Kasten mit Name, Leben und Abstand. */
public final class PlayerESP extends Module {
    public final Setting glow = add(Setting.bool("leuchten", "Leucht-Umriss durch Wände", true));
    private final Setting box = add(Setting.bool("kasten", "2D-Kasten um Spieler", true));
    private final Setting names = add(Setting.bool("namen", "Name, Leben und Abstand anzeigen", true));
    public final Setting mobs = add(Setting.bool("mobs", "Auch Mobs markieren", false));
    private final Setting range = add(Setting.number("distanz", "Maximale Entfernung (Blöcke)", 512, 8, 1024));

    public PlayerESP() {
        super("PlayerESP", Category.SICHT, "Zeigt Spieler durch Wände an.");
    }

    /** Wird vom Glowing-Mixin abgefragt. */
    public boolean shouldGlow(Entity e) {
        if (!isEnabled() || !glow.on() || e == mc.player) return false;
        if (e instanceof Player) return true;
        return mobs.on() && e instanceof net.minecraft.world.entity.Mob;
    }

    @Override
    public void onHud(GuiGraphicsExtractor g, float partial) {
        if (!inGame() || (!box.on() && !names.on())) return;
        for (Player p : mc.level.players()) {
            if (p == mc.player || p.isSpectator() || mc.player.distanceTo(p) > range.num()) continue;
            int color = Friends.is(p.getGameProfile().name()) ? 0xFF55FF55 : 0xFFFF3B3B;
            var bb = p.getBoundingBox().move(p.getPosition(partial).subtract(p.position()));
            double[] r = box.on() ? Draw.worldBox(g, bb, color, false) : dev.teizen.client.util.Projector.box(bb);
            if (r == null || !names.on()) continue;
            String txt = p.getGameProfile().name() + " §c" + Math.round(p.getHealth() + p.getAbsorptionAmount()) + "❤ §7" + Math.round(mc.player.distanceTo(p)) + "m";
            int w = mc.font.width(txt);
            int x = (int) ((r[0] + r[2]) / 2 - w / 2.0), y = (int) r[1] - 11;
            g.fill(x - 2, y - 1, x + w + 2, y + 9, 0x90000000);
            g.text(mc.font, txt, x, y, 0xFFFFFFFF, false);
        }
    }
}
