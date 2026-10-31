package dev.teizen.client.mixin;

import dev.teizen.client.TeizenClient;
import dev.teizen.client.modules.AntiKnockback;
import dev.teizen.client.modules.Velocity;
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

/** AntiKnockback / Velocity: Bewegung vor und nach dem Paket vergleichen und den Rückstoß herausrechnen. */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Unique
    private Vec3 teizen$before;

    /** {seitlich, hoch} – wie viel vom Rückstoß bleibt, oder null wenn nichts geändert wird. */
    @Unique
    private static double[] teizen$factors() {
        if (TeizenClient.modules() == null) return null;
        Velocity v = TeizenClient.modules().get(Velocity.class);
        if (v != null && v.isEnabled()) return new double[]{v.horizontal.num() / 100.0, v.vertical.num() / 100.0};
        AntiKnockback a = TeizenClient.modules().get(AntiKnockback.class);
        if (a != null && a.isEnabled()) return new double[]{a.strength.num(), a.strength.num()};
        return null;
    }

    @Unique
    private void teizen$store() {
        var p = Minecraft.getInstance().player;
        teizen$before = p != null ? p.getDeltaMovement() : null;
    }

    @Unique
    private void teizen$restore() {
        var p = Minecraft.getInstance().player;
        double[] f = teizen$factors();
        if (p == null || teizen$before == null) { teizen$before = null; return; }
        Vec3 after = p.getDeltaMovement();
        if (f != null && !after.equals(teizen$before)) {
            Vec3 d = after.subtract(teizen$before);
            p.setDeltaMovement(teizen$before.add(d.x * f[0], d.y * f[1], d.z * f[0]));
            dev.teizen.client.SelfTest.knockbacks++;
        }
        // Fly: bei Schaden schickt der Server SEINE Geschwindigkeit (er glaubt, man fällt) -> man wurde nach unten gerissen.
        // Beim Fliegen den Abwärts-Teil davon ignorieren.
        dev.teizen.client.modules.Fly.onServerMotion(teizen$before);
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

    /** Notifier / PopChams: Totem geplatzt usw. */
    @Inject(method = "handleEntityEvent", at = @At("TAIL"))
    private void teizen$entityEvent(net.minecraft.network.protocol.game.ClientboundEntityEventPacket packet, CallbackInfo ci) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        dev.teizen.client.modules.Notifier.onEntityEvent(packet.getEntity(mc.level), packet.getEventId());
    }

    /** BreakIndicators: was andere gerade abbauen. */
    @Inject(method = "handleBlockDestruction", at = @At("TAIL"))
    private void teizen$blockDestruction(net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket packet, CallbackInfo ci) {
        dev.teizen.client.modules.BreakIndicators.onDestruction(packet.getId(), packet.getPos(), packet.getProgress());
    }
}
