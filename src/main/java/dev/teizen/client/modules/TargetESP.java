package dev.teizen.client.modules;

import dev.teizen.client.TeizenClient;
import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.util.Draw;
import dev.teizen.client.util.Projector;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * TargetESP: markiert dein aktuelles Ziel – rote Box, Name und Lebensbalken. Ziel ist das von KillAura/AimAssist,
 * sonst der Gegner, den du anschaust, sonst der, den du zuletzt geschlagen hast.
 */
public final class TargetESP extends Module {
    private final Setting bar = add(Setting.bool("lebensbalken", "Lebensbalken und Name über dem Ziel", true));
    private final Setting color = add(Setting.choice("farbe", "Farbe der Markierung", "rot", "rot", "lila", "orange", "weiss"));

    private static Entity lastHit;
    private static int lastHitTick;
    public LivingEntity current;
    public int drawn;

    public TargetESP() {
        super("TargetESP", Category.SICHT, "Markiert dein aktuelles Ziel mit Box und Lebensbalken.");
    }

    /** Mixin: du hast gerade dieses Wesen geschlagen. */
    public static void onAttack(Entity e) {
        lastHit = e;
        lastHitTick = mc.player == null ? 0 : mc.player.tickCount;
    }

    private LivingEntity find() {
        KillAura ka = TeizenClient.modules().get(KillAura.class);
        if (ka != null && ka.isEnabled() && ka.target != null && ka.target.isAlive()) return ka.target;
        AimAssist aa = TeizenClient.modules().get(AimAssist.class);
        if (aa != null && aa.isEnabled() && aa.target != null && aa.target.isAlive()) return aa.target;
        if (mc.crosshairPickEntity instanceof LivingEntity le && le.isAlive() && le != mc.player) return le;
        if (lastHit instanceof LivingEntity le && le.isAlive() && mc.player.tickCount - lastHitTick < 100 && mc.player.distanceTo(le) < 32) return le;
        return null;
    }

    private int col() {
        if (color.is("lila")) return 0xFFC050FF;
        if (color.is("orange")) return 0xFFFF9020;
        if (color.is("weiss")) return 0xFFFFFFFF;
        return 0xFFFF3030;
    }

    @Override
    public void onTick() {
        if (!inGame()) { current = null; return; }
        current = find();
    }

    @Override
    public void onHud(GuiGraphicsExtractor g, float partial) {
        if (!inGame() || current == null || !current.isAlive()) return;
        LivingEntity t = current;
        Vec3 pos = t.getPosition(partial);
        AABB box = t.getBoundingBox().move(pos.subtract(t.position())).inflate(0.08);
        int c = col();
        Draw.worldBox(g, box, Draw.alpha(c, 0.18), true);
        Draw.worldBox(g, box, c, false);
        drawn++;
        if (!bar.on()) return;
        double[] s = Projector.project(pos.x, box.maxY + 0.35, pos.z);
        if (s == null) return;
        float hp = t.getHealth() + t.getAbsorptionAmount();
        float max = Math.max(1, t.getMaxHealth());
        String name = t instanceof Player p ? p.getGameProfile().name() : t.getDisplayName().getString();
        String txt = "§f" + name + " §c" + Math.round(hp) + "❤";
        int w = Math.max(50, mc.font.width(txt) + 6);
        int x = (int) s[0] - w / 2, y = (int) s[1] - 16;
        g.fill(x, y, x + w, y + 15, 0xA0000000);
        g.text(mc.font, txt, x + 3, y + 2, 0xFFFFFFFF, true);
        double f = Math.max(0, Math.min(1, hp / max));
        int barCol = f > 0.66 ? 0xFF33DD33 : f > 0.33 ? 0xFFEEDD22 : 0xFFEE3333;
        g.fill(x + 2, y + 11, x + w - 2, y + 13, 0xFF333333);
        g.fill(x + 2, y + 11, x + 2 + (int) Math.round((w - 4) * f), y + 13, barCol);
    }

    @Override
    public String info() { return current == null ? "" : current.getName().getString(); }
}
