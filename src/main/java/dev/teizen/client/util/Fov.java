package dev.teizen.client.util;

import net.minecraft.client.Minecraft;

/** Aktuelles Sichtfeld (wird von einem Mixin in GameRenderer aktualisiert, sonst Optionswert). */
public final class Fov {
    private static volatile double last = -1;

    private Fov() {}

    public static void update(double fov) {
        if (fov > 1 && fov < 179) last = fov;
    }

    public static double current() {
        if (last > 0) return last;
        try {
            Object v = Minecraft.getInstance().options.fov().get();
            if (v instanceof Number n) return n.doubleValue();
        } catch (Throwable ignored) {
        }
        return 70.0;
    }
}
