package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;

/** Rückstoß getrennt einstellen: zur Seite und nach oben (in Prozent). */
public final class Velocity extends Module {
    public final Setting horizontal = add(Setting.number("horizontal", "Rückstoß zur Seite in % (0 = keiner, 100 = normal)", 0, 0, 200));
    public final Setting vertical = add(Setting.number("vertikal", "Rückstoß nach oben in % (0 = keiner, 100 = normal)", 0, 0, 200));

    public Velocity() {
        super("Velocity", Category.KAMPF, "Rückstoß zur Seite und nach oben in % einstellen.");
    }

    @Override
    public String info() {
        return "H" + Math.round(horizontal.num()) + "% V" + Math.round(vertical.num()) + "%";
    }
}
