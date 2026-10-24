package dev.teizen.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Ausgaben nur im eigenen Chat (wird nicht an den Server gesendet). */
public final class Chat {
    private static final String PREFIX = "§5§lTeizen §8» §7";

    private Chat() {}

    public static void raw(String msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui == null || mc.gui.hud == null) return;
        mc.execute(() -> mc.gui.hud.getChat().addClientSystemMessage(Component.literal(msg)));
    }

    public static void info(String msg) { raw(PREFIX + msg); }

    public static void ok(String msg) { raw(PREFIX + "§a" + msg); }

    public static void error(String msg) { raw(PREFIX + "§c" + msg); }
}
