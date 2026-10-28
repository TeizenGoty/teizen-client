package dev.teizen.client.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HUD-Editor: jedes HUD-Teil kann mit der Maus verschoben werden. Gespeichert wird nur die Verschiebung (dx, dy)
 * vom Standard-Platz. Die Teile melden beim Zeichnen ihren Rahmen (bounds), damit der Editor sie zeigen kann.
 */
public final class HudLayout {
    private HudLayout() {}

    public record Box(int x1, int y1, int x2, int y2) {
        public int w() { return x2 - x1; }
        public int h() { return y2 - y1; }
        public Box move(int dx, int dy) { return new Box(x1 + dx, y1 + dy, x2 + dx, y2 + dy); }
    }

    /** Alle verschiebbaren Teile (Name im Editor). */
    public static final Map<String, String> NAMES = new LinkedHashMap<>();
    static {
        NAMES.put("liste", "Cheat-Liste");
        NAMES.put("logo", "Teizen-Schrift");
        NAMES.put("seed", "Seed-Fortschritt");
        NAMES.put("zaehler", "Spieler im Blick");
        NAMES.put("spielerliste", "Spieler-Liste");
        NAMES.put("beobachter", "Beobachter (AntiVanish)");
        NAMES.put("effekte", "Effekte (PotionHUD)");
    }

    private static final Map<String, int[]> OFFSETS = new HashMap<>();
    private static final Map<String, Box> BOUNDS = new HashMap<>();
    private static final Map<String, Long> SEEN = new HashMap<>();
    private static long frame;

    /** Jeden Frame einmal aufrufen (Hud.render). */
    public static void frame() { frame++; }

    /** Wurde das Teil gerade gezeichnet? */
    public static boolean visible(String id) {
        Long f = SEEN.get(id);
        return f != null && frame - f <= 3;
    }

    /** Standard-Rahmen (ohne Verschiebung), zuletzt gemeldet – oder ein Platzhalter. */
    public static Box bounds(String id, int sw, int sh) {
        Box b = BOUNDS.get(id);
        if (b != null) return b;
        return switch (id) {
            case "liste" -> new Box(sw - 70, 1, sw, 40);
            case "logo" -> new Box(2, 2, 75, 12);
            case "seed" -> new Box(2, 14, 120, 24);
            case "zaehler" -> new Box(1, sh - 12, 30, sh - 1);
            case "spielerliste" -> new Box(4, 14, 120, 34);
            case "beobachter" -> new Box(sw - 100, 1, sw - 1, 22);
            case "effekte" -> new Box(sw - 90, sh / 2 - 40, sw - 4, sh / 2 - 10);
            default -> new Box(10, 10, 60, 20);
        };
    }

    /** Teil meldet, wo es (ohne Verschiebung) gezeichnet wird. */
    public static void report(String id, int x1, int y1, int x2, int y2) {
        BOUNDS.put(id, new Box(Math.min(x1, x2), Math.min(y1, y2), Math.max(x1, x2), Math.max(y1, y2)));
        SEEN.put(id, frame);
    }

    /** Verschiebung, so begrenzt, dass das Teil auf dem Bildschirm bleibt. */
    public static int[] offset(String id, int sw, int sh) {
        int[] o = OFFSETS.get(id);
        if (o == null) return new int[]{0, 0};
        Box b = bounds(id, sw, sh);
        int dx = Math.max(-b.x1(), Math.min(sw - b.x2(), o[0]));
        int dy = Math.max(-b.y1(), Math.min(sh - b.y2(), o[1]));
        return new int[]{dx, dy};
    }

    public static boolean moved(String id) { return OFFSETS.containsKey(id); }

    public static void set(String id, int dx, int dy) {
        if (dx == 0 && dy == 0) OFFSETS.remove(id); else OFFSETS.put(id, new int[]{dx, dy});
    }

    public static void reset(String id) { OFFSETS.remove(id); }

    public static void resetAll() { OFFSETS.clear(); }

    /** Vor dem Zeichnen eines Teils: an den verschobenen Platz rücken. Immer mit pop() abschließen. */
    public static void push(GuiGraphicsExtractor g, String id) {
        int[] o = offset(id, g.guiWidth(), g.guiHeight());
        g.pose().pushMatrix();
        g.pose().translate(o[0], o[1]);
    }

    public static void pop(GuiGraphicsExtractor g) {
        g.pose().popMatrix();
    }

    /** Für den Editor: liegt der Punkt (Bildschirm) auf dem Teil? */
    public static Box screenBox(String id) {
        Minecraft mc = Minecraft.getInstance();
        int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
        int[] o = offset(id, sw, sh);
        return bounds(id, sw, sh).move(o[0], o[1]);
    }

    // ------------------------------------------------------------ Config

    public static JsonObject toJson() {
        JsonObject o = new JsonObject();
        for (Map.Entry<String, int[]> e : OFFSETS.entrySet()) {
            JsonArray a = new JsonArray();
            a.add(e.getValue()[0]);
            a.add(e.getValue()[1]);
            o.add(e.getKey(), a);
        }
        return o;
    }

    public static void fromJson(JsonObject o) {
        OFFSETS.clear();
        if (o == null) return;
        for (Map.Entry<String, JsonElement> e : o.entrySet()) {
            try {
                JsonArray a = e.getValue().getAsJsonArray();
                OFFSETS.put(e.getKey(), new int[]{a.get(0).getAsInt(), a.get(1).getAsInt()});
            } catch (RuntimeException ignored) {}
        }
    }
}
