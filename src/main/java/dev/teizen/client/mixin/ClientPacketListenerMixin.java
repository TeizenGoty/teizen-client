package dev.teizen.client.mixin;

import dev.teizen.client.TeizenClient;
import dev.teizen.client.modules.AntiKnockback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** AntiKnockback: Bewegung vor und nach dem Paket vergleichen und den Rückstoß herausrechnen. */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Unique
    private Vec3 teizen$before;

    @Unique
    private static AntiKnockback teizen$akb() {
        if (TeizenClient.modules() == null) return null;
        AntiKnockback a = TeizenClient.modules().get(AntiKnockback.class);
        return a != null && a.isEnabled() ? a : null;
    }

    @Unique
    private void teizen$store() {
        var p = Minecraft.getInstance().player;
        teizen$before = (p != null && teizen$akb() != null) ? p.getDeltaMovement() : null;
    }

    @Unique
    private void teizen$restore() {
        var p = Minecraft.getInstance().player;
        AntiKnockback a = teizen$akb();
        if (p == null || a == null || teizen$before == null) return;
        Vec3 after = p.getDeltaMovement();
        if (!after.equals(teizen$before)) {
            double s = a.strength.num();
            p.setDeltaMovement(teizen$before.add(after.subtract(teizen$before).scale(s)));
        }
        teizen$before = null;
    }

    @Inject(method = "handleSetEntityMotion", at = @At("HEAD"))
    private void teizen$motionHead(ClientboundSetEntityMotionPacket packet, CallbackInfo ci) { teizen$store(); }

    @Inject(method = "handleSetEntityMotion", at = @At("TAIL"))
    private void teizen$motionTail(ClientboundSetEntityMotionPacket packet, CallbackInfo ci) { teizen$restore(); }

    @Inject(method = "handleExplosion", at = @At("HEAD"))
    private void teizen$explHead(ClientboundExplodePacket packet, CallbackInfo ci) { teizen$store(); }

    @Inject(method = "handleExplosion", at = @At("TAIL"))
    private void teizen$explTail(ClientboundExplodePacket packet, CallbackInfo ci) { teizen$restore(); }
}
