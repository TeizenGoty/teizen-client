package dev.teizen.client.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.teizen.client.TeizenClient;
import dev.teizen.client.util.Chat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Profile: mit einem Klick mehrere Cheats an (z.B. PvP, Farmen, Bauen).
 * Eigene Profile speichern die gerade eingeschalteten Cheats samt ihren Einstellungen.
 * Profil aus = nur das wieder ausschalten, was das Profil eingeschaltet hat, und alte Einstellungen zurück.
 */
public final class Profiles {
    private Profiles() {}

    public record Profile(String name, List<String> modules, Map<String, Map<String, String>> settings, boolean builtIn) {}

    private static final Map<String, Profile> ALL = new LinkedHashMap<>();
    /** Gerade aktives Profil (oder null). */
    public static String active;
    private static final Set<String> turnedOn = new LinkedHashSet<>();
    private static final Map<String, Map<String, String>> oldSettings = new HashMap<>();

    static {
        builtIn("PvP", "KillAura", "Reach", "AimAssist", "TriggerBot", "Criticals", "AutoTotem", "AutoArmor", "AutoShield", "AntiKnockback", "Velocity");
        builtIn("Farmen", "AutoReplant", "AutoFish", "AutoEat", "AutoTool", "AutoReplenish");
        builtIn("Bauen", "Scaffold", "FastPlace", "AirPlace", "AutoReplenish", "SafeWalk");
        builtIn("Abbauen", "AutoTool", "FastBreak", "XRay", "AutoEat");
        builtIn("Erkunden", "PlayerESP", "StorageESP", "Fullbright", "StructureFinder", "AutoSprint", "Radar");
    }

    private static void builtIn(String name, String... mods) {
        ALL.put(key(name), new Profile(name, List.of(mods), Map.of(), true));
    }

    private static String key(String n) { return n.toLowerCase(Locale.ROOT); }

    public static List<Profile> all() { return new ArrayList<>(ALL.values()); }

    public static Profile get(String name) { return name == null ? null : ALL.get(key(name)); }

    public static boolean isActive(Profile p) { return active != null && key(active).equals(key(p.name())); }

    /** Profil an/aus. */
    public static void toggle(String name) {
        Profile p = get(name);
        if (p == null) { Chat.error("Kein Profil \"" + name + "\". Alle: ///profil"); return; }
        if (isActive(p)) { off(true); return; }
        if (active != null) off(false);
        ModuleManager mm = TeizenClient.modules();
        turnedOn.clear();
        oldSettings.clear();
        int n = 0;
        for (String mn : p.modules()) {
            Module m = mm.byName(mn);
            if (m == null || !ModuleManager.shown(m)) continue;
            Map<String, String> want = p.settings().get(m.name);
            if (want != null) {
                Map<String, String> old = new HashMap<>();
                for (Map.Entry<String, String> e : want.entrySet()) {
                    Setting s = m.setting(e.getKey());
                    if (s == null) continue;
                    old.put(e.getKey(), value(s));
                    set(s, e.getValue());
                }
                oldSettings.put(m.name, old);
            }
            if (!m.isEnabled()) {
                m.setEnabled(true);
                if (m.isEnabled()) { turnedOn.add(m.name); n++; }
            }
        }
        active = p.name();
        Config.save();
        Chat.ok("Profil §f" + p.name() + "§a an – " + n + " Cheats dazu eingeschaltet.");
    }

    /** Aktives Profil aus: eingeschaltete Cheats aus, alte Einstellungen zurück. */
    public static void off(boolean say) {
        if (active == null) { if (say) Chat.info("Kein Profil an."); return; }
        ModuleManager mm = TeizenClient.modules();
        for (String mn : turnedOn) {
            Module m = mm.byName(mn);
            if (m != null && m.isEnabled()) m.setEnabled(false);
        }
        for (Map.Entry<String, Map<String, String>> e : oldSettings.entrySet()) {
            Module m = mm.byName(e.getKey());
            if (m == null) continue;
            for (Map.Entry<String, String> s : e.getValue().entrySet()) {
                Setting st = m.setting(s.getKey());
                if (st != null) set(st, s.getValue());
            }
        }
        String was = active;
        active = null;
        turnedOn.clear();
        oldSettings.clear();
        Config.save();
        if (say) Chat.ok("Profil §f" + was + "§a aus.");
    }

