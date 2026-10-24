package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

/** Kein Fallschaden: sagt dem Server beim Fallen, dass du am Boden bist. */
public final class NoFall extends Module {
    private final Setting from = add(Setting.number("ab", "Ab welcher Fallhöhe geschützt wird (Blöcke)", 2, 1, 20));

    public NoFall() {
        super("NoFall", Category.BEWEGUNG, "Kein Fallschaden mehr.");
    }

    @Override
    public void onTick() {
        if (!inGame() || mc.player.isCreative() || mc.player.isSpectator()) return;
        if (mc.player.fallDistance > from.num() && !mc.player.isFallFlying()) {
            mc.player.connection.send(new ServerboundMovePlayerPacket.StatusOnly(true, mc.player.horizontalCollision));
            mc.player.fallDistance = 0;
        }
    }
}
