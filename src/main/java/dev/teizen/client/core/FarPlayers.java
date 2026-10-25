package dev.teizen.client.core;

import dev.teizen.client.util.ServerQuery;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Wo sind die Spieler, die zu weit weg sind, um sie normal zu sehen?
 * Eigene Welt / LAN: direkt vom eingebauten Server. Server: per /data (braucht OP).
 * Wird von PlayerFinder, Radar und der Xaero-Minimap benutzt.
 */
public final class FarPlayers {
    private static final Minecraft mc = Minecraft.getInstance();
    private static final Pattern NUM = Pattern.compile("-?\\d+(?:\\.\\d+)?(?:[eE]-?\\d+)?");

    public record P(UUID id, String name, double x, double y, double z, ResourceKey<Level> dim, int tick) {}

    private static volatile Map<UUID, P> data = new HashMap<>();
    private static final Map<UUID, double[]> pendingPos = new HashMap<>();
    private static int ticks, lastWanted = -1000, lastUpdate = -1000;

    private FarPlayers() {}

    /** Ein Modul braucht die Daten (sonst wird nichts abgefragt). */
    public static void want() { lastWanted = ticks; }

    /** Alle bekannten weit entfernten Spieler (ohne dich, ohne die, die du sowieso siehst). */
    public static Collection<P> all() {
        List<P> out = new ArrayList<>();
        for (P p : data.values()) {
            if (mc.level != null && mc.level.getPlayerByUUID(p.id()) != null) continue; // sieht man normal
            out.add(p);
        }
        return out;
    }

    public static String dimName(ResourceKey<Level> d) {
        if (d == null) return "?";
        if (d == Level.OVERWORLD) return "Oberwelt";
        if (d == Level.NETHER) return "Nether";
        if (d == Level.END) return "End";
        return d.identifier().getPath();
    }

    public static void tick() {
        ticks++;
        if (mc.player == null || mc.level == null) { data = new HashMap<>(); return; }
        if (ticks - lastWanted > 40) return;
        if (ticks - lastUpdate < 20) return;
        lastUpdate = ticks;
        IntegratedServer server = mc.getSingleplayerServer();
        UUID me = mc.player.getUUID();
        if (server != null) {
            final int now = ticks;
            server.execute(() -> {
                Map<UUID, P> m = new HashMap<>();
                for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
                    if (sp.getUUID().equals(me)) continue;
                    m.put(sp.getUUID(), new P(sp.getUUID(), sp.getGameProfile().name(), sp.getX(), sp.getY(), sp.getZ(), sp.level().dimension(), now));
                }
                mc.execute(() -> data = m);
            });
            return;
        }
        if (!ServerQuery.available() || ServerQuery.busy() > 0 || ticks - lastUpdate < 0) return;
        if (ticks % 40 >= 20) return; // alle 2 Sekunden
        // alte Einträge wegwerfen
        Map<UUID, P> m = new HashMap<>(data);
        m.values().removeIf(p -> ticks - p.tick() > 200);
        data = m;
        int asked = 0;
        for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
            UUID id = info.getProfile().id();
            if (id.equals(me) || mc.level.getPlayerByUUID(id) != null) continue;
            if (asked++ >= 6) break;
            String name = info.getProfile().name();
            ServerQuery.data(name, "Pos", txt -> {
                Matcher mm = NUM.matcher(txt);
                double[] v = new double[3];
                for (int i = 0; i < 3; i++) { if (!mm.find()) return; v[i] = Double.parseDouble(mm.group()); }
                pendingPos.put(id, v);
            }, null);
            ServerQuery.data(name, "Dimension", txt -> {
                double[] v = pendingPos.remove(id);
                if (v == null) return;
                String d = txt.replace("\"", "").trim();
                ResourceKey<Level> dim;
                try {
                    Identifier rid = Identifier.tryParse(d);
                    dim = rid == null ? mc.level.dimension() : ResourceKey.create(Registries.DIMENSION, rid);
                } catch (Throwable t) {
                    dim = mc.level.dimension();
                }
                if (Level.OVERWORLD.identifier().equals(dim.identifier())) dim = Level.OVERWORLD;
                else if (Level.NETHER.identifier().equals(dim.identifier())) dim = Level.NETHER;
                else if (Level.END.identifier().equals(dim.identifier())) dim = Level.END;
                Map<UUID, P> n = new HashMap<>(data);
                n.put(id, new P(id, name, v[0], v[1], v[2], dim, ticks));
                data = n;
            }, () -> pendingPos.remove(id));
        }
    }
}
