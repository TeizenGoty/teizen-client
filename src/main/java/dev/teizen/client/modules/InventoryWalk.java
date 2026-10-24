package dev.teizen.client.modules;

import com.mojang.blaze3d.platform.InputConstants;
import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.gui.ClickGui;
import dev.teizen.client.gui.Overview;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.util.Mth;

import java.util.function.IntPredicate;

/** Weiterlaufen (und springen), während das Inventar oder eine Kiste offen ist. Pfeiltasten = umschauen. */
public final class InventoryWalk extends Module {
    private final Setting allScreens = add(Setting.bool("menues", "Auch im Teizen-Menü und in der Übersicht laufen", true));
    private final Setting sprint = add(Setting.bool("sprinten", "Sprinten erlaubt", true));
    private final Setting look = add(Setting.bool("pfeiltasten", "Mit den Pfeiltasten umschauen", true));
    private final Setting lookSpeed = add(Setting.number("drehtempo", "Wie schnell die Pfeiltasten drehen (Grad pro Tick)", 6, 1, 30));

    /** Wie der Zustand einer Taste abgefragt wird (der Selbsttest kann das ersetzen). */
    public static IntPredicate keyDown = code -> InputConstants.isKeyDown(mc.getWindow(), code);
    private boolean active;

    public InventoryWalk() {
        super("InventoryWalk", Category.BEWEGUNG, "Laufen, während Inventar oder Kiste offen sind.");
    }

    private boolean allowed(Screen s) {
        if (s == null) return false;
        if (s.getFocused() instanceof EditBox) return false; // gerade wird Text getippt
        if (s instanceof ClickGui g) return allScreens.on() && !g.isEditing();
        if (s instanceof Overview) return allScreens.on();
        if (s instanceof CreativeModeInventoryScreen) return true;
        return s instanceof AbstractContainerScreen<?>;
    }

    private static boolean held(KeyMapping k) {
        InputConstants.Key key = KeyMappingHelper.getBoundKeyOf(k);
        if (key == null || key.getType() != InputConstants.Type.KEYSYM || key.getValue() < 0) return false;
        return keyDown.test(key.getValue());
    }

    @Override
    public void onTick() {
        if (!inGame()) return;
        Screen s = mc.gui.screen();
        if (!allowed(s)) {
            if (active) {
                active = false;
                // nach dem Schließen nicht weiterlaufen, wenn die Taste losgelassen wurde
                for (KeyMapping k : moveKeys()) if (!held(k)) k.setDown(false);
            }
            return;
        }
        active = true;
        for (KeyMapping k : moveKeys()) k.setDown(held(k));
        if (!sprint.on()) mc.options.keySprint.setDown(false);
        if (look.on()) {
            float sp = lookSpeed.flt();
            float dy = 0, dp = 0;
            if (keyDown.test(org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT)) dy -= sp;
            if (keyDown.test(org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT)) dy += sp;
            if (keyDown.test(org.lwjgl.glfw.GLFW.GLFW_KEY_UP)) dp -= sp;
            if (keyDown.test(org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN)) dp += sp;
            if (dy != 0 || dp != 0) {
                mc.player.setYRot(mc.player.getYRot() + dy);
                mc.player.setXRot(Mth.clamp(mc.player.getXRot() + dp, -90f, 90f));
            }
        }
    }

    private KeyMapping[] moveKeys() {
        var o = mc.options;
        return new KeyMapping[]{o.keyUp, o.keyDown, o.keyLeft, o.keyRight, o.keyJump, o.keySprint};
    }
}
