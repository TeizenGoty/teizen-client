package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.util.Rot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Zieht dein Fadenkreuz sanft zum nächsten Gegner. Mit Bogen, Armbrust oder Dreizack wird die Flugbahn
 * vorausberechnet (Schwerkraft + wohin das Ziel läuft) – und mit "autoschuss" spannt und schießt es von selbst.
 */
public final class AimAssist extends Module {
    private final Setting range = add(Setting.number("reichweite", "Wie weit Gegner erfasst werden (Blöcke, Nahkampf)", 6, 1, 64));
    private final Setting strength = add(Setting.number("staerke", "Wie stark gezogen wird (1 = sanft, 10 = sofort)", 3, 0.5, 10));
    private final Setting fov = add(Setting.number("sichtfeld", "Nur Gegner in diesem Winkel um das Fadenkreuz (Grad)", 90, 10, 360));
    private final Setting vertical = add(Setting.bool("hoch", "Auch nach oben/unten zielen", true));
    private final Setting onlyClick = add(Setting.bool("nurklick", "Nur solange die Angriffstaste (bzw. beim Bogen: Spannen) gedrückt ist", false));
    private final Setting players = add(Setting.bool("spieler", "Spieler", true));
    private final Setting monsters = add(Setting.bool("monster", "Monster", true));
    private final Setting animals = add(Setting.bool("tiere", "Tiere", false));
    private final Setting walls = add(Setting.bool("durchwaende", "Auch Gegner hinter Wänden (nur Nahkampf)", false));
    private final Setting bow = add(Setting.bool("bogen", "Bogen/Armbrust/Dreizack: Flugbahn vorausberechnen", true));
    private final Setting bowRange = add(Setting.number("bogenreichweite", "Wie weit mit dem Bogen gezielt wird (Blöcke)", 60, 5, 150));
    private final Setting autoShoot = add(Setting.bool("autoschuss", "Mit Bogen/Armbrust automatisch spannen und schießen, wenn ein Gegner da ist", true));
    private final Setting fastBow = add(Setting.bool("schnellbogen", "FastBow: Bogen schon nach kurzem Spannen loslassen = viel mehr Pfeile (dafür schwächer)", false));
    private final Setting fastCharge = add(Setting.number("spannung", "FastBow: nach wie vielen Ticks geschossen wird (20 = voll gespannt)", 10, 3, 20));

    public LivingEntity target;
    private long lastNanos;
    /** Wie oft automatisch geschossen wurde (Anzeige + Selbsttest). */
    public int shots;
    private boolean autoHeld;
    private int noTarget;

    private enum Weapon { BOW, CROSSBOW, TRIDENT }

    public AimAssist() {
        super("AimAssist", Category.KAMPF, "Zielt für dich – auch mit dem Bogen, und schießt automatisch.");
    }

    @Override
    protected void onDisable() {
        target = null;
        releaseAuto();
    }

    private void releaseAuto() {
        if (autoHeld && mc.options != null) mc.options.keyUse.setDown(false);
        autoHeld = false;
    }

    /** Winkel zwischen Blickrichtung und Ziel in Grad. */
    public static double angleTo(Vec3 point) {
        float[] r = Rot.to(point);
        double dy = Mth.wrapDegrees(r[0] - mc.player.getYRot());
        double dp = r[1] - mc.player.getXRot();
        return Math.sqrt(dy * dy + dp * dp);
    }

    private static Vec3 aimPoint(Entity e, float partial) {
        return e.getPosition(partial).add(0, e.getBbHeight() * 0.7, 0);
    }

    private Weapon weapon() {
        if (!bow.on()) return null;
        ItemStack s = mc.player.getMainHandItem();
        if (s.getItem() instanceof BowItem) return Weapon.BOW;
        if (s.getItem() instanceof CrossbowItem) return Weapon.CROSSBOW;
        if (s.is(Items.TRIDENT)) return Weapon.TRIDENT;
        return null;
    }

    /** Pfeil-Geschwindigkeit (Blöcke pro Tick), wenn jetzt geschossen würde. */
    private double speed(Weapon w) {
        return switch (w) {
            case BOW -> {
                int t = mc.player.isUsingItem() ? mc.player.getTicksUsingItem() : 20;
                if (autoShoot.on()) t = Math.max(t, 20); // Auto-Schuss wartet bis voll gespannt
                yield Math.max(0.3, BowItem.getPowerForTime(t) * 3.0);
            }
            case CROSSBOW -> 3.15;
            case TRIDENT -> 2.5;
        };
    }

