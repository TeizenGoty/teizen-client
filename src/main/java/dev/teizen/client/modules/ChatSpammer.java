package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.util.Chat;

import java.util.Random;

/** Schickt eine (oder abwechselnd mehrere) Nachricht(en) immer wieder in den Chat. */
public final class ChatSpammer extends Module {
    public final Setting text = add(Setting.text("nachricht", "Was gesendet wird (mehrere mit | trennen = abwechselnd)", "Teizen Client on top!"));
    private final Setting interval = add(Setting.number("sekunden", "Abstand zwischen zwei Nachrichten (Sekunden)", 3, 0.5, 600));
    private final Setting count = add(Setting.number("anzahl", "Wie oft gesendet wird (0 = endlos)", 0, 0, 10000));
    private final Setting random = add(Setting.bool("zufall", "Zufällige Zeichen anhängen (gegen Spam-Filter)", false));

    public int sent;
    /** Wie viele der gesendeten Nachrichten wieder im Chat angekommen sind (Selbsttest). */
    public static int echoes;
    private int wait, index;
    private final Random rnd = new Random();

    public ChatSpammer() {
        super("ChatSpammer", Category.SONSTIGES, "Schickt eine Nachricht immer wieder in den Chat.");
    }

    @Override
    protected void onEnable() {
        sent = 0;
        wait = 0;
        index = 0;
    }

    @Override
    public void onTick() {
        if (!inGame()) return;
        if (wait > 0) { wait--; return; }
        String[] parts = text.str().split("\\|");
        String msg = parts[index++ % parts.length].trim();
        if (msg.isEmpty() || msg.startsWith("///")) return;
        if (random.on()) {
            StringBuilder sb = new StringBuilder(" ");
            for (int i = 0; i < 4; i++) sb.append((char) ('a' + rnd.nextInt(26)));
            msg += sb;
        }
        if (msg.length() > 256) msg = msg.substring(0, 256);
        if (msg.startsWith("/")) mc.player.connection.sendCommand(msg.substring(1));
        else mc.player.connection.sendChat(msg);
        sent++;
        wait = Math.max(1, (int) Math.round(interval.num() * 20)) - 1;
        if (count.integer() > 0 && sent >= count.integer()) {
            setEnabled(false);
            Chat.info("ChatSpammer fertig: " + sent + " Nachrichten gesendet.");
        }
    }

    /** Einmal anmelden: zählt, ob unsere Nachrichten im Chat ankommen. */
    public static void register() {
        net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.CHAT.register((message, signed, sender, params, time) -> {
            var mm = dev.teizen.client.TeizenClient.modules();
            if (mm == null) return;
            ChatSpammer cs = mm.get(ChatSpammer.class);
            if (cs == null) return;
            String txt = message.getString();
            for (String part : cs.text.str().split("\\|")) {
                if (!part.isBlank() && txt.contains(part.trim())) { echoes++; return; }
            }
        });
    }

    @Override
    public String info() { return sent + (count.integer() > 0 ? "/" + count.integer() : ""); }
}
