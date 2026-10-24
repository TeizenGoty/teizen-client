package dev.teizen.client.mixin;

import dev.teizen.client.modules.NoSlowdown;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** NoSlowdown: Essen, Bogen und Blocken machen dich nicht langsamer. */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
    @Inject(method = "isSlowDueToUsingItem", at = @At("HEAD"), cancellable = true)
    private void teizen$noSlow(CallbackInfoReturnable<Boolean> cir) {
        NoSlowdown n = NoSlowdown.active();
        if (n != null && n.items.on()) cir.setReturnValue(false);
    }

    /** Das eigentliche Abbremsen beim Benutzen eines Items (normal 0.2 = 20 % Tempo). */
    @Inject(method = "itemUseSpeedMultiplier", at = @At("HEAD"), cancellable = true)
    private void teizen$fullSpeed(CallbackInfoReturnable<Float> cir) {
        NoSlowdown n = NoSlowdown.active();
        if (n != null && n.items.on()) cir.setReturnValue(1.0f);
    }
}
