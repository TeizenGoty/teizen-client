package dev.teizen.client.mixin;

import dev.teizen.client.TeizenClient;
import dev.teizen.client.modules.Freecam;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Freecam: Mausbewegung dreht die Kamera statt den Spieler. SeeInvisible: Unsichtbare sichtbar machen. */
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

    @Inject(method = "isInvisibleTo", at = @At("HEAD"), cancellable = true)
    private void teizen$seeInvisible(net.minecraft.world.entity.player.Player viewer, CallbackInfoReturnable<Boolean> cir) {
        if (TeizenClient.modules() == null || viewer == null || viewer != Minecraft.getInstance().player) return;
        var si = TeizenClient.modules().get(dev.teizen.client.modules.SeeInvisible.class);
        if (si != null && si.reveal((Entity) (Object) this)) cir.setReturnValue(false);
    }

    /** BoatNoclip: im Boot in der Wand nicht ersticken (eigene Welt). */
    @Inject(method = "isInWall", at = @At("HEAD"), cancellable = true)
    private void teizen$boatNoclipWall(CallbackInfoReturnable<Boolean> cir) {
        if (dev.teizen.client.modules.BoatNoclip.noSuffocation((Entity) (Object) this)) cir.setReturnValue(false);
    }

    /** Fly-Lavaschutz: Abwärtsbewegung VOR dem Bewegen so kürzen, dass man nie in die Lava kommt. */
    @org.spongepowered.asm.mixin.injection.ModifyVariable(method = "move", at = @At("HEAD"), argsOnly = true)
    private net.minecraft.world.phys.Vec3 teizen$lavaClamp(net.minecraft.world.phys.Vec3 mv) {
        if ((Object) this != Minecraft.getInstance().player || TeizenClient.modules() == null) return mv;
        return dev.teizen.client.modules.Fly.clampLava(mv);
    }

    /** Fly: kein zufälliges Sprinten in der Luft. */
    @Inject(method = "setSprinting", at = @At("HEAD"), cancellable = true)
    private void teizen$flySprint(boolean on, CallbackInfo ci) {
        if ((Object) this == Minecraft.getInstance().player && dev.teizen.client.modules.Fly.blockSprint(on)) ci.cancel();
    }
}
