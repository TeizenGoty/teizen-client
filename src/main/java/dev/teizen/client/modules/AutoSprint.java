package dev.teizen.client.modules;

import dev.teizen.client.core.Module;

/** Immer sprinten, wenn du nach vorne läufst. */
public final class AutoSprint extends Module {
    public AutoSprint() {
        super("AutoSprint", Category.BEWEGUNG, "Du sprintest automatisch.");
    }

    @Override
    public void onTick() {
        if (!inGame()) return;
        if (mc.options.keyUp.isDown() && !mc.player.isShiftKeyDown() && !mc.player.horizontalCollision
                && (mc.player.getFoodData().getFoodLevel() > 6 || mc.player.getAbilities().mayfly)) {
            mc.player.setSprinting(true);
        }
    }
}
