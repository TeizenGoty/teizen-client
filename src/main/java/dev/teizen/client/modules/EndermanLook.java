package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.util.Rot;
import net.minecraft.world.phys.Vec3;

/** EndermanLook: Endermen werden nicht wütend, wenn du sie anschaust (für den Server schaust du dann kurz weg). */
public final class EndermanLook extends Module {
    public int dodged;
    public boolean looking;

    public EndermanLook() {
        super("EndermanLook", Category.SONSTIGES, "Endermen werden nicht wütend, wenn du sie anschaust.");
    }

    @Override
    public void onTick() {
        if (!inGame()) return;
        looking = false;
        Vec3 eye = mc.player.getEyePosition();
        Vec3 look = mc.player.getViewVector(1f).normalize();
        for (var e : mc.level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, mc.player.getBoundingBox().inflate(64),
                x -> x.getType() == net.minecraft.world.entity.EntityType.ENDERMAN)) {
            if (!e.isAlive()) continue;
            Vec3 d = new Vec3(e.getX() - eye.x, e.getEyeY() - eye.y, e.getZ() - eye.z);
            double len = d.length();
            double dot = look.dot(d.scale(1 / len));
            if (dot > 1.0 - 0.025 / len && mc.player.hasLineOfSight(e)) { looking = true; break; }
        }
        if (looking) {
            // für den Server nach unten schauen (deine Kamera bleibt, wie sie ist)
            Rot.sendSilent(mc.player.getYRot(), 90f);
            dodged++;
        }
    }
}
