package dev.teizen.client.mixin;

import dev.teizen.client.modules.Freecam;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Freecam: Hand ausblenden und keinen Block-Rahmen zeigen, solange Abbauen gesperrt ist. */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void teizen$freecamHand(CallbackInfo ci) {
        if (Freecam.hidesHand()) ci.cancel();
    }

    @Inject(method = "shouldRenderBlockOutline", at = @At("HEAD"), cancellable = true)
    private void teizen$freecamOutline(CallbackInfoReturnable<Boolean> cir) {
        if (Freecam.blocksInteract()) cir.setReturnValue(false);
    }
}
