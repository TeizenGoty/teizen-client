package dev.teizen.client.mixin;

import dev.teizen.client.modules.ElytraFly;
import dev.teizen.client.modules.Spider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** ElytraFly: eigene Geschwindigkeit statt Elytra-Physik. Spider: Wände zählen wie Leitern. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Inject(method = "updateFallFlyingMovement", at = @At("HEAD"), cancellable = true)
    private void teizen$elytraFly(Vec3 move, CallbackInfoReturnable<Vec3> cir) {
        Vec3 v = ElytraFly.velocity((LivingEntity) (Object) this);
        if (v != null) cir.setReturnValue(v);
    }

    @Inject(method = "onClimbable", at = @At("HEAD"), cancellable = true)
    private void teizen$spider(CallbackInfoReturnable<Boolean> cir) {
        if (Spider.climbing((LivingEntity) (Object) this)) cir.setReturnValue(true);
    }
}
