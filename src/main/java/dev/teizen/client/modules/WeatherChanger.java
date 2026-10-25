package dev.teizen.client.modules;

import dev.teizen.client.TeizenClient;
import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;

/** WeatherChanger: eigenes Wetter – klar, Regen oder Gewitter. Nur bei dir, der Server merkt nichts. */
public final class WeatherChanger extends Module {
    private final Setting weather = add(Setting.choice("wetter", "Welches Wetter du siehst", "klar", "klar", "regen", "gewitter"));

    public WeatherChanger() {
        super("WeatherChanger", Category.SICHT, "Eigenes Wetter: klar, Regen oder Gewitter (nur bei dir).");
    }

    private static WeatherChanger active() {
        if (TeizenClient.modules() == null) return null;
        WeatherChanger m = TeizenClient.modules().get(WeatherChanger.class);
        return m != null && m.isEnabled() ? m : null;
    }

    /** Mixin: Regenstärke 0..1, oder null = normal. */
    public static Float rain() {
        WeatherChanger m = active();
        if (m == null) return null;
        return m.weather.is("klar") ? 0f : 1f;
    }

    /** Mixin: Gewitterstärke 0..1, oder null = normal. */
    public static Float thunder() {
        WeatherChanger m = active();
        if (m == null) return null;
        return m.weather.is("gewitter") ? 1f : 0f;
    }

    @Override
    public String info() { return weather.str(); }
}
