package dev.teizen.client.mixin;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
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

    @Shadow @org.spongepowered.asm.mixin.Final private java.util.List<net.minecraft.client.multiplayer.chat.GuiMessage> allMessages;
    @Shadow private void refreshTrimmedMessages() {}

    /** BetterChat: Uhrzeit + gleiche Nachrichten zusammenfassen. */
    @org.spongepowered.asm.mixin.injection.ModifyVariable(method = "addMessage", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private Component teizen$betterChat(Component message) {
        return dev.teizen.client.modules.BetterChat.decorate(message, () -> {
            if (!allMessages.isEmpty()) { allMessages.remove(0); refreshTrimmedMessages(); }
        });
    }

    /** BetterChat: längerer Verlauf. */
    @org.spongepowered.asm.mixin.injection.ModifyConstant(method = {"addMessageToQueue", "addMessageToDisplayQueue"}, constant = @org.spongepowered.asm.mixin.injection.Constant(intValue = 100), require = 0)
    private int teizen$history(int v) { return dev.teizen.client.modules.BetterChat.historySize(v); }
}
