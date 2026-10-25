package dev.teizen.client.mixin;

import dev.teizen.client.modules.NoSlowdown;
import dev.teizen.client.modules.SafeWalk;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** SafeWalk (nicht von Kanten fallen) und NoSlowdown (Spinnennetze, Beerenbüsche, Seelensand, Honig). */
@Mixin(Player.class)
public abstract class PlayerMixin {
    @Inject(method = "isStayingOnGroundSurface", at = @At("HEAD"), cancellable = true)
    private void teizen$safeWalk(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this == Minecraft.getInstance().player && SafeWalk.on()) cir.setReturnValue(true);
    }

    @Inject(method = "getBlockSpeedFactor", at = @At("HEAD"), cancellable = true)
    private void teizen$noSlowBlocks(CallbackInfoReturnable<Float> cir) {
        if ((Object) this != Minecraft.getInstance().player) return;
        NoSlowdown n = NoSlowdown.active();
        if (n != null && n.blocks.on()) cir.setReturnValue(1.0f);
    }

    /** Spinnennetze/Beerenbüsche: in 26.2 überschreibt Player diese Methode. */
    @Inject(method = "makeStuckInBlock", at = @At("HEAD"), cancellable = true)
    private void teizen$noStuck(net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.phys.Vec3 multiplier, CallbackInfo ci) {
        if ((Object) this != Minecraft.getInstance().player) return;
        NoSlowdown n = NoSlowdown.active();
        if (n != null && n.blocks.on()) ci.cancel();
    }

    /** ElytraReset: dieselbe Leertaste, die die Elytra geschlossen hat, öffnet sie nicht sofort wieder. */
    @Inject(method = "tryToStartFallFlying", at = @At("HEAD"), cancellable = true)
    private void teizen$elytraReset(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this == Minecraft.getInstance().player && dev.teizen.client.modules.ElytraReset.blockRedeploy()) cir.setReturnValue(false);
    }
}
