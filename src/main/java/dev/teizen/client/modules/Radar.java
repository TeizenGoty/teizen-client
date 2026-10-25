package dev.teizen.client.modules;

import dev.teizen.client.compat.XaeroCompat;
import dev.teizen.client.core.FarPlayers;
import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.util.Chat;

import java.util.ArrayList;
import java.util.List;

/**
 * Radar: zeigt ALLE Spieler auf Xaero's Minimap und Xaero's World Map – auch die, die weit weg sind
 * (eigene Welt direkt, auf Servern per /data mit OP). Geht nur, wenn Xaero's Minimap oder World Map installiert ist.
 */
public final class Radar extends Module {
    private final Setting far = add(Setting.bool("weit", "Auch weit entfernte Spieler auf der Karte (eigene Welt direkt, Server: OP)", true));

    public Radar() {
        super("Radar", Category.SICHT, "Alle Spieler auf Xaero's Minimap/World Map (braucht Xaero).");
        XaeroCompat.setSource(() -> {
            if (!isEnabled() || !far.on() || mc.player == null) return List.of();
            return new ArrayList<>(FarPlayers.all());
        });
    }

    /** Ist Xaero's Minimap oder World Map installiert? */
    public static boolean xaero() {
        return XaeroCompat.minimapInstalled() || XaeroCompat.worldMapInstalled();
    }

    @Override
    public String requirement() { return xaero() ? null : "braucht Xaero's Minimap"; }

    @Override
    protected void onEnable() {
        if (!xaero()) {
            Chat.error("Radar braucht Xaero's Minimap oder Xaero's World Map (bei Modrinth/CurseForge herunterladen und in den mods-Ordner).");
            setEnabled(false);
        }
    }

    @Override
    public void onTick() {
        if (!inGame()) return;
        if (far.on()) FarPlayers.want();
        XaeroCompat.tick();
    }

    @Override
    public String info() {
        int n = FarPlayers.all().size();
        return n > 0 ? n + " weit" : "";
    }
}
