package dev.teizen.client.mixin;

import dev.teizen.client.modules.TimeChanger;
import net.minecraft.client.ClientClockManager;
import net.minecraft.core.Holder;
import net.minecraft.world.clock.WorldClock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** TimeChanger: die Uhr der Welt zeigt (nur bei dir) die gewählte Tageszeit. */
@Mixin(ClientClockManager.class)
public abstract class ClientClockManagerMixin {
    @Inject(method = "getTotalTicks", at = @At("RETURN"), cancellable = true)
    private void teizen$time(Holder<WorldClock> clock, CallbackInfoReturnable<Long> cir) {
        Long t = TimeChanger.override(cir.getReturnValueJ());
        if (t != null) cir.setReturnValue(t);
    }
}
