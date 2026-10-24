package dev.teizen.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Blickrichtungen berechnen und (unsichtbar) an den Server senden. */
public final class Rot {
    private static final Minecraft mc = Minecraft.getInstance();

    private Rot() {}

    /** [yaw, pitch] um von den Augen auf ein Ziel zu schauen. */
    public static float[] to(Vec3 target) {
        LocalPlayer p = mc.player;
        Vec3 eye = p.getEyePosition();
        double dx = target.x - eye.x, dy = target.y - eye.y, dz = target.z - eye.z;
        double h = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
        float pitch = (float) -(Mth.atan2(dy, h) * Mth.RAD_TO_DEG);
        return new float[]{Mth.wrapDegrees(yaw), Mth.clamp(pitch, -90f, 90f)};
    }

    /** Schickt eine Blickrichtung an den Server, ohne die Kamera zu bewegen. */
    public static void sendSilent(float yaw, float pitch) {
        LocalPlayer p = mc.player;
        p.connection.send(new ServerboundMovePlayerPacket.Rot(yaw, pitch, p.onGround(), p.horizontalCollision));
    }

    /** Dreht die Kamera wirklich (sichtbar). */
    public static void look(float yaw, float pitch) {
        LocalPlayer p = mc.player;
        p.setYRot(yaw);
        p.setXRot(pitch);
        p.setYHeadRot(yaw);
    }
}
