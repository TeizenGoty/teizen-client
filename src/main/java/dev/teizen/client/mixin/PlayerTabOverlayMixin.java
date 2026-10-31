package dev.teizen.client.mixin;

import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** BetterTab: Entfernung hinter den Namen in der Spielerliste. */
@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabOverlayMixin {
    @Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
    private void teizen$betterTab(PlayerInfo info, CallbackInfoReturnable<Component> cir) {
        Component c = dev.teizen.client.modules.BetterTab.decorate(info, cir.getReturnValue());
        if (c != cir.getReturnValue()) cir.setReturnValue(c);
    }
}
