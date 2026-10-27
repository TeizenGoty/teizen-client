package dev.teizen.client.mixin;

import dev.teizen.client.modules.TimeChanger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** TimeChanger (26.3): die Uhr der Welt zeigt (nur bei dir) die gewählte Tageszeit. In 26.3 steckt die Zeit in ClientClockInstance. */
@Mixin(targets = "net.minecraft.client.ClientClockManager$ClientClockInstance")
public abstract class ClientClockManagerMixin {
    @Inject(method = "totalTicks", at = @At("RETURN"), cancellable = true)
    private void teizen$time(CallbackInfoReturnable<Long> cir) {
        Long t = TimeChanger.override(cir.getReturnValueJ());
        if (t != null) cir.setReturnValue(t);
    }
}
