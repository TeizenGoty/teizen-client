package dev.teizen.client.mixin;

import dev.teizen.client.modules.NoSlowdown;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NoSlowdown: Spinnennetze und Beerenbüsche halten dich nicht fest. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Inject(method = "makeStuckInBlock", at = @At("HEAD"), cancellable = true)
    private void teizen$noStuck(BlockState state, Vec3 multiplier, CallbackInfo ci) {
        if ((Object) this != Minecraft.getInstance().player) return;
        NoSlowdown n = NoSlowdown.active();
        if (n != null && n.blocks.on()) ci.cancel();
    }
}
