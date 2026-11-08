package dev.teizen.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Teizen-Knopf oben links im Minecraft-Hauptmenü: Teizen-Menü auch ohne Welt öffnen. */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
    protected TitleScreenMixin(Component title) { super(title); }

    @Inject(method = "init", at = @At("TAIL"))
    private void teizen$button(CallbackInfo ci) {
        this.addRenderableWidget(Button.builder(Component.literal("§dTeizen"),
                b -> Minecraft.getInstance().gui.setScreen(new dev.teizen.client.gui.ClickGui())).bounds(4, 4, 64, 20).build());
    }
}
