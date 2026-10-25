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
        double a = angle(x, z);
        double cx = Projector.screenCenterX(), cy = Projector.screenCenterY();
        double sin = Math.sin(a), cos = Math.cos(a);
        float px = (float) (cx + sin * radius), py = (float) (cy - cos * radius);
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(px, py);
        pose.rotate((float) a);
        triangle(g, 9, 0xC0000000);
        pose.translate(0f, -1f);
        triangle(g, 7, color | 0xFF000000);
        pose.popMatrix();
        if (label != null && !label.isEmpty()) {
            double lr = radius + 16;
            int lx = (int) (cx + sin * lr), ly = (int) (cy - cos * lr) - 4;
            int w = mc.font.width(label);
            // Text nicht aus dem Bild schieben
            lx = Math.max(2 + w / 2, Math.min(g.guiWidth() - 2 - w / 2, lx));
            ly = Math.max(2, Math.min(g.guiHeight() - 10, ly));
            g.text(mc.font, label, lx - w / 2, ly, color | 0xFF000000, true);
        }
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
