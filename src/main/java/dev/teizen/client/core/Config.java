package dev.teizen.client.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.teizen.client.TeizenClient;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Speichert Einstellungen, Tasten und Freunde in config/teizenclient.json. */
public final class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Version der Datei: bei neuen Standard-Tasten werden alte "keine Taste"-Einträge nicht übernommen. */
    private static final int VERSION = 3;

    /** Funktionen, deren neue Standard-Taste übernommen werden soll (auch in den Minecraft-Optionen). */
    public static final java.util.Set<String> keepDefaultKey = new java.util.HashSet<>();

    private Config() {}

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("teizenclient.json");
    }

    public static void save() {
        try {
            JsonObject root = new JsonObject();
            JsonObject mods = new JsonObject();
            for (Module m : TeizenClient.modules().all()) {
                JsonObject o = new JsonObject();
                o.addProperty("an", m.isEnabled());
                o.addProperty("taste", dev.teizen.client.compat.K.toGlfw(m.key));
                o.addProperty("favorit", m.favorite);
                JsonObject set = new JsonObject();
                for (Map.Entry<String, Setting> e : m.settings.entrySet()) {
                    Setting s = e.getValue();
                    if (s.type == Setting.Type.ACTION) continue;
                    switch (s.type) {
                        case NUMBER -> set.addProperty(e.getKey(), s.num());
                        case BOOL -> set.addProperty(e.getKey(), s.on());
                        default -> set.addProperty(e.getKey(), s.str());
                    }
                }
                o.add("einstellungen", set);
                mods.add(m.name, o);
            }
            root.add("funktionen", mods);
            JsonArray fr = new JsonArray();
            for (String f : Friends.NAMES) fr.add(f);
            root.add("freunde", fr);
            root.addProperty("menuTaste", dev.teizen.client.compat.K.toGlfw(TeizenClient.menuKey));
            root.addProperty("version", VERSION);
            root.addProperty("opAusblenden", ModuleManager.hideOp);
            root.addProperty("hudGroesse", dev.teizen.client.gui.Hud.scale);
            Files.writeString(file(), GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (Exception e) {
            TeizenClient.LOG.warn("Config konnte nicht gespeichert werden", e);
        }
    }

    public static void load() {
        Path f = file();
        if (!Files.exists(f)) return;
        try {
            JsonObject root = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), JsonObject.class);
            if (root == null) return;
            if (root.has("menuTaste")) { int mk = dev.teizen.client.compat.K.fromGlfw(root.get("menuTaste").getAsInt()); if (mk >= 0) TeizenClient.menuKey = mk; }
            if (root.has("opAusblenden")) ModuleManager.hideOp = root.get("opAusblenden").getAsBoolean();
            if (root.has("hudGroesse")) dev.teizen.client.gui.Hud.scale = Math.max(0f, Math.min(2f, root.get("hudGroesse").getAsFloat()));
            if (root.has("freunde")) for (JsonElement e : root.getAsJsonArray("freunde")) Friends.add(e.getAsString());
            if (!root.has("funktionen")) return;
            JsonObject mods = root.getAsJsonObject("funktionen");
            int version = root.has("version") ? root.get("version").getAsInt() : 1;
            for (Module m : TeizenClient.modules().all()) {
                if (!mods.has(m.name)) continue;
                JsonObject o = mods.getAsJsonObject(m.name);
                if (o.has("taste")) {
                    int k = dev.teizen.client.compat.K.fromGlfw(o.get("taste").getAsInt());
                    // neue Standard-Taste (z.B. AutoCrystal = J) nicht durch ein altes "keine Taste" überschreiben
                    if (version < 2 && k == -1 && m.key >= 0) keepDefaultKey.add(m.name.toLowerCase(java.util.Locale.ROOT));
                    else m.key = k;
                }
                if (o.has("favorit")) m.favorite = o.get("favorit").getAsBoolean();
                if (o.has("einstellungen")) {
                    JsonObject set = o.getAsJsonObject("einstellungen");
                    for (Map.Entry<String, Setting> e : m.settings.entrySet()) {
                        if (!set.has(e.getKey())) continue;
                        Setting s = e.getValue();
                        if (s.type == Setting.Type.ACTION) continue;
                        // Version 3: Pfeile sind kleiner geworden – alte große Pfeil-Größe nicht übernehmen
                        if (version < 3 && e.getKey().equals("pfeilgroesse")) continue;
                        switch (s.type) {
                            case NUMBER -> s.setNumber(set.get(e.getKey()).getAsDouble());
                            case BOOL -> s.setBool(set.get(e.getKey()).getAsBoolean());
                            default -> s.setText(set.get(e.getKey()).getAsString());
                        }
                    }
                }
                // Funktionen werden beim Start bewusst NICHT automatisch eingeschaltet (sicherer).
            }
        } catch (Exception e) {
            TeizenClient.LOG.warn("Config konnte nicht geladen werden", e);
        }
    }
}
