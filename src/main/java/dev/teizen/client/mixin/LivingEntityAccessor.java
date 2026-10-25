package dev.teizen.client.mixin;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/** PotionHUD: die Effekt-Partikel anderer Spieler lesen (daraus lassen sich die Effekte erkennen). */
@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
    @Accessor("DATA_EFFECT_PARTICLES")
    static EntityDataAccessor<List<ParticleOptions>> teizen$effectParticles() {
        throw new AssertionError();
    }
}
