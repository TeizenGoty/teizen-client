package dev.teizen.client.modules;

import dev.teizen.client.compat.XaeroCompat;
import dev.teizen.client.core.FarPlayers;
import dev.teizen.client.core.Friends;
import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Radar: kleine Minimap mit allen Spielern (und optional Monstern/Tieren) als Punkte.
 * Ist Xaero's Minimap / World Map installiert, erscheinen die Spieler (auch weit entfernte) dort ebenfalls.
 */
public final class Radar extends Module {
    private final Setting zoom = add(Setting.number("zoom", "Wie viele Blöcke bis zum Rand", 64, 8, 512));
    private final Setting size = add(Setting.number("groesse", "Größe der Karte (Pixel)", 90, 40, 250));
    private final Setting corner = add(Setting.choice("ecke", "Wo die Karte ist", "unten-rechts", "unten-rechts", "oben-links", "unten-links", "oben-rechts"));
    private final Setting rotate = add(Setting.bool("drehen", "Karte dreht sich mit dir (oben = Blickrichtung)", true));
    private final Setting monsters = add(Setting.bool("monster", "Monster zeigen", true));
    private final Setting animals = add(Setting.bool("tiere", "Tiere zeigen", false));
    private final Setting names = add(Setting.bool("namen", "Spielernamen an den Punkten", true));
    private final Setting far = add(Setting.bool("weit", "Auch weit entfernte Spieler (eigene Welt direkt, Server: OP)", true));
    private final Setting xaero = add(Setting.bool("xaero", "Spieler auch auf Xaero's Minimap / World Map zeigen", true));

    /** Punkte, die beim letzten Bild gezeichnet wurden (Selbsttest). */
    public int shownPlayers;

    public Radar() {
        super("Radar", Category.SICHT, "Minimap mit allen Spielern (auch auf Xaero's Minimap).");
        XaeroCompat.setSource(() -> {
            if (!isEnabled() || !xaero.on() || mc.player == null) return List.of();
            return new ArrayList<>(FarPlayers.all());
        });
    }

    @Override
    public void onTick() {
        if (!inGame()) return;
        if (far.on() || xaero.on()) FarPlayers.want();
        if (xaero.on()) XaeroCompat.tick();
    }

