package dev.teizen.client.gui;

import dev.teizen.client.TeizenClient;
import dev.teizen.client.core.Config;
import dev.teizen.client.core.KeyBinds;
import dev.teizen.client.core.Module;
import dev.teizen.client.core.ModuleManager;
import dev.teizen.client.core.Setting;
import dev.teizen.client.util.Keys;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import dev.teizen.client.compat.K;

import java.util.ArrayList;
import java.util.List;

/**
 * Das Teizen-Menü (neues Design): links die Kategorien mit Suche, in der Mitte die Funktionen als Karten,
 * rechts die Einstellungen der gewählten Funktion.
 * Bedienung wie immer: Linksklick = an/aus, Rechtsklick = Einstellungen rechts zeigen,
 * Mittelklick = Taste festlegen, Shift+Klick = Favorit. Einfach lostippen = Suche.
 * Einstellungen: Zahl/Text anklicken und eintippen (Enter = übernehmen, Esc = abbrechen).
 * Übersichts-Taste (Standard O) wechselt zur Übersicht.
 */
public final class ClickGui extends Screen {
    // ---- Farben (wie im Entwurf "Variante A")
    private static final int GROUND = 0xEE0D0A16, SIDE = 0xF5120E1F, CARD = 0xFF17112A, CARD_SEL = 0xFF221A3D, CARD_HOVER = 0xFF1C1535,
            LINE = 0xFF2A2147, LINE2 = 0xFF3A2F63, TEXT = 0xFFEDE9FE, MUTED = 0xFFA99BD1, DIM = 0xFF8F82B8, ACCENT = 0xFF7C3AED,
            ACCENT_SOFT = 0xFF2B1F55, ON_TEXT = 0xFF86EFAC, ON_BG = 0xFF14301F, FAV = 0xFFF5B544, PANIC = 0xFFDC2626, PANIC_HOVER = 0xFFEF4444,
            TRACK_OFF = 0xFF3A2F63;

    /** Gewählte Kategorie (null = Favoriten) und Funktion – bleiben beim nächsten Öffnen. */
    private static Module.Category category = Module.Category.KAMPF;
    private static boolean favTab;
    private static Module selected;
    /** Filter in der Mitte: 0 = alle, 1 = an, 2 = aus. */
    private static int filter;

    /** Texteingabe für dieses Menü eingeschaltet (26.3 braucht das, siehe TextFocus). */
    private boolean textOn;
    private final SettingRows rows = new SettingRows();
    private final SearchBox search = new SearchBox();
    private Module binding;
    private int scrollMid, scrollRight, midContent, rightContent;

    /** Klickbare Flächen aus dem letzten Bild. */
    private enum Kind { CAT, FAV_TAB, FILTER, CARD, SWITCH, SETTING, PANIC, OP, MERKEN, DETAIL_SWITCH, PROFILE, HUD }
    private record Hit(Kind kind, int x1, int y1, int x2, int y2, Object a, Object b) {
        boolean in(double mx, double my) { return mx >= x1 && mx < x2 && my >= y1 && my < y2; }
    }
    private final List<Hit> hits = new ArrayList<>();

    public ClickGui() {
        super(Component.literal("Teizen Client"));
        if (selected == null) selected = TeizenClient.modules().byName("killaura");
    }

