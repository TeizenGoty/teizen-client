package dev.teizen.client.gui;

import dev.teizen.client.core.Config;
import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.util.Chat;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Einstellungs-Zeilen eines Cheats – gemeinsam für das Menü und den Bearbeiten-Bildschirm.
 * Schalter: Klick = an/aus · Auswahl: Klick = weiter · Zahl/Text: Klick, eintippen, Enter ·
 * Liste (z.B. Erze): Klick klappt auf, "alle" = alle an/aus, dann einzeln an/aus.
 */
public final class SettingRows {
    public enum Kind { SETTING, MULTI_ALL, MULTI_OPTION }

    public record Row(Kind kind, Setting setting, String option) {}

    private static final Set<String> openMulti = new HashSet<>();

    static {
        // Erz-Listen gleich aufgeklappt zeigen: ein Klick auf "alle", dann einzeln abwählen
        openMulti.add("XRay/erze");
        openMulti.add("AutoMine/erze");
        openMulti.add("StructureFinder/strukturen");
    }

    private Setting editing;
    private Module editingModule;
    private String buffer = "";
    private String error = "";

    private static String key(Module m, Setting s) { return m.name + "/" + s.name; }

    /** Alle Zeilen eines Cheats (aufgeklappte Listen mit ihren Einträgen). */
    public static List<Row> rows(Module m) {
        List<Row> out = new ArrayList<>();
        for (Setting s : m.settings.values()) {
            out.add(new Row(Kind.SETTING, s, null));
            if (s.type == Setting.Type.MULTI && openMulti.contains(key(m, s))) {
                out.add(new Row(Kind.MULTI_ALL, s, null));
                for (String c : s.choices) out.add(new Row(Kind.MULTI_OPTION, s, c));
            }
        }
        return out;
    }

    /** Liste sofort aufklappen (z.B. Erze beim XRay). */
    public static void open(Module m, Setting s) { openMulti.add(key(m, s)); }

    public boolean isEditing() { return editing != null; }

    public Setting editing() { return editing; }

    private static void checkbox(GuiGraphicsExtractor g, int x, int y, boolean on) {
        g.fill(x, y, x + 8, y + 8, 0xFF9C88C8);
        g.fill(x + 1, y + 1, x + 7, y + 7, 0xFF140A26);
        if (on) g.fill(x + 2, y + 2, x + 6, y + 6, 0xFF55FF55);
    }

    public void render(GuiGraphicsExtractor g, Font font, Module m, Row r, int x, int y, int w, int h, int mouseX, int mouseY) {
        Setting s = r.setting();
        boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        int ty = y + (h - 8) / 2;
        switch (r.kind()) {
            case MULTI_ALL -> {
                g.fill(x, y, x + w, y + h, hover ? 0xE0241440 : 0xD0120A20);
                checkbox(g, x + 12, ty, s.allSelected());
                g.text(font, "§falle " + (s.allSelected() ? "§7(alle aus)" : "§7(alle an)"), x + 24, ty, 0xFFFFFFFF, false);
                return;
            }
            case MULTI_OPTION -> {
                g.fill(x, y, x + w, y + h, hover ? 0xE0241440 : 0xD0120A20);
                boolean on = s.isSelected(r.option());
                checkbox(g, x + 18, ty, on);
                String label = s.optionLabel(r.option());
                if (font.width(label) > w - 34) label = font.plainSubstrByWidth(label, w - 38) + "…";
                g.text(font, (on ? "§f" : "§7") + label, x + 30, ty, 0xFFFFFFFF, false);
                return;
            }
            default -> { }
        }
        boolean isEditing = s == editing;
        g.fill(x, y, x + w, y + h, isEditing ? 0xF0301060 : (hover ? 0xE0221238 : 0xD00E0718));
        String label;
        if (isEditing) {
            boolean blink = (System.currentTimeMillis() / 500) % 2 == 0;
            String vis = buffer;
            boolean cut = false;
            while (!vis.isEmpty() && font.width("§7" + s.name + ": §e" + (cut ? "…" : "") + vis + "_") > w - 10) {
                vis = vis.substring(1);
                cut = true;
            }
            label = "§7" + s.name + ": §e" + (cut ? "…" : "") + vis + (blink ? "_" : " ");
            g.fill(x + 1, y, x + w - 1, y + 1, 0xFFFFD040);
            g.fill(x + 1, y + h - 1, x + w - 1, y + h, 0xFFFFD040);
        } else {
            String value = switch (s.type) {
                case BOOL -> s.on() ? "§aan" : "§caus";
                case MULTI -> "§f" + s.display() + (openMulti.contains(key(m, s)) ? " §7▾" : " §7▸");
                default -> "§f" + s.display();
            };
            label = "§7" + s.name + ": " + value;
            if (font.width(label) > w - 10) label = font.plainSubstrByWidth(label, w - 14) + "…";
        }
        g.text(font, label, x + 8, ty, 0xFFFFFFFF, false);
        if (hover && !isEditing) {
            String extra = switch (s.type) {
                case NUMBER -> " (anklicken = Zahl eintippen, " + Setting.fmt(s.min) + " bis " + Setting.fmt(s.max) + ")";
                case TEXT -> " (anklicken = Text eintippen)";
                case MULTI -> " (anklicken = Liste auf/zu, Rechtsklick = Text eintippen)";
                case CHOICE -> " (" + String.join(" / ", s.choices) + ")";
                default -> "";
            };
            g.setTooltipForNextFrame(font, Component.literal(s.description + extra), mouseX, mouseY);
        }
    }

