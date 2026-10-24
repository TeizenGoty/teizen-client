package dev.teizen.client.modules;

import dev.teizen.client.core.Friends;
import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.util.Draw;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Linien von der Bildschirmmitte zu jedem Spieler – nah = rot, weit = grün. */
public final class Tracers extends Module {
    private final Setting maxDist = add(Setting.number("distanz", "Maximale Entfernung", 256, 16, 1024));

    public Tracers() {
        super("Tracers", Category.SICHT, "Linien zu allen Spielern, auch durch Wände.");
    }

    @Override
    public void onHud(GuiGraphicsExtractor g, float partial) {
        if (!inGame()) return;
        for (Player p : mc.level.players()) {
            if (p == mc.player || p.isSpectator()) continue;
            double d = mc.player.distanceTo(p);
            if (d > maxDist.num()) continue;
            int color;
            if (Friends.is(p.getGameProfile().name())) color = 0xFF55FF55;
            else {
                float t = (float) Math.min(1, d / 64.0);
                int r = 255, gg = (int) (220 * t);
                color = 0xFF000000 | (r << 16) | (gg << 8) | 40;
            }
            Vec3 pos = p.getPosition(partial);
            Draw.tracer(g, pos.x, pos.y + p.getBbHeight() * 0.6, pos.z, color);
        }
    }
}
