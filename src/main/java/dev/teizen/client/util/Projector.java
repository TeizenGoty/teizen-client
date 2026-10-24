package dev.teizen.client.util;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3fc;
import org.joml.Vector4f;

/**
 * Rechnet Welt-Positionen in Bildschirm-Positionen um (für ESP, Tracer, Markierungen).
 * Arbeitet komplett im HUD, deshalb unabhängig von Shadern/Sodium und immer durch Wände sichtbar.
 */
public final class Projector {
    private static final Minecraft mc = Minecraft.getInstance();

    private static double cx, cy, cz;
    private static double fx, fy, fz, ux, uy, uz, lx, ly, lz;
    private static double tanHalf, halfW, halfH, aspect;
    private static boolean ready;
    private static final Matrix4f viewProj = new Matrix4f();
    private static boolean useMatrix;

    private Projector() {}

    /** Einmal pro Frame vor dem Zeichnen aufrufen. */
    public static boolean begin(GuiGraphicsExtractor g) {
        Camera cam = mc.gameRenderer.mainCamera();
        if (cam == null || !cam.isInitialized()) { ready = false; return false; }
        Vec3 p = cam.position();
        cx = p.x; cy = p.y; cz = p.z;
        Vector3fc f = cam.forwardVector(), u = cam.upVector(), l = cam.leftVector();
        fx = f.x(); fy = f.y(); fz = f.z();
        ux = u.x(); uy = u.y(); uz = u.z();
        lx = l.x(); ly = l.y(); lz = l.z();
        double fov = cam.getFov() > 1 ? cam.getFov() : Fov.current();
        try {
            cam.getViewRotationProjectionMatrix(viewProj);
            useMatrix = viewProj.isFinite();
            if (useMatrix) {
                // Prüfen: ein Punkt genau vor der Kamera muss in der Bildmitte landen
                Vector4f t = new Vector4f(f.x() * 10f, f.y() * 10f, f.z() * 10f, 1f).mul(viewProj);
                useMatrix = t.w > 0.01f && Math.abs(t.x / t.w) < 0.05f && Math.abs(t.y / t.w) < 0.05f;
            }
        } catch (Throwable t) {
            useMatrix = false;
        }
        tanHalf = Math.tan(Math.toRadians(fov) / 2.0);
        halfW = g.guiWidth() / 2.0;
        halfH = g.guiHeight() / 2.0;
        aspect = halfW / halfH;
        ready = true;
        return true;
    }

    /** @return {x, y} in GUI-Koordinaten oder null wenn hinter der Kamera. */
    public static double[] project(double x, double y, double z) {
        if (!ready) return null;
        double dx = x - cx, dy = y - cy, dz = z - cz;
        double depth = dx * fx + dy * fy + dz * fz;
        if (depth < 0.05) return null;
        if (useMatrix) {
            Vector4f v = new Vector4f((float) dx, (float) dy, (float) dz, 1f).mul(viewProj);
            if (v.w > 0.01f) {
                double nx = v.x / v.w, ny = v.y / v.w;
                return new double[]{(nx + 1) * halfW, (1 - ny) * halfH};
            }
        }
        double right = -(dx * lx + dy * ly + dz * lz);
        double up = dx * ux + dy * uy + dz * uz;
        double sx = halfW + (right / (depth * tanHalf * aspect)) * halfW;
        double sy = halfH - (up / (depth * tanHalf)) * halfH;
        return new double[]{sx, sy};
    }

    /** Richtung (Winkel) zu einem Punkt, auch wenn er hinter einem liegt – für Pfeile am Rand. */
    public static double[] direction(double x, double y, double z) {
        double dx = x - cx, dy = y - cy, dz = z - cz;
        double right = -(dx * lx + dy * ly + dz * lz);
        double up = dx * ux + dy * uy + dz * uz;
        double len = Math.sqrt(right * right + up * up);
        if (len < 1e-6) return new double[]{0, -1};
        return new double[]{right / len, -up / len};
    }

    /** Projiziert eine Box. @return {minX, minY, maxX, maxY} oder null. */
    public static double[] box(AABB b) {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        int visible = 0;
        for (int i = 0; i < 8; i++) {
            double[] s = project((i & 1) == 0 ? b.minX : b.maxX, (i & 2) == 0 ? b.minY : b.maxY, (i & 4) == 0 ? b.minZ : b.maxZ);
            if (s == null) return null; // Box teilweise hinter der Kamera -> nicht zeichnen (sonst riesige Fehl-Rahmen)
            visible++;
            minX = Math.min(minX, s[0]); minY = Math.min(minY, s[1]);
            maxX = Math.max(maxX, s[0]); maxY = Math.max(maxY, s[1]);
        }
        if (visible == 0) return null;
        return new double[]{minX, minY, maxX, maxY};
    }

    public static double screenCenterX() { return halfW; }
    public static double screenCenterY() { return halfH; }
}
