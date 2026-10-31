package dev.teizen.client.mixin;

import dev.teizen.client.modules.NoRender;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NoRender: kein Regen/Schnee. */
@Mixin(WeatherEffectRenderer.class)
public abstract class WeatherEffectRendererMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0)
    private void teizen$weather(CallbackInfo ci) { if (NoRender.weather()) ci.cancel(); }
}
