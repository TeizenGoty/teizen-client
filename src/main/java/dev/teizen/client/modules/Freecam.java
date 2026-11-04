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
 * Freecam wie die Freecam-Mod: die Kamera löst sich vom Spieler und fliegt frei herum (WASD, Leertaste, Shift),
 * du selbst bleibst stehen und bist sichtbar. Standard: unbegrenzt weit ("maxabstand" 0).
 * Blöcke abbauen/benutzen ist mit der Kamera gesperrt (wie bei der Mod), außer "interagieren" ist an.
 */
public final class Freecam extends Module {
    private final Setting speed = add(Setting.number("tempo", "Tempo der Kamera (Blöcke pro Tick, Sprinten = doppelt)", 1, 0.05, 10));
    private final Setting maxDist = add(Setting.number("maxabstand", "Wie weit die Kamera weg darf (Blöcke, 0 = unbegrenzt)", 0, 0, 100000));
    public final Setting showPlayer = add(Setting.bool("spielerzeigen", "Dich selbst sehen, während die Kamera weg ist", true));
    public final Setting hideHand = add(Setting.bool("handweg", "Hand und Item in der Hand ausblenden", true));
    public final Setting interact = add(Setting.bool("interagieren", "Mit der Kamera abbauen/benutzen erlauben (sonst gesperrt)", false));
    public final Setting seeWalls = add(Setting.bool("durchwaende", "In Wänden alles sichtbar machen (AUS lassen, wenn du Sodium/Iris nutzt – kann abstürzen)", false));

    public ArmorStand cam;
    /** Wie oft der eigene Spieler extra gezeichnet wurde (Selbsttest). */
    public int playerDrawn;
    private ClientInput savedInput;
    /** Aktuelle Geschwindigkeit der Kamera (weich anfahren und abbremsen). */
    private Vec3 vel = Vec3.ZERO;
    private boolean savedSmartCull, smartCullTouched;
    public final Setting smooth = add(Setting.bool("weich", "Weich anfahren und abbremsen statt ruckartig", true));
    /** Von NoClip eingeschaltet (dann ohne Abstands-Grenze). */
    public boolean byNoClip;
    /** Tempo von NoClip (0 = eigenes Tempo). */
    public double speedOverride;

    public Freecam() {
        super("Freecam", Category.BEWEGUNG, "Kamera fliegt frei herum, du bleibst stehen.");
    }

    public boolean active() {
        return isEnabled() && cam != null;
    }

    /** Freecam an und Abbauen/Benutzen gesperrt? (für den Minecraft-Mixin) */
    public static boolean blocksInteract() {
        if (dev.teizen.client.TeizenClient.modules() == null) return false;
        Freecam f = dev.teizen.client.TeizenClient.modules().get(Freecam.class);
        return f != null && f.active() && !f.interact.on();
    }

    /** Freecam an und Hand ausblenden? (für den GameRenderer-Mixin) */
    public static boolean hidesHand() {
        if (dev.teizen.client.TeizenClient.modules() == null) return false;
        Freecam f = dev.teizen.client.TeizenClient.modules().get(Freecam.class);
        return f != null && f.active() && f.hideHand.on();
    }

    @Override
    protected void onEnable() {
        if (inGame()) start();
    }

    private void start() {
        // Die Kamera nimmt ihre Blickrichtung direkt aus yRot/xRot (ein Rüstungsständer würde sonst die
        // Kopf-Drehung nehmen, die sich nie ändert -> man konnte nicht nach links/rechts schauen).
        cam = new ArmorStand(mc.level, mc.player.getX(), mc.player.getY(), mc.player.getZ()) {
            @Override
            public float getViewYRot(float partial) { return getYRot(); }

            @Override
            public float getViewXRot(float partial) { return getXRot(); }
        };
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
        vel = Vec3.ZERO;
        // in Wänden nicht alles schwarz/unsichtbar: Sicht-Verdeckung (Culling) aus, solange die Kamera frei ist
        if (seeWalls.on()) { try { savedSmartCull = mc.smartCull; mc.smartCull = false; smartCullTouched = true; } catch (Throwable ignored) {} }
    }

    @Override
    protected void onDisable() {
        if (mc.player != null) {
            if (mc.getCameraEntity() == cam) mc.setCameraEntity(mc.player);
            if (!(mc.player.input instanceof KeyboardInput))
                mc.player.input = savedInput instanceof KeyboardInput ? savedInput : new KeyboardInput(mc.options);
        }
        if (smartCullTouched) { try { mc.smartCull = savedSmartCull; } catch (Throwable ignored) {} smartCullTouched = false; }
        cam = null;
        savedInput = null;
        vel = Vec3.ZERO;
        byNoClip = false;
    }

    /** Mausbewegung dreht die Kamera statt den Spieler (aus dem Entity-Mixin). */
    public void turn(double dx, double dy) {
        if (cam == null) return;
        cam.turn(dx, dy);
        cam.setYHeadRot(cam.getYRot());
        cam.setYBodyRot(cam.getYRot());
    }

    /** Spieler zur Kamera teleportieren (braucht OP / eigene Welt). @return true wenn der Befehl rausging */
    public boolean teleportHere() {
        if (cam == null || !inGame()) return false;
        if (!dev.teizen.client.util.Op.available()) { dev.teizen.client.util.Chat.error("TP zur Kamera braucht OP (eigener Server)."); return false; }
        dev.teizen.client.util.Op.run(String.format(java.util.Locale.ROOT, "tp @s %.2f %.2f %.2f", cam.getX(), cam.getY(), cam.getZ()));
        dev.teizen.client.util.Chat.ok("Zur Kamera teleportiert.");
        return true;
    }

    /** Blickrichtung der Kamera (Selbsttest). */
    public float camYaw() { return cam == null ? 0 : cam.getYRot(); }

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
        double yaw = Math.toRadians(cam.getYRot()), pitch = Math.toRadians(cam.getXRot());
        Vec3 look = new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
        Vec3 left = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
        Vec3 move = look.scale(f).add(left.scale(s)).add(0, u, 0);
        double sp = (speedOverride > 0 ? speedOverride : speed.num()) * (mc.options.keySprint.isDown() ? 2 : 1);
        Vec3 want = move.lengthSqr() < 1e-6 ? Vec3.ZERO : move.normalize().scale(sp);
        // weich: in ~4 Ticks auf Tempo, in ~3 Ticks wieder stehen
        if (smooth.on()) vel = vel.add(want.subtract(vel).scale(want == Vec3.ZERO ? 0.35 : 0.3));
        else vel = want;
        if (vel.lengthSqr() < 1e-5) { vel = Vec3.ZERO; return; }
        Vec3 next = cam.position().add(vel);
        double max = byNoClip ? 0 : maxDist.num();
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
        String t = (byNoClip ? "NoClip: " : "Freecam: ") + Math.round(distance()) + " m" + (!byNoClip && maxDist.num() > 0 ? " / " + Setting.fmt(maxDist.num()) + " m" : " §7(unbegrenzt)");
        g.text(mc.font, t, g.guiWidth() / 2 - mc.font.width(t) / 2, 4, 0xFF55FFFF, true);
    }

    @Override
    public String info() { return Setting.fmt(speed.num()); }
}
