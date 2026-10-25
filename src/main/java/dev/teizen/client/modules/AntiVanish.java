package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.util.Chat;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * AntiVanish (wie bei Anubis): zeigt dir, wenn dir jemand heimlich zuschaut.
 * Erkannt wird:
 *  - Zuschauer: ein Spieler steht in der Tab-Liste im Zuschauer-Modus (schaut meist jemandem zu).
 *  - Versteckt: ein Spieler ist in deiner Nähe zu sehen, steht aber nicht (mehr) in der Tab-Liste (typisches Vanish von Moderatoren).
 * Bei einem neuen Beobachter kommt eine Warnung im Chat und ein Ton; oben rechts steht eine Liste.
 */
public final class AntiVanish extends Module {
    private final Setting spectators = add(Setting.bool("zuschauer", "Zuschauer melden (jemand im Zuschauer-Modus)", true));
    private final Setting hidden = add(Setting.bool("versteckt", "Versteckte melden (in der Nähe, aber nicht in der Tab-Liste)", true));
    private final Setting chat = add(Setting.bool("chat", "Warnung im Chat, wenn ein neuer Beobachter auftaucht", true));
    private final Setting sound = add(Setting.bool("ton", "Warnton bei einem neuen Beobachter", true));
    private final Setting list = add(Setting.bool("liste", "Liste oben rechts anzeigen", true));

    public record Watcher(String name, String kind) {}

    /** Aktuell erkannte Beobachter (für HUD und Selbsttest). */
    public final List<Watcher> watchers = new ArrayList<>();
    /** Schon gemeldete Namen (damit nicht jede Sekunde gewarnt wird). */
    private final Set<String> announced = new HashSet<>();

    public AntiVanish() {
        super("AntiVanish", Category.SICHT, "Zeigt dir, wenn dir jemand heimlich zuschaut (Zuschauer oder versteckt).");
    }

    @Override
    protected void onEnable() {
        watchers.clear();
        announced.clear();
    }

    @Override
    public void onTick() {
        if (!inGame() || mc.player.tickCount % 10 != 0) return;
        ClientPacketListener con = mc.getConnection();
        if (con == null) return;
        UUID me = mc.player.getUUID();

        Map<String, Watcher> now = new LinkedHashMap<>();
        if (spectators.on()) {
            for (PlayerInfo info : con.getOnlinePlayers()) {
                if (info.getProfile().id().equals(me)) continue;
                if (info.getGameMode() == GameType.SPECTATOR) now.putIfAbsent(info.getProfile().name(), new Watcher(info.getProfile().name(), "Zuschauer"));
            }
        }
        if (hidden.on()) {
            Set<UUID> listed = new HashSet<>();
            for (PlayerInfo info : con.getListedOnlinePlayers()) listed.add(info.getProfile().id());
            for (Player p : mc.level.players()) {
                if (p == mc.player) continue;
                if (!listed.contains(p.getUUID())) {
                    String n = p.getGameProfile().name();
                    now.putIfAbsent(n, new Watcher(n, "versteckt"));
                }
            }
        }

        // neue Beobachter melden
        for (Watcher w : now.values()) {
            if (announced.add(w.name())) {
                if (chat.on()) Chat.info("§c⚠ §f" + w.name() + "§7 " + (w.kind().equals("Zuschauer")
                        ? "schaut dir gerade zu (Zuschauer-Modus)." : "ist versteckt in deiner Nähe (nicht in der Tab-Liste)."));
                if (sound.on()) mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 0.7f, 1.4f);
            }
        }
        announced.retainAll(now.keySet());
        watchers.clear();
        watchers.addAll(now.values());
    }

    @Override
    public void onHud(GuiGraphicsExtractor g, float partial) {
        if (!list.on() || watchers.isEmpty()) return;
        String head = "§c👁 Beobachter:";
        int y = 2;
        int x = g.guiWidth() - 2;
        drawRight(g, head, x, y);
        y += 11;
        for (Watcher w : watchers) {
            drawRight(g, "§f" + w.name() + " §7(" + w.kind() + ")", x, y);
            y += 10;
        }
    }

    private void drawRight(GuiGraphicsExtractor g, String text, int right, int y) {
        int w = mc.font.width(text);
        g.fill(right - w - 3, y - 1, right + 1, y + 9, 0x90000000);
        g.text(mc.font, text, right - w - 1, y, 0xFFFFFFFF, true);
    }

    @Override
    public String info() { return watchers.isEmpty() ? "" : "§c" + watchers.size(); }
}
