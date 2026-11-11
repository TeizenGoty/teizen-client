package dev.teizen.client.gui;

import dev.teizen.client.TeizenClient;
import dev.teizen.client.compat.K;
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

import java.util.ArrayList;
import java.util.List;

/**
 * Das Teizen-Menü ("Glas"-Design): ein durchsichtiges Fenster in der Mitte. Links die Kategorien, in der Mitte
 * die Funktionen, rechts klappt beim Anklicken einer Funktion ihr Bereich mit Taste und Einstellungen auf.
 * Bedienung: Schalter links in der Zeile = an/aus · Klick auf den Namen = Einstellungen · Doppelklick = an/aus ·
 * Stern = Favorit · Tasten-Feld = Taste festlegen (Rechtsklick = löschen). Suche: erst ins Feld klicken.
 * Jeder Klick macht ein Geräusch (links unten wählbar).
 */
public final class ClickGui extends Screen {
    private static final int TEXT = 0xFFF4F4F8, SOFT = 0xFFD2D2DE, MUTED = 0xFFA3A3B5, DIM = 0xFF7C7C8E,
            ACCENT = 0xFF8B5CF6, ACCENT_HI = 0xFFA78BFA, SURF = 0x16FFFFFF, SURF_H = 0x2CFFFFFF, SEL = 0x408B5CF6, SEL_H = 0x598B5CF6,
            FAV = 0xFFFBBF24, PANIC = 0xFFDC2626, PANIC_H = 0xFFEF4444, BODY = 0xC80B0B11, EDGE = 0x34FFFFFF, SIDE = 0x3A000000,
            TRACK = 0x46FFFFFF, ON_TEXT = 0xFF6EE7B7;

    /** Gewählte Kategorie und Funktion – bleiben beim nächsten Öffnen. */
    private static Module.Category category = Module.Category.KAMPF;
    private static boolean favTab;
    /** "Alle": alle Funktionen auf einen Blick (Standard). */
    private static boolean allTab = true;
    private static Module selected;
    /** Bereich rechts (Einstellungen) offen? */
    private static boolean drawer;
    /** Filter: 0 = alle, 1 = an, 2 = aus. */
    private static int filter;

    private boolean textOn;
    private final SettingRows rows = new SettingRows();
    private final SearchBox search = new SearchBox();
    private Module binding;
    private int scrollMid, scrollRight, midContent, rightContent;
    private double restMid, restRight;
    private int barMidY1, barMidY2, barRightY1, barRightY2, dragBar;
    /** Linke Kante des rechten Bereichs im letzten Bild (zum Scrollen). */
    private int drawerX = Integer.MAX_VALUE;

    private enum Kind { CAT, FAV_TAB, ALL_TAB, FILTER, CARD, SWITCH, SETTING, PANIC, OP, MERKEN, DETAIL_SWITCH, PROFILE, HUD, SCHEM, PACKS, KEY, STAR,
        BAR_MID, BAR_RIGHT, CLOSE, SOUND }
    private record Hit(Kind kind, int x1, int y1, int x2, int y2, Object a, Object b) {
        boolean in(double mx, double my) { return mx >= x1 && mx < x2 && my >= y1 && my < y2; }
    }
    private final List<Hit> hits = new ArrayList<>();

