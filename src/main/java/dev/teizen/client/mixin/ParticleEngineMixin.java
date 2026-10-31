package dev.teizen.client.mixin;

import dev.teizen.client.modules.NoRender;
import net.minecraft.client.particle.ParticleEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NoRender: keine Partikel. */
@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {
    @Inject(method = "add", at = @At("HEAD"), cancellable = true, require = 0)
    private void teizen$particles(CallbackInfo ci) { if (NoRender.particles()) ci.cancel(); }
}
