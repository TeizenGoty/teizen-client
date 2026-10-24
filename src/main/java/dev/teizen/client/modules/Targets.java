package dev.teizen.client.modules;

import dev.teizen.client.core.Friends;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;
import java.util.List;

/** Gemeinsame Ziel-Auswahl für KillAura, TriggerBot usw. */
public final class Targets {
    private static final Minecraft mc = Minecraft.getInstance();

    private Targets() {}

    public static boolean valid(Entity e, boolean players, boolean monsters, boolean animals) {
        LocalPlayer self = mc.player;
        if (!(e instanceof LivingEntity le) || e == self || !le.isAlive() || le.getHealth() <= 0) return false;
        if (e instanceof Player p) {
            if (!players || p.isSpectator() || p.isCreative()) return false;
            return !Friends.is(p.getGameProfile().name());
        }
        if (e instanceof Enemy) return monsters;
        if (e instanceof Mob) return animals;
        return false;
    }

    public static LivingEntity nearest(double range, boolean players, boolean monsters, boolean animals, boolean throughWalls) {
        LocalPlayer self = mc.player;
        AABB area = self.getBoundingBox().inflate(range + 1);
        List<Entity> list = mc.level.getEntities(self, area, e -> valid(e, players, monsters, animals));
        return list.stream()
                .map(e -> (LivingEntity) e)
                .filter(e -> distanceToBox(e) <= range)
                .filter(e -> throughWalls || self.hasLineOfSight(e))
                .min(Comparator.comparingDouble(Targets::distanceToBox))
                .orElse(null);
    }

    /** Abstand Augen -> nächster Punkt der Hitbox (so rechnet auch der Server). */
    public static double distanceToBox(Entity e) {
        AABB b = e.getBoundingBox();
        var eye = mc.player.getEyePosition();
        double x = Math.max(b.minX, Math.min(eye.x, b.maxX));
        double y = Math.max(b.minY, Math.min(eye.y, b.maxY));
        double z = Math.max(b.minZ, Math.min(eye.z, b.maxZ));
        return eye.distanceTo(new net.minecraft.world.phys.Vec3(x, y, z));
    }
}
