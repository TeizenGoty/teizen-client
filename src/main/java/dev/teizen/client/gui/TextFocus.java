package dev.teizen.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * 26.3 (SDL): Minecraft schaltet die Texteingabe nur ein, solange ein echtes Textfeld (EditBox) fokussiert ist –
 * sonst kommen getippte Buchstaben gar nicht an. Unsere Menüs haben eigene Felder (Suche, Zahlen), darum wird
 * ein unsichtbares EditBox fokussiert, solange so ein Menü offen ist. Auf 26.2 schadet das nicht.
 */
public final class TextFocus {
    private static EditBox box;

    private TextFocus() {}

    /** Texteingabe an (beim Öffnen eines Menüs). */
    public static void on() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (box == null) box = new EditBox(mc.font, 0, 0, 1, 1, Component.empty());
            box.setFocused(false);
            box.setFocused(true);
        } catch (Throwable t) {
            dev.teizen.client.TeizenClient.LOG.warn("Texteingabe einschalten ging nicht", t);
        }
    }

    /** Texteingabe wieder aus (beim Schließen). */
    public static void off() {
        try {
            if (box != null) box.setFocused(false);
        } catch (Throwable t) {
            dev.teizen.client.TeizenClient.LOG.warn("Texteingabe ausschalten ging nicht", t);
        }
    }
}
