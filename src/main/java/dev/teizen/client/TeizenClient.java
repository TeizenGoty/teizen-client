package dev.teizen.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.teizen.client.core.Commands;
import dev.teizen.client.core.Config;
import dev.teizen.client.core.Finder;
import dev.teizen.client.core.Module;
import dev.teizen.client.core.ModuleManager;
import dev.teizen.client.gui.ClickGui;
import dev.teizen.client.gui.Hud;
import dev.teizen.client.util.Chat;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public final class TeizenClient implements ClientModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("TeizenClient");
    private static ModuleManager modules;
    /** Taste fürs Menü (Standard: rechte Shift-Taste). */
    public static int menuKey = GLFW.GLFW_KEY_RIGHT_SHIFT;

    private final Map<Integer, Boolean> wasDown = new HashMap<>();
    private boolean greeted;

    public static ModuleManager modules() { return modules; }

    @Override
    public void onInitializeClient() {
        modules = new ModuleManager();
        Config.load();
        // Tasten unter Optionen -> Steuerung (Kategorie "Teizen Client")
        dev.teizen.client.core.KeyBinds.register();

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            handleKeys(mc);
            SelfTest.tick();
            modules.get(dev.teizen.client.modules.AutoReconnect.class).globalTick(mc);
            if (mc.player == null || mc.level == null) { greeted = false; dev.teizen.client.core.Locator.tick(); return; }
            if (!greeted) {
                greeted = true;
                Chat.info("geladen! §f" + dev.teizen.client.gui.SettingsScreen.keyName(menuKey) + "§7 = Menü, §f///help§7 = Befehle");
            }
            modules.tick();
            dev.teizen.client.core.FarPlayers.tick();
            dev.teizen.client.util.ServerQuery.tick();
            dev.teizen.client.core.Seed.tick();
            modules.get(dev.teizen.client.modules.Fly.class).fallGuard();
            Finder.tick();
            dev.teizen.client.core.Locator.tick();
        });

        // ///-Befehle abfangen (Minecraft sieht "///xray" als Befehl "//xray")
        ClientSendMessageEvents.ALLOW_COMMAND.register(command -> !Commands.handle("/" + command));
        ClientSendMessageEvents.ALLOW_CHAT.register(message -> !Commands.handle(message));
        // Vorschläge beim Tippen von "///" (wie bei normalen Befehlen)
        dev.teizen.client.core.CommandSuggestions.register();
        // /locate-Antworten aus dem Chat lesen (StructureFinder, PortalFinder, ///find structure)
        dev.teizen.client.core.Locator.register();
        // Countdown auf dem "Verbindung getrennt"-Bildschirm
        dev.teizen.client.modules.AutoReconnect.register();
        dev.teizen.client.modules.ChatSpammer.register();
        dev.teizen.client.modules.EnchantPreview.register();
        // Antworten auf /data-Abfragen (PlayerFinder, Radar, PotionHUD, EnchantPreview) lesen und verstecken
        dev.teizen.client.util.ServerQuery.register();

        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("teizenclient", "hud"), Hud::render);

        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> {
            Config.save();
            // SeedCrackerX lässt beim Beenden Threads offen -> Minecraft hängt und schreibt einen Crash-Report
            dev.teizen.client.core.Seed.stopCracker();
        });
        LOG.info("Teizen Client geladen ({} Funktionen)", modules.all().size());
    }

    private boolean pressed(Minecraft mc, int key) {
        boolean down = InputConstants.isKeyDown(mc.getWindow(), key);
        Boolean before = wasDown.put(key, down);
        return down && (before == null || !before);
    }

    private void handleKeys(Minecraft mc) {
        if (mc.getWindow() == null) return;
        dev.teizen.client.core.KeyBinds.tick(mc);
        boolean noScreen = mc.gui.screen() == null;
        if (pressed(mc, menuKey) && noScreen && mc.player != null) {
            mc.gui.setScreen(new ClickGui());
        }
        for (Module m : modules.all()) {
            if (m.key < 0 || m.key == menuKey) continue;
            if (pressed(mc, m.key) && noScreen && mc.player != null) {
                m.toggle();
                Config.save();
            }
        }
    }
}
