package dev.teizen.client.modules;

import dev.teizen.client.TeizenClient;
import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.util.Draw;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/** PopChams: zeigt kurz einen leuchtenden "Geist" da, wo bei einem Spieler ein Totem geplatzt ist. */
public final class PopChams extends Module {
    private final Setting time = add(Setting.number("dauer", "So lange sichtbar (Sekunden)", 2, 0.5, 10));

    public record Pop(AABB box, String name, long at) {}
    public final List<Pop> pops = new ArrayList<>();
    public int seen;

    public PopChams() {
        super("PopChams", Category.SICHT, "Zeigt kurz, wo bei einem Spieler ein Totem geplatzt ist.");
    }

    public static void onPop(Entity e) {
        if (TeizenClient.modules() == null || !(e instanceof Player p)) return;
        PopChams c = TeizenClient.modules().get(PopChams.class);
        if (c == null || !c.isEnabled()) return;
        c.pops.add(new Pop(p.getBoundingBox(), p == mc.player ? "Du" : p.getGameProfile().name(), System.currentTimeMillis()));
        c.seen++;
    }

    @Override
    public void onHud(GuiGraphicsExtractor g, float partial) {
        long now = System.currentTimeMillis(), life = (long) (time.num() * 1000);
        pops.removeIf(p -> now - p.at() > life);
        for (Pop p : pops) {
            double a = 1.0 - (double) (now - p.at()) / life;
            // steigt langsam nach oben, wie ein Geist
            AABB b = p.box().move(0, (1 - a) * 1.2, 0);
            double[] r = Draw.worldBox(g, b, Draw.alpha(0xFFFFD040, 0.25 + 0.6 * a), true);
            if (r != null) g.text(mc.font, "§e✦ " + p.name() + " Totem", (int) ((r[0] + r[2]) / 2) - 30, (int) r[1] - 10, Draw.alpha(0xFFFFFFFF, a), true);
        }
    }
}
