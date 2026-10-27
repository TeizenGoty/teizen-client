package dev.teizen.client.compat;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** Lädt die Mixins, die es nur für eine Minecraft-Version gibt (mixin/v262 bzw. mixin/v263). */
public final class MixinPlugin implements IMixinConfigPlugin {
    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) { return true; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        String v = Mc.running().equals("26.3") ? "v263" : "v262";
        // nur, wenn der Versions-Code in der Jar steckt (Entwicklungs-Client hat nur eine Version)
        if (MixinPlugin.class.getClassLoader().getResource("dev/teizen/client/mixin/" + v + "/ClockMixin.class") == null) return List.of();
        return List.of(v + ".ClockMixin");
    }

    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
