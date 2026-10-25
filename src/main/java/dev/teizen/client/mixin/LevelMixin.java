package dev.teizen.client.mixin;

import dev.teizen.client.modules.WeatherChanger;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** WeatherChanger: eigenes Wetter (nur die Welt auf deinem Bildschirm). */
@Mixin(Level.class)
public abstract class LevelMixin {
    @Inject(method = "getRainLevel", at = @At("HEAD"), cancellable = true)
    private void teizen$rain(float partial, CallbackInfoReturnable<Float> cir) {
        if (!((Object) this instanceof ClientLevel)) return;
        Float v = WeatherChanger.rain();
        if (v != null) cir.setReturnValue(v);
    }

    @Inject(method = "getThunderLevel", at = @At("HEAD"), cancellable = true)
    private void teizen$thunder(float partial, CallbackInfoReturnable<Float> cir) {
        if (!((Object) this instanceof ClientLevel)) return;
        Float v = WeatherChanger.thunder();
        if (v != null) cir.setReturnValue(v);
    }
}
