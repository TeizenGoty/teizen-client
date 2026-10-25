package dev.teizen.client.mixin;

import dev.teizen.client.modules.SkinChanger;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** SkinChanger: dein Skin (nur bei dir). */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {
    @Inject(method = "getSkin", at = @At("HEAD"), cancellable = true)
    private void teizen$skin(CallbackInfoReturnable<PlayerSkin> cir) {
        PlayerSkin s = SkinChanger.skinFor((AbstractClientPlayer) (Object) this);
        if (s != null) cir.setReturnValue(s);
    }
}