    /** Klick auf eine Zeile. */
    public void click(Module m, Row r, int button) {
        Setting s = r.setting();
        switch (r.kind()) {
            case MULTI_ALL -> s.toggleAll();
            case MULTI_OPTION -> s.toggleOption(r.option());
            case SETTING -> {
                switch (s.type) {
                    case BOOL -> s.setBool(!s.on());
                    case CHOICE -> {
                        List<String> c = s.choices;
                        int i = c.indexOf(s.str());
                        s.setText(c.get((i + (button == 1 ? c.size() - 1 : 1)) % c.size()));
                    }
                    case NUMBER, TEXT -> startEdit(m, s);
                    case MULTI -> {
                        if (button == 1) startEdit(m, s);
                        else if (!openMulti.remove(key(m, s))) openMulti.add(key(m, s));
                    }
                }
            }
        }
        Config.save();
    }

    public void startEdit(Module m, Setting s) {
        editing = s;
        editingModule = m;
        buffer = s.type == Setting.Type.MULTI ? s.str() : s.display();
        error = "";
    }

    /** @return false wenn der Wert ungültig war (Eingabe bleibt offen) */
    public boolean finish(boolean apply) {
        if (editing == null) return true;
        if (apply) {
            String err = editing.parse(buffer);
            if (err != null) {
                error = err + " – nochmal eintippen oder Esc";
                return false;
            }
            Chat.ok(editingModule.name + " " + editing.name + " = " + editing.display());
            Config.save();
        }
        editing = null;
        editingModule = null;
        error = "";
        return true;
    }

    /** Hinweiszeile während der Eingabe (oder null). */
    public String hint() {
        if (editing == null) return null;
        if (!error.isEmpty()) return "§c" + error;
        return "§e" + editingModule.name + " " + editing.name + ": eintippen, §fEnter§e = übernehmen, §fEsc§e = abbrechen"
                + (editing.type == Setting.Type.NUMBER ? " §7(" + Setting.fmt(editing.min) + " bis " + Setting.fmt(editing.max) + ")" : "");
    }

    public boolean charTyped(CharacterEvent e) {
        if (editing == null) return false;
        String c = e.codepointAsString();
        if (editing.type == Setting.Type.NUMBER) {
            if (c.matches("[0-9.,-]")) buffer += c;
        } else if (e.isAllowedChatCharacter() && buffer.length() < 256) {
            buffer += c;
        }
        error = "";
        return true;
    }

    /** @return true wenn die Taste von der Eingabe verbraucht wurde */
    public boolean keyPressed(KeyEvent e) {
        if (editing == null) return false;
        int k = e.key();
        if (k == GLFW.GLFW_KEY_ENTER || k == GLFW.GLFW_KEY_KP_ENTER) finish(true);
        else if (k == GLFW.GLFW_KEY_ESCAPE) finish(false);
        else if (k == GLFW.GLFW_KEY_BACKSPACE && !buffer.isEmpty()) { buffer = buffer.substring(0, buffer.length() - 1); error = ""; }
        else if (k == GLFW.GLFW_KEY_DELETE) { buffer = ""; error = ""; }
        return true;
    }
}
