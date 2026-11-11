package dev.teizen.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/** Klick-Geräusche im Menü: 10 eigene Klänge zur Auswahl (0 = aus). */
public final class Clicks {
    private Clicks() {}

    public static final String[] NAMES = {"aus", "Pop", "Blub", "Tick", "Tock", "Glas", "Holz", "Taste", "Tropfen", "Soft", "Knack"};
    /** Gewählter Klang (Index in NAMES). */
    public static int current = 1;
    /** Für den Selbsttest: so oft wurde ein Klick abgespielt. */
    public static int played;

    public static String name() { return NAMES[Math.floorMod(current, NAMES.length)]; }

    /** Nächster (d = 1) oder voriger (d = -1) Klang, gleich zum Anhören. */
    public static void cycle(int d) {
        current = Math.floorMod(current + d, NAMES.length);
        play(1f);
    }

    /** Name setzen (///klick). @return false wenn es den Namen nicht gibt */
    public static boolean set(String n) {
        for (int i = 0; i < NAMES.length; i++) if (NAMES[i].equalsIgnoreCase(n)) { current = i; play(1f); return true; }
        return false;
    }

    public static void play(float pitch) {
        if (current <= 0 || current >= NAMES.length) return;
        try {
            Identifier id = Identifier.fromNamespaceAndPath("teizenclient", "click_" + current);
            Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(id, SoundSource.MASTER, 0.7f, pitch,
                    RandomSource.create(), false, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true));
            played++;
        } catch (Throwable ignored) {}
    }
}