    public ClickGui() {
        super(Component.literal("Teizen Client"));
        search.autoType = false; // Tippen landet nicht mehr von selbst in der Suche
        search.clean = true;
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

    private static List<Module> all() {
        List<Module> l = new ArrayList<>();
        for (Module.Category c : Module.Category.values()) l.addAll(inCat(c));
        return l;
    }

    private static List<Module> favs() {
        List<Module> l = new ArrayList<>();
        for (Module m : TeizenClient.modules().favorites()) if (ModuleManager.shown(m)) l.add(m);
        return l;
    }

    private List<Module> middle() {
        if (search.active()) return SearchBox.search(search.query);
        List<Module> base = favTab ? favs() : allTab ? all() : inCat(category);
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

    /** Fläche mit runden Ecken (Radius r), ohne dass sich Teile überlappen (wichtig bei durchsichtigen Farben). */
    static void rr(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int r, int c) { rr(g, x1, y1, x2, y2, r, c, true, true); }

    static void rr(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int r, int c, boolean left, boolean right) {
        int w = x2 - x1, h = y2 - y1;
        if (w <= 0 || h <= 0) return;
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int in = r - (int) Math.round(Math.sqrt(r * r - dy * dy));
            int a = left ? in : 0, b = right ? in : 0;
            g.fill(x1 + a, y1 + i, x2 - b, y1 + i + 1, c);
            g.fill(x1 + a, y2 - i - 1, x2 - b, y2 - i, c);
        }
        g.fill(x1, y1 + r, x2, y2 - r, c);
    }

    /** Für die anderen Bildschirme: kleine runde Fläche. */
    static void round(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int c) { rr(g, x1, y1, x2, y2, 3, c); }

    /** Dünner Rand um eine runde Fläche. */
    private static void outline(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int r, int c) {
        g.fill(x1 + r, y1, x2 - r, y1 + 1, c);
        g.fill(x1 + r, y2 - 1, x2 - r, y2, c);
        g.fill(x1, y1 + r, x1 + 1, y2 - r, c);
        g.fill(x2 - 1, y1 + r, x2, y2 - r, c);
    }

    /** Schalter 16x9. */
    private static void toggle(GuiGraphicsExtractor g, int x, int y, boolean on) {
        rr(g, x, y, x + 16, y + 9, 4, on ? ACCENT : TRACK);
        int k = on ? x + 8 : x + 1;
        rr(g, k, y + 1, k + 7, y + 8, 3, 0xFFFFFFFF);
    }

    private static void big(GuiGraphicsExtractor g, Font font, String s, int x, int y, int color) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(1.5f, 1.5f);
        MenuFont.text(g, font, s, 0, 0, color, false);
        g.pose().popMatrix();
    }

    private static String cut(Font font, String s, int w) { return MenuFont.cut(font, s, w); }

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

    private static boolean over(int mx, int my, int x1, int y1, int x2, int y2) { return mx >= x1 && mx < x2 && my >= y1 && my < y2; }

    // ------------------------------------------------------------------ Zeichnen

    /** Menü wird mindestens so groß (in GUI-Pixeln) gezeichnet, sonst verkleinert. */
    private static final int VIRTUAL_W = 720, VIRTUAL_H = 400;
    private float uiScale = 1f;
    private boolean drawErr;
    private static float tipScale = 1f;
    /** Fenster im letzten Bild. */
    private int wx, wy, ww, wh;

    static void tip(GuiGraphicsExtractor g, Font font, Component c, int mx, int my) {
        g.setTooltipForNextFrame(font, c, Math.round(mx * tipScale), Math.round(my * tipScale));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        if (!textOn) { textOn = true; TextFocus.on(); }
        int realW = width, realH = height;
        uiScale = Math.min(1f, Math.min(realW / (float) VIRTUAL_W, realH / (float) VIRTUAL_H));
        width = Math.round(realW / uiScale);
        height = Math.round(realH / uiScale);
        g.pose().pushMatrix();
        g.pose().scale(uiScale, uiScale);
        tipScale = uiScale;
        try {
            draw(g, (int) (mouseX / uiScale), (int) (mouseY / uiScale));
        } catch (RuntimeException e) {
            if (!drawErr) { drawErr = true; TeizenClient.LOG.warn("Menü zeichnen", e); }
        } finally {
            MenuFont.active = false;
            tipScale = 1f;
            g.pose().popMatrix();
            width = realW;
            height = realH;
        }
    }

    private void draw(GuiGraphicsExtractor g, int mx, int my) {
        Font font = getFont();
        hits.clear();
        ww = Math.min(width - 24, 780);
        wh = Math.min(height - 20, 430);
        wx = (width - ww) / 2;
        wy = (height - wh) / 2;
        g.fill(0, 0, width, height, 0x4A000000); // Welt bleibt sichtbar, nur leicht abgedunkelt
        rr(g, wx, wy, wx + ww, wy + wh, 9, BODY);
        outline(g, wx, wy, wx + ww, wy + wh, 5, EDGE);
        MenuFont.active = true;
        int sw = 120;
        boolean dr = drawer && selected != null;
        int dw = dr ? Math.min(184, (ww - sw) / 2) : 0;
        drawerX = dr ? wx + ww - dw : Integer.MAX_VALUE;
        drawSide(g, font, sw, mx, my);
        drawMiddle(g, font, wx + sw, ww - sw - dw, mx, my);
        if (dr) drawDrawer(g, font, wx + ww - dw, dw, mx, my);
    }

