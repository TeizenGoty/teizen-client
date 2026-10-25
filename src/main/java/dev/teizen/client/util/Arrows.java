package dev.teizen.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Pfeile im Kreis um das Fadenkreuz, die zu einem Ziel zeigen (oben = geradeaus, rechts = rechts herum drehen).
 * Für PlayerFinder, XRay, PortalFinder und StructureFinder. Funktioniert auch in der Freecam.
 */
public final class Arrows {
    private static final Minecraft mc = Minecraft.getInstance();

    private Arrows() {}

    /** Winkel auf dem Bildschirm in Bogenmaß: 0 = oben (geradeaus), im Uhrzeigersinn. */
    public static double angle(double x, double z) {
        double yaw = Math.toRadians(Projector.camYaw());
        double dx = x - Projector.camX(), dz = z - Projector.camZ();
        double fwd = -Math.sin(yaw) * dx + Math.cos(yaw) * dz;
        double right = -Math.cos(yaw) * dx - Math.sin(yaw) * dz;
        return Math.atan2(right, fwd);
    }

    /** Höhen-Unterschied als kurzer Text (z.B. " ↑5" oder " ↓12"), leer wenn fast gleich hoch. */
    public static String height(double y) {
        long d = Math.round(y - Projector.camY());
        if (Math.abs(d) < 3) return "";
        return d > 0 ? " §a↑" + d : " §c↓" + (-d);
    }

    /**
     * Pfeil zeichnen.
     * @param radius Abstand vom Fadenkreuz in Pixeln
     * @param label  Text neben dem Pfeil (oder null)
     */
    public static void draw(GuiGraphicsExtractor g, double x, double y, double z, int color, String label, double radius) {
        draw(g, x, y, z, color, label, radius, 5);
    }

    /** Größe nach Entfernung: nah = volle Größe, weit weg kleiner (mindestens 60 %). */
    private static int sized(int size, double f) {
        return Math.max(3, (int) Math.round(size * (0.6 + 0.4 * f)));
    }

    /** Wie viele Pfeile seit dem letzten Zurücksetzen gezeichnet wurden (Selbsttest). */
    public static int drawn;

    /** Pfeil mit wählbarer Größe (Pixel). */
    public static void draw(GuiGraphicsExtractor g, double x, double y, double z, int color, String label, double radius, int size) {
        drawn++;
        double f = Draw.fade(x, y, z);
        size = sized(size, f);
        color = Draw.alpha(color | 0xFF000000, f);
        double a = angle(x, z);
        double cx = Projector.screenCenterX(), cy = Projector.screenCenterY();
        double sin = Math.sin(a), cos = Math.cos(a);
        float px = (float) (cx + sin * radius), py = (float) (cy - cos * radius);
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(px, py);
        pose.rotate((float) a);
        triangle(g, size + 1, Draw.alpha(0xA0000000, f));
        pose.translate(0f, -1f);
        triangle(g, size, color);
        pose.popMatrix();
        if (label != null && !label.isEmpty()) {
            double lr = radius + 9 + size;
            int lx = (int) (cx + sin * lr), ly = (int) (cy - cos * lr) - 4;
            int w = mc.font.width(label);
            // Text nicht aus dem Bild schieben
            lx = Math.max(2 + w / 2, Math.min(g.guiWidth() - 2 - w / 2, lx));
            ly = Math.max(2, Math.min(g.guiHeight() - 10, ly));
            g.text(mc.font, label, lx - w / 2, ly, color, true);
        }
    }

    /**
     * Pfeil am Bildschirmrand (wenn das Ziel nicht im Bild ist), zeigt in die Richtung, in die man sich drehen muss.
     * @return true wenn gezeichnet (false = Ziel ist im Bild)
     */
    public static boolean drawEdge(GuiGraphicsExtractor g, double x, double y, double z, int color, String label, int size) {
        double cx = Projector.screenCenterX(), cy = Projector.screenCenterY();
        double[] s = Projector.project(x, y, z);
        if (s != null && s[0] > 10 && s[0] < g.guiWidth() - 10 && s[1] > 10 && s[1] < g.guiHeight() - 10) return false;
        double f = Draw.fade(x, y, z);
        size = sized(size, f);
        color = Draw.alpha(color | 0xFF000000, f);
        double[] d = Projector.direction(x, y, z);
        double dx = d[0], dy = d[1];
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-6) { dx = 0; dy = -1; } else { dx /= len; dy /= len; }
        double mx = cx - size * 3, my = cy - size * 3 - 10;
        double t = Math.min(Math.abs(dx) < 1e-6 ? 1e9 : mx / Math.abs(dx), Math.abs(dy) < 1e-6 ? 1e9 : my / Math.abs(dy));
        float px = (float) (cx + dx * t), py = (float) (cy + dy * t);
        double a = Math.atan2(dx, -dy);
        drawn++;
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(px, py);
        pose.rotate((float) a);
        triangle(g, size + 1, Draw.alpha(0xA0000000, f));
        pose.translate(0f, -1f);
        triangle(g, size, color);
        pose.popMatrix();
        if (label != null && !label.isEmpty()) {
            int w = mc.font.width(label);
            int lx = (int) (px - dx * (size + 12)) - w / 2, ly = (int) (py - dy * (size + 12)) - 4;
            lx = Math.max(2, Math.min(g.guiWidth() - w - 2, lx));
            ly = Math.max(2, Math.min(g.guiHeight() - 10, ly));
            g.text(mc.font, label, lx, ly, color, true);
        }
        return true;
    }

    /** Dreieck mit Spitze nach oben: Spitze bei y = -size, Grundseite bei y = +size/2. */
    private static void triangle(GuiGraphicsExtractor g, int size, int argb) {
        int rows = size + size / 2;
        for (int i = 0; i < rows; i++) {
            int half = (int) Math.round(i * 0.62);
            int yy = -size + i;
            g.fill(-half, yy, half + 1, yy + 1, argb);
        }
    }
}
