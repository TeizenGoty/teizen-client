package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.util.Chat;
import net.minecraft.world.effect.MobEffects;

/**
 * God: unsterblich (Resistenz V), kein Feuer-/Lava-Schaden, mehr Herzen, schnell heilen.
 * Geht über Befehle – braucht OP (bzw. "Cheats erlaubt" in der eigenen Welt).
 */
public final class God extends Module {
    private final Setting hearts = add(Setting.number("herzen", "Wie viele Herzen du hast (normal 10)", 20, 1, 512));
    private final Setting immortal = add(Setting.bool("unsterblich", "Kein Schaden mehr (Resistenz V)", true));
    private final Setting fire = add(Setting.bool("feuer", "Kein Feuer- und Lava-Schaden", true));
    private final Setting regen = add(Setting.bool("heilung", "Schnell heilen (Regeneration)", true));
    private final Setting water = add(Setting.bool("wasser", "Unter Wasser atmen", true));
    private final Setting food = add(Setting.bool("hunger", "Nie Hunger (Sättigung)", false));

    private String applied = "";
    private int lastApply = -1000;
    private int tries;
    private boolean warned;

    public God() {
        super("God", Category.KAMPF, "Unsterblich, kein Lava-Schaden, mehr Herzen (braucht OP).");
    }

    private void cmd(String c) {
        mc.player.connection.sendCommand(c);
    }

    private String signature() {
        return hearts.integer() + "|" + immortal.on() + fire.on() + regen.on() + water.on() + food.on();
    }

    @Override
    protected void onEnable() {
        applied = "";
        tries = 0;
        warned = false;
        lastApply = -1000;
    }

    @Override
    protected void onDisable() {
        if (!inGame()) return;
        cmd("effect clear @s minecraft:resistance");
        cmd("effect clear @s minecraft:fire_resistance");
        cmd("effect clear @s minecraft:regeneration");
        cmd("effect clear @s minecraft:water_breathing");
        cmd("effect clear @s minecraft:saturation");
        cmd("attribute @s minecraft:max_health base reset");
        applied = "";
    }

    private boolean missing() {
        if (immortal.on() && !mc.player.hasEffect(MobEffects.RESISTANCE)) return true;
        if (fire.on() && !mc.player.hasEffect(MobEffects.FIRE_RESISTANCE)) return true;
        return Math.abs(mc.player.getMaxHealth() - hearts.integer() * 2) > 0.5;
    }

    @Override
    public void onTick() {
        if (!inGame()) return;
        int now = mc.player.tickCount;
        String sig = signature();
        boolean changed = !sig.equals(applied);
        if (!changed && (now - lastApply < 60 || !missing())) {
            if (!missing()) tries = 0;
            return;
        }
        if (now - lastApply < 20) return;
        if (!changed && ++tries > 3) {
            if (!warned) {
                warned = true;
                Chat.error("God geht nicht: du brauchst OP-Rechte (bzw. in der eigenen Welt \"Cheats erlaubt\").");
            }
            return;
        }
        lastApply = now;
        boolean firstTime = applied.isEmpty();
        applied = sig;
        cmd("attribute @s minecraft:max_health base set " + hearts.integer() * 2);
        effect("resistance", immortal.on(), 4);
        effect("fire_resistance", fire.on(), 0);
        effect("regeneration", regen.on(), 2);
        effect("water_breathing", water.on(), 0);
        effect("saturation", food.on(), 0);
        if (firstTime) cmd("effect give @s minecraft:instant_health 1 20 true");
    }

    private void effect(String id, boolean on, int amp) {
        if (on) cmd("effect give @s minecraft:" + id + " infinite " + amp + " true");
        else if (mc.player.hasEffect(holder(id))) cmd("effect clear @s minecraft:" + id);
    }

    private static net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> holder(String id) {
        return switch (id) {
            case "resistance" -> MobEffects.RESISTANCE;
            case "fire_resistance" -> MobEffects.FIRE_RESISTANCE;
            case "regeneration" -> MobEffects.REGENERATION;
            case "water_breathing" -> MobEffects.WATER_BREATHING;
            default -> MobEffects.SATURATION;
        };
    }

    @Override
    public String info() { return hearts.integer() + "❤"; }
}
