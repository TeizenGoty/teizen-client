package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/** Weicht Pfeilen, Dreizacken, Feuerbällen usw. automatisch zur Seite aus, wenn sie dich treffen würden. */
public final class ProjectileDodge extends Module {
    private final Setting range = add(Setting.number("reichweite", "Ab welcher Entfernung ausgewichen wird (Blöcke)", 16, 3, 64));
    private final Setting speed = add(Setting.number("tempo", "Wie schnell zur Seite ausgewichen wird", 0.6, 0.1, 2));
    private final Setting jump = add(Setting.bool("springen", "Beim Ausweichen hochspringen", false));
    private final Setting safe = add(Setting.bool("sicher", "Nicht in Löcher oder gegen Wände ausweichen", true));

    public int dodges;
    private final Set<Integer> dodged = new HashSet<>();

    public ProjectileDodge() {
        super("ProjectileDodge", Category.BEWEGUNG, "Weicht Pfeilen und Feuerbällen automatisch aus.");
    }

    @Override
    protected void onDisable() { dodged.clear(); }

    /** Würde dieses Geschoss uns in den nächsten Ticks treffen? */
    private boolean threat(Projectile p, AABB me) {
        Vec3 v = p.getDeltaMovement();
        if (v.lengthSqr() < 0.04) return false;
        double grav = p instanceof AbstractHurtingProjectile ? 0 : 0.05;
        double drag = p instanceof AbstractHurtingProjectile ? 1.0 : 0.99;
        Vec3 pos = p.position();
        for (int t = 0; t < 40; t++) {
            Vec3 next = pos.add(v);
            if (me.contains(next) || me.clip(pos, next).isPresent()) return true;
            pos = next;
            v = v.scale(drag).subtract(0, grav, 0);
            if (pos.y < me.minY - 4 && v.y < 0) return false;
        }
        return false;
    }

    private boolean freeAt(Vec3 dir) {
        if (!safe.on()) return true;
        Vec3 target = mc.player.position().add(dir.scale(1.2));
        BlockPos feet = BlockPos.containing(target);
        boolean wall = !mc.level.getBlockState(feet).getCollisionShape(mc.level, feet).isEmpty()
                || !mc.level.getBlockState(feet.above()).getCollisionShape(mc.level, feet.above()).isEmpty();
        boolean ground = !mc.level.getBlockState(feet.below()).isAir() || !mc.level.getBlockState(feet.below(2)).isAir();
        return !wall && ground;
    }

    @Override
    public void onTick() {
        if (!inGame() || mc.player.getAbilities().flying || mc.player.isPassenger()) return;
        AABB me = mc.player.getBoundingBox().inflate(0.35);
        for (Projectile p : mc.level.getEntitiesOfClass(Projectile.class, mc.player.getBoundingBox().inflate(range.num()))) {
            if (p.getOwner() == mc.player || p instanceof FishingHook || !p.isAlive()) continue;
            if (!threat(p, me)) continue;
            Vec3 v = p.getDeltaMovement();
            Vec3 hv = new Vec3(v.x, 0, v.z);
            Vec3 side = hv.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : new Vec3(-hv.z, 0, hv.x).normalize();
            // auf die Seite, auf der wir schon etwas neben der Flugbahn stehen
            Vec3 rel = mc.player.position().subtract(p.position());
            if (rel.x * side.x + rel.z * side.z < 0) side = side.scale(-1);
            if (!freeAt(side) && freeAt(side.scale(-1))) side = side.scale(-1);
            Vec3 cur = mc.player.getDeltaMovement();
            double y = jump.on() && mc.player.onGround() ? 0.42 : cur.y;
            mc.player.setDeltaMovement(side.x * speed.num(), y, side.z * speed.num());
            if (dodged.add(p.getId())) dodges++;
            if (dodged.size() > 200) dodged.clear();
            return;
        }
    }

    @Override
    public String info() { return dodges > 0 ? String.valueOf(dodges) : ""; }
}
