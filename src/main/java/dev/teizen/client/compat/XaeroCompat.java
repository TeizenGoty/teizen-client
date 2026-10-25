package dev.teizen.client.compat;

import dev.teizen.client.TeizenClient;
import dev.teizen.client.core.FarPlayers;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;

/**
 * Zeigt die Spieler vom Radar / PlayerFinder auch auf Xaero's Minimap und Xaero's World Map
 * (über deren "Player Tracker"-Schnittstelle, wie es auch Party-Mods machen). Ganz ohne feste Abhängigkeit:
 * ist Xaero nicht installiert, passiert einfach nichts.
 */
public final class XaeroCompat {
    private static boolean triedMinimap, triedMap;
    public static boolean minimapOk, worldMapOk;
    /** Liefert die Spieler, die angezeigt werden sollen (leer = keine). */
    private static Supplier<List<FarPlayers.P>> source = Collections::emptyList;

    private XaeroCompat() {}

    public static void setSource(Supplier<List<FarPlayers.P>> s) { source = s; }

    private static Boolean hasMinimap, hasWorldMap;

    private static boolean classExists(String name) {
        try {
            Class.forName(name, false, XaeroCompat.class.getClassLoader());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean minimapInstalled() {
        if (hasMinimap == null) hasMinimap = FabricLoader.getInstance().isModLoaded("xaerominimap")
                || FabricLoader.getInstance().isModLoaded("xaerobetterpvp") || classExists("xaero.common.IXaeroMinimap");
        return hasMinimap;
    }

    public static boolean worldMapInstalled() {
        if (hasWorldMap == null) hasWorldMap = FabricLoader.getInstance().isModLoaded("xaeroworldmap") || classExists("xaero.map.WorldMap");
        return hasWorldMap;
    }

    /** Jeden Tick im Spiel aufrufen; meldet sich einmal bei Xaero an (sobald Xaero fertig geladen ist). */
    public static void tick() {
        if (!triedMinimap && minimapInstalled()) {
            try {
                minimapOk = registerMinimap();
                triedMinimap = minimapOk;
            } catch (Throwable t) {
                triedMinimap = true;
                TeizenClient.LOG.warn("Xaero Minimap: Anmeldung fehlgeschlagen", t);
            }
        }
        if (!triedMap && worldMapInstalled()) {
            try {
                worldMapOk = registerWorldMap();
                triedMap = worldMapOk;
            } catch (Throwable t) {
                triedMap = true;
                TeizenClient.LOG.warn("Xaero World Map: Anmeldung fehlgeschlagen", t);
            }
        }
    }

    /** Statisches Feld finden, dessen Wert eine Instanz von {@code type} ist (z.B. HudMod.INSTANCE). */
    private static Object staticOfType(Class<?> owner, Class<?> type) throws IllegalAccessException {
        for (Class<?> c = owner; c != null; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers())) continue;
                f.setAccessible(true);
                Object v = f.get(null);
                if (v != null && type.isInstance(v)) return v;
            }
        }
        return null;
    }

    private static Object readerProxy(Class<?> readerIface) {
        return Proxy.newProxyInstance(readerIface.getClassLoader(), new Class<?>[]{readerIface}, (proxy, m, args) -> {
            if (m.getDeclaringClass() == Object.class) return objectMethod(proxy, m, args);
            FarPlayers.P p = (FarPlayers.P) args[0];
            return switch (m.getName()) {
                case "getId" -> p.id();
                case "getX" -> p.x();
                case "getY" -> p.y();
                case "getZ" -> p.z();
                case "getDimension" -> p.dim();
                default -> null;
            };
        });
    }

    private static Object systemProxy(Class<?> systemIface, Object reader) {
        return Proxy.newProxyInstance(systemIface.getClassLoader(), new Class<?>[]{systemIface}, (proxy, m, args) -> {
            if (m.getDeclaringClass() == Object.class) return objectMethod(proxy, m, args);
            return switch (m.getName()) {
                case "getReader" -> reader;
                case "getTrackedPlayerIterator" -> {
                    List<FarPlayers.P> list;
                    try { list = new ArrayList<>(source.get()); } catch (Throwable t) { list = List.of(); }
                    Iterator<FarPlayers.P> it = list.iterator();
                    yield it;
                }
                default -> null;
            };
        });
    }

    private static Object objectMethod(Object proxy, Method m, Object[] args) {
        return switch (m.getName()) {
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            case "toString" -> "TeizenRadar";
            default -> null;
        };
    }

    private static boolean registerMinimap() throws Exception {
        ClassLoader cl = XaeroCompat.class.getClassLoader();
        Class<?> iMinimap = Class.forName("xaero.common.IXaeroMinimap", false, cl);
        Class<?> hudMod = Class.forName("xaero.common.HudMod", false, cl);
        Object mod = staticOfType(hudMod, iMinimap);
        if (mod == null) return false; // noch nicht geladen -> später nochmal
        Object manager = iMinimap.getMethod("getRenderedPlayerTrackerManager").invoke(mod);
        if (manager == null) return false;
        Class<?> sysIface = Class.forName("xaero.hud.minimap.player.tracker.system.IRenderedPlayerTracker", false, cl);
        Class<?> readerIface = Class.forName("xaero.hud.minimap.player.tracker.system.ITrackedPlayerReader", false, cl);
        Object system = systemProxy(sysIface, readerProxy(readerIface));
        manager.getClass().getMethod("register", String.class, sysIface).invoke(manager, "teizen_radar", system);
        TeizenClient.LOG.info("Xaero Minimap: Teizen-Radar angemeldet");
        return true;
    }

    private static boolean registerWorldMap() throws Exception {
        ClassLoader cl = XaeroCompat.class.getClassLoader();
        Class<?> worldMap = Class.forName("xaero.map.WorldMap", false, cl);
        Class<?> managerClass = Class.forName("xaero.map.radar.tracker.system.PlayerTrackerSystemManager", false, cl);
        Object manager = staticOfType(worldMap, managerClass);
        if (manager == null) return false;
        Class<?> sysIface = Class.forName("xaero.map.radar.tracker.system.IPlayerTrackerSystem", false, cl);
        Class<?> readerIface = Class.forName("xaero.map.radar.tracker.system.ITrackedPlayerReader", false, cl);
        Object system = systemProxy(sysIface, readerProxy(readerIface));
        managerClass.getMethod("register", String.class, sysIface).invoke(manager, "teizen_radar", system);
        TeizenClient.LOG.info("Xaero World Map: Teizen-Radar angemeldet");
        return true;
    }
}