    @Override
    public void removed() {
        TextFocus.off();
        textOn = false;
        super.removed();
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // ------------------------------------------------------------------ Inhalt

    private static int catColor(Module.Category c) {
        return switch (c) {
            case KAMPF -> 0xFFF87171;
            case ABBAU -> 0xFF22D3EE;
            case BEWEGUNG -> 0xFF4ADE80;
            case SICHT -> 0xFFFBBF24;
            case BAUEN -> 0xFFFB923C;
            case SONSTIGES -> 0xFFA1A1AA;
        };
    }

    private static List<Module> inCat(Module.Category c) {
        List<Module> l = new ArrayList<>();
        for (Module m : TeizenClient.modules().inCategory(c)) if (ModuleManager.shown(m)) l.add(m);
        return l;
    }

    private static List<Module> favs() {
        List<Module> l = new ArrayList<>();
        for (Module m : TeizenClient.modules().favorites()) if (ModuleManager.shown(m)) l.add(m);
        return l;
    }

    /** Was in der Mitte steht (Suche, Favoriten oder Kategorie, mit Filter). */
    private List<Module> middle() {
        if (search.active()) return SearchBox.search(search.query);
        List<Module> base = favTab ? favs() : inCat(category);
        if (filter == 0) return base;
        List<Module> out = new ArrayList<>();
        for (Module m : base) if (m.isEnabled() == (filter == 1)) out.add(m);
        return out;
    }

    private static int onCount(List<Module> l) {
        int n = 0;
        for (Module m : l) if (m.isEnabled()) n++;
        return n;
    }

    // ------------------------------------------------------------------ Zeichnen: Helfer

    /** Fläche mit runden Ecken (Radius 2 Pixel). */
    static void round(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int c) {
        if (x2 - x1 < 4 || y2 - y1 < 4) { g.fill(x1, y1, x2, y2, c); return; }
        g.fill(x1 + 2, y1, x2 - 2, y2, c);
        g.fill(x1 + 1, y1 + 1, x1 + 2, y2 - 1, c);
        g.fill(x2 - 2, y1 + 1, x2 - 1, y2 - 1, c);
        g.fill(x1, y1 + 2, x1 + 1, y2 - 2, c);
        g.fill(x2 - 1, y1 + 2, x2, y2 - 2, c);
    }

    /** Runde Fläche mit Rand. */
    private static void box(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int fill, int border) {
        round(g, x1, y1, x2, y2, border);
        round(g, x1 + 1, y1 + 1, x2 - 1, y2 - 1, fill);
    }

    /** Schalter (Spur + runder Knopf), 18x10. */
    private static void toggle(GuiGraphicsExtractor g, int x, int y, boolean on) {
        round(g, x, y, x + 18, y + 10, on ? ACCENT : TRACK_OFF);
        int k = on ? x + 9 : x + 1;
        round(g, k, y + 1, k + 8, y + 9, 0xFFFFFFFF);
    }

    /** Größere Überschrift (1,5-fach). */
    private static void big(GuiGraphicsExtractor g, Font font, String s, int x, int y, int color) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(1.5f, 1.5f);
        MenuFont.text(g, font, s, 0, 0, color, false);
        g.pose().popMatrix();
    }

    private static String cut(Font font, String s, int w) { return MenuFont.cut(font, s, w); }

