package dev.teizen.client.modules;

import dev.teizen.client.TeizenClient;
import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;

/** TimeChanger: eigene Tageszeit – nur bei dir, der Server und andere merken nichts. */
public final class TimeChanger extends Module {
    private final Setting time = add(Setting.choice("zeit", "Welche Tageszeit", "mittag", "morgen", "mittag", "abend", "nacht", "mitternacht", "eigene"));
    private final Setting custom = add(Setting.number("eigenezeit", "Bei zeit = eigene: Zeit in Ticks (0 = Morgen, 6000 = Mittag, 18000 = Mitternacht)", 6000, 0, 23999));

    /** Wie oft die Zeit schon ersetzt wurde (Selbsttest). */
    public static int hits;

    public TimeChanger() {
        super("TimeChanger", Category.SICHT, "Eigene Tageszeit (nur bei dir).");
    }

    private long ticksOfDay() {
        return switch (time.str()) {
            case "morgen" -> 0;
            case "abend" -> 12000;
            case "nacht" -> 14000;
            case "mitternacht" -> 18000;
            case "eigene" -> (long) custom.num();
            default -> 6000;
        };
    }

    /** Mixin (Uhr der Welt): echte Zeit -> unsere Zeit (gleicher Tag, andere Uhrzeit), oder null. */
    public static Long override(long real) {
        if (TeizenClient.modules() == null) return null;
        TimeChanger m = TeizenClient.modules().get(TimeChanger.class);
        if (m == null || !m.isEnabled()) return null;
        hits++;
        return Math.floorDiv(real, 24000L) * 24000L + m.ticksOfDay();
    }

    @Override
    public String info() { return time.str(); }
}
