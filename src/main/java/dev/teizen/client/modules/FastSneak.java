package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * FastSneak: beim Schleichen (Shift) läufst du so schnell wie normal, statt langsam. Sonst alles wie beim Schleichen
 * (nicht von Kanten fallen, Kisten nicht aus Versehen öffnen). Praktisch beim Bauen an Kanten.
 */
public final class FastSneak extends Module {
    public FastSneak() {
        super("FastSneak", Category.BEWEGUNG, "Schleichen (Shift) ist so schnell wie normales Gehen.");
    }

    @Override
    public void onTick() {
        if (!inGame()) return;
        AttributeInstance a = mc.player.getAttribute(Attributes.SNEAKING_SPEED);
        if (a != null && a.getBaseValue() != 1.0) a.setBaseValue(1.0);
    }

    @Override
    protected void onDisable() {
        if (!inGame()) return;
        AttributeInstance a = mc.player.getAttribute(Attributes.SNEAKING_SPEED);
        // auf normal zurück (falls NoSlowdown "schnell schleichen" an hat, hebt dessen onTick es gleich wieder an)
        if (a != null) a.setBaseValue(0.3);
    }
}
