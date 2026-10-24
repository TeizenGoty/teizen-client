package dev.teizen.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Schätzt, wie viel Schaden eine Explosion (Kristall, Anker) an einem Wesen macht. */
public final class Explosions {
    private static final Minecraft mc = Minecraft.getInstance();

    private Explosions() {}

    /**
     * Vanilla-Formel: Abstand, wie viel vom Wesen sichtbar ist, Schwierigkeit, Rüstung und Resistenz.
     * @param ignore Block, der bei der Sicht-Prüfung nicht zählt (der Anker selbst), oder null
     */
    public static double damage(LivingEntity e, Vec3 center, float power, BlockPos ignore) {
        double q = power * 2.0;
        double dist = Math.sqrt(e.distanceToSqr(center)) / q;
        if (dist > 1.0) return 0;
        double seen = seenPercent(center, e.getBoundingBox(), ignore);
        double impact = (1.0 - dist) * seen;
        double dmg = (impact * impact + impact) / 2.0 * 7.0 * q + 1.0;
        if (e instanceof Player) {
            switch (mc.level.getLevelData().getDifficulty()) {
                case PEACEFUL -> dmg = 0;
                case EASY -> dmg = Math.min(dmg / 2.0 + 1.0, dmg);
                case HARD -> dmg = dmg * 1.5;
                default -> { }
            }
        }
        double armor = e.getArmorValue();
        double tough = e.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        double f = Math.max(armor / 5.0, armor - dmg / (2.0 + tough / 4.0));
        dmg *= 1.0 - Math.min(20.0, f) / 25.0;
        MobEffectInstance res = e.getEffect(MobEffects.RESISTANCE);
        if (res != null) dmg *= Math.max(0.0, 1.0 - (res.getAmplifier() + 1) * 0.2);
        return Math.max(0, dmg);
    }

    /** Anteil der Hitbox, den die Explosion "sieht" (12 Strahlen). */
    public static double seenPercent(Vec3 c, AABB b, BlockPos ignore) {
        int hit = 0, total = 0;
        double[] xs = {b.minX + 0.05, b.maxX - 0.05};
        double[] ys = {b.minY + 0.05, (b.minY + b.maxY) / 2, b.maxY - 0.05};
        double[] zs = {b.minZ + 0.05, b.maxZ - 0.05};
        for (double x : xs) for (double y : ys) for (double z : zs) {
            total++;
            if (!blocked(c, new Vec3(x, y, z), ignore)) hit++;
        }
        return (double) hit / total;
    }

    private static boolean blocked(Vec3 a, Vec3 b, BlockPos ignore) {
        Vec3 d = b.subtract(a);
        double len = d.length();
        if (len < 1e-3) return false;
        int steps = (int) Math.ceil(len / 0.25);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int s = 1; s < steps; s++) {
            double t = s / (double) steps;
            m.set(Mth.floor(a.x + d.x * t), Mth.floor(a.y + d.y * t), Mth.floor(a.z + d.z * t));
            if (ignore != null && m.equals(ignore)) continue;
            BlockState st = mc.level.getBlockState(m);
            if (st.isAir()) continue;
            if (st.isCollisionShapeFullBlock(mc.level, m)) return true;
        }
        return false;
    }
}