    private void drawSide(GuiGraphicsExtractor g, Font font, int sw, int mx, int my) {
        rr(g, wx, wy, wx + sw, wy + wh, 9, SIDE, true, false);
        int x = wx + 8, w = sw - 16, x2 = x + w;
        // Logo
        rr(g, x, wy + 10, x + 16, wy + 26, 5, ACCENT);
        MenuFont.centered(g, font, "§lT", x + 8, wy + 14, 0xFFFFFFFF);
        MenuFont.text(g, font, "§lTEIZEN", x + 21, wy + 10, TEXT, false);
        String sub = "Build " + TeizenClient.BUILD + (!MenuFont.enabled ? " §c(Schrift aus)" : !MenuFont.loaded() ? " §c(Schrift fehlt)" : "");
        MenuFont.text(g, font, cut(font, sub, w - 21), x + 21, wy + 19, DIM, false);
        // Kategorien
        int y = wy + 36;
        boolean s = search.active();
        y = nav(g, font, x, x2, y, "Alle", 0xFFC4B5FD, all(), allTab && !favTab && !s, mx, my, Kind.ALL_TAB, null);
        y = nav(g, font, x, x2, y, "Favoriten", FAV, favs(), favTab && !s, mx, my, Kind.FAV_TAB, null);
        y += 3;
        for (Module.Category c : Module.Category.values()) {
            List<Module> l = inCat(c);
            if (l.isEmpty()) continue;
            y = nav(g, font, x, x2, y, c.title, catColor(c), l, !favTab && !allTab && category == c && !s, mx, my, Kind.CAT, c);
        }
        int catEnd = y;
        // unten
        int by = wy + wh - 22;
        boolean ph = over(mx, my, x, by, x2, by + 14);
        rr(g, x, by, x2, by + 14, 5, ph ? PANIC_H : PANIC);
        MenuFont.centered(g, font, "§lPANIK", x + w / 2, by + 3, 0xFFFFFFFF);
        hits.add(new Hit(Kind.PANIC, x, by, x2, by + 14, null, null));
        by -= 15;
        chip(g, font, x, by, w, "Merken", dev.teizen.client.core.Restore.enabled ? "an" : "aus", dev.teizen.client.core.Restore.enabled, mx, my, Kind.MERKEN);
        by -= 14;
        chip(g, font, x, by, w, "OP-Sachen", ModuleManager.hideOp ? "versteckt" : "da", !ModuleManager.hideOp, mx, my, Kind.OP);
        by -= 14;
        chip(g, font, x, by, w, "Klick", Clicks.name(), Clicks.current > 0, mx, my, Kind.SOUND);
        if (over(mx, my, x, by, x2, by + 13)) tip(g, font, MenuFont.comp("Klick-Geräusch im Menü: Linksklick = nächstes, Rechtsklick = voriges"), mx, my);
        by -= 16;
        String[] labels = {"HUD verschieben", "Schematics", "Texturpakete"};
        Kind[] kinds = {Kind.HUD, Kind.SCHEM, Kind.PACKS};
        for (int i = 0; i < labels.length; i++) {
            boolean h = over(mx, my, x, by, x2, by + 13);
            rr(g, x, by, x2, by + 13, 4, h ? SURF_H : SURF);
            MenuFont.centered(g, font, labels[i], x + w / 2, by + 3, h ? TEXT : SOFT);
            hits.add(new Hit(kinds[i], x, by, x2, by + 13, null, null));
            by -= 14;
        }
        // Profile, wenn Platz ist
        int py = catEnd + 5, room = by + 10;
        var profs = dev.teizen.client.core.Profiles.all();
        if (!profs.isEmpty() && py + 10 + 14 <= room) {
            MenuFont.text(g, font, "PROFILE", x + 2, py, DIM, false);
            py += 10;
            for (var pr : profs) {
                if (py + 13 > room) break;
                boolean act = dev.teizen.client.core.Profiles.isActive(pr);
                boolean h = over(mx, my, x, py, x2, py + 13);
                if (act || h) rr(g, x, py, x2, py + 13, 4, act ? SEL : SURF_H);
                String st = act ? "an" : "aus";
                MenuFont.text(g, font, cut(font, pr.name(), w - 12 - MenuFont.width(font, st)), x + 5, py + 3, act ? TEXT : SOFT, false);
                MenuFont.text(g, font, st, x2 - 5 - MenuFont.width(font, st), py + 3, act ? ON_TEXT : DIM, false);
                hits.add(new Hit(Kind.PROFILE, x, py, x2, py + 13, pr.name(), null));
                py += 14;
            }
        }
    }

