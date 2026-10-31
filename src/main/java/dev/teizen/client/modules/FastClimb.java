package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;

/** FastClimb: Leitern, Ranken und Gerüste schneller hoch. */
public final class FastClimb extends Module {
    private final Setting speed = add(Setting.number("tempo", "Klettertempo (Blöcke pro Tick, normal 0.2)", 0.4, 0.2, 1));
    public double climbed;
    private double lastY = Double.NaN;

    public FastClimb() {
        super("FastClimb", Category.BEWEGUNG, "Leitern und Ranken schneller hochklettern.");
    }

    @Override
    public void onTick() {
        if (!inGame()) return;
        double y = mc.player.getY();
        if (mc.player.onClimbable() && (mc.player.horizontalCollision || mc.options.keyJump.isDown()) && !mc.player.isShiftKeyDown()) {
            var v = mc.player.getDeltaMovement();
            if (v.y < speed.num()) mc.player.setDeltaMovement(v.x, speed.num(), v.z);
            if (!Double.isNaN(lastY) && y > lastY) climbed += y - lastY;
        }
        lastY = y;
    }
}
