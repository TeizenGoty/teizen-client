package dev.teizen.client.mixin;

import dev.teizen.client.modules.FakeLag;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** FakeLag: ausgehende Pakete zurückhalten und später schicken. */
@Mixin(Connection.class)
public abstract class ConnectionMixin {
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V", at = @At("HEAD"), cancellable = true)
    private void teizen$fakeLag(Packet<?> packet, ChannelFutureListener listener, boolean flush, CallbackInfo ci) {
        if (FakeLag.hold((Connection) (Object) this, packet, listener, flush)) ci.cancel();
    }
}
