package dev.teizen.client.modules;

import dev.teizen.client.TeizenClient;
import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** Zeigt am Fadenkreuz ein Kreuz (und den Schaden), sobald ein Schlag wirklich trifft. */
public final class HitMarker extends Module {
    private final Setting duration = add(Setting.number("dauer", "Wie lange das Kreuz zu sehen ist (Ticks)", 8, 2, 40));
    private final Setting size = add(Setting.number("groesse", "Größe des Kreuzes", 6, 2, 20));
    private final Setting damage = add(Setting.bool("schaden", "Schaden als Zahl anzeigen", true));
    private final Setting sound = add(Setting.bool("ton", "Kurzer Ton bei einem Treffer", true));

    public int hits;
    private LivingEntity target;
    private float healthBefore;
    private int waitTicks;
    private int show;
    private float lastDamage;
    private boolean kill;

    public HitMarker() {
        super("HitMarker", Category.KAMPF, "Kreuz am Fadenkreuz, wenn dein Schlag trifft.");
    }

    /** Vor jedem eigenen Schlag (aus dem Mixin). */
    public static void onAttack(Entity e) {
        if (TeizenClient.modules() == null) return;
        HitMarker h = TeizenClient.modules().get(HitMarker.class);
        if (h == null || !h.isEnabled() || !(e instanceof LivingEntity le)) return;
        h.target = le;
        h.healthBefore = le.getHealth() + le.getAbsorptionAmount();
        h.waitTicks = 10;
    }

    @Override
    public void onTick() {
        if (show > 0) show--;
        if (target == null || waitTicks <= 0) { target = null; return; }
        waitTicks--;
        float now = target.isAlive() ? target.getHealth() + target.getAbsorptionAmount() : 0;
        // Treffer = Gegner hat Leben verloren oder zuckt gerade (Schaden kam an)
        if (now < healthBefore - 0.01f || target.hurtTime > 0 || !target.isAlive()) {
            lastDamage = Math.max(0, healthBefore - now);
            kill = !target.isAlive() || now <= 0;
            show = duration.integer();
            hits++;
            target = null;
            if (sound.on() && mc.player != null) mc.player.playSound(kill ? SoundEvents.PLAYER_LEVELUP : SoundEvents.ARROW_HIT_PLAYER, 0.4f, kill ? 1.6f : 1.2f);
        }
    }

    @Override
    public void onHud(GuiGraphicsExtractor g, float partial) {
        if (show <= 0) return;
        int cx = g.guiWidth() / 2, cy = g.guiHeight() / 2;
        int alpha = Math.min(255, 60 + show * 255 / Math.max(1, duration.integer()));
        int color = (alpha << 24) | (kill ? 0xFF3030 : 0xFFFFFF);
        int s = size.integer(), gap = 3;
        for (int i = gap; i <= gap + s; i++) {
            g.fill(cx + i, cy + i, cx + i + 2, cy + i + 1, color);
            g.fill(cx - i - 1, cy + i, cx - i + 1, cy + i + 1, color);
            g.fill(cx + i, cy - i - 1, cx + i + 2, cy - i, color);
            g.fill(cx - i - 1, cy - i - 1, cx - i + 1, cy - i, color);
        }
        if (damage.on() && lastDamage > 0) {
            String t = (kill ? "§c✖ " : "§f-") + String.format(java.util.Locale.ROOT, "%.1f", lastDamage / 2f) + "❤";
            g.text(mc.font, t, cx + gap + s + 4, cy - gap - s - 8, 0xFFFFFFFF, true);
        }
    }

    @Override
    public String info() { return String.valueOf(hits); }
}
