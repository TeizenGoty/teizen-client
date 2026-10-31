package dev.teizen.client.modules;

import dev.teizen.client.TeizenClient;
import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;

/** Timer: das ganze Spiel läuft bei dir schneller (oder langsamer) – laufen, abbauen, essen. Zu schnell = Server setzt zurück. */
public final class Timer extends Module {
    private final Setting speed = add(Setting.number("tempo", "Spiel-Tempo (1 = normal, 2 = doppelt so schnell)", 1.5, 0.1, 5));

    public Timer() {
        super("Timer", Category.BEWEGUNG, "Das ganze Spiel läuft bei dir schneller (laufen, abbauen, essen).");
    }

    /** Mixin (Minecraft.getTickTargetMillis): Millisekunden pro Tick anpassen. */
    public static float adjust(float ms) {
        if (TeizenClient.modules() == null) return ms;
        Timer t = TeizenClient.modules().get(Timer.class);
        if (t == null || !t.isEnabled() || t.speed.num() <= 0) return ms;
        return (float) (ms / t.speed.num());
    }

    /** Für Anzeige/Selbsttest. */
    public double factor() { return isEnabled() ? speed.num() : 1; }

    @Override
    public String info() { return "x" + Setting.fmt(speed.num()); }
}
