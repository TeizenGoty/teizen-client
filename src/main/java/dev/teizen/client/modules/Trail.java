package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.util.Draw;
import dev.teizen.client.util.Projector;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;

/** Trail (Breadcrumbs): zeichnet eine Linie, wo du gelaufen/geflogen bist. */
public final class Trail extends Module {
    private final Setting length = add(Setting.number("laenge", "So viele Punkte merken", 600, 50, 3000));
    private final Setting color = add(Setting.choice("farbe", "Farbe der Spur", "lila", "lila", "rot", "gruen", "blau", "gelb", "weiss"));
    public final ArrayDeque<Vec3> points = new ArrayDeque<>();
    private Object lastLevel;

    public Trail() {
        super("Trail", Category.SICHT, "Zeichnet eine Spur, wo du warst (Breadcrumbs).");
    }

    @Override
    protected void onEnable() { points.clear(); }

    @Override
    public void onTick() {
        if (!inGame()) return;
        if (lastLevel != mc.level) { points.clear(); lastLevel = mc.level; }
        Vec3 p = mc.player.position().add(0, 0.1, 0);
        if (points.isEmpty() || points.peekLast().distanceToSqr(p) > 0.25) {
            points.addLast(p);
            while (points.size() > length.integer()) points.removeFirst();
        }
    }

    private int argb() {
        return switch (color.str()) {
            case "rot" -> 0xFFFF4040;
            case "gruen" -> 0xFF40FF60;
            case "blau" -> 0xFF4090FF;
            case "gelb" -> 0xFFFFE040;
            case "weiss" -> 0xFFFFFFFF;
            default -> 0xFFB060FF;
        };
    }

    @Override
    public void onHud(GuiGraphicsExtractor g, float partial) {
        if (points.size() < 2) return;
        int c = argb();
        double[] prev = null;
        int i = 0, n = points.size();
        for (Vec3 v : points) {
            double[] s = Projector.project(v.x, v.y, v.z);
            if (s != null && prev != null) Draw.line(g, prev[0], prev[1], s[0], s[1], Draw.alpha(c, 0.3 + 0.7 * i / n));
            prev = s;
            i++;
        }
    }

    @Override
    public String info() { return String.valueOf(points.size()); }
}
