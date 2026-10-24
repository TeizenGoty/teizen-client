package dev.teizen.client.mixin;

import dev.teizen.client.modules.NoSlowdown;
import dev.teizen.client.modules.SafeWalk;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** SafeWalk (nicht von Kanten fallen) und NoSlowdown (Seelensand, Honig). */
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
}
