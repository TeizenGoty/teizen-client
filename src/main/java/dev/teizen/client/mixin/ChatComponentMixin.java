package dev.teizen.client.mixin;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Client-Meldungen (z.B. Antworten von SeedMapper-Befehlen) mitlesen – die kommen nicht übers Netzwerk. */
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
    @Inject(method = "addClientSystemMessage", at = @At("HEAD"))
    private void teizen$clientMessage(Component message, CallbackInfo ci) {
        dev.teizen.client.core.Locator.onClientMessage(message);
    }
}