    private void chip(GuiGraphicsExtractor g, Font font, int x, int y, int w, String label, String value, boolean on, int mx, int my, Kind kind) {
        boolean h = over(mx, my, x, y, x + w, y + 13);
        rr(g, x, y, x + w, y + 13, 4, h ? SURF_H : SURF);
        MenuFont.text(g, font, label, x + 5, y + 3, MUTED, false);
        MenuFont.text(g, font, value, x + w - 5 - MenuFont.width(font, value), y + 3, on ? ACCENT_HI : DIM, false);
        hits.add(new Hit(kind, x, y, x + w, y + 13, null, null));
    }

    private int nav(GuiGraphicsExtractor g, Font font, int x, int x2, int y, String title, int dot, List<Module> l, boolean cur,
                    int mx, int my, Kind kind, Module.Category c) {
        boolean h = over(mx, my, x, y, x2, y + 15);
        if (cur || h) rr(g, x, y, x2, y + 15, 5, cur ? SEL : SURF_H);
        rr(g, x + 5, y + 5, x + 10, y + 10, 2, dot);
        String cnt = onCount(l) + "/" + l.size();
        MenuFont.text(g, font, cut(font, (cur ? "§l" : "") + title, x2 - x - 22 - MenuFont.width(font, cnt)), x + 15, y + 4, cur ? TEXT : SOFT, false);
        MenuFont.text(g, font, cnt, x2 - 5 - MenuFont.width(font, cnt), y + 4, cur ? ACCENT_HI : DIM, false);
        hits.add(new Hit(kind, x, y, x2, y + 15, c, null));
        return y + 16;
    }

    private void drawMiddle(GuiGraphicsExtractor g, Font font, int x0, int w, int mx, int my) {
        int x = x0 + 12, iw = w - 24;
        List<Module> list = middle();
        String title;
        List<Module> base;
        if (search.active()) { title = "Suche"; base = list; }
        else if (favTab) { title = "Favoriten"; base = favs(); }
        else if (allTab) { title = "Alle Funktionen"; base = all(); }
        else { title = category.title; base = inCat(category); }
        // Kopf: Titel links, Filter + Suche rechts
        int sW = iw >= 380 ? 132 : 96, sx = x + iw - sW, sy = wy + 10;
        search.render(g, font, sx, sy, sW, mx, my);
        int fx = sx - 6;
        if (!search.active() && iw >= 300) {
            String[] labels = {"Alle", "An", "Aus"};
            for (int i = labels.length - 1; i >= 0; i--) {
                int bw = MenuFont.width(font, labels[i]) + 12;
                fx -= bw;
                boolean act = filter == i, h = over(mx, my, fx, sy + 1, fx + bw, sy + 15);
                rr(g, fx, sy + 1, fx + bw, sy + 15, 5, act ? ACCENT : (h ? SURF_H : SURF));
                MenuFont.centered(g, font, labels[i], fx + bw / 2, sy + 4, act ? 0xFFFFFFFF : SOFT);
                hits.add(new Hit(Kind.FILTER, fx, sy + 1, fx + bw, sy + 15, i, null));
                fx -= 3;
            }
        }
        big(g, font, "§l" + cut(font, title, Math.max(20, (fx - x - 6) * 2 / 3)), x, wy + 9, TEXT);
        String sub = search.active() ? list.size() + " Treffer für \"" + search.query + "\"" : base.size() + " Funktionen · " + onCount(base) + " an";
        MenuFont.text(g, font, cut(font, sub, Math.max(20, fx - x - 6)), x, wy + 25, MUTED, false);

        int top = wy + 40, bottom = wy + wh - 20;
        boolean grouped = allTab && !favTab && !search.active();
        // Einträge: Überschrift (Kategorie) oder Funktion
        List<Object> items = new ArrayList<>();
        if (grouped) {
            Module.Category last = null;
            for (Module m : list) {
                if (m.category != last) { last = m.category; items.add(last); }
                items.add(m);
            }
        } else items.addAll(list);
        int gap = 5, rh = grouped ? 15 : 31;
        int cols = grouped ? Math.max(1, Math.min(5, (iw + gap) / 118)) : Math.max(1, Math.min(3, (iw + gap) / 165));
        int cw = (iw - gap * (cols - 1)) / cols;
        int perCol = Math.max(1, (items.size() + cols - 1) / cols);
        midContent = grouped ? perCol * rh + 4 : ((items.size() + cols - 1) / cols) * rh + 4;
        scrollMid = Math.max(0, Math.min(scrollMid, Math.max(0, midContent - (bottom - top))));
        g.enableScissor(x0, top, x0 + w, bottom);
        for (int i = 0; i < items.size(); i++) {
            int col = grouped ? i / perCol : i % cols, row = grouped ? i % perCol : i / cols;
            int cx = x + col * (cw + gap), cy = top + row * rh - scrollMid;
            if (cy + rh < top || cy > bottom) continue;
            Object o = items.get(i);
            if (o instanceof Module.Category c) {
                rr(g, cx + 2, cy + 6, cx + 6, cy + 10, 2, catColor(c));
                MenuFont.text(g, font, cut(font, c.title.toUpperCase(java.util.Locale.ROOT), cw - 12), cx + 10, cy + 4, MUTED, false);
                continue;
            }
            row(g, font, (Module) o, cx, cy, cw, rh - 2, !grouped, top, bottom, mx, my);
        }
        g.disableScissor();
        scrollBar(g, x0 + w - 6, top, bottom, midContent, scrollMid, mx, my, Kind.BAR_MID);
        if (list.isEmpty())
            MenuFont.centered(g, font, search.active() ? "§7Nichts gefunden für \"" + search.query + "\"" : favTab ? "§7Noch keine Favoriten – klick auf einen Stern." : "§7Hier ist gerade nichts.",
                    x0 + w / 2, top + 24, 0xFFFFFFFF);
        // Fuß
        String foot;
        if (binding != null) foot = "§dDrück jetzt eine Taste für " + binding.name + " §7(Entf = keine)";
        else if (rows.hint() != null) foot = rows.hint();
        else foot = "§fSchalter§7 an/aus · §fName§7 Einstellungen · §fDoppelklick§7 an/aus · §fStern§7 Favorit · Übersicht: §f" + KeyBinds.overviewName();
        if (MenuFont.width(font, foot) > iw) foot = "§fSchalter§7 an/aus · §fName§7 Einstellungen · §fStern§7 Favorit";
        MenuFont.text(g, font, cut(font, foot, iw), x, bottom + 6, MUTED, false);
    }

