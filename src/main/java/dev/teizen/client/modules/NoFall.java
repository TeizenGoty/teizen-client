package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

/** Kein Fallschaden: sagt dem Server beim Fallen, dass du am Boden bist. */
public final class NoFall extends Module {
    public NoFall() {
        super("NoFall", Category.BEWEGUNG, "Kein Fallschaden mehr.");
    }

    @Override
    public void onTick() {
        if (!inGame() || mc.player.isCreative() || mc.player.isSpectator()) return;
        if (mc.player.fallDistance > 2.0 && !mc.player.isFallFlying()) {
            mc.player.connection.send(new ServerboundMovePlayerPacket.StatusOnly(true, mc.player.horizontalCollision));
            mc.player.fallDistance = 0;
        }
    }
}
