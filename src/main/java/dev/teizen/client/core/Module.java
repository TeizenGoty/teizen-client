package dev.teizen.client.core;

import dev.teizen.client.util.Chat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Basisklasse aller Funktionen. */
public abstract class Module {
    public enum Category {
        KAMPF("Kampf"), ABBAU("Abbau"), BEWEGUNG("Bewegung"), SICHT("Sicht"), BAUEN("Bauen"), SONSTIGES("Sonstiges");
        public final String title;
        Category(String title) { this.title = title; }
    }

    protected static final Minecraft mc = Minecraft.getInstance();

    public final String name;
    public final String description;
    public final Category category;
    public final Map<String, Setting> settings = new LinkedHashMap<>();
    private boolean enabled;
    /** GLFW-Tastencode, -1 = keine Taste. */
    public int key = -1;
    /** Favorit: steht im Menü und in der Übersicht ganz oben. */
    public boolean favorite;
    /** Braucht OP-Rechte (bzw. "Cheats erlaubt") – kann im Menü ausgeblendet werden. */
    private boolean needsOp;

    /** Im Konstruktor aufrufen: diese Funktion braucht OP. */
    protected void opOnly() { needsOp = true; }

    public boolean needsOp() { return needsOp; }

    /** Was fehlt, damit es geht (z.B. "braucht Xaero's Minimap"), oder null. */
    public String requirement() { return null; }

    protected Module(String name, Category category, String description) {
        this.name = name;
        this.category = category;
        this.description = description;
    }

    protected Setting add(Setting s) {
        settings.put(s.name.toLowerCase(Locale.ROOT), s);
        return s;
    }

    public Setting setting(String n) {
        return settings.get(n.toLowerCase(Locale.ROOT));
    }

    public boolean isEnabled() { return enabled; }

    public void setEnabled(boolean on) {
        if (on == enabled) return;
        // Version mit Key-Pflicht ohne Key-Mod: nichts lässt sich einschalten (ausschalten geht immer)
        if (on && !Lock.ok()) { Lock.complain(); return; }
        enabled = on;
        try {
            if (on) onEnable(); else onDisable();
        } catch (Throwable t) {
            Chat.error(name + ": " + t);
        }
    }

    public void toggle() {
        if (!enabled && !Lock.ok()) { Lock.complain(); return; }
        setEnabled(!enabled);
        Chat.info(name + (enabled ? " §aan" : " §caus"));
    }

    public boolean inGame() {
        return mc.player != null && mc.level != null && mc.gameMode != null;
    }

    protected void onEnable() {}
    protected void onDisable() {}
    public void onTick() {}
    /** HUD-Zeichnen (auch 2D-ESP), nur wenn eingeschaltet. */
    public void onHud(GuiGraphicsExtractor g, float partial) {}
    /** Kurzer Zusatztext für die Modul-Liste (z.B. Reichweite). */
    public String info() { return ""; }
}
