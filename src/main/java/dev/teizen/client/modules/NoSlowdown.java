package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Nicht langsamer beim Essen/Bogen/Schild, in Spinnennetzen, auf Seelensand und Honig (optional beim Schleichen). */
public final class NoSlowdown extends Module {
    public final Setting items = add(Setting.bool("essen", "Nicht langsamer beim Essen, Bogen spannen, Blocken", true));
    public final Setting blocks = add(Setting.bool("bloecke", "Spinnennetze, Seelensand, Honig, Beerenbüsche bremsen nicht", true));
    private final Setting sneak = add(Setting.bool("schleichen", "Beim Schleichen normal schnell", false));

    public NoSlowdown() {
        super("NoSlowdown", Category.BEWEGUNG, "Nicht langsamer beim Essen, Blocken, in Netzen usw.");
    }

    public static NoSlowdown active() {
        var mm = dev.teizen.client.TeizenClient.modules();
        if (mm == null) return null;
        NoSlowdown n = mm.get(NoSlowdown.class);
        return n != null && n.isEnabled() ? n : null;
    }

    @Override
    public void onTick() {
        if (!inGame()) return;
        AttributeInstance a = mc.player.getAttribute(Attributes.SNEAKING_SPEED);
        double want = sneak.on() ? 1.0 : 0.3;
        if (a != null && a.getBaseValue() != want) a.setBaseValue(want);
    }

    @Override
    protected void onDisable() {
        if (!inGame()) return;
        AttributeInstance a = mc.player.getAttribute(Attributes.SNEAKING_SPEED);
        if (a != null) a.setBaseValue(0.3);
    }
}
