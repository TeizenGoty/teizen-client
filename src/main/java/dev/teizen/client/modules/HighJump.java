package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Höher springen. */
public final class HighJump extends Module {
    private final Setting power = add(Setting.number("kraft", "Sprungkraft (normal 0.42)", 0.8, 0.42, 3));

    public HighJump() {
        super("HighJump", Category.BEWEGUNG, "Du springst viel höher.");
    }

    @Override
    public void onTick() {
        if (!inGame()) return;
        AttributeInstance a = mc.player.getAttribute(Attributes.JUMP_STRENGTH);
        if (a != null && a.getBaseValue() != power.num()) a.setBaseValue(power.num());
    }

    @Override
    protected void onDisable() {
        if (!inGame()) return;
        AttributeInstance a = mc.player.getAttribute(Attributes.JUMP_STRENGTH);
        if (a != null) a.setBaseValue(0.42);
    }
}
