package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;

/** Parkour: springt automatisch genau an der Kante. */
public final class Parkour extends Module {
    private final Setting edge = add(Setting.number("kante", "So nah an der Kante springen (Blöcke)", 0.05, 0.001, 0.3));
    public int jumps;

    public Parkour() {
        super("Parkour", Category.BEWEGUNG, "Springt automatisch an der Kante.");
    }

    @Override
    public void onTick() {
        if (!inGame()) return;
        var p = mc.player;
        if (!p.onGround() || p.isShiftKeyDown() || mc.options.keyJump.isDown() || p.getAbilities().flying) return;
        var v = p.getDeltaMovement();
        double h = Math.sqrt(v.x * v.x + v.z * v.z);
        if (h < 0.01) return;
        // nächster Schritt in Laufrichtung: steht dann nichts mehr unter einem?
        double s = Math.max(edge.num(), h);
        var box = p.getBoundingBox().move(v.x / h * s, -0.5, v.z / h * s).inflate(-0.001, 0, -0.001);
        if (mc.level.noCollision(p, box)) {
            p.jumpFromGround();
            jumps++;
        }
    }
}
