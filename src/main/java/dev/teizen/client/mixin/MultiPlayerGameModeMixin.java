package dev.teizen.client.mixin;

import dev.teizen.client.modules.Criticals;
import dev.teizen.client.util.Breaker;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Criticals vor jedem Schlag + Abbau-Schutz für AutoMine/Nuker. */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
    @Inject(method = "attack", at = @At("HEAD"))
    private void teizen$crit(Player player, Entity target, CallbackInfo ci) {
        Criticals.beforeAttack();
        dev.teizen.client.modules.HitMarker.onAttack(target);
    }

    /** ShieldBreaker / MaceSwap / SpearSwap: Waffe nur für diesen Schlag wechseln. */
    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void teizen$weaponSwap(Player player, Entity target, CallbackInfo ci) {
        dev.teizen.client.modules.TargetESP.onAttack(target);
        if (dev.teizen.client.util.WeaponSwap.before(target)) ci.cancel();
    }

    @Inject(method = "attack", at = @At("RETURN"))
    private void teizen$weaponBack(Player player, Entity target, CallbackInfo ci) {
        dev.teizen.client.util.WeaponSwap.after();
    }

    /** AutoMine/Nuker: Abbau nicht abbrechen lassen, nur weil die Maustaste nicht gedrückt ist. */
    @Inject(method = "stopDestroyBlock", at = @At("HEAD"), cancellable = true)
    private void teizen$keepBreaking(CallbackInfo ci) {
        if (Breaker.holding()) ci.cancel();
    }
}
