package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;

/** AirJump: in der Luft nochmal springen (Leertaste drücken). */
public final class AirJump extends Module {
    private final Setting power = add(Setting.number("kraft", "Sprungkraft (0.42 = normaler Sprung)", 0.42, 0.1, 1.5));
    private final Setting hold = add(Setting.bool("halten", "Leertaste halten = immer weiter hoch", false));
    private boolean wasDown;
    public int jumps;

    public AirJump() {
        super("AirJump", Category.BEWEGUNG, "In der Luft nochmal springen.");
    }

    @Override
    public void onTick() {
        if (!inGame()) return;
        boolean down = mc.options.keyJump.isDown();
        if (down && (!wasDown || hold.on()) && !mc.player.onGround() && !mc.player.getAbilities().flying && !mc.player.isFallFlying()
                && !mc.player.isInWater() && !mc.player.isPassenger()) {
            var v = mc.player.getDeltaMovement();
            if (!hold.on() || v.y < 0) {
                mc.player.setDeltaMovement(v.x, power.num(), v.z);
                mc.player.fallDistance = 0;
                jumps++;
            }
        }
        wasDown = down;
    }
}
