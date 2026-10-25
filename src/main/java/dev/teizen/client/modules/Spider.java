package dev.teizen.client.modules;

import dev.teizen.client.TeizenClient;
import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import net.minecraft.world.entity.LivingEntity;

/** Spider: Wände hochklettern wie an einer Leiter – einfach gegen die Wand laufen. Schleichen = festhalten. */
public final class Spider extends Module {
    private final Setting speed = add(Setting.number("tempo", "Klettertempo (Blöcke pro Tick, Leiter = 0.2)", 0.25, 0.1, 1));

    public double climbed;
    private double lastY = Double.NaN;

    public Spider() {
        super("Spider", Category.BEWEGUNG, "Wände hochklettern wie eine Spinne.");
    }

    /** Mixin: zählt die Wand gerade wie eine Leiter? */
    public static boolean climbing(LivingEntity e) {
        if (TeizenClient.modules() == null || e != mc.player) return false;
        Spider s = TeizenClient.modules().get(Spider.class);
        return s != null && s.isEnabled() && mc.player.horizontalCollision;
    }

    @Override
    public void onTick() {
        if (!inGame()) return;
        double y = mc.player.getY();
        if (!Double.isNaN(lastY) && y > lastY && mc.player.horizontalCollision) climbed += y - lastY;
        lastY = y;
        if (mc.player.horizontalCollision && !mc.player.isShiftKeyDown()) {
            var v = mc.player.getDeltaMovement();
            if (v.y < speed.num()) mc.player.setDeltaMovement(v.x, speed.num(), v.z);
        }
    }
}
