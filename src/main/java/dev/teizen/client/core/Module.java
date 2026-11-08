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
            Chat.error(name + " konnte nicht " + (on ? "eingeschaltet" : "ausgeschaltet") + " werden: " + t);
            if (on) { enabled = false; try { onDisable(); } catch (Throwable ignored) {} } // sauber zurück, kein halb-an Zustand
        }
    }

    /** Umschalt-Sound beim Ein-/Ausschalten im Spiel (///sound an|aus). Standard an. Eigener Minecraft-Klang. */
    public static boolean sound = true;
    /** Welcher Klang (///sound <name>): freundlicher Name oder roher Sound-Name. */
    public static String soundName = "klick";

    /** Freundliche Namen -> Minecraft-Sound. */
    private static final Map<String, String> SOUNDS = new LinkedHashMap<>();
    static {
        SOUNDS.put("klick", "ui.button.click");
        SOUNDS.put("pling", "block.note_block.pling");
        SOUNDS.put("glocke", "block.note_block.bell");
        SOUNDS.put("amethyst", "block.amethyst_block.chime");
        SOUNDS.put("pop", "block.note_block.hat");
        SOUNDS.put("hebel", "block.lever.click");
        SOUNDS.put("ding", "block.note_block.chime");
        SOUNDS.put("exp", "entity.experience_orb.pickup");
    }

    /** Liste der auswählbaren Klänge (freundliche Namen). */
    public static java.util.Set<String> soundChoices() { return SOUNDS.keySet(); }

    /** Den eingestellten Klang abspielen (an = höher, aus = tiefer). */
    public static void playToggle(boolean on) {
        if (!sound) return;
        String id = SOUNDS.getOrDefault(soundName, soundName);
        net.minecraft.sounds.SoundEvent ev = null;
        try {
            var rl = net.minecraft.resources.Identifier.tryParse(id.contains(":") ? id : "minecraft:" + id);
            if (rl != null) ev = net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.getValue(rl);
        } catch (Throwable ignored) {}
        if (ev == null) ev = net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(); // Rückfall
        // als Menü-Geräusch abspielen (hängt nicht an der Spieler-Position/Lautstärke "Spieler" – vorher oft stumm)
        try {
            // Kategorie "Gesamtlautstärke" (Master), relativ zur Kamera -> immer hörbar, egal welche Regler es gibt
            mc.getSoundManager().play(new net.minecraft.client.resources.sounds.SimpleSoundInstance(ev.location(), net.minecraft.sounds.SoundSource.MASTER,
                    0.8f, on ? 1.7f : 1.1f, net.minecraft.util.RandomSource.create(), false, 0,
                    net.minecraft.client.resources.sounds.SoundInstance.Attenuation.NONE, 0, 0, 0, true));
        } catch (Throwable t) {
            if (mc.player != null) mc.player.playSound(ev, 0.8f, on ? 1.7f : 1.1f);
        }
    }

    public void toggle() {
        if (!enabled && !Lock.ok()) { Lock.complain(); return; }
        setEnabled(!enabled);
        Chat.info(name + (enabled ? " §aan" : " §caus"));
        playToggle(enabled);
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
