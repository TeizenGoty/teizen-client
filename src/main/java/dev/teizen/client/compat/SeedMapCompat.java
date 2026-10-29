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
    /** Welcher Seed zuletzt an die Mods gegeben wurde. */
    private static Long givenSeedMapper, givenAtlas;
    /** Für den Selbsttest: zuletzt gebaute Chunkbase-Adresse. */
    public static String lastUrl = "";

    private SeedMapCompat() {}

    public static boolean seedMapper() { return FabricLoader.getInstance().isModLoaded("seedmapper"); }

    public static boolean seedAtlas() { return FabricLoader.getInstance().isModLoaded("seedatlas_xaero"); }

    /** Befehl eines Client-Mods ausführen (ohne "/"); Fabric fängt Client-Befehle beim Senden selbst ab. */
    public static boolean clientCommand(String command) {
        if (mc.player == null) return false;
        mc.player.connection.sendCommand(command);
        return true;
    }

    /**
     * Client-Befehl direkt ausführen (z.B. "sm:locate feature structure village").
     * @return null wenn er lief, sonst die Fehlermeldung (z.B. "Couldn't locate structure ...")
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static String runClientCommand(String command) {
        try {
            // Fabric API: neu "ClientCommands", alt "ClientCommandManager"
            com.mojang.brigadier.CommandDispatcher d = null;
            for (String cn : new String[]{"net.fabricmc.fabric.api.client.command.v2.ClientCommands",
                    "net.fabricmc.fabric.api.client.command.v2.ClientCommandManager"}) {
                try {
                    d = (com.mojang.brigadier.CommandDispatcher) Class.forName(cn).getMethod("getActiveDispatcher").invoke(null);
                    if (d != null) break;
                } catch (ClassNotFoundException | NoSuchMethodException ignored) {}
            }
            if (d == null || mc.getConnection() == null) return "Client-Befehle nicht bereit";
            Object src = mc.getConnection().getSuggestionsProvider();
            d.execute(command, src);
            return null;
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            return e.getRawMessage().getString();
        } catch (java.lang.reflect.InvocationTargetException e) {
            return String.valueOf(e.getCause());
        } catch (Throwable t) {
            return t.toString();
        }
    }

    /** Unsere Struktur-Namen -> Seed-Atlas-Markierungen. */
    private static String atlasMarker(String name) {
        return switch (name) {
            case "village" -> "VILLAGE";
            case "stronghold" -> "STRONGHOLD";
            case "fortress" -> "NETHER_FORTRESS";
            case "bastion" -> "BASTION";
            case "monument" -> "OCEAN_MONUMENT";
            case "mansion" -> "WOODLAND_MANSION";
            case "ancient_city" -> "ANCIENT_CITY";
            case "end_city" -> "END_CITY";
            case "trial_chambers" -> "TRIAL_CHAMBERS";
            case "desert_pyramid" -> "DESERT_PYRAMID";
            case "jungle_pyramid" -> "JUNGLE_TEMPLE";
            case "igloo" -> "IGLOO";
            case "swamp_hut" -> "SWAMP_HUT";
            case "pillager_outpost" -> "PILLAGER_OUTPOST";
            case "ruined_portal" -> "RUINED_PORTAL";
            case "shipwreck" -> "SHIPWRECK";
            case "ocean_ruin" -> "OCEAN_RUINS";
            case "mineshaft" -> "MINESHAFT";
            case "buried_treasure" -> "BURIED_TREASURE";
            case "trail_ruins" -> "TRAIL_RUINS";
            default -> null;
        };
    }

    /** Seed Atlas: Karten-Ebene und Struktur-Markierungen einschalten (die gewählten Strukturen auf jeden Fall). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static boolean atlasShowStructures(java.util.List<String> selected) {
        if (!seedAtlas()) return false;
        try {
            Class<?> st = Class.forName("org.seedatlas.xaero.config.SeedAtlasClientState");
            st.getMethod("setLayerEnabled", boolean.class).invoke(null, true);
            st.getMethod("setStructuresEnabled", boolean.class).invoke(null, true);
            Class mt = Class.forName("org.seedatlas.xaero.config.MarkerType");
            Method setMarker = st.getMethod("setMarkerEnabled", String.class, boolean.class);
            for (String n : selected) {
                String e = atlasMarker(n);
                if (e == null) continue;
                Object m = Enum.valueOf(mt, e);
                String id = (String) mt.getMethod("id").invoke(m);
                setMarker.invoke(null, id, true);
            }
            return true;
        } catch (Throwable t) {
            TeizenClient.LOG.warn("Seed Atlas: Strukturen einschalten ging nicht", t);
            return false;
        }
    }

    /** Sind bei Seed Atlas die Strukturen an? (Selbsttest) */
    public static boolean atlasStructuresOn() {
        try {
            Class<?> st = Class.forName("org.seedatlas.xaero.config.SeedAtlasClientState");
            Object cfg = st.getMethod("config").invoke(null);
            return (Boolean) cfg.getClass().getMethod("structuresEnabled").invoke(cfg);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Xaero's Weltkarte öffnen (wie ein Druck auf ihre Taste). @return true wenn die Taste gefunden wurde */
    public static boolean openXaeroWorldMap() {
        try {
            for (net.minecraft.client.KeyMapping k : mc.options.keyMappings) {
                String n = k.getName().toLowerCase(Locale.ROOT);
                if (n.contains("xaero") && n.contains("map") && !n.contains("minimap") && (n.contains("open") || n.endsWith("world_map") || n.endsWith("worldmap"))) {
                    java.lang.reflect.Field f = net.minecraft.client.KeyMapping.class.getDeclaredField("clickCount");
                    f.setAccessible(true);
                    f.setInt(k, f.getInt(k) + 1);
                    TeizenClient.LOG.info("Xaero-Weltkarte über Taste " + k.getName() + " geöffnet");
                    return true;
                }
            }
        } catch (Throwable t) {
            TeizenClient.LOG.warn("Xaero-Weltkarte öffnen ging nicht", t);
        }
        return false;
    }

    /** Namen aller Xaero-Tasten (Selbsttest-Log). */
    public static String xaeroKeys() {
        StringBuilder sb = new StringBuilder();
        for (net.minecraft.client.KeyMapping k : mc.options.keyMappings) if (k.getName().toLowerCase(Locale.ROOT).contains("xaero")) sb.append(k.getName()).append(' ');
        return sb.toString();
    }

    /** Seed an alle installierten Karten-Mods geben (nur wenn er sich geändert hat, außer force). */
    public static void giveSeed(long seed, boolean force) {
        giveSeed(seed, force, java.util.List.of());
    }

    /** Seed geben; bei Seed Atlas auch gleich die Strukturen einschalten. */
    public static void giveSeed(long seed, boolean force, java.util.List<String> structures) {
        if (seedMapper() && (force || givenSeedMapper == null || givenSeedMapper != seed)) {
            if (clientCommand("sm:config Seed set " + seed)) givenSeedMapper = seed;
        }
        if (seedAtlas() && (force || givenAtlas == null || givenAtlas != seed)) {
            if (clientCommand("seedatlas seed " + seed)) givenAtlas = seed;
        }
        if (seedAtlas()) atlasShowStructures(structures);
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
    public static String open(long seed, String mode, java.util.List<String> structures) {
        giveSeed(seed, false, structures);
        boolean auto = mode.equals("auto");
        if ((auto || mode.equals("seedmapper")) && seedMapper()) {
            clientCommand("sm:seedmap");
            return "SeedMapper-Karte";
        }
        if ((auto || mode.equals("seedatlas")) && seedAtlas()) {
            if (openXaeroWorldMap()) return "Xaero's Weltkarte mit allen Strukturen (Seed Atlas)";
            return "Xaero's Weltkarte (Seed Atlas) – drück die Karten-Taste (Standard M)";
        }
        String url = chunkbaseUrl(seed);
        lastUrl = url;
        // Chunkbase kennt die neuen Versionen (26.x) noch nicht – nur bis ~1.21. Für die richtige Version: SeedMapper oder Seed Atlas.
        dev.teizen.client.util.Chat.info("§eChunkbase geht nur bis ~1.21 – für " + dev.teizen.client.compat.V.MC
                + " stimmen die Strukturen dort evtl. nicht. Installier §fSeedMapper§e oder §fSeed Atlas§e (zeigt die Karte im Spiel, richtige Version).");
        try {
            dev.teizen.client.util.Browser.open(url);
        } catch (Throwable t) {
            TeizenClient.LOG.warn("Browser konnte nicht geöffnet werden", t);
            return "Chunkbase (Adresse im Chat)";
        }
        return "Chunkbase Seed Map im Browser (nur bis ~1.21)";
    }
}
