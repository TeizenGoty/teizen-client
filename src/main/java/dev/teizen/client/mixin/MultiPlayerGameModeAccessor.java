package dev.teizen.client.mixin;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MultiPlayerGameMode.class)
public interface MultiPlayerGameModeAccessor {
    @Accessor("destroyProgress")
    float teizen$getDestroyProgress();

    @Accessor("destroyProgress")
    void teizen$setDestroyProgress(float value);

    @Accessor("destroyDelay")
    int teizen$getDestroyDelay();

    @Accessor("destroyDelay")
    void teizen$setDestroyDelay(int value);

    /** Ausgewählten Hotbar-Platz sofort an den Server schicken. */
    @Invoker("ensureHasSentCarriedItem")
    void teizen$sendCarriedItem();
}
