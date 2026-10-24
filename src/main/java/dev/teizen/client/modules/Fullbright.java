package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/** Alles ist hell – Höhlen, Nacht, Nether (nur auf deinem Bildschirm). */
public final class Fullbright extends Module {
    public Fullbright() {
        super("Fullbright", Category.SICHT, "Alles ist immer hell.");
    }

    @Override
    public void onTick() {
        if (!inGame()) return;
        MobEffectInstance cur = mc.player.getEffect(MobEffects.NIGHT_VISION);
        if (cur == null || cur.getDuration() < 220) {
            // Nachtsicht nur auf dem Client – der Server bekommt davon nichts mit
            mc.player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 420, 0, false, false, false));
        }
    }

    @Override
    protected void onDisable() {
        if (inGame()) mc.player.removeEffect(MobEffects.NIGHT_VISION);
    }
}
