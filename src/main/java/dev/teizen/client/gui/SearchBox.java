package dev.teizen.client.gui;

import dev.teizen.client.TeizenClient;
import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Suchfeld mit Lupe: findet Cheats nach Name, Beschreibung, Kategorie und Einstellungen. */
public final class SearchBox {
    public String query = "";
    public boolean focused;
    private int x, y, w, h;

    public boolean active() { return !query.isBlank(); }

    /** Lupe: Ring + Griff, aus Pixeln gezeichnet. */
    private static void lens(GuiGraphicsExtractor g, int x, int y, int color) {
        for (int dy = -3; dy <= 3; dy++) {
            for (int dx = -3; dx <= 3; dx++) {
                double d = Math.sqrt(dx * dx + dy * dy);
                if (d >= 2.2 && d <= 3.4) g.fill(x + dx, y + dy, x + dx + 1, y + dy + 1, color);
            }
        }
        for (int i = 0; i < 3; i++) g.fill(x + 3 + i, y + 3 + i, x + 5 + i, y + 5 + i, color);
    }

    public void render(GuiGraphicsExtractor g, Font font, int x, int y, int w, int mouseX, int mouseY) {
        this.x = x; this.y = y; this.w = w; this.h = 12;
        boolean hover = inside(mouseX, mouseY);
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, focused ? 0xFFFFD040 : (hover ? 0xFF9C88C8 : 0xFF5B1FB8));
        g.fill(x, y, x + w, y + h, 0xF0100820);
        lens(g, x + 7, y + 5, focused || active() ? 0xFFFFD040 : 0xFFB8A6D9);
        String text;
        if (query.isEmpty() && !focused) text = "§8Cheat suchen… (einfach lostippen)";
        else text = "§f" + query + (focused && (System.currentTimeMillis() / 500) % 2 == 0 ? "§e_" : "");
        if (font.width(text) > w - 22) text = "§f…" + query.substring(Math.max(0, query.length() - 20));
        g.text(font, text, x + 16, y + 2, 0xFFFFFFFF, false);
        if (active()) g.text(font, "§7✕", x + w - 9, y + 2, 0xFFFFFFFF, false);
    }

    public boolean inside(double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** Klick ins Feld: fokussieren, auf das ✕: leeren. @return true wenn der Klick das Feld getroffen hat */
    public boolean click(double mx, double my) {
        if (!inside(mx, my)) { focused = false; return false; }
        if (active() && mx >= x + w - 11) { query = ""; focused = false; return true; }
        focused = true;
        return true;
    }

    /** Tippen: bei Fokus oder wenn ein Buchstabe ohne Fokus kommt (Suche startet von selbst). */
    public boolean charTyped(CharacterEvent e) {
        String c = e.codepointAsString();
        if (!focused && !c.matches("[\\p{L}\\p{N}]")) return false;
        if (!e.isAllowedChatCharacter() || query.length() > 40) return focused;
        focused = true;
        query += c;
        return true;
    }

    public boolean keyPressed(KeyEvent e) {
        if (!focused) return false;
        int k = e.key();
        if (k == GLFW.GLFW_KEY_BACKSPACE) { if (!query.isEmpty()) query = query.substring(0, query.length() - 1); return true; }
        if (k == GLFW.GLFW_KEY_ESCAPE) { if (query.isEmpty()) focused = false; else query = ""; return true; }
        if (k == GLFW.GLFW_KEY_ENTER || k == GLFW.GLFW_KEY_KP_ENTER) { focused = false; return true; }
        if (k == GLFW.GLFW_KEY_DELETE) { query = ""; return true; }
        return k != GLFW.GLFW_KEY_TAB; // andere Tasten (auch die Menü-Taste) gehen beim Tippen nicht weiter
    }

    /** Treffer: erst Namen, die so anfangen, dann alle, die es irgendwo enthalten. */
    public static List<Module> search(String q) {
        String s = q.toLowerCase(Locale.ROOT).trim();
        List<Module> start = new ArrayList<>(), rest = new ArrayList<>();
        for (Module m : TeizenClient.modules().favoritesFirst()) {
            if (!dev.teizen.client.core.ModuleManager.shown(m)) continue;
            String name = m.name.toLowerCase(Locale.ROOT);
            if (name.startsWith(s)) { start.add(m); continue; }
            StringBuilder all = new StringBuilder(name).append(' ').append(m.description).append(' ').append(m.category.title);
            for (Setting st : m.settings.values()) all.append(' ').append(st.name).append(' ').append(st.description);
            if (all.toString().toLowerCase(Locale.ROOT).contains(s)) rest.add(m);
        }
        start.addAll(rest);
        return start;
    }
}
