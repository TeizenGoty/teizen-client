package dev.teizen.client.mixin;

import dev.teizen.client.TeizenClient;
import dev.teizen.client.modules.PlayerESP;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import dev.teizen.client.modules.Freecam;

/** PlayerESP: Spieler leuchten (Umriss durch Wände). Freecam: mit der Kamera nicht abbauen/benutzen (wie die Freecam-Mod). */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
    private void teizen$glow(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (TeizenClient.modules() == null) return;
        PlayerESP esp = TeizenClient.modules().get(PlayerESP.class);
        if (esp != null && esp.shouldGlow(entity)) cir.setReturnValue(true);
    }

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void teizen$freecamAttack(CallbackInfoReturnable<Boolean> cir) {
        if (Freecam.blocksInteract()) cir.setReturnValue(false);
    }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void teizen$freecamMine(boolean down, CallbackInfo ci) {
        if (Freecam.blocksInteract()) ci.cancel();
    }

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void teizen$freecamUse(CallbackInfo ci) {
        if (Freecam.blocksInteract()) { ci.cancel(); return; }
        if (dev.teizen.client.modules.ClickTP.onUse()) { ci.cancel(); return; }
        if (dev.teizen.client.modules.AirPlace.onUse()) ci.cancel();
    }

    @Inject(method = "pickBlockOrEntity", at = @At("HEAD"), cancellable = true)
    private void teizen$freecamPick(CallbackInfo ci) {
        if (Freecam.blocksInteract()) ci.cancel();
    }
}
