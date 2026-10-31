package dev.teizen.client.modules;

import dev.teizen.client.TeizenClient;
import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import net.minecraft.network.chat.Component;

/** BetterChat: Uhrzeit vor jeder Nachricht, gleiche Nachrichten zusammenfassen (x2, x3 …), längerer Chat-Verlauf. */
public final class BetterChat extends Module {
    public final Setting time = add(Setting.bool("uhrzeit", "Uhrzeit [12:34] vor jeder Nachricht", true));
    public final Setting antiSpam = add(Setting.bool("antispam", "Gleiche Nachricht hintereinander nur einmal mit (x2), (x3) …", true));
    public final Setting longer = add(Setting.bool("verlauf", "Längerer Chat-Verlauf (1000 statt 100 Nachrichten)", true));

    private String last = "";
    private int count = 1;
    /** Für den Selbsttest. */
    public int merged, stamped;

    public BetterChat() {
        super("BetterChat", Category.SONSTIGES, "Chat mit Uhrzeit, ohne Spam-Wiederholungen und mit längerem Verlauf.");
    }

    public static BetterChat active() {
        if (TeizenClient.modules() == null) return null;
        BetterChat b = TeizenClient.modules().get(BetterChat.class);
        return b != null && b.isEnabled() ? b : null;
    }

    /** Wie viele Nachrichten der Chat behält. */
    public static int historySize(int vanilla) {
        BetterChat b = active();
        return b != null && b.longer.on() ? Math.max(vanilla, 1000) : vanilla;
    }

    /**
     * Aus ChatComponent.addMessage. @param removeLast wird aufgerufen, wenn die letzte (gleiche) Nachricht ersetzt werden soll
     * @return die Nachricht, wie sie angezeigt wird
     */
    public static Component decorate(Component msg, Runnable removeLast) {
        BetterChat b = active();
        if (b == null || msg == null) return msg;
        String plain = msg.getString();
        Component out = msg;
        if (b.antiSpam.on()) {
            if (plain.equals(b.last)) {
                b.count++;
                removeLast.run();
                out = Component.empty().append(msg).append(Component.literal(" §7(x" + b.count + ")"));
                b.merged++;
            } else {
                b.count = 1;
                b.last = plain;
            }
        }
        if (b.time.on()) {
            var t = java.time.LocalTime.now();
            out = Component.literal(String.format(java.util.Locale.ROOT, "§8[%02d:%02d] §r", t.getHour(), t.getMinute())).append(out);
            b.stamped++;
        }
        return out;
    }
}
