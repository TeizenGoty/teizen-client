package dev.teizen.client.mixin;

import dev.teizen.client.modules.BoatFly;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** BoatFly / BoatNoclip: keine normale Boot-Physik (Schwerkraft, Bremsen, Paddeln) – wir steuern selbst. */
@Mixin(AbstractBoat.class)
public abstract class AbstractBoatMixin {
    @Inject(method = "floatBoat", at = @At("HEAD"), cancellable = true)
    private void teizen$noFloat(CallbackInfo ci) {
        if (BoatFly.controls((AbstractBoat) (Object) this)) ci.cancel();
    }

    @Inject(method = "controlBoat", at = @At("HEAD"), cancellable = true)
    private void teizen$noPaddle(CallbackInfo ci) {
        if (BoatFly.controls((AbstractBoat) (Object) this)) ci.cancel();
    }
}
