package dev.teizen.client.modules;

import dev.teizen.client.core.FarPlayers;
import dev.teizen.client.core.Friends;
import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.util.Arrows;
import dev.teizen.client.util.Draw;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Pfeile zu allen Spielern (wie beim PortalFinder): um das Fadenkreuz zeigt ein Pfeil in die Richtung jedes Spielers,
 * mit Name, Entfernung und Höhe. Auch weit entfernte Spieler (eigene Welt direkt, auf Servern per /data mit OP).
 */
public final class PlayerFinder extends Module {
    private final Setting arrows = add(Setting.bool("pfeile", "Pfeile um das Fadenkreuz zu den Spielern", true));
    private final Setting list = add(Setting.bool("liste", "Liste links oben: Name, Entfernung, Richtung", true));
    private final Setting boxes = add(Setting.bool("rahmen", "Rahmen und Name am Spieler (auch durch Wände)", true));
    private final Setting tracers = add(Setting.bool("linien", "Linien zu den Spielern (der nächste dick)", false));
    private final Setting far = add(Setting.bool("weit", "Auch weit entfernte Spieler (eigene Welt direkt, Server: OP)", true));
    private final Setting ring = add(Setting.number("kreis", "Größe des Pfeil-Kreises (Pixel)", 60, 20, 200));

    public record Target(String name, double x, double y, double z, String dim, boolean sameDim, boolean near, boolean friend) {}

    public final List<Target> targets = new ArrayList<>();

    public PlayerFinder() {
        super("PlayerFinder", Category.SICHT, "Pfeile zu allen Spielern, auch weit weg.");
    }

    @Override
    protected void onDisable() { targets.clear(); }

    @Override
    public void onTick() {
        if (!inGame()) return;
        if (far.on()) FarPlayers.want();
        List<Target> out = new ArrayList<>();
        for (Player p : mc.level.players()) {
            if (p == mc.player || p.isSpectator()) continue;
            String n = p.getGameProfile().name();
            out.add(new Target(n, p.getX(), p.getY(), p.getZ(), FarPlayers.dimName(mc.level.dimension()), true, true, Friends.is(n)));
        }
        if (far.on()) {
            for (FarPlayers.P p : FarPlayers.all()) {
                boolean same = p.dim() == mc.level.dimension();
                out.add(new Target(p.name(), p.x(), p.y(), p.z(), FarPlayers.dimName(p.dim()), same, false, Friends.is(p.name())));
            }
        }
        Vec3 me = mc.player.position();
        out.sort(Comparator.comparingDouble((Target t) -> t.sameDim() ? 0 : 1).thenComparingDouble(t -> dist2(t, me)));
        targets.clear();
        targets.addAll(out);
    }

    private static double dist2(Target t, Vec3 me) {
        double dx = t.x() - me.x, dy = t.y() - me.y, dz = t.z() - me.z;
        return dx * dx + dy * dy + dz * dz;
    }

    private static int color(Target t) {
        if (t.friend()) return 0xFF55FF55;
        return t.near() ? 0xFFFF5050 : 0xFFFFA040;
    }

    @Override
    public void onHud(GuiGraphicsExtractor g, float partial) {
        if (!inGame() || targets.isEmpty()) return;
        Vec3 eye = mc.player.getEyePosition();
        int line = 0;
        boolean first = true;
        for (Target t : targets) {
            int c = color(t);
            double cy = t.y() + 1.0;
            long dist = Math.round(Math.sqrt(dist2(t, mc.player.position())));
            if (t.sameDim()) {
                // Spieler, die man sieht: weich mitbewegen
                double x = t.x(), y = t.y(), z = t.z();
                if (t.near()) {
                    for (Player p : mc.level.players()) {
                        if (p.getGameProfile().name().equals(t.name())) {
                            Vec3 v = p.getPosition(partial);
                            x = v.x; y = v.y; z = v.z;
                            break;
                        }
                    }
                    cy = y + 1.0;
                }
                if (arrows.on()) Arrows.draw(g, x, cy, z, c, t.name() + " " + dist + "m" + Arrows.height(y), ring.num());
                if (tracers.on()) {
                    if (first) Draw.tracerNearest(g, x, cy, z, c, t.name() + " " + dist + "m");
                    else Draw.tracer(g, x, cy, z, c);
                }
                if (boxes.on()) {
                    double[] r = Draw.worldBox(g, new AABB(x - 0.3, y, z - 0.3, x + 0.3, y + 1.8, z + 0.3), c, false);
                    if (r != null) {
                        String s = t.name() + " §f" + dist + "m";
                        g.text(mc.font, s, (int) ((r[0] + r[2]) / 2) - mc.font.width(s) / 2, (int) r[1] - 10, c, true);
                    }
                }
                first = false;
            }
            if (list.on() && line < 12) {
                String s;
                if (t.sameDim()) {
                    double dx = t.x() - eye.x, dz = t.z() - eye.z;
                    s = "§f" + t.name() + " §7" + dist + " m " + StructureFinder.direction(dx, dz) + Arrows.height(t.y());
                } else {
                    s = "§f" + t.name() + " §8im " + t.dim() + " §7(" + Math.round(t.x()) + " " + Math.round(t.z()) + ")";
                }
                g.text(mc.font, s, 4, 14 + line * 10, c, true);
                line++;
            }
        }
    }

    @Override
    public String info() { return String.valueOf(targets.size()); }
}
