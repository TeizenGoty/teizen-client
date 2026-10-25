package dev.teizen.client.compat;

import dev.teizen.client.TeizenClient;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;

import java.lang.reflect.Method;
import java.util.Locale;

/**
 * Seed-Karten verbinden:
 * - SeedMapper (Mod, Karte im Spiel: /sm:seedmap)
 * - Seed Atlas for Xaero's World Map (Strukturen direkt auf Xaero's Weltkarte)
 * - sonst Chunkbase Seed Map im Browser.
 * Der Seed wird automatisch an die Mods übergeben.
 */
public final class SeedMapCompat {
    private static final Minecraft mc = Minecraft.getInstance();
    private static Method execute;
    private static boolean lookedUp;
    /** Welcher Seed zuletzt an die Mods gegeben wurde. */
    private static Long givenSeedMapper, givenAtlas;
    /** Für den Selbsttest: zuletzt gebaute Chunkbase-Adresse. */
    public static String lastUrl = "";

    private SeedMapCompat() {}

    public static boolean seedMapper() { return FabricLoader.getInstance().isModLoaded("seedmapper"); }

    public static boolean seedAtlas() { return FabricLoader.getInstance().isModLoaded("seedatlas_xaero"); }

    /** Befehl eines Client-Mods ausführen (ohne "/"). @return true wenn ein Client-Befehl das übernommen hat */
    public static boolean clientCommand(String command) {
        if (!lookedUp) {
            lookedUp = true;
            try {
                Class<?> c = Class.forName("net.fabricmc.fabric.impl.command.client.ClientCommandInternals");
                execute = c.getMethod("executeCommand", String.class);
            } catch (Throwable t) {
                TeizenClient.LOG.warn("Client-Befehle: Fabric-Schnittstelle nicht gefunden, nehme den Chat-Weg", t);
            }
        }
        try {
            if (execute != null) return (Boolean) execute.invoke(null, command);
        } catch (Throwable t) {
            TeizenClient.LOG.warn("Client-Befehl fehlgeschlagen: " + command, t);
        }
        if (mc.player != null) mc.player.connection.sendCommand(command);
        return false;
    }

    /** Seed an alle installierten Karten-Mods geben (nur wenn er sich geändert hat, außer force). */
    public static void giveSeed(long seed, boolean force) {
        if (seedMapper() && (force || givenSeedMapper == null || givenSeedMapper != seed)) {
            if (clientCommand("sm:config Seed set " + seed)) givenSeedMapper = seed;
        }
        if (seedAtlas() && (force || givenAtlas == null || givenAtlas != seed)) {
            if (clientCommand("seedatlas seed " + seed)) givenAtlas = seed;
        }
    }

    public static Long givenSeedMapper() { return givenSeedMapper; }

    public static Long givenAtlas() { return givenAtlas; }

    /** Chunkbase Seed Map mit Seed und deiner Position. */
    public static String chunkbaseUrl(long seed) {
        String dim = "overworld";
        int x = 0, z = 0;
        if (mc.player != null && mc.level != null) {
            if (mc.level.dimension() == Level.NETHER) dim = "nether";
            else if (mc.level.dimension() == Level.END) dim = "end";
            x = mc.player.blockPosition().getX();
            z = mc.player.blockPosition().getZ();
        }
        return String.format(Locale.ROOT, "https://www.chunkbase.com/apps/seed-map#seed=%d&dimension=%s&x=%d&z=%d&zoom=0.5", seed, dim, x, z);
    }

    /**
     * Karte öffnen. modus: auto / seedmapper / seedatlas / chunkbase.
     * @return was geöffnet wurde (für die Chat-Meldung)
     */
    public static String open(long seed, String mode) {
        giveSeed(seed, false);
        boolean auto = mode.equals("auto");
        if ((auto || mode.equals("seedmapper")) && seedMapper()) {
            clientCommand("sm:seedmap");
            return "SeedMapper-Karte";
        }
        if ((auto || mode.equals("seedatlas")) && seedAtlas()) {
            return "Xaero's Weltkarte (Seed Atlas) – drück die Karten-Taste (Standard M)";
        }
        String url = chunkbaseUrl(seed);
        lastUrl = url;
        try {
            net.minecraft.util.Util.getPlatform().openUri(url);
        } catch (Throwable t) {
            TeizenClient.LOG.warn("Browser konnte nicht geöffnet werden", t);
            return "Chunkbase (Adresse im Chat)";
        }
        return "Chunkbase Seed Map im Browser";
    }
}
