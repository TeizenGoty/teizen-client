package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.util.Draw;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;

/**
 * Freecam: die Kamera löst sich vom Spieler und fliegt frei herum, du selbst bleibst stehen.
 * Mit "reichweite" stellst du ein, wie weit sich die Kamera vom Spieler entfernen darf.
 */
public final class Freecam extends Module {
    private final Setting speed = add(Setting.number("tempo", "Tempo der Kamera (Blöcke pro Tick, Sprinten = doppelt)", 1, 0.05, 10));
    private final Setting maxDist = add(Setting.number("reichweite", "Wie weit die Kamera weg darf (Blöcke, 0 = unbegrenzt)", 64, 0, 2000));

    public ArmorStand cam;
    private ClientInput savedInput;

    public Freecam() {
        super("Freecam", Category.BEWEGUNG, "Kamera fliegt frei herum, du bleibst stehen.");
    }

    public boolean active() {
        return isEnabled() && cam != null;
    }

    @Override
    protected void onEnable() {
        if (inGame()) start();
    }

    private void start() {
        cam = new ArmorStand(mc.level, mc.player.getX(), mc.player.getY(), mc.player.getZ());
        cam.setNoGravity(true);
        cam.setInvisible(true);
        cam.noPhysics = true;
        Vec3 eye = mc.player.getEyePosition();
        cam.setPos(eye.x, eye.y - cam.getEyeHeight(), eye.z);
        cam.setYRot(mc.player.getYRot());
        cam.setXRot(mc.player.getXRot());
        cam.setYHeadRot(mc.player.getYRot());
        cam.setOldPosAndRot();
        savedInput = mc.player.input;
        mc.player.input = new ClientInput();
        mc.setCameraEntity(cam);
    }

    @Override
    protected void onDisable() {
        if (mc.player != null) {
            if (mc.getCameraEntity() == cam) mc.setCameraEntity(mc.player);
            if (!(mc.player.input instanceof KeyboardInput))
                mc.player.input = savedInput instanceof KeyboardInput ? savedInput : new KeyboardInput(mc.options);
        }
        cam = null;
        savedInput = null;
    }

    /** Mausbewegung dreht die Kamera statt den Spieler (aus dem Entity-Mixin). */
    public void turn(double dx, double dy) {
        if (cam != null) cam.turn(dx, dy);
    }

    /** Abstand Kamera – Spieler-Augen. */
    public double distance() {
        if (cam == null || mc.player == null) return 0;
        return cam.getEyePosition().distanceTo(mc.player.getEyePosition());
    }

    @Override
    public void onTick() {
        if (!inGame()) return;
        if (cam == null) { start(); return; }
        if (cam.level() != mc.level || mc.getCameraEntity() != cam) { setEnabled(false); return; }
        if (!(mc.player.input.getClass() == ClientInput.class)) { savedInput = mc.player.input; mc.player.input = new ClientInput(); }
        cam.setOldPosAndRot();
        double f = (mc.options.keyUp.isDown() ? 1 : 0) - (mc.options.keyDown.isDown() ? 1 : 0);
        double s = (mc.options.keyLeft.isDown() ? 1 : 0) - (mc.options.keyRight.isDown() ? 1 : 0);
        double u = (mc.options.keyJump.isDown() ? 1 : 0) - (mc.options.keyShift.isDown() ? 1 : 0);
        if (f == 0 && s == 0 && u == 0) return;
        double yaw = Math.toRadians(cam.getYRot()), pitch = Math.toRadians(cam.getXRot());
        Vec3 look = new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
        Vec3 left = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
        Vec3 move = look.scale(f).add(left.scale(s)).add(0, u, 0);
        if (move.lengthSqr() < 1e-6) return;
        double sp = speed.num() * (mc.options.keySprint.isDown() ? 2 : 1);
        Vec3 next = cam.position().add(move.normalize().scale(sp));
        double max = maxDist.num();
        if (max > 0) {
            Vec3 eye = mc.player.getEyePosition();
            Vec3 camEye = next.add(0, cam.getEyeHeight(), 0);
            Vec3 d = camEye.subtract(eye);
            if (d.length() > max) next = eye.add(d.normalize().scale(max)).subtract(0, cam.getEyeHeight(), 0);
        }
        cam.setPos(next.x, next.y, next.z);
    }

    /** Wo du selbst stehst (die Kamera ist ja woanders). */
    @Override
    public void onHud(GuiGraphicsExtractor g, float partial) {
        if (cam == null || !inGame()) return;
        var bb = mc.player.getBoundingBox();
        double[] r = Draw.worldBox(g, bb, 0xFF55FFFF, false);
        if (r != null) g.text(mc.font, "Du", (int) ((r[0] + r[2]) / 2) - mc.font.width("Du") / 2, (int) r[1] - 10, 0xFF55FFFF, true);
        String t = "Freecam: " + Math.round(distance()) + " m" + (maxDist.num() > 0 ? " / " + Setting.fmt(maxDist.num()) + " m" : "");
        g.text(mc.font, t, g.guiWidth() / 2 - mc.font.width(t) / 2, 4, 0xFF55FFFF, true);
    }

    @Override
    public String info() { return Setting.fmt(speed.num()); }
}
