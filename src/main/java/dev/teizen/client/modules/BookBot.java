package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.util.Chat;
import net.minecraft.network.protocol.game.ServerboundEditBookPacket;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/** BookBot: schreibt alle Bücher mit Feder in deiner Hotbar automatisch voll (eigener Text oder Zufall), auf Wunsch signiert. */
public final class BookBot extends Module {
    private final Setting mode = add(Setting.choice("modus", "text = dein Text (wird wiederholt), zufall = zufällige Zeichen", "text", "text", "zufall"));
    private final Setting text = add(Setting.text("text", "Text für die Seiten", "Teizen Client war hier."));
    private final Setting pages = add(Setting.number("seiten", "Seiten pro Buch", 50, 1, 100));
    private final Setting sign = add(Setting.bool("signieren", "Buch danach signieren (fertiges Buch mit Titel)", false));
    private final Setting title = add(Setting.text("titel", "Titel beim Signieren", "Teizen"));
    private final Setting delay = add(Setting.number("pause", "Ticks zwischen zwei Büchern", 10, 1, 100));
    private int wait;
    public int written;

    public BookBot() {
        super("BookBot", Category.SONSTIGES, "Schreibt Bücher mit Feder automatisch voll (und signiert sie).");
    }

    @Override
    protected void onEnable() { wait = 0; doneSlots = 0; }

    private List<String> makePages() {
        List<String> out = new ArrayList<>();
        Random r = new Random();
        for (int p = 0; p < pages.integer(); p++) {
            StringBuilder sb = new StringBuilder();
            if (mode.is("zufall")) {
                for (int i = 0; i < 250; i++) sb.append((char) (0x21 + r.nextInt(0x5D)));
            } else {
                String t = text.str().isEmpty() ? "Teizen" : text.str();
                while (sb.length() + t.length() + 1 <= 250) sb.append(t).append(' ');
            }
            out.add(sb.toString().trim());
        }
        return out;
    }

    @Override
    public void onTick() {
        if (!inGame() || mc.gui.screen() != null) return;
        if (wait > 0) { wait--; return; }
        var inv = mc.player.getInventory();
        for (int slot = 0; slot < 9; slot++) {
            if (!inv.getItem(slot).is(Items.WRITABLE_BOOK) || (!sign.on() && (doneSlots & (1 << slot)) != 0)) continue;
            String t = title.str().isBlank() ? "Teizen" : title.str();
            if (t.length() > 32) t = t.substring(0, 32);
            mc.player.connection.send(new ServerboundEditBookPacket(slot, makePages(), sign.on() ? Optional.of(t) : Optional.empty()));
            written++;
            wait = delay.integer();
            // ohne Signieren bleibt es ein Buch mit Feder -> nur einmal pro Buch: danach weiter zum nächsten Slot merken
            if (!sign.on()) doneSlots |= 1 << slot;
            if (!sign.on() && allDone()) finish();
            return;
        }
        finish();
    }

    private int doneSlots;

    private boolean allDone() {
        var inv = mc.player.getInventory();
        for (int s = 0; s < 9; s++) if (inv.getItem(s).is(Items.WRITABLE_BOOK) && (doneSlots & (1 << s)) == 0) return false;
        return true;
    }

    private void finish() {
        Chat.ok("BookBot: " + written + " Buch/Bücher geschrieben.");
        doneSlots = 0;
        setEnabled(false);
    }

    @Override
    public String info() { return String.valueOf(written); }
}
