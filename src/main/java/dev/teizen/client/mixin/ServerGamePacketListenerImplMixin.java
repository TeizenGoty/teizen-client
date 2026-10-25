package dev.teizen.client.mixin;

import dev.teizen.client.modules.BoatNoclip;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** BoatNoclip in der eigenen Welt: der eingebaute Server lässt dein Boot durch Blöcke. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {
    @Shadow public ServerPlayer player;

    @Inject(method = "handleMoveVehicle", at = @At("HEAD"))
    private void teizen$boatNoclip(ServerboundMoveVehiclePacket packet, CallbackInfo ci) {
        BoatNoclip.serverPrepare(player);
    }

    @Inject(method = "isEntityCollidingWithAnythingNew", at = @At("HEAD"), cancellable = true)
    private void teizen$boatNoCollide(LevelReader level, Entity entity, AABB box, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
        if (entity != player && BoatNoclip.serverActive(player)) cir.setReturnValue(false);
    }
}