    /** Eigenes Profil aus den gerade eingeschalteten Cheats (mit Einstellungen) speichern. */
    public static boolean save(String name) {
        if (name == null || name.isBlank() || !name.matches("[A-Za-z0-9_äöüÄÖÜß-]{1,20}")) {
            Chat.error("Name: nur Buchstaben/Zahlen, höchstens 20 Zeichen, z.B. ///profil speichern mein_pvp");
            return false;
        }
        Profile old = get(name);
        if (old != null && old.builtIn()) { Chat.error("\"" + old.name() + "\" ist ein fertiges Profil – nimm einen anderen Namen."); return false; }
        List<String> mods = new ArrayList<>();
        Map<String, Map<String, String>> settings = new LinkedHashMap<>();
        for (Module m : TeizenClient.modules().all()) {
            if (!m.isEnabled() || m instanceof dev.teizen.client.modules.PanicButton) continue;
            mods.add(m.name);
            Map<String, String> s = new LinkedHashMap<>();
            for (Map.Entry<String, Setting> e : m.settings.entrySet()) {
                if (e.getValue().type == Setting.Type.ACTION) continue;
                s.put(e.getKey(), value(e.getValue()));
            }
            if (!s.isEmpty()) settings.put(m.name, s);
        }
        if (mods.isEmpty()) { Chat.error("Es ist gerade kein Cheat an. Erst die Cheats einschalten, dann speichern."); return false; }
        ALL.put(key(name), new Profile(name, mods, settings, false));
        Config.save();
        Chat.ok("Profil §f" + name + "§a gespeichert (" + mods.size() + " Cheats): §7" + String.join(", ", mods));
        return true;
    }

    public static boolean delete(String name) {
        Profile p = get(name);
        if (p == null) { Chat.error("Kein Profil \"" + name + "\"."); return false; }
        if (p.builtIn()) { Chat.error("Fertige Profile kann man nicht löschen."); return false; }
        if (isActive(p)) off(false);
        ALL.remove(key(name));
        Config.save();
        Chat.ok("Profil §f" + p.name() + "§a gelöscht.");
        return true;
    }

    public static void list() {
        StringBuilder sb = new StringBuilder("§7Profile: ");
        for (Profile p : ALL.values()) sb.append(isActive(p) ? "§a§l" : (p.builtIn() ? "§f" : "§d")).append(p.name()).append("§7, ");
        Chat.info(sb.substring(0, sb.length() - 2));
        Chat.info("§d///profil <Name> §7an/aus · §d///profil speichern <Name> §7(jetzige Cheats) · §d///profil loeschen <Name> §7· §d///profil aus");
    }

    private static String value(Setting s) {
        return switch (s.type) {
            case NUMBER -> Double.toString(s.num());
            case BOOL -> Boolean.toString(s.on());
            default -> s.str();
        };
    }

    private static void set(Setting s, String v) {
        try {
            switch (s.type) {
                case NUMBER -> s.setNumber(Double.parseDouble(v));
                case BOOL -> s.setBool(Boolean.parseBoolean(v));
                case ACTION -> { }
                default -> s.setText(v);
            }
        } catch (RuntimeException ignored) {}
    }

    // ------------------------------------------------------------ Config

    public static JsonObject toJson() {
        JsonObject root = new JsonObject();
        JsonObject own = new JsonObject();
        for (Profile p : ALL.values()) {
            if (p.builtIn()) continue;
            JsonObject o = new JsonObject();
            o.addProperty("name", p.name());
            JsonArray mods = new JsonArray();
            for (String m : p.modules()) mods.add(m);
            o.add("cheats", mods);
            JsonObject set = new JsonObject();
            for (Map.Entry<String, Map<String, String>> e : p.settings().entrySet()) {
                JsonObject s = new JsonObject();
                for (Map.Entry<String, String> v : e.getValue().entrySet()) s.addProperty(v.getKey(), v.getValue());
                set.add(e.getKey(), s);
            }
            o.add("einstellungen", set);
            own.add(key(p.name()), o);
        }
        root.add("eigene", own);
        return root;
    }

    public static void fromJson(JsonObject root) {
        if (root == null || !root.has("eigene")) return;
        for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("eigene").entrySet()) {
            try {
                JsonObject o = e.getValue().getAsJsonObject();
                String name = o.get("name").getAsString();
                if (get(name) != null && get(name).builtIn()) continue;
                List<String> mods = new ArrayList<>();
                for (JsonElement m : o.getAsJsonArray("cheats")) mods.add(m.getAsString());
                Map<String, Map<String, String>> settings = new LinkedHashMap<>();
                if (o.has("einstellungen")) {
                    for (Map.Entry<String, JsonElement> s : o.getAsJsonObject("einstellungen").entrySet()) {
                        Map<String, String> v = new LinkedHashMap<>();
                        for (Map.Entry<String, JsonElement> x : s.getValue().getAsJsonObject().entrySet()) v.put(x.getKey(), x.getValue().getAsString());
                        settings.put(s.getKey(), v);
                    }
                }
                ALL.put(key(name), new Profile(name, mods, settings, false));
            } catch (RuntimeException ex) {
                TeizenClient.LOG.warn("Profil konnte nicht geladen werden: " + e.getKey(), ex);
            }
        }
    }
}
