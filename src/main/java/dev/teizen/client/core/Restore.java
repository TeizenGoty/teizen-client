package dev.teizen.client.core;

import dev.teizen.client.TeizenClient;
import dev.teizen.client.util.Chat;
import net.minecraft.client.Minecraft;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Merkt sich, welche Funktionen an waren, und schaltet sie nach einem Neustart beim ersten Betreten einer Welt
 * wieder ein. Speichert von selbst, sobald sich etwas ändert (auch wenn das Spiel abstürzt / hart beendet wird).
 * Ausschalten: ///merken aus
 */
public final class Restore {
    private Restore() {}

    public static boolean enabled = true;
    /** Beim Laden gefunden: diese waren an. */
    public static final Set<String> PENDING = new LinkedHashSet<>();
    /** Aufgaben, die man nicht einfach wieder starten will (laufen los / bauen / räumen aus). */
    private static final Set<String> SKIP = Set.of("panicbutton", "autobuild", "automine", "highwaybuilder", "bookbot",
            "shulkerunpacker", "villagerroller", "clicktp", "veinminer", "liquidfiller", "autobrewer", "fakeplayer", "swarm");

    private static boolean done;
    private static String lastState = "";
    private static int saveWait;
    /** Selbsttest: zuletzt wieder eingeschaltet. */
    public static int restored;

    public static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) return;
        if (!done) {
            done = true;
            if (enabled && !PENDING.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                int n = 0;
                for (String name : PENDING) {
                    Module m = TeizenClient.modules().byName(name);
                    if (m == null || m.isEnabled() || SKIP.contains(m.name.toLowerCase(java.util.Locale.ROOT))) continue;
                    if (m.needsOp() && !dev.teizen.client.util.Op.available()) continue;
                    m.setEnabled(true);
                    if (m.isEnabled()) { n++; if (sb.length() < 200) sb.append(sb.length() == 0 ? "" : ", ").append(m.name); }
                }
                restored = n;
                if (n > 0) Chat.info("Wieder an wie beim letzten Mal (" + n + "): §f" + sb + "§7  – aus mit ///panic, nicht mehr merken: ///merken aus");
            }
            PENDING.clear();
            lastState = state();
            return;
        }
        // Änderungen sofort sichern (kurz gesammelt), damit es auch nach einem Absturz stimmt
        String st = state();
        if (!st.equals(lastState)) { lastState = st; saveWait = 20; }
        if (saveWait > 0 && --saveWait == 0) Config.save();
    }

    private static String state() {
        StringBuilder sb = new StringBuilder();
        for (Module m : TeizenClient.modules().all()) if (m.isEnabled()) sb.append(m.name).append(',');
        return sb.toString();
    }
}
