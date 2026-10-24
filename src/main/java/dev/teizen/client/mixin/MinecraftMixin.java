package dev.teizen.client.mixin;

import dev.teizen.client.TeizenClient;
import dev.teizen.client.modules.PlayerESP;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** PlayerESP: Spieler leuchten (Umriss durch Wände). */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
    private void teizen$glow(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (TeizenClient.modules() == null) return;
        PlayerESP esp = TeizenClient.modules().get(PlayerESP.class);
        if (esp != null && esp.shouldGlow(entity)) cir.setReturnValue(true);
    }
}
