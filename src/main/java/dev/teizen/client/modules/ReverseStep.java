package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;

/** ReverseStep: an Stufen/Kanten nicht langsam runterfallen, sondern sofort unten sein. */
public final class ReverseStep extends Module {
    private final Setting height = add(Setting.number("hoehe", "Bis zu so vielen Blöcken runter", 3, 1, 5));
    private final Setting speed = add(Setting.number("tempo", "Wie schnell runter (Blöcke pro Tick)", 1.5, 0.3, 3));
    private boolean wasOnGround;
    public int steps;

    public ReverseStep() {
        super("ReverseStep", Category.BEWEGUNG, "Stufen und Kanten sofort runter statt langsam zu fallen.");
    }

    @Override
    public void onTick() {
        if (!inGame()) return;
        var p = mc.player;
        boolean ground = p.onGround();
        if (wasOnGround && !ground && p.getDeltaMovement().y <= 0 && !mc.options.keyJump.isDown() && !p.getAbilities().flying
                && !p.isInWater() && !p.onClimbable() && !p.isFallFlying() && !p.isPassenger()) {
            // gibt es in Reichweite einen Boden?
            var box = p.getBoundingBox();
            for (int i = 1; i <= height.integer(); i++) {
                if (!mc.level.noCollision(p, box.move(0, -i, 0))) {
                    p.setDeltaMovement(p.getDeltaMovement().x, -speed.num(), p.getDeltaMovement().z);
                    steps++;
                    break;
                }
            }
        }
        wasOnGround = ground;
    }
}