    /** Text in Zeilen umbrechen (an Leerzeichen). */
    private static List<String> wrap(Font font, String s, int w, int maxLines) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : s.split(" ")) {
            String test = line.isEmpty() ? word : line + " " + word;
            if (MenuFont.width(font, test) > w && !line.isEmpty()) {
                out.add(line.toString());
                line = new StringBuilder(word);
                if (out.size() == maxLines) break;
            } else line = new StringBuilder(test);
        }
        if (out.size() < maxLines && !line.isEmpty()) out.add(line.toString());
        if (out.size() == maxLines && MenuFont.width(font, String.join(" ", out)) < MenuFont.width(font, s) - 2) {
            String last = out.get(maxLines - 1);
            out.set(maxLines - 1, cut(font, last + " …", w));
        }
        return out;
    }

    // ------------------------------------------------------------------ Zeichnen

    private int sideW() { return Math.max(100, Math.min(124, width * 19 / 100)); }

    /** Rechte Spalte – immer da (3 Spalten), nur etwas schmaler. */
    private int rightW() { return Math.max(104, Math.min(168, width * 23 / 100)); }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        if (!textOn) { textOn = true; TextFocus.on(); } // 26.3: sonst kommen keine Buchstaben an
        Font font = getFont();
        hits.clear();
        int side = sideW(), right = rightW();
        int midX = side, midW = width - side - right;

        g.fill(0, 0, width, height, GROUND);
        MenuFont.active = true;
        try {
            drawSide(g, font, side, mouseX, mouseY);
            drawMiddle(g, font, midX, midW, mouseX, mouseY);
            if (right > 0) drawRight(g, font, width - right, right, mouseX, mouseY);
        } finally {
            MenuFont.active = false;
        }
    }

    private void drawSide(GuiGraphicsExtractor g, Font font, int w, int mx, int my) {
        g.fill(0, 0, w, height, SIDE);
        g.fill(w - 1, 0, w, height, LINE);
        // Logo
        round(g, 6, 6, 20, 20, ACCENT);
        MenuFont.centered(g, font, "§lT", 13, 9, 0xFFFFFFFF);
        MenuFont.text(g, font, "§lTEIZEN", 24, 6, TEXT, false);
        MenuFont.text(g, font, cut(font, "26.2 / 26.3", w - 28), 24, 15, MUTED, false);
        // Suche
        search.render(g, font, 6, 26, w - 12, mx, my);
        // Kategorien
        int y = 46;
        MenuFont.text(g, font, "§lKATEGORIEN", 8, y, DIM, false);
        y += 11;
        List<Module> fl = favs();
        if (!fl.isEmpty()) {
            y = catRow(g, font, w, y, "★ Favoriten", FAV, fl, favTab && !search.active(), mx, my, Kind.FAV_TAB, null);
        }
        for (Module.Category c : Module.Category.values()) {
            List<Module> l = inCat(c);
            if (l.isEmpty()) continue;
            y = catRow(g, font, w, y, c.title, catColor(c), l, !favTab && category == c && !search.active(), mx, my, Kind.CAT, c);
        }
        int catEnd = y;
        // unten: HUD verschieben, OP, Merken, PANIK
        int by = height - 16;
        boolean ph = mx >= 6 && mx < w - 6 && my >= by && my < by + 12;
        round(g, 6, by, w - 6, by + 12, ph ? PANIC_HOVER : PANIC);
        String pl = MenuFont.width(font, "PANIK · alles aus") + 8 <= w - 12 ? "§lPANIK · alles aus" : "§lPANIK";
        MenuFont.centered(g, font, pl, w / 2, by + 2, 0xFFFFFFFF);
        hits.add(new Hit(Kind.PANIC, 6, by, w - 6, by + 12, null, null));
        by -= 14;
        boolean merken = dev.teizen.client.core.Restore.enabled;
        drawChipRow(g, font, 6, by, w - 12, "Merken", merken ? "§aan" : "§caus", mx, my);
        hits.add(new Hit(Kind.MERKEN, 6, by, w - 6, by + 12, null, null));
        by -= 14;
        drawChipRow(g, font, 6, by, w - 12, "OP", ModuleManager.hideOp ? "§cversteckt" : "§asichtbar", mx, my);
        hits.add(new Hit(Kind.OP, 6, by, w - 6, by + 12, null, null));
        by -= 14;
        boolean hh = mx >= 6 && mx < w - 6 && my >= by && my < by + 12;
        round(g, 6, by, w - 6, by + 12, hh ? ACCENT : ACCENT_SOFT);
        MenuFont.centered(g, font, "HUD verschieben", w / 2, by + 2, 0xFFFFFFFF);
        hits.add(new Hit(Kind.HUD, 6, by, w - 6, by + 12, null, null));
        // Profile (ein Klick = mehrere Cheats an/aus), nur wenn Platz ist
        int py = catEnd + 4, room = by - 4;
        var profs = dev.teizen.client.core.Profiles.all();
        if (!profs.isEmpty() && py + 11 + 15 <= room) {
            MenuFont.text(g, font, "§lPROFILE", 8, py, DIM, false);
            py += 11;
            for (var pr : profs) {
                if (py + 14 > room) break;
                boolean act = dev.teizen.client.core.Profiles.isActive(pr);
                boolean ph2 = mx >= 4 && mx < w - 4 && my >= py && my < py + 14;
                if (act || ph2) round(g, 4, py, w - 4, py + 14, act ? ON_BG : CARD_HOVER);
                round(g, 8, py + 4, 14, py + 10, pr.builtIn() ? 0xFF50E080 : 0xFFE080FF);
                String st = act ? "an" : "aus";
                MenuFont.text(g, font, cut(font, pr.name(), w - 30 - MenuFont.width(font, st)), 17, py + 3, act ? 0xFFFFFFFF : 0xFFC9BFE6, false);
                MenuFont.text(g, font, st, w - 8 - MenuFont.width(font, st), py + 3, act ? ON_TEXT : DIM, false);
                hits.add(new Hit(Kind.PROFILE, 4, py, w - 4, py + 14, pr.name(), null));
                py += 15;
            }
        }
    }

    private void drawChipRow(GuiGraphicsExtractor g, Font font, int x, int y, int w, String label, String value, int mx, int my) {
        boolean h = mx >= x && mx < x + w && my >= y && my < y + 12;
        box(g, x, y, x + w, y + 12, h ? CARD_HOVER : CARD, LINE2);
        MenuFont.text(g, font, label, x + 4, y + 2, MUTED, false);
        MenuFont.text(g, font, value, x + w - 4 - MenuFont.width(font, value), y + 2, TEXT, false);
    }

    private int catRow(GuiGraphicsExtractor g, Font font, int w, int y, String title, int dot, List<Module> l, boolean cur,
                       int mx, int my, Kind kind, Module.Category c) {
        boolean h = mx >= 4 && mx < w - 4 && my >= y && my < y + 14;
        if (cur || h) round(g, 4, y, w - 4, y + 14, cur ? CARD_SEL : CARD_HOVER);
        if (cur) g.fill(4, y + 3, 5, y + 11, ACCENT);
        round(g, 8, y + 4, 14, y + 10, dot);
        String cnt = onCount(l) + "/" + l.size();
        MenuFont.text(g, font, cut(font, title, w - 26 - MenuFont.width(font, cnt)), 17, y + 3, cur ? 0xFFFFFFFF : 0xFFC9BFE6, false);
        MenuFont.text(g, font, cnt, w - 8 - MenuFont.width(font, cnt), y + 3, cur ? 0xFFC4B5FD : DIM, false);
        hits.add(new Hit(kind, 4, y, w - 4, y + 14, c, null));
        return y + 15;
    }

    private void drawMiddle(GuiGraphicsExtractor g, Font font, int x0, int w, int mx, int my) {
        int pad = 8;
        int x = x0 + pad, iw = w - pad * 2;
        // Kopf
        String title;
        List<Module> list = middle();
        List<Module> base;
        if (search.active()) { title = "Suche"; base = list; }
        else if (favTab) { title = "★ Favoriten"; base = favs(); }
        else { title = category.title; base = inCat(category); }
        big(g, font, "§l" + title, x, 6, TEXT);
        String sub = search.active() ? list.size() + " Treffer für \"" + search.query + "\""
                : base.size() + " Funktionen · " + onCount(base) + " an";
        MenuFont.text(g, font, sub, x, 22, MUTED, false);
        // Filter (nicht bei Suche)
        if (!search.active()) {
            String[] labels = {"Alle", "An", "Aus"};
            int fx = x + iw;
            for (int i = labels.length - 1; i >= 0; i--) {
                int bw = MenuFont.width(font, labels[i]) + 10;
                fx -= bw;
                boolean act = filter == i, h = mx >= fx && mx < fx + bw && my >= 8 && my < 20;
                round(g, fx, 8, fx + bw, 20, act ? ACCENT : (h ? CARD_HOVER : CARD));
                MenuFont.centered(g, font, labels[i], fx + bw / 2, 10, act ? 0xFFFFFFFF : 0xFFC9BFE6);
                hits.add(new Hit(Kind.FILTER, fx, 8, fx + bw, 20, i, null));
                fx -= 2;
            }
        }
        // Karten
        int top = 36, bottom = height - 16;
        int cols = iw >= 300 ? 3 : Math.max(1, iw / 100); // immer 3 Karten nebeneinander (nur bei winzigem Fenster weniger)
        int gap = 6;
        int cw = (iw - gap * (cols - 1)) / cols, ch = 42;
        int rowsN = (list.size() + cols - 1) / cols;
        midContent = rowsN * (ch + gap);
        scrollMid = Math.max(0, Math.min(scrollMid, Math.max(0, midContent - (bottom - top))));
        g.enableScissor(x0, top, x0 + w, bottom);
        for (int i = 0; i < list.size(); i++) {
            Module m = list.get(i);
            int cx = x + (i % cols) * (cw + gap), cy = top + (i / cols) * (ch + gap) - scrollMid;
            if (cy + ch < top || cy > bottom) continue;
            boolean sel = m == selected, h = mx >= cx && mx < cx + cw && my >= cy && my < cy + ch && my >= top && my < bottom;
            box(g, cx, cy, cx + cw, cy + ch, sel ? CARD_SEL : (h ? CARD_HOVER : CARD), sel ? ACCENT : LINE);
            String name = (m.favorite ? "§6★ §r" : "") + m.name + (m.needsOp() ? " §6OP" : "");
            MenuFont.text(g, font, cut(font, "§l" + name, cw - 32), cx + 6, cy + 5, TEXT, false);
            MenuFont.text(g, font, cut(font, m.description, cw - 12), cx + 6, cy + 16, MUTED, false);
            // Schalter
            toggle(g, cx + cw - 24, cy + 5, m.isEnabled());
            hits.add(new Hit(Kind.SWITCH, cx + cw - 26, cy + 3, cx + cw - 3, cy + 17, m, null));
            // Taste + Status
            String key = m == binding ? "§eTaste drücken…" : (m.key >= 0 ? "Taste " + Keys.name(m.key) : "keine Taste");
            round(g, cx + 6, cy + 28, cx + 10 + MenuFont.width(font, key), cy + 38, ACCENT_SOFT);
            MenuFont.text(g, font, key, cx + 8, cy + 29, 0xFFC4B5FD, false);
            String st = m.isEnabled() ? "an" : "aus";
            MenuFont.text(g, font, st, cx + cw - 6 - MenuFont.width(font, st), cy + 29, m.isEnabled() ? ON_TEXT : DIM, false);
            if (h) g.setTooltipForNextFrame(font, MenuFont.comp(m.description + (m.favorite ? " (★ Favorit)" : "")), mx, my); // normale Schrift
            if (cy + ch > top && cy < bottom) hits.add(new Hit(Kind.CARD, cx, Math.max(cy, top), cx + cw, Math.min(cy + ch, bottom), m, null));
        }
        g.disableScissor();
        if (list.isEmpty())
            MenuFont.centered(g, font, search.active() ? "§7Kein Cheat gefunden für \"" + search.query + "\"" : "§7Hier ist gerade nichts.", x0 + w / 2, top + 20, 0xFFFFFFFF);
        // Fuß
        g.fill(x0, bottom + 1, x0 + w, bottom + 2, LINE);
        String foot;
        if (binding != null) foot = "§eDrück eine Taste für " + binding.name + " (Entf = keine)";
        else if (rows.hint() != null) foot = rows.hint();
        else foot = "§fKlick§7 an/aus · §fRechtsklick§7 Einstellungen · §fMitte§7 Taste · §fShift§7 Favorit · Übersicht: §f" + KeyBinds.overviewName();
        if (MenuFont.width(font, foot) > iw) foot = "§fKlick§7 an/aus · §fRechts§7 Einst. · §fMitte§7 Taste";
        if (MenuFont.width(font, foot) > iw) foot = cut(font, foot, iw);
        MenuFont.text(g, font, foot, x, bottom + 4, MUTED, false);
    }

    private void drawRight(GuiGraphicsExtractor g, Font font, int x0, int w, int mx, int my) {
        g.fill(x0, 0, x0 + w, height, SIDE);
        g.fill(x0, 0, x0 + 1, height, LINE);
        Module m = selected;
        int x = x0 + 8, iw = w - 16;
        if (m == null) {
            MenuFont.text(g, font, "§7Rechtsklick auf eine Funktion", x, 10, MUTED, false);
            return;
        }
        big(g, font, "§l" + cut(font, m.name, (iw - 30) * 2 / 3), x, 6, TEXT);
        // Status-Knopf (anklickbar)
        String st = m.isEnabled() ? "AN" : "AUS";
        int sw = MenuFont.width(font, st) + 8, sx = x + iw - sw;
        round(g, sx, 6, sx + sw, 17, m.isEnabled() ? ON_BG : CARD);
        MenuFont.text(g, font, st, sx + 4, 8, m.isEnabled() ? ON_TEXT : DIM, false);
        hits.add(new Hit(Kind.DETAIL_SWITCH, sx, 6, sx + sw, 17, m, null));
        int y = 24;
        for (String line : wrap(font, m.description, iw, 3)) { MenuFont.text(g, font, line, x, y, MUTED, false); y += 9; }
        String key = m.key >= 0 ? "Taste: §f" + Keys.name(m.key) : "Taste: §7keine";
        MenuFont.text(g, font, cut(font, key, iw), x, y + 2, MUTED, false);
        y += 14;
        g.fill(x, y, x + iw, y + 1, LINE);
        y += 4;
        MenuFont.text(g, font, "§lEINSTELLUNGEN", x, y, DIM, false);
        y += 11;
        List<SettingRows.Row> rs = SettingRows.rows(m);
        if (rs.isEmpty()) { MenuFont.text(g, font, "§7Keine Einstellungen.", x, y, MUTED, false); return; }
        int rh = 14, top = y, bottom = height - 4;
        rightContent = rs.size() * (rh + 1);
        scrollRight = Math.max(0, Math.min(scrollRight, Math.max(0, rightContent - (bottom - top))));
        g.enableScissor(x0, top, x0 + w, bottom);
        int ry = top - scrollRight;
        for (SettingRows.Row r : rs) {
            if (ry + rh > top && ry < bottom) {
                rows.render(g, font, m, r, x, ry, iw, rh, mx, my);
                hits.add(new Hit(Kind.SETTING, x, Math.max(ry, top), x + iw, Math.min(ry + rh, bottom), m, r));
            }
            ry += rh + 1;
        }
        g.disableScissor();
    }

    // ------------------------------------------------------------------ Bedienung

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        // Klick woanders hin = Eingabe übernehmen
        if (rows.isEditing() && !rows.finish(true)) return true;
        double mx = e.x(), my = e.y();
        if (search.click(mx, my)) return true;
        int button = dev.teizen.client.compat.V.mouse(e.button());
        boolean shift = (e.modifiers() & K.MOD_SHIFT) != 0;
        // von oben nach unten: Schalter liegen auf den Karten -> zuerst prüfen
        Hit hit = null;
        for (Hit h : hits) if (h.in(mx, my) && (hit == null || h.kind == Kind.SWITCH)) hit = h;
        if (hit == null) return super.mouseClicked(e, doubleClick);
        switch (hit.kind) {
            case PANIC -> {
                int n = dev.teizen.client.modules.PanicButton.panic();
                dev.teizen.client.util.Chat.ok("PANIK: " + n + " Cheats ausgeschaltet.");
            }
            case OP -> {
                ModuleManager.hideOp = !ModuleManager.hideOp;
                scrollMid = 0;
                Config.save();
                dev.teizen.client.util.Chat.info(ModuleManager.hideOp ? "OP-Cheats ausgeblendet" : "OP-Cheats werden wieder gezeigt");
            }
            case MERKEN -> {
                dev.teizen.client.core.Commands.handle("///merken " + (dev.teizen.client.core.Restore.enabled ? "aus" : "an"));
            }
            case CAT -> { category = (Module.Category) hit.a; favTab = false; scrollMid = 0; search.query = ""; search.focused = false; }
            case FAV_TAB -> { favTab = true; scrollMid = 0; search.query = ""; search.focused = false; }
            case FILTER -> { filter = (Integer) hit.a; scrollMid = 0; }
            case PROFILE -> dev.teizen.client.core.Profiles.toggle((String) hit.a);
            case HUD -> net.minecraft.client.Minecraft.getInstance().gui.setScreen(new HudEditorScreen(this));
            case SWITCH, DETAIL_SWITCH -> {
                Module m = (Module) hit.a;
                if (button == 0) { m.toggle(); selected = m; Config.save(); }
                else return moduleClick(m, button, shift);
            }
            case CARD -> { return moduleClick((Module) hit.a, button, shift); }
            case SETTING -> rows.click((Module) hit.a, (SettingRows.Row) hit.b, button);
        }
        return true;
    }

    private boolean moduleClick(Module m, int button, boolean shift) {
        if (shift && button == 0) m.favorite = !m.favorite;
        else if (button == 0) { m.toggle(); select(m); }
        else if (button == 1) select(m);
        else if (button == 2) binding = m;
        Config.save();
        return true;
    }

    private void select(Module m) {
        if (selected != m) scrollRight = 0;
        selected = m;
    }

    /** Wie ein Klick auf eine Einstellung (auch für den Selbsttest). */
    public void clickSetting(Module m, Setting s, int button) {
        select(m);
        rows.click(m, new SettingRows.Row(SettingRows.Kind.SETTING, s, null), button);
    }

    public boolean isEditing() { return rows.isEditing(); }

    /** Suche setzen (Selbsttest). */
    public void setSearch(String q) { search.query = q; search.focused = !q.isEmpty(); }

    /** Cheats, die gerade (mit Suche) in der Mitte angezeigt werden. */
    public List<Module> shownModules() { return middle(); }

    /** Einstellungen eines Cheats rechts zeigen (Selbsttest / XRay-Erze direkt zeigen). */
    public void expand(Module m) {
        favTab = false;
        category = m.category;
        filter = 0;
        select(m);
    }

    /** Gerade rechts gezeigte Funktion. */
    public Module selected() { return selected; }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        int side = sideW(), right = rightW();
        if (right > 0 && mx >= width - right) scrollRight = Math.max(0, scrollRight - (int) (sy * 16));
        else if (mx >= side) scrollMid = Math.max(0, scrollMid - (int) (sy * 16));
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent e) {
        if (rows.charTyped(e)) return true;
        if (binding == null && search.charTyped(e)) { scrollMid = 0; return true; }
        return super.charTyped(e);
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (rows.keyPressed(e)) return true; // während eine Zahl/ein Text eingetippt wird
        if (binding != null) {
            int k = e.key();
            binding.key = (k == K.DELETE || k == K.BACKSPACE || k == K.ESCAPE) ? -1 : k;
            binding = null;
            Config.save();
            return true;
        }
        if (search.keyPressed(e)) return true;
        if (e.key() == TeizenClient.menuKey) {
            onClose();
            return true;
        }
        if (KeyBinds.overview() != null && KeyBinds.overview().matches(e)) {
            onClose(); // Taste O öffnet und schließt das Menü
            return true;
        }
        return super.keyPressed(e);
    }
}
