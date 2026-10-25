package dev.teizen.client.mixin;

import dev.teizen.client.modules.NameProtect;
import net.minecraft.util.StringDecomposer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** NameProtect: jeder Text, der gezeichnet wird, läuft hier durch – dein Name wird ersetzt. */
@Mixin(StringDecomposer.class)
public abstract class StringDecomposerMixin {
    @ModifyVariable(method = "iterateFormatted(Ljava/lang/String;ILnet/minecraft/network/chat/Style;Lnet/minecraft/network/chat/Style;Lnet/minecraft/util/FormattedCharSink;)Z",
            at = @At("HEAD"), argsOnly = true, index = 0)
    private static String teizen$nameFormatted(String text) { return NameProtect.apply(text); }

    @ModifyVariable(method = "iterate", at = @At("HEAD"), argsOnly = true, index = 0)
    private static String teizen$nameIterate(String text) { return NameProtect.apply(text); }

    @ModifyVariable(method = "iterateBackwards", at = @At("HEAD"), argsOnly = true, index = 0)
    private static String teizen$nameBackwards(String text) { return NameProtect.apply(text); }
}