    /** Eine Funktion als Zeile: Schalter, Name, (Beschreibung), Taste, Stern. */
    private void row(GuiGraphicsExtractor g, Font font, Module m, int cx, int cy, int cw, int h, boolean wide, int top, int bottom, int mx, int my) {
        boolean on = m.isEnabled(), sel = drawer && m == selected;
        boolean hv = over(mx, my, cx, cy, cx + cw, cy + h) && my >= top && my < bottom;
        rr(g, cx, cy, cx + cw, cy + h, 5, on ? (hv ? SEL_H : SEL) : (hv ? SURF_H : SURF));
        if (sel) outline(g, cx, cy, cx + cw, cy + h, 3, ACCENT_HI);
        int line = wide ? cy + 5 : cy + 3;
        toggle(g, cx + 5, wide ? cy + 5 : cy + 2, on);
        // rechts: Stern, davor die Taste
        int starX = cx + cw - 12;
        boolean hs = over(mx, my, starX - 2, cy, cx + cw, cy + (wide ? 16 : h)) && hv;
        g.text(font, "★", starX, line, m.favorite ? FAV : (hs ? 0xFFE5E5EE : 0x66FFFFFF), false);
        int right = starX - 4;
        String key = m == binding ? "…" : m.key >= 0 ? Keys.name(m.key) : (wide ? "Taste" : null);
        int kx1 = 0, kx2 = 0;
        if (key != null) {
            int kw = MenuFont.width(font, key) + 7;
            if (wide || kw <= 30) {
                kx2 = right;
                kx1 = right - kw;
                boolean hk = over(mx, my, kx1, line - 2, kx2, line + 9) && hv;
                rr(g, kx1, line - 2, kx2, line + 9, 3, m == binding ? ACCENT : (hk ? SURF_H : SURF));
                MenuFont.text(g, font, key, kx1 + 4, line, m == binding ? 0xFFFFFFFF : m.key >= 0 ? ACCENT_HI : DIM, false);
                right = kx1 - 4;
            }
        }
        String name = m.name + (m.needsOp() ? " §6OP" : "");
        MenuFont.text(g, font, cut(font, (on && wide ? "§l" : "") + name, right - (cx + 26)), cx + 26, line, on ? TEXT : SOFT, false);
        if (wide) MenuFont.text(g, font, cut(font, m.description, cw - 12), cx + 6, cy + 18, MUTED, false);
        if (hv && !wide) tip(g, font, MenuFont.comp(m.description), mx, my);
        // Klickflächen (nur der sichtbare Teil)
        int y1 = Math.max(cy, top), y2 = Math.min(cy + h, bottom);
        if (y2 <= y1) return;
        hits.add(new Hit(Kind.CARD, cx, y1, cx + cw, y2, m, null));
        int zy2 = Math.min(wide ? cy + 16 : cy + h, bottom);
        if (zy2 > y1) {
            hits.add(new Hit(Kind.SWITCH, cx, y1, cx + 24, zy2, m, null));
            hits.add(new Hit(Kind.STAR, starX - 3, y1, cx + cw, zy2, m, null));
            if (kx2 > kx1) hits.add(new Hit(Kind.KEY, kx1, y1, kx2, zy2, m, null));
        }
    }