    /**
     * Flugbahn: Simuliert den Pfeil (pro Tick: weiter, 1 % langsamer, 0.05 nach unten) und sucht den Winkel,
     * mit dem er auf Höhe {@code h} in Entfernung {@code d} ankommt. @return {Winkel nach oben in Grad, Flugzeit} oder null
     */
    static double[] solve(double d, double h, double v) {
        double lo = -85, hi = 0;
        // höchsten Punkt (größte Reichweite) finden: ca. 40°
        double best = -1e9, bestA = 40;
        for (double a = 0; a <= 60; a += 2) {
            double[] r = sim(a, d, v);
            if (r != null && r[0] > best) { best = r[0]; bestA = a; }
        }
        hi = bestA;
        double[] top = sim(hi, d, v);
        if (top == null || top[0] < h) return null; // zu weit weg
        double[] low = sim(lo, d, v);
        if (low != null && low[0] > h) return new double[]{lo, low[1]};
        for (int i = 0; i < 40; i++) {
            double mid = (lo + hi) / 2;
            double[] r = sim(mid, d, v);
            if (r == null || r[0] < h) lo = mid; else hi = mid;
        }
        double[] r = sim(hi, d, v);
        return new double[]{hi, r == null ? 20 : r[1]};
    }

    /** {Höhe bei Entfernung d, Ticks} oder null, wenn der Pfeil vorher zu tief fällt. */
    private static double[] sim(double angleDeg, double d, double v) {
        double a = Math.toRadians(angleDeg);
        double vx = v * Math.cos(a), vy = v * Math.sin(a);
        double x = 0, y = 0;
        for (int t = 1; t < 400; t++) {
            double nx = x + vx, ny = y + vy;
            if (nx >= d) {
                double f = (d - x) / Math.max(1e-6, nx - x);
                return new double[]{y + (ny - y) * f, t - 1 + f};
            }
            x = nx;
            y = ny;
            vx *= 0.99;
            vy = vy * 0.99 - 0.05;
            if (y < -300) return null;
        }
        return null;
    }

    /** {yaw, pitch} für einen Treffer mit dem Bogen (mit Vorhalt), oder null wenn zu weit. */
    private float[] ballistic(LivingEntity t, float partial, Weapon w) {
        Vec3 eye = mc.player.getEyePosition(partial).add(0, -0.1, 0);
        double v = speed(w);
        Vec3 base = t.getPosition(partial).add(0, t.getBbHeight() * 0.55, 0);
        Vec3 vel = new Vec3(t.getX() - t.xo, (t.onGround() ? 0 : t.getY() - t.yo), t.getZ() - t.zo);
        double time = 0;
        Vec3 at = base;
        double[] sol = null;
        for (int i = 0; i < 4; i++) {
            at = base.add(vel.x * time, vel.y * time * 0.5, vel.z * time);
            double dx = at.x - eye.x, dz = at.z - eye.z;
            sol = solve(Math.sqrt(dx * dx + dz * dz), at.y - eye.y, v);
            if (sol == null) return null;
            time = sol[1];
        }
        float yaw = (float) (Mth.atan2(at.z - eye.z, at.x - eye.x) * Mth.RAD_TO_DEG) - 90f;
        return new float[]{Mth.wrapDegrees(yaw), (float) Mth.clamp(-sol[0], -90, 90)};
    }

    private float[] want(LivingEntity t, float partial) {
        Weapon w = weapon();
        if (w != null) {
            float[] b = ballistic(t, partial, w);
            if (b != null) return b;
        }
        return Rot.to(aimPoint(t, partial));
    }

