package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.util.Draw;
import dev.teizen.client.util.Projector;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Trajectories: zeigt die Flugbahn und wo es landet – Perle, Pfeil (Bogen/Armbrust), Dreizack, Schneeball, Ei,
 * Wurftränke und XP-Flaschen. Trifft die Bahn ein Wesen, wird es markiert.
 */
public final class Trajectories extends Module {
    private final Setting line = add(Setting.bool("linie", "Flugbahn als Linie zeigen", true));
    private final Setting color = add(Setting.choice("farbe", "Farbe der Flugbahn", "weiss", "weiss", "gelb", "gruen", "rot"));

    /** Ergebnis der letzten Berechnung (Selbsttest). */
    public Vec3 landing;
    public Entity hitEntity;
    private final List<Vec3> path = new ArrayList<>();

    public Trajectories() {
        super("Trajectories", Category.SICHT, "Zeigt Flugbahn und Landepunkt von Perlen, Pfeilen, Dreizack usw.");
    }

    /** Flug-Werte: Tempo, Schwerkraft, Bremsen, Winkel-Versatz. */
    private record Kind(double speed, double gravity, double drag, float pitchOffset) {}

    private Kind kind(ItemStack s) {
        if (s.is(Items.ENDER_PEARL) || s.is(Items.SNOWBALL) || s.is(Items.EGG) || s.is(Items.BLUE_EGG) || s.is(Items.BROWN_EGG))
            return new Kind(1.5, 0.03, 0.99, 0);
        if (s.is(Items.SPLASH_POTION) || s.is(Items.LINGERING_POTION)) return new Kind(0.5, 0.05, 0.99, -20);
        if (s.is(Items.EXPERIENCE_BOTTLE)) return new Kind(0.7, 0.07, 0.99, -20);
        if (s.is(Items.TRIDENT)) {
            if (!mc.player.isUsingItem()) return null;
            return new Kind(2.5, 0.05, 0.99, 0);
        }
        if (s.getItem() instanceof BowItem) {
            if (!mc.player.isUsingItem()) return null;
            float p = BowItem.getPowerForTime(mc.player.getTicksUsingItem());
            if (p < 0.1f) return null;
            return new Kind(p * 3.0, 0.05, 0.99, 0);
        }
        if (s.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(s)) return new Kind(3.15, 0.05, 0.99, 0);
        return null;
    }

    private ItemStack held() {
        ItemStack m = mc.player.getMainHandItem();
        if (kind(m) != null) return m;
        ItemStack o = mc.player.getOffhandItem();
        if (kind(o) != null) return o;
        return ItemStack.EMPTY;
    }

    /** Flugbahn berechnen (wie Minecraft: erst fliegen, dann bremsen, dann Schwerkraft). */
    public void simulate(float partial) {
        path.clear();
        landing = null;
        hitEntity = null;
        ItemStack s = held();
        if (s.isEmpty()) return;
        Kind k = kind(s);
        float yaw = mc.player.getViewYRot(partial), pitch = mc.player.getViewXRot(partial);
        float px = pitch + k.pitchOffset();
        double vx = -Mth.sin(yaw * Mth.DEG_TO_RAD) * Mth.cos(px * Mth.DEG_TO_RAD);
        double vy = -Mth.sin(px * Mth.DEG_TO_RAD);
        double vz = Mth.cos(yaw * Mth.DEG_TO_RAD) * Mth.cos(px * Mth.DEG_TO_RAD);
        double len = Math.sqrt(vx * vx + vy * vy + vz * vz);
        Vec3 vel = new Vec3(vx / len, vy / len, vz / len).scale(k.speed());
        Vec3 mv = mc.player.getDeltaMovement();
        vel = vel.add(mv.x, mc.player.onGround() ? 0 : mv.y, mv.z);
        Vec3 pos = mc.player.getPosition(partial).add(0, mc.player.getEyeHeight() - 0.1, 0);
        path.add(pos);
        for (int i = 0; i < 300; i++) {
            Vec3 next = pos.add(vel);
            BlockHitResult bh = mc.level.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
            Vec3 end = bh.getType() == HitResult.Type.BLOCK ? bh.getLocation() : next;
            // trifft es ein Wesen?
            AABB sweep = new AABB(pos, end).inflate(1);
            Entity best = null;
            double bestD = Double.MAX_VALUE;
            for (Entity e : mc.level.getEntities(mc.player, sweep, e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator())) {
                Optional<Vec3> h = e.getBoundingBox().inflate(0.3).clip(pos, end);
                if (h.isPresent() && h.get().distanceToSqr(pos) < bestD) { bestD = h.get().distanceToSqr(pos); best = e; }
            }
            if (best != null) {
                Vec3 at = best.getBoundingBox().inflate(0.3).clip(pos, end).orElse(end);
                path.add(at);
                landing = at;
                hitEntity = best;
                return;
            }
            path.add(end);
            if (bh.getType() == HitResult.Type.BLOCK) { landing = end; return; }
            if (end.y < mc.level.getMinY() - 16) return;
            pos = next;
            vel = vel.scale(k.drag()).add(0, -k.gravity(), 0);
        }
    }

    private int col() {
        if (color.is("gelb")) return 0xFFFFE040;
        if (color.is("gruen")) return 0xFF50FF70;
        if (color.is("rot")) return 0xFFFF5050;
        return 0xFFFFFFFF;
    }

    @Override
    public void onTick() {
        if (inGame()) simulate(1f); else { path.clear(); landing = null; }
    }

    @Override
    public void onHud(GuiGraphicsExtractor g, float partial) {
        if (!inGame()) return;
        simulate(partial);
        if (path.size() < 2) return;
        int c = col();
        if (line.on()) {
            double[] prev = null;
            for (int i = 1; i < path.size(); i++) {
                Vec3 p = path.get(i);
                double[] s = Projector.project(p.x, p.y, p.z);
                if (s != null && prev != null) Draw.solidLine(g, prev[0], prev[1], s[0], s[1], Draw.alpha(c, 0.85));
                prev = s;
            }
        }
        if (hitEntity != null) {
            Draw.worldBox(g, hitEntity.getBoundingBox().inflate(0.05), 0xFFFF4040, true);
        } else if (landing != null) {
            Draw.worldBox(g, new AABB(landing.x - 0.25, landing.y - 0.25, landing.z - 0.25, landing.x + 0.25, landing.y + 0.25, landing.z + 0.25), c, true);
        }
    }
}