    private void scrollBar(GuiGraphicsExtractor g, int x, int top, int bottom, int content, int scroll, int mx, int my, Kind kind) {
        int view = bottom - top;
        if (content <= view) {
            if (kind == Kind.BAR_MID) barMidY2 = barMidY1; else barRightY2 = barRightY1;
            return;
        }
        int th = Math.max(18, view * view / content);
        int ty = top + (int) ((long) (view - th) * scroll / Math.max(1, content - view));
        boolean h = over(mx, my, x - 3, top, x + 6, bottom);
        rr(g, x, top, x + 3, bottom, 1, 0x18FFFFFF);
        rr(g, x, ty, x + 3, ty + th, 1, h || dragBar != 0 ? ACCENT_HI : 0x70FFFFFF);
        hits.add(new Hit(kind, x - 3, top, x + 6, bottom, null, null));
        if (kind == Kind.BAR_MID) { barMidY1 = top; barMidY2 = bottom; }
        else { barRightY1 = top; barRightY2 = bottom; }
    }

    private void dragTo(double my) {
        if (dragBar == 1 && barMidY2 > barMidY1) {
            int view = barMidY2 - barMidY1;
            double f = Math.max(0, Math.min(1, (my - barMidY1) / view));
            scrollMid = (int) (f * Math.max(0, midContent - view));
        } else if (dragBar == 2 && barRightY2 > barRightY1) {
            int view = barRightY2 - barRightY1;
            double f = Math.max(0, Math.min(1, (my - barRightY1) / view));
            scrollRight = (int) (f * Math.max(0, rightContent - view));
        }
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (dragBar != 0) { dragTo(e.y() / uiScale); return true; }
        return super.mouseDragged(e, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        dragBar = 0;
        return super.mouseReleased(e);
    }

    /** Rechts: die gewählte Funktion mit Taste und Einstellungen. */
    private void drawDrawer(GuiGraphicsExtractor g, Font font, int x0, int w, int mx, int my) {
        rr(g, x0, wy, x0 + w, wy + wh, 9, SIDE, false, true);
        g.fill(x0, wy + 8, x0 + 1, wy + wh - 8, 0x22FFFFFF);
        Module m = selected;
        int x = x0 + 10, iw = w - 20, x2 = x + iw;
        // Schließen
        boolean hc = over(mx, my, x2 - 12, wy + 8, x2 + 2, wy + 22);
        rr(g, x2 - 12, wy + 8, x2 + 2, wy + 22, 5, hc ? SURF_H : SURF);
        MenuFont.centered(g, font, "x", x2 - 5, wy + 11, hc ? TEXT : MUTED);
        hits.add(new Hit(Kind.CLOSE, x2 - 12, wy + 8, x2 + 2, wy + 22, null, null));
        big(g, font, "§l" + cut(font, m.name, (iw - 18) * 2 / 3), x, wy + 9, TEXT);
        int y = wy + 28;
        // An/Aus + Favorit
        int half = (iw - 4) / 2;
        boolean on = m.isEnabled(), h1 = over(mx, my, x, y, x + half, y + 15), h2 = over(mx, my, x + half + 4, y, x2, y + 15);
        rr(g, x, y, x + half, y + 15, 5, on ? (h1 ? ACCENT_HI : ACCENT) : (h1 ? SURF_H : SURF));
        MenuFont.centered(g, font, on ? "§lAN" : "AUS", x + half / 2, y + 4, on ? 0xFFFFFFFF : SOFT);
        hits.add(new Hit(Kind.DETAIL_SWITCH, x, y, x + half, y + 15, m, null));
        rr(g, x + half + 4, y, x2, y + 15, 5, h2 ? SURF_H : SURF);
        String fl = "Favorit";
        int fw = MenuFont.width(font, fl) + 10;
        int fxs = x + half + 4 + (half - fw) / 2;
        g.text(font, "★", fxs, y + 4, m.favorite ? FAV : 0x66FFFFFF, false);
        MenuFont.text(g, font, fl, fxs + 10, y + 4, m.favorite ? FAV : SOFT, false);
        hits.add(new Hit(Kind.STAR, x + half + 4, y, x2, y + 15, m, null));
        y += 20;
        for (String line : wrap(font, m.description + (m.needsOp() ? " (braucht OP)" : ""), iw, 4)) { MenuFont.text(g, font, line, x, y, MUTED, false); y += 9; }
        y += 4;
        // Taste
        String key = m == binding ? "Taste drücken…" : m.key >= 0 ? Keys.name(m.key) : "keine";
        boolean hk = over(mx, my, x, y, x2, y + 15);
        rr(g, x, y, x2, y + 15, 5, m == binding ? ACCENT : (hk ? SURF_H : SURF));
        MenuFont.text(g, font, "Taste", x + 6, y + 4, m == binding ? 0xFFFFFFFF : MUTED, false);
        MenuFont.text(g, font, key, x2 - 6 - MenuFont.width(font, key), y + 4, m == binding ? 0xFFFFFFFF : m.key >= 0 ? ACCENT_HI : DIM, false);
        hits.add(new Hit(Kind.KEY, x, y, x2, y + 15, m, null));
        if (hk) tip(g, font, MenuFont.comp("Anklicken, dann Taste drücken. Rechtsklick = Taste löschen."), mx, my);
        y += 21;
        MenuFont.text(g, font, "EINSTELLUNGEN", x, y, DIM, false);
        y += 11;
        List<SettingRows.Row> rs = SettingRows.rows(m);
        if (rs.isEmpty()) { MenuFont.text(g, font, "§7Keine Einstellungen.", x, y, MUTED, false); barRightY2 = barRightY1; return; }
        int rh = 15, top = y, bottom = wy + wh - 8;
        rightContent = rs.size() * (rh + 2);
        scrollRight = Math.max(0, Math.min(scrollRight, Math.max(0, rightContent - (bottom - top))));
        g.enableScissor(x0, top, x0 + w, bottom);
        int ry = top - scrollRight;
        for (SettingRows.Row r : rs) {
            if (ry + rh > top && ry < bottom) {
                rows.render(g, font, m, r, x, ry, iw - 4, rh, mx, my);
                hits.add(new Hit(Kind.SETTING, x, Math.max(ry, top), x + iw - 4, Math.min(ry + rh, bottom), m, r));
            }
            ry += rh + 2;
        }
        g.disableScissor();
        scrollBar(g, x0 + w - 6, top, bottom, rightContent, scrollRight, mx, my, Kind.BAR_RIGHT);
    }

    // ------------------------------------------------------------------ Bedienung

    /** An/aus aus dem Menü: mit Menü-Klick (an = höher, aus = tiefer) statt dem normalen Umschalt-Ton. */
    private void flip(Module m) {
        Module.quiet = Clicks.current > 0;
        try { m.toggle(); } finally { Module.quiet = false; }
        Clicks.play(m.isEnabled() ? 1.15f : 0.85f);
        Config.save();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        if (rows.isEditing() && !rows.finish(true)) return true;
        double mx = e.x() / uiScale, my = e.y() / uiScale;
        if (search.click(mx, my)) { Clicks.play(1f); return true; }
        int button = dev.teizen.client.compat.V.mouse(e.button());
        boolean shift = (e.modifiers() & K.MOD_SHIFT) != 0;
        // die zuletzt gezeichnete Fläche liegt oben (Schalter/Stern/Taste liegen auf der Zeile)
        Hit hit = null;
        for (Hit h : hits) if (h.in(mx, my)) hit = h;
        if (hit == null) return super.mouseClicked(e, doubleClick);
        if (binding != null && hit.kind != Kind.KEY) binding = null; // woanders hingeklickt = Taste festlegen abbrechen
        boolean silent = false;
        switch (hit.kind) {
            case PANIC -> {
                int n = dev.teizen.client.modules.PanicButton.panic();
                dev.teizen.client.util.Chat.ok("PANIK: " + n + " Cheats ausgeschaltet.");
            }
            case OP -> {
                ModuleManager.hideOp = !ModuleManager.hideOp;
                scrollMid = 0;
                Config.save();
            }
            case MERKEN -> dev.teizen.client.core.Commands.handle("///merken " + (dev.teizen.client.core.Restore.enabled ? "aus" : "an"));
            case SOUND -> { Clicks.cycle(button == 1 ? -1 : 1); Config.save(); silent = true; }
            case ALL_TAB -> { allTab = true; favTab = false; tab(); }
            case CAT -> { category = (Module.Category) hit.a; favTab = false; allTab = false; tab(); }
            case FAV_TAB -> { favTab = true; allTab = false; tab(); }
            case FILTER -> { filter = (Integer) hit.a; scrollMid = 0; }
            case BAR_MID -> { dragBar = 1; dragTo(my); silent = true; }
            case BAR_RIGHT -> { dragBar = 2; dragTo(my); silent = true; }
            case CLOSE -> drawer = false;
            case PROFILE -> dev.teizen.client.core.Profiles.toggle((String) hit.a);
            case HUD -> net.minecraft.client.Minecraft.getInstance().gui.setScreen(new HudEditorScreen(this));
            case SCHEM -> net.minecraft.client.Minecraft.getInstance().gui.setScreen(new SchematicsScreen(this));
            case PACKS -> net.minecraft.client.Minecraft.getInstance().gui.setScreen(new TexturePackScreen(this));
            case SWITCH, DETAIL_SWITCH -> {
                Module m = (Module) hit.a;
                if (button == 0) { flip(m); silent = true; }
                else if (button == 2) binding = m;
                else select(m);
            }
            case CARD -> {
                Module m = (Module) hit.a;
                if (button == 2) { binding = m; select(m); }
                else if (shift && button == 0) { m.favorite = !m.favorite; Config.save(); }
                else if (button == 0 && doubleClick) { flip(m); silent = true; }
                else select(m); // Klick auf den Namen = gleich die Einstellungen
            }
            case STAR -> {
                Module m = (Module) hit.a;
                m.favorite = !m.favorite;
                Config.save();
                Clicks.play(m.favorite ? 1.25f : 0.9f);
                silent = true;
            }
            case KEY -> {
                Module m = (Module) hit.a;
                if (button == 1) { m.key = -1; binding = null; Config.save(); }
                else { binding = m; select(m); }
            }
            case SETTING -> rows.click((Module) hit.a, (SettingRows.Row) hit.b, button);
        }
        if (!silent) Clicks.play(1f);
        return true;
    }

    private void tab() {
        scrollMid = 0;
        search.query = "";
        search.focused = false;
    }

    private void select(Module m) {
        if (selected != m) scrollRight = 0;
        selected = m;
        drawer = true;
    }

    /** Wie ein Klick auf eine Einstellung (auch für den Selbsttest). */
    public void clickSetting(Module m, Setting s, int button) {
        select(m);
        rows.click(m, new SettingRows.Row(SettingRows.Kind.SETTING, s, null), button);
    }

    public boolean isEditing() { return rows.isEditing(); }

    /** Suche setzen (Selbsttest). */
    public void setSearch(String q) { search.query = q; search.focused = !q.isEmpty(); }

    public List<Module> shownModules() { return middle(); }

    /** Einstellungen eines Cheats rechts zeigen (Selbsttest / XRay-Erze direkt zeigen). */
    public void expand(Module m) {
        favTab = false;
        allTab = false;
        category = m.category;
        filter = 0;
        select(m);
    }

    public Module selected() { return selected; }

    /** Ansicht "Alle" ohne rechten Bereich (Selbsttest). */
    public static void showAll() { allTab = true; favTab = false; drawer = false; filter = 0; }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        mx /= uiScale;
        if (mx >= drawerX) {
            double d = restRight - sy * 18;
            int step = (int) d;
            restRight = d - step;
            scrollRight = Math.max(0, scrollRight + step);
        } else {
            double d = restMid - sy * 18;
            int step = (int) d;
            restMid = d - step;
            scrollMid = Math.max(0, scrollMid + step);
        }
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
        if (rows.keyPressed(e)) return true;
        if (binding != null) {
            int k = e.key();
            binding.key = (k == K.DELETE || k == K.BACKSPACE || k == K.ESCAPE) ? -1 : k;
            binding = null;
            Config.save();
            Clicks.play(1.2f);
            return true;
        }
        if (search.keyPressed(e)) return true;
        if (e.key() == K.ESCAPE && drawer && selected != null) { drawer = false; return true; } // Esc schließt erst den rechten Bereich
        if (e.key() == TeizenClient.menuKey) {
            onClose();
            return true;
        }
        if (KeyBinds.overview() != null && KeyBinds.overview().matches(e)) {
            onClose();
            return true;
        }
        return super.keyPressed(e);
    }
}