    @Override
    public void onHud(GuiGraphicsExtractor g, float partial) {
        if (!inGame()) return;
        int s = size.integer();
        int x0, y0;
        switch (corner.str()) {
            case "oben-links" -> { x0 = 4; y0 = 14; }
            case "unten-links" -> { x0 = 4; y0 = g.guiHeight() - s - 50; }
            case "oben-rechts" -> { x0 = g.guiWidth() - s - 4; y0 = g.guiHeight() / 2 - s / 2; }
            default -> { x0 = g.guiWidth() - s - 4; y0 = g.guiHeight() - s - 4; }
        }
        int cx = x0 + s / 2, cy = y0 + s / 2;
        double scale = (s / 2.0 - 3) / zoom.num();
        // Hintergrund + Rahmen + Kreuz
        g.fill(x0 - 1, y0 - 1, x0 + s + 1, y0 + s + 1, 0xFF5B1FB8);
        g.fill(x0, y0, x0 + s, y0 + s, 0xB0100820);
        g.fill(cx, y0 + 2, cx + 1, y0 + s - 2, 0x30FFFFFF);
        g.fill(x0 + 2, cy, x0 + s - 2, cy + 1, 0x30FFFFFF);
        Vec3 me = mc.player.getPosition(partial);
        double yaw = Math.toRadians(rotate.on() ? mc.player.getViewYRot(partial) : 180);
        // Norden
        double[] np = toMap(0, -zoom.num(), yaw, scale, rotate.on());
        int nx = (int) Math.max(x0 + 4, Math.min(x0 + s - 8, cx + np[0])), ny = (int) Math.max(y0 + 2, Math.min(y0 + s - 10, cy + np[1]));
        g.text(mc.font, "N", nx - 2, ny - 4, 0xFFFF5555, true);
        // Monster / Tiere
        if (monsters.on() || animals.on()) {
            double r = zoom.num();
            for (Entity e : mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(r, 32, r))) {
                boolean mon = e instanceof Enemy, ani = !mon && e instanceof Mob;
                if (!(mon && monsters.on()) && !(ani && animals.on())) continue;
                Vec3 p = e.getPosition(partial);
                dot(g, p.x - me.x, p.z - me.z, yaw, scale, cx, cy, x0, y0, s, mon ? 0xFFFF7040 : 0xFF70E070, 1, null);
            }
        }
        int n = 0;
        for (Player p : mc.level.players()) {
            if (p == mc.player || p.isSpectator()) continue;
            Vec3 v = p.getPosition(partial);
            String name = p.getGameProfile().name();
            int c = Friends.is(name) ? 0xFF55FF55 : 0xFFFF4040;
            if (dot(g, v.x - me.x, v.z - me.z, yaw, scale, cx, cy, x0, y0, s, c, 2, names.on() ? name : null)) n++;
        }
        if (far.on()) {
            for (FarPlayers.P p : FarPlayers.all()) {
                if (p.dim() != mc.level.dimension()) continue;
                if (dot(g, p.x() - me.x, p.z() - me.z, yaw, scale, cx, cy, x0, y0, s, 0xFFFFA040, 2, names.on() ? p.name() : null)) n++;
            }
        }
        shownPlayers = n;
        // ich: Pfeil in der Mitte
        int pc = 0xFFFFFFFF;
        g.fill(cx - 1, cy - 3, cx + 2, cy - 2, pc);
        g.fill(cx - 2, cy - 2, cx + 3, cy + 2, pc);
        String t = Math.round(zoom.num()) + "m";
        g.text(mc.font, t, x0 + s - mc.font.width(t) - 2, y0 + s - 9, 0xFF9C88C8, false);
    }

    private static double[] toMap(double dx, double dz, double yaw, double scale, boolean rotate) {
        double x = dx, y = dz;
        if (rotate) {
            // Blickrichtung nach oben drehen
            double fwd = -Math.sin(yaw) * dx + Math.cos(yaw) * dz;
            double right = -Math.cos(yaw) * dx - Math.sin(yaw) * dz;
            x = right;
            y = -fwd;
        }
        return new double[]{x * scale, y * scale};
    }

    /** Punkt auf die Karte; außerhalb wird er an den Rand gesetzt. @return true wenn gezeichnet */
    private boolean dot(GuiGraphicsExtractor g, double dx, double dz, double yaw, double scale, int cx, int cy, int x0, int y0, int s,
                        int color, int r, String label) {
        double[] m = toMap(dx, dz, yaw, scale, rotate.on());
        double px = cx + m[0], py = cy + m[1];
        boolean edge = false;
        double lim = s / 2.0 - 3;
        double ax = px - cx, ay = py - cy;
        double big = Math.max(Math.abs(ax), Math.abs(ay));
        if (big > lim) { ax = ax / big * lim; ay = ay / big * lim; edge = true; }
        int ix = (int) (cx + ax), iy = (int) (cy + ay);
        if (edge && r < 2) return false; // Monster am Rand nicht zeigen
        g.fill(ix - r - 1, iy - r - 1, ix + r + 1, iy + r + 1, 0xC0000000);
        g.fill(ix - r, iy - r, ix + r, iy + r, edge ? (color & 0x00FFFFFF) | 0x90000000 : color);
        if (label != null && !edge) {
            var pose = g.pose();
            pose.pushMatrix();
            pose.translate(ix, iy - r - 5);
            pose.scale(0.6f, 0.6f);
            g.text(mc.font, label, -mc.font.width(label) / 2, 0, color, true);
            pose.popMatrix();
        }
        return true;
    }

    @Override
    public String info() { return String.valueOf(shownPlayers); }
}