    @Override
    public void onTick() {
        if (!inGame() || mc.gui.screen() != null) { target = null; releaseAuto(); return; }
        Weapon w = weapon();
        boolean ranged = w != null;
        boolean auto = ranged && autoShoot.on();
        double r = ranged ? bowRange.num() : range.num();
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        double maxAngle = auto ? 360 : fov.num() / 2.0;
        for (Entity e : mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(r + 1),
                x -> Targets.valid(x, players.on(), monsters.on(), animals.on()))) {
            if (Targets.distanceToBox(e) > r) continue;
            if ((ranged || !walls.on()) && !mc.player.hasLineOfSight(e)) continue;
            double a = angleTo(aimPoint(e, 1f));
            if (a > maxAngle) continue;
            // Auto-Schuss: das nächste Ziel; sonst: das, auf das man am ehesten schaut
            double score = auto ? mc.player.distanceTo(e) : a;
            if (score < bestScore) { bestScore = score; best = (LivingEntity) e; }
        }
        target = best;
        if (auto) autoShootTick(w); else releaseAuto();
        fastBowTick();
    }

    /** Wie viele Pfeile FastBow schon geschossen hat (Selbsttest). */
    public int fastShots;

    /** FastBow: hältst du selbst den Bogen gespannt, wird nach kurzer Spannung geschossen und sofort neu gespannt. */
    private void fastBowTick() {
        if (!fastBow.on() || autoHeld || !mc.player.isUsingItem()) return;
        if (!mc.player.getUseItem().is(net.minecraft.world.item.Items.BOW)) return;
        if (mc.player.getTicksUsingItem() < fastCharge.integer()) return;
        if (target != null && autoShoot.on()) return; // Auto-Schuss zielt erst und schießt dann selbst
        mc.gameMode.releaseUsingItem(mc.player);
        ((dev.teizen.client.mixin.MinecraftAccessor) mc).teizen$setRightClickDelay(0);
        fastShots++;
        shots++;
    }

    private void autoShootTick(Weapon w) {
        if (target == null) {
            if (++noTarget > 20) releaseAuto();
            return;
        }
        noTarget = 0;
        float[] want = want(target, 1f);
        double dy = Mth.wrapDegrees(want[0] - mc.player.getYRot()), dp = want[1] - mc.player.getXRot();
        boolean aimed = Math.sqrt(dy * dy + dp * dp) < 1.5;
        ItemStack stack = mc.player.getMainHandItem();
        switch (w) {
            case BOW, TRIDENT -> {
                int need = w == Weapon.BOW ? (fastBow.on() ? fastCharge.integer() : 20) : 11;
                if (!mc.player.isUsingItem()) {
                    mc.options.keyUse.setDown(true);
                    autoHeld = true;
                } else if (mc.player.getTicksUsingItem() >= need && aimed) {
                    mc.options.keyUse.setDown(false);
                    mc.gameMode.releaseUsingItem(mc.player);
                    autoHeld = false;
                    shots++;
                }
            }
            case CROSSBOW -> {
                if (!CrossbowItem.isCharged(stack)) {
                    if (!mc.player.isUsingItem()) {
                        mc.options.keyUse.setDown(true);
                        autoHeld = true;
                    } else if (mc.player.getTicksUsingItem() >= CrossbowItem.getChargeDuration(stack, mc.player) + 1) {
                        mc.options.keyUse.setDown(false);
                        mc.gameMode.releaseUsingItem(mc.player);
                        autoHeld = false;
                    }
                } else if (aimed && !mc.player.isUsingItem()) {
                    mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                    dev.teizen.client.compat.V.swing(InteractionHand.MAIN_HAND);
                    shots++;
                }
            }
        }
    }

    /** Pro Bild drehen (nicht nur 20x pro Sekunde), damit es weich aussieht. */
    @Override
    public void onHud(GuiGraphicsExtractor g, float partial) {
        long now = System.nanoTime();
        double dt = lastNanos == 0 ? 0.016 : Math.min(0.1, (now - lastNanos) / 1e9);
        lastNanos = now;
        if (target == null || !target.isAlive() || !inGame() || mc.gui.screen() != null) return;
        Weapon w = weapon();
        boolean auto = w != null && autoShoot.on();
        if (onlyClick.on() && !auto && !mc.options.keyAttack.isDown() && !(w != null && mc.player.isUsingItem())) return;
        float[] want = want(target, partial);
        double frac = strength.num() >= 10 ? 1.0 : 1.0 - Math.exp(-strength.num() * 2.5 * dt);
        if (auto) frac = Math.max(frac, 1.0 - Math.exp(-14 * dt)); // beim Auto-Schuss genau treffen
        float yaw = mc.player.getYRot(), pitch = mc.player.getXRot();
        float dYaw = (float) (Mth.wrapDegrees(want[0] - yaw) * frac);
        float dPitch = (vertical.on() || w != null) ? (float) ((want[1] - pitch) * frac) : 0f;
        mc.player.setYRot(yaw + dYaw);
        mc.player.setXRot(Mth.clamp(pitch + dPitch, -90f, 90f));
        mc.player.yRotO += dYaw;
        mc.player.xRotO += dPitch;
    }

    @Override
    public String info() { return weapon() != null ? "Bogen" + (shots > 0 ? " " + shots : "") : Setting.fmt(strength.num()); }
}
