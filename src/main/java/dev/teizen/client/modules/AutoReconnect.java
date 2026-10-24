package dev.teizen.client.modules;

import dev.teizen.client.TeizenClient;
import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

/** Verbindet sich nach einem Rauswurf / Verbindungsabbruch automatisch wieder mit dem Server. */
public final class AutoReconnect extends Module {
    private final Setting delay = add(Setting.number("sekunden", "Wartezeit vor dem Neu-Verbinden (Sekunden)", 5, 1, 120));
    private final Setting attempts = add(Setting.number("versuche", "Wie oft hintereinander versucht wird (0 = immer)", 0, 0, 100));

    private static ServerData last;
    private long disconnectedAt = -1;
    private int tries;
    public int reconnects;

    public AutoReconnect() {
        super("AutoReconnect", Category.SONSTIGES, "Verbindet sich nach einem Rauswurf automatisch neu.");
    }

    /** Einmal anmelden: Countdown auf dem "Verbindung getrennt"-Bildschirm. */
    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof DisconnectedScreen)) return;
            ScreenEvents.afterExtract(screen).register((s, g, mx, my, delta) -> {
                AutoReconnect ar = TeizenClient.modules().get(AutoReconnect.class);
                if (ar == null || !ar.isEnabled() || last == null) return;
                String t = ar.statusText();
                if (t == null) return;
                g.centeredText(client.font, t, s.width / 2, 10, 0xFFFFFFFF);
            });
        });
    }

    private String statusText() {
        if (disconnectedAt < 0) return null;
        if (attempts.integer() > 0 && tries >= attempts.integer()) return "§cAutoReconnect: keine Versuche mehr übrig";
        long left = Math.max(0, delay.integer() * 1000L - (System.currentTimeMillis() - disconnectedAt));
        return "§dAutoReconnect: §fneu verbinden in " + (left + 999) / 1000 + " s §7(" + last.ip + ")";
    }

    /** Läuft jeden Tick – auch wenn man gerade nicht in einer Welt ist. */
    public void globalTick(Minecraft mc) {
        if (mc.player != null && mc.level != null) {
            ServerData cur = mc.getCurrentServer();
            if (cur != null && !cur.isRealm()) last = cur;
            disconnectedAt = -1;
            tries = 0;
            return;
        }
        if (!isEnabled() || last == null) { disconnectedAt = -1; return; }
        if (!(mc.gui.screen() instanceof DisconnectedScreen)) { disconnectedAt = -1; return; }
        if (attempts.integer() > 0 && tries >= attempts.integer()) return;
        long now = System.currentTimeMillis();
        if (disconnectedAt < 0) { disconnectedAt = now; return; }
        if (now - disconnectedAt < delay.integer() * 1000L) return;
        tries++;
        reconnects++;
        disconnectedAt = -1;
        ServerData target = last;
        ConnectScreen.startConnecting(new JoinMultiplayerScreen(new TitleScreen()), mc, ServerAddress.parseString(target.ip), target, false, null);
    }

    @Override
    public String info() { return Setting.fmt(delay.num()) + "s"; }
}
