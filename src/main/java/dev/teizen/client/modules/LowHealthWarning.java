package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.sounds.SoundEvents;

/** LowHealthWarning: bei wenig Leben roter Rand, große Warnung in der Mitte und ein Warnton. */
public final class LowHealthWarning extends Module {
    private final Setting hearts = add(Setting.number("herzen", "Ab wie vielen Herzen gewarnt wird", 4, 1, 10));
    private final Setting sound = add(Setting.bool("ton", "Warnton (alle 2 Sekunden)", true));
    private final Setting border = add(Setting.bool("rand", "Roter blinkender Rand", true));

    public boolean low;
    public int warnings;
    private int ticks;

    public LowHealthWarning() {
        super("LowHealthWarning", Category.SICHT, "Warnt mit rotem Rand und Ton, wenn du wenig Leben hast.");
    }

    private float hp() { return mc.player.getHealth() + mc.player.getAbsorptionAmount(); }

    @Override
    public void onTick() {
        if (!inGame() || !mc.player.isAlive()) { low = false; return; }
        boolean now = hp() <= hearts.num() * 2;
        if (now && !low) warnings++;
        low = now;
        if (low && sound.on() && ticks++ % 40 == 0) mc.player.playSound(SoundEvents.NOTE_BLOCK_BASS.value(), 1f, 0.6f);
        if (!low) ticks = 0;
    }

    @Override
    public void onHud(GuiGraphicsExtractor g, float partial) {
        if (!low || !inGame()) return;
        int w = g.guiWidth(), h = g.guiHeight();
        double pulse = 0.5 + 0.5 * Math.sin((mc.player.tickCount + partial) * 0.3);
        if (border.on()) {
            int a = (int) (60 + 100 * pulse);
            int c = (a << 24) | 0xFF0000;
            int t = 8;
            g.fill(0, 0, w, t, c);
            g.fill(0, h - t, w, h, c);
            g.fill(0, t, t, h - t, c);
            g.fill(w - t, t, w, h - t, c);
        }
        String txt = "§c§l⚠ WENIG LEBEN: " + Math.round(hp() / 2.0 * 10) / 10.0 + " ❤";
        int tw = mc.font.width(txt);
        g.text(mc.font, txt, w / 2 - tw / 2, h / 2 - 30, 0xFFFFFFFF, true);
    }

    @Override
    public String info() { return low ? "§c!" : ""; }
}
