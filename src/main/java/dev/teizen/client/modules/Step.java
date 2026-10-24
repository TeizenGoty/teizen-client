package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Ganze Blöcke hochlaufen ohne zu springen. */
public final class Step extends Module {
    private final Setting height = add(Setting.number("hoehe", "Wie hoch du hochläufst (Blöcke)", 1, 0.6, 3));

    public Step() {
        super("Step", Category.BEWEGUNG, "Du läufst Blöcke hoch ohne zu springen.");
    }

    @Override
    public void onTick() {
        if (!inGame()) return;
        AttributeInstance a = mc.player.getAttribute(Attributes.STEP_HEIGHT);
        if (a != null && a.getBaseValue() != height.num()) a.setBaseValue(height.num());
    }

    @Override
    protected void onDisable() {
        if (!inGame()) return;
        AttributeInstance a = mc.player.getAttribute(Attributes.STEP_HEIGHT);
        if (a != null) a.setBaseValue(0.6);
    }

    @Override
    public String info() { return Setting.fmt(height.num()); }
}
