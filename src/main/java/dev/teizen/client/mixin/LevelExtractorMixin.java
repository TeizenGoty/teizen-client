package dev.teizen.client.mixin;

import dev.teizen.client.TeizenClient;
import dev.teizen.client.modules.Freecam;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Freecam: den eigenen Spieler trotzdem zeichnen (Minecraft lässt ihn weg, wenn die Kamera woanders ist). */
@Mixin(LevelExtractor.class)
public abstract class LevelExtractorMixin {
    @Shadow
    protected abstract EntityRenderState extractEntity(Entity entity, float partial);

    @Inject(method = "extractVisibleEntities", at = @At("RETURN"))
    private void teizen$freecamPlayer(Camera camera, Frustum frustum, DeltaTracker delta, LevelRenderState state, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || TeizenClient.modules() == null) return;
        Freecam f = TeizenClient.modules().get(Freecam.class);
        if (f == null || !f.active() || !f.showPlayer.on() || camera.entity() == mc.player) return;
        float pt = delta.getGameTimeDeltaPartialTick(!mc.level.tickRateManager().isEntityFrozen(mc.player));
        state.entityRenderStates.add(extractEntity(mc.player, pt));
        f.playerDrawn++;
    }
}
