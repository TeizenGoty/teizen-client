package dev.teizen.client.mixin;

import dev.teizen.client.TeizenClient;
import dev.teizen.client.modules.Freecam;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Freecam: Mausbewegung dreht die Kamera statt den Spieler. */
@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    private void teizen$freecamTurn(double dx, double dy, CallbackInfo ci) {
        if ((Object) this != Minecraft.getInstance().player || TeizenClient.modules() == null) return;
        Freecam f = TeizenClient.modules().get(Freecam.class);
        if (f != null && f.active()) {
            f.turn(dx, dy);
            ci.cancel();
        }
    }
}
