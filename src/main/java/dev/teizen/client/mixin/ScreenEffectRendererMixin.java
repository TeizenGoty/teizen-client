package dev.teizen.client.mixin;

import dev.teizen.client.modules.NoRender;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NoRender: Feuer / Wasser / Block-im-Gesicht / Totem-Animation ausblenden. */
@Mixin(ScreenEffectRenderer.class)
public abstract class ScreenEffectRendererMixin {
    @Inject(method = "submitFire", at = @At("HEAD"), cancellable = true, require = 0)
    private static void teizen$fire(CallbackInfo ci) { if (NoRender.fire()) ci.cancel(); }

    @Inject(method = "submitWater", at = @At("HEAD"), cancellable = true, require = 0)
    private static void teizen$water(CallbackInfo ci) { if (NoRender.water()) ci.cancel(); }

    @Inject(method = "submitBlockSprite", at = @At("HEAD"), cancellable = true, require = 0)
    private static void teizen$inWall(CallbackInfo ci) { if (NoRender.inWall()) ci.cancel(); }

    @Inject(method = "displayItemActivation", at = @At("HEAD"), cancellable = true, require = 0)
    private void teizen$totem(CallbackInfo ci) { if (NoRender.totem()) ci.cancel(); }
}
