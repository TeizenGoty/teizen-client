package dev.teizen.client.modules;

import dev.teizen.client.core.Module;

/** Du fällst nicht mehr von Kanten herunter (wie beim Schleichen, aber in normalem Tempo). */
public final class SafeWalk extends Module {
    public SafeWalk() {
        super("SafeWalk", Category.BEWEGUNG, "Du fällst nicht von Kanten herunter.");
    }

    public static boolean on() {
        var mm = dev.teizen.client.TeizenClient.modules();
        if (mm == null) return false;
        SafeWalk s = mm.get(SafeWalk.class);
        return s != null && s.isEnabled();
    }
}
