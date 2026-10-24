package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.util.Rot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Zieht dein Fadenkreuz sanft zum nächsten Gegner (du schlägst selbst). */
public final class AimAssist extends Module {
    private final Setting range = add(Setting.number("reichweite", "Wie weit Gegner erfasst werden (Blöcke)", 6, 1, 64));
    private final Setting strength = add(Setting.number("staerke", "Wie stark gezogen wird (1 = sanft, 10 = sofort)", 3, 0.5, 10));
    private final Setting fov = add(Setting.number("sichtfeld", "Nur Gegner in diesem Winkel um das Fadenkreuz (Grad)", 90, 10, 360));
    private final Setting vertical = add(Setting.bool("hoch", "Auch nach oben/unten zielen", true));
    private final Setting onlyClick = add(Setting.bool("nurklick", "Nur solange die Angriffstaste gedrückt ist", false));
    private final Setting players = add(Setting.bool("spieler", "Spieler", true));
    private final Setting monsters = add(Setting.bool("monster", "Monster", true));
    private final Setting animals = add(Setting.bool("tiere", "Tiere", false));
    private final Setting walls = add(Setting.bool("durchwaende", "Auch Gegner hinter Wänden", false));

    public LivingEntity target;
    private long lastNanos;

    public AimAssist() {
        super("AimAssist", Category.KAMPF, "Zieht dein Fadenkreuz sanft zum nächsten Gegner.");
    }

    @Override
    protected void onDisable() { target = null; }

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

    @Override
    public void onTick() {
        if (!inGame() || mc.gui.screen() != null) { target = null; return; }
        double r = range.num();
        LivingEntity best = null;
        double bestAngle = fov.num() / 2.0;
        for (Entity e : mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(r + 1),
                x -> Targets.valid(x, players.on(), monsters.on(), animals.on()))) {
            if (Targets.distanceToBox(e) > r) continue;
            if (!walls.on() && !mc.player.hasLineOfSight(e)) continue;
            double a = angleTo(aimPoint(e, 1f));
            if (a <= bestAngle) { bestAngle = a; best = (LivingEntity) e; }
        }
        target = best;
    }

    /** Pro Bild drehen (nicht nur 20x pro Sekunde), damit es weich aussieht. */
    @Override
    public void onHud(GuiGraphicsExtractor g, float partial) {
        long now = System.nanoTime();
        double dt = lastNanos == 0 ? 0.016 : Math.min(0.1, (now - lastNanos) / 1e9);
        lastNanos = now;
        if (target == null || !target.isAlive() || !inGame() || mc.gui.screen() != null) return;
        if (onlyClick.on() && !mc.options.keyAttack.isDown()) return;
        float[] want = Rot.to(aimPoint(target, partial));
        double frac = strength.num() >= 10 ? 1.0 : 1.0 - Math.exp(-strength.num() * 2.5 * dt);
        float yaw = mc.player.getYRot(), pitch = mc.player.getXRot();
        float dYaw = (float) (Mth.wrapDegrees(want[0] - yaw) * frac);
        float dPitch = vertical.on() ? (float) ((want[1] - pitch) * frac) : 0f;
        mc.player.setYRot(yaw + dYaw);
        mc.player.setXRot(Mth.clamp(pitch + dPitch, -90f, 90f));
        mc.player.yRotO += dYaw;
        mc.player.xRotO += dPitch;
    }

    @Override
    public String info() { return Setting.fmt(strength.num()); }
}
