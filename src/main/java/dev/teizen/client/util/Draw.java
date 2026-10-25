package dev.teizen.client.util;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.phys.AABB;

/** Einfaches 2D-Zeichnen im HUD. */
public final class Draw {
    private Draw() {}

    public static void rect(GuiGraphicsExtractor g, double x1, double y1, double x2, double y2, int argb) {
        g.fill((int) Math.floor(x1), (int) Math.floor(y1), (int) Math.ceil(x2), (int) Math.ceil(y2), argb);
    }

    /** Rahmen (ohne Füllung). */
    public static void outline(GuiGraphicsExtractor g, double x1, double y1, double x2, double y2, int argb) {
        int a = (int) x1, b = (int) y1, c = (int) x2, d = (int) y2;
        if (c - a < 2) c = a + 2;
        if (d - b < 2) d = b + 2;
        g.fill(a, b, c, b + 1, argb);
        g.fill(a, d - 1, c, d, argb);
        g.fill(a, b, a + 1, d, argb);
        g.fill(c - 1, b, c, d, argb);
    }

    /** Linie aus kleinen Quadraten (Abstand 2px). */
    public static void line(GuiGraphicsExtractor g, double x1, double y1, double x2, double y2, int argb) {
        double dx = x2 - x1, dy = y2 - y1;
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len < 1) return;
        int steps = (int) Math.min(len / 2.0, 900);
        double sx = dx / steps, sy = dy / steps;
        double x = x1, y = y1;
        for (int i = 0; i <= steps; i++) {
            int ix = (int) x, iy = (int) y;
            g.fill(ix, iy, ix + 1, iy + 1, argb);
            x += sx; y += sy;
        }
    }

    /** Zeichnet eine Welt-Box als 2D-Rahmen. @return Bildschirm-Box oder null */
    public static double[] worldBox(GuiGraphicsExtractor g, AABB box, int argb, boolean fill) {
        double[] r = Projector.box(box);
        if (r == null) return null;
        int a = (argb >>> 24) & 0xFF;
        if (fill) rect(g, r[0], r[1], r[2], r[3], (argb & 0x00FFFFFF) | ((0x30 * a / 255) << 24));
        outline(g, r[0] - 1, r[1] - 1, r[2] + 1, r[3] + 1, (0x90 * a / 255) << 24);
        outline(g, r[0], r[1], r[2], r[3], argb);
        return r;
    }

    /**
     * Wie sichtbar etwas in dieser Entfernung ist: bis 24 Blöcke voll, dann immer blasser (mindestens 0.3).
     */
    public static double fade(double x, double y, double z) {
        double dx = x - Projector.camX(), dy = y - Projector.camY(), dz = z - Projector.camZ();
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        return d <= 24 ? 1.0 : Math.max(0.3, 1.0 - (d - 24) / 200.0);
    }

    /** Durchgehende 1-Pixel-Linie (für die Linie zum nächsten Ziel). */
    public static void solidLine(GuiGraphicsExtractor g, double x1, double y1, double x2, double y2, int argb) {
        double dx = x2 - x1, dy = y2 - y1;
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len < 1) return;
        int steps = (int) Math.min(len, 1500);
        double sx = dx / steps, sy = dy / steps;
        double x = x1, y = y1;
        for (int i = 0; i <= steps; i++) {
            int ix = (int) x, iy = (int) y;
            g.fill(ix, iy, ix + 1, iy + 1, argb);
            x += sx; y += sy;
        }
    }

    /** Tracer-Linie von der Bildschirmmitte zu einem Welt-Punkt (oder Pfeil am Rand, wenn dahinter). Weiter weg = blasser. */
    public static void tracer(GuiGraphicsExtractor g, double x, double y, double z, int argb) {
        argb = alpha(argb, fade(x, y, z));
        double cx = Projector.screenCenterX(), cy = Projector.screenCenterY();
        double[] s = Projector.project(x, y, z);
        if (s != null) {
            line(g, cx, cy, s[0], s[1], argb);
        } else {
            double[] d = Projector.direction(x, y, z);
            double r = Math.min(cx, cy) * 0.85;
            line(g, cx, cy, cx + d[0] * r, cy + d[1] * r, argb);
        }
    }

    /** Dicke Linie (3 Pixel breit, mit dunklem Rand). */
    public static void thickLine(GuiGraphicsExtractor g, double x1, double y1, double x2, double y2, int argb) {
        double dx = x2 - x1, dy = y2 - y1;
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len < 1) return;
        int steps = (int) Math.min(len / 1.5, 1200);
        double sx = dx / steps, sy = dy / steps;
        double x = x1, y = y1;
        for (int i = 0; i <= steps; i++) {
            int ix = (int) x, iy = (int) y;
            g.fill(ix - 2, iy - 2, ix + 2, iy + 2, 0x70000000);
            x += sx; y += sy;
        }
        x = x1; y = y1;
        for (int i = 0; i <= steps; i++) {
            int ix = (int) x, iy = (int) y;
            g.fill(ix - 1, iy - 1, ix + 2, iy + 2, argb);
            x += sx; y += sy;
        }
    }

    /**
     * Tracer zum NÄCHSTEN Ziel: normale durchgehende Linie (nicht blass) mit kleinem Punkt am Ende.
     * Beschriftung nur, wenn label nicht null ist (Strukturen).
     */
    public static void tracerNearest(GuiGraphicsExtractor g, double x, double y, double z, int argb, String label) {
        double cx = Projector.screenCenterX(), cy = Projector.screenCenterY();
        double[] s = Projector.project(x, y, z);
        int c = argb | 0xFF000000;
        double ex, ey;
        if (s != null) {
            ex = s[0]; ey = s[1];
        } else {
            double[] d = Projector.direction(x, y, z);
            double r = Math.min(cx, cy) * 0.85;
            ex = cx + d[0] * r; ey = cy + d[1] * r;
        }
        solidLine(g, cx, cy, ex, ey, c);
        int ix = (int) ex, iy = (int) ey;
        g.fill(ix - 2, iy - 2, ix + 2, iy + 2, 0x90000000);
        g.fill(ix - 1, iy - 1, ix + 1, iy + 1, c);
        if (label != null && !label.isEmpty()) {
            var font = net.minecraft.client.Minecraft.getInstance().font;
            String t = label;
            int w = font.width(t);
            int tx = Math.max(2, Math.min(g.guiWidth() - w - 2, ix + 7));
            int ty = Math.max(2, Math.min(g.guiHeight() - 12, iy - 4));
            g.fill(tx - 2, ty - 2, tx + w + 2, ty + 10, 0xA0000000);
            g.text(font, t, tx, ty, c, true);
        }
    }

    /** Farbe mit anderer Deckkraft (0..1). */
    public static int alpha(int argb, double a) {
        int al = (int) Math.round(Math.max(0, Math.min(1, a)) * ((argb >>> 24) & 0xFF));
        return (al << 24) | (argb & 0x00FFFFFF);
    }

    public static int rgb(int r, int g, int b) {
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
