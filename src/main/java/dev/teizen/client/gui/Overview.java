package dev.teizen.client.gui;

import dev.teizen.client.TeizenClient;
import dev.teizen.client.core.Config;
import dev.teizen.client.core.KeyBinds;
import dev.teizen.client.core.Module;
import dev.teizen.client.core.ModuleManager;
import dev.teizen.client.core.Setting;
import dev.teizen.client.modules.PanicButton;
import dev.teizen.client.util.Chat;
import dev.teizen.client.util.Keys;
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
 * Übersicht (Hauptbildschirm, Taste O): ALLE Cheats als große Kacheln, nach Kategorie sortiert –
 * jede Kategorie fängt in einer neuen Reihe an. Mausrad = scrollen.
 * Linksklick = an/aus · Rechtsklick = Einstellungen direkt hier aufklappen · Mitte/Shift = Favorit.
 * Lupe oben: einfach lostippen. Roter Knopf: alles aus.
 */
public final class Overview extends Screen {
    private static final int CELL_W = 124, CELL_H = 24, GAP = 4, TOP = 44, HEAD_H = 14, ROW_H = 13;
    private static final int ON = 0xFF6A24D8, OFF = 0xC0201236, HOVER = 0xD0362060;

    private record Cell(Module m, int x, int y) {}
    private record Header(String title, int y, int color) {}
    private record PanelRow(SettingRows.Row row, int x, int y, int w) {}

    private final SearchBox search = new SearchBox();
    private final SettingRows rows = new SettingRows();
    private int scroll;
    private Module expanded;
    private Module binding;

    // Aufbau (wird bei jedem Bild neu berechnet)
    private final List<Cell> cells = new ArrayList<>();
    private final List<Header> headers = new ArrayList<>();
    private final List<PanelRow> panelRows = new ArrayList<>();
    private int panelY = -1, panelH, gridX, gridW, contentH;
    private int btnOnX, btnKeyX, btnFavX, btnCloseX, btnW;
    private int panicX, panicY, panicW;
    private int opX, opY = 20, opW;
    /** Rechts: Knöpfe "Alle", "Kampf", "Abbau" ... – zeigt nur diese Kategorie. null = alle. */
    public static Module.Category filter;
    private static final int SIDE_W = 84, SIDE_BTN_H = 18;
    private int sideX;

    public Overview() {
        super(Component.literal("Teizen Übersicht"));
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private static int catColor(Module.Category c) {
        return switch (c) {
            case KAMPF -> 0xFFE04848;
            case ABBAU -> 0xFF33C8E8;
            case BEWEGUNG -> 0xFF48D868;
            case SICHT -> 0xFFE8D040;
            case BAUEN -> 0xFFE89040;
            case SONSTIGES -> 0xFFB088F0;
        };
    }

    private record Section(String title, List<Module> mods, int color) {}

    private List<Section> sections() {
        List<Section> out = new ArrayList<>();
        if (search.active()) {
            List<Module> hits = SearchBox.search(search.query);
            out.add(new Section("Suche: " + hits.size() + " Treffer", hits, 0xFF1F8AB8));
            return out;
        }
        List<Module> fav = new ArrayList<>();
        for (Module m : TeizenClient.modules().favorites()) if (ModuleManager.shown(m)) fav.add(m);
        if (!fav.isEmpty() && filter == null) out.add(new Section("★ Favoriten", fav, 0xFFE8B030));
        if (filter != null) {
            // nur eine Kategorie (Knopf rechts): alle ihre Cheats, Favoriten zuerst
            List<Module> l = new ArrayList<>();
            for (Module m : TeizenClient.modules().inCategory(filter)) if (m.favorite && ModuleManager.shown(m)) l.add(m);
            for (Module m : TeizenClient.modules().inCategory(filter)) if (!m.favorite && ModuleManager.shown(m)) l.add(m);
            out.add(new Section(filter.title, l, catColor(filter)));
            return out;
        }
        for (Module.Category c : Module.Category.values()) {
            List<Module> l = new ArrayList<>();
            for (Module m : TeizenClient.modules().inCategory(c)) if (!m.favorite && ModuleManager.shown(m)) l.add(m);
            if (!l.isEmpty()) out.add(new Section(c.title, l, catColor(c)));
        }
        return out;
    }

    /** Alle angezeigten Cheats in Reihenfolge (Selbsttest). */
    private List<Module> shown() {
        List<Module> out = new ArrayList<>();
        for (Section s : sections()) out.addAll(s.mods());
        return out;
    }

    public List<Module> shownModules() { return shown(); }

    /** Suche setzen (Selbsttest). */
    public void setSearch(String q) { search.query = q; search.focused = !q.isEmpty(); scroll = 0; }

    /** Cheat, dessen Einstellungen gerade aufgeklappt sind. */
    public Module expanded() { return expanded; }

    public boolean isEditing() { return rows.isEditing(); }

    /** Einstellungen eines Cheats auf-/zuklappen (wie Rechtsklick). */
    public void toggleExpand(Module m) {
        if (rows.isEditing()) rows.finish(true);
        expanded = expanded == m ? null : m;
        binding = null;
    }

    /** Wie ein Klick auf eine Einstellung im aufgeklappten Bereich (Selbsttest). */
    public void clickSetting(Setting s, int button) {
        if (expanded != null) rows.click(expanded, new SettingRows.Row(SettingRows.Kind.SETTING, s, null), button);
    }

    /** Wie ein Klick auf eine beliebige Zeile im aufgeklappten Bereich (Selbsttest). */
    public void clickRow(SettingRows.Row r, int button) {
        if (expanded != null) rows.click(expanded, r, button);
    }

    public int scroll() { return scroll; }

    /** OP-Cheats ein-/ausblenden (Knopf oben rechts). */
    public void toggleOpHidden() {
        ModuleManager.hideOp = !ModuleManager.hideOp;
        if (expanded != null && !ModuleManager.shown(expanded)) expanded = null;
        scroll = 0;
        Config.save();
        Chat.info(ModuleManager.hideOp ? "OP-Cheats ausgeblendet (" + TeizenClient.modules().hiddenCount() + " versteckt)" : "OP-Cheats werden wieder gezeigt");
    }

    /** Mitte des OP-Knopfs (nach dem ersten Bild). */
    public double[] opButtonCenter() { return new double[]{opX + opW / 2.0, opY + 6.5}; }

    /** Mitte des roten PANIK-Knopfs (nach dem ersten Bild). */
    public double[] panicCenter() { return new double[]{panicX + panicW / 2.0, panicY + 6.5}; }

    /** Fängt jede Kategorie in einer neuen Reihe an (erste Kachel ganz links)? (Selbsttest) */
    public boolean categoriesStartNewRow() {
        layout();
        for (Header h : headers) {
            Cell first = null;
            for (Cell c : cells) if (c.y() > h.y() && (first == null || c.y() < first.y() || (c.y() == first.y() && c.x() < first.x()))) first = c;
            if (first == null) continue;
            if (first.x() != gridX) return false;
            // keine Kachel einer anderen Kategorie in derselben Reihe
            for (Cell c : cells) if (c.y() == first.y() && c.m().category != first.m().category && !search.active() && !first.m().favorite) return false;
        }
        return !headers.isEmpty();
    }

    public int maxScroll() { return Math.max(0, contentH - (height - 16 - TOP)); }

    private void layout() {
        cells.clear();
        headers.clear();
        panelRows.clear();
        panelY = -1;
        sideX = width - SIDE_W - 9;
        int avail = sideX - 8;
        int cols = Math.max(1, Math.min(7, (avail - 8 + GAP) / (CELL_W + GAP)));
        gridW = cols * (CELL_W + GAP) - GAP;
        gridX = Math.max(4, (avail - gridW) / 2);
        int y = 0;
        for (Section s : sections()) {
            headers.add(new Header(s.title() + " §7(" + s.mods().size() + ")", y, s.color()));
            y += HEAD_H;
            List<Module> mods = s.mods();
            for (int i = 0; i < mods.size(); i += cols) {
                boolean hasPanel = false;
                for (int c = 0; c < cols && i + c < mods.size(); c++) {
                    Module m = mods.get(i + c);
                    cells.add(new Cell(m, gridX + c * (CELL_W + GAP), y));
                    if (m == expanded) hasPanel = true;
                }
                y += CELL_H + GAP;
                if (hasPanel) {
                    panelY = y;
                    panelH = buildPanel(y);
                    y += panelH + GAP + 2;
                }
            }
            y += 6;
        }
        contentH = y;
    }

    /** Einstellungs-Bereich unter der Reihe des Cheats; Einstellungen in Spalten. @return Höhe */
    private int buildPanel(int y) {
        List<SettingRows.Row> list = SettingRows.rows(expanded);
        int k = Math.max(1, Math.min(3, gridW / 180));
        if (list.size() <= 4) k = Math.min(k, Math.max(1, list.size()));
        // Blöcke: eine Einstellung mit ihrer aufgeklappten Liste bleibt zusammen in einer Spalte
        List<List<SettingRows.Row>> blocks = new ArrayList<>();
        for (SettingRows.Row r : list) {
            if (r.kind() == SettingRows.Kind.SETTING || blocks.isEmpty()) blocks.add(new ArrayList<>());
            blocks.get(blocks.size() - 1).add(r);
        }
        int target = Math.max(1, (list.size() + k - 1) / k);
        int colW = (gridW - 8 - (k - 1) * 6) / k;
        int col = 0, row = 0, maxRows = 1;
        for (List<SettingRows.Row> b : blocks) {
            if (row > 0 && row + b.size() > target && col < k - 1) {
                col++;
                row = 0;
            }
            for (SettingRows.Row r : b) {
                panelRows.add(new PanelRow(r, gridX + 4 + col * (colW + 6), y + 18 + row * ROW_H, colW));
                row++;
            }
            maxRows = Math.max(maxRows, row);
        }
        int body = list.isEmpty() ? ROW_H : maxRows * ROW_H;
        return 18 + body + 6;
    }

    private boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        var font = getFont();
        layout();
        int maxS = maxScroll();
        if (scroll > maxS) scroll = maxS;
        int on = 0;
        for (Module m : TeizenClient.modules().all()) if (m.isEnabled()) on++;
        g.fill(0, 0, width, height, 0x80000000);

        int off = TOP - scroll;
        int clipTop = TOP - 2, clipBottom = height - 16;
        for (Header h : headers) {
            int y = h.y() + off;
            if (y < clipTop - HEAD_H || y > clipBottom) continue;
            g.fill(gridX, y + HEAD_H - 3, gridX + gridW, y + HEAD_H - 2, (h.color() & 0x00FFFFFF) | 0x80000000);
            g.text(font, "§l" + h.title(), gridX + 2, y + 2, h.color(), true);
        }
        for (Cell c : cells) {
            Module m = c.m();
            int x = c.x(), y = c.y() + off;
            if (y + CELL_H < clipTop || y > clipBottom) continue;
            boolean hover = in(mouseX, mouseY, x, y, CELL_W, CELL_H) && mouseY >= clipTop && mouseY < clipBottom;
            g.fill(x, y, x + CELL_W, y + CELL_H, m.isEnabled() ? ON : (hover ? HOVER : OFF));
            g.fill(x, y, x + 3, y + CELL_H, catColor(m.category));
            if (m.favorite) g.fill(x, y, x + CELL_W, y + 1, 0xFFE8B030);
            if (m == expanded) g.fill(x, y + CELL_H - 2, x + CELL_W, y + CELL_H, 0xFFFFD040);
            String state = m.isEnabled() ? "§aAN" : "§8aus";
            String name = (m.favorite ? "§6★ §r" : "") + m.name;
            int room = CELL_W - 7 - font.width(state) - 6;
            if (font.width(name) > room) name = font.plainSubstrByWidth(name, room - font.width("…")) + "…";
            g.text(font, name, x + 7, y + 2, m.isEnabled() ? 0xFFFFFFFF : 0xFFC8B8E8, false);
            g.text(font, state, x + CELL_W - 4 - font.width(state), y + 2, 0xFFFFFFFF, false);
            String sub = "§7" + m.category.title + (m.needsOp() ? " §6OP" : "") + (m.key >= 0 ? " §8· §fTaste " + Keys.name(m.key) : "");
            if (m.requirement() != null) sub += " §c" + m.requirement();
            String info = m.isEnabled() ? m.info() : "";
            if (info != null && !info.isEmpty()) sub += " §8· §b" + info;
            if (!m.settings.isEmpty()) sub += m == expanded ? " §e▾" : " §8▸";
            if (font.width(sub) > CELL_W - 10) sub = font.plainSubstrByWidth(sub, CELL_W - 14) + "…";
            g.text(font, sub, x + 7, y + 13, 0xFFFFFFFF, false);
            if (hover) g.setTooltipForNextFrame(font, Component.literal(m.description + " §7(Rechtsklick = Einstellungen)"), mouseX, mouseY);
        }
        if (expanded != null && panelY >= 0) {
            int y = panelY + off;
            g.fill(gridX - 1, y - 1, gridX + gridW + 1, y + panelH + 1, 0xFFFFD040);
            g.fill(gridX, y, gridX + gridW, y + panelH, 0xF0100820);
            // Kopfzeile mit Knöpfen
            btnW = 78;
            btnCloseX = gridX + gridW - 16;
            btnFavX = btnCloseX - 4 - btnW;
            btnKeyX = btnFavX - 4 - btnW;
            btnOnX = btnKeyX - 4 - btnW;
            String title = "§e§l" + expanded.name + " §7– " + expanded.description;
            int titleRoom = btnOnX - gridX - 10;
            if (font.width(title) > titleRoom) title = font.plainSubstrByWidth(title, titleRoom - 6) + "…";
            g.text(font, title, gridX + 5, y + 5, 0xFFFFFFFF, false);
            button(g, btnOnX, y + 2, btnW, expanded.isEnabled() ? "§aAN" : "§caus", expanded.isEnabled(), mouseX, mouseY);
            button(g, btnKeyX, y + 2, btnW, binding == expanded ? "§eTaste drücken…" : "Taste: §f" + (expanded.key >= 0 ? Keys.name(expanded.key) : "keine"), binding == expanded, mouseX, mouseY);
            button(g, btnFavX, y + 2, btnW, expanded.favorite ? "§6★ Favorit" : "☆ Favorit", false, mouseX, mouseY);
            button(g, btnCloseX, y + 2, 14, "§c✕", false, mouseX, mouseY);
            if (panelRows.isEmpty()) g.text(font, "§7Dieser Cheat hat keine Einstellungen.", gridX + 6, y + 21, 0xFFFFFFFF, false);
            for (PanelRow r : panelRows) {
                int ry = r.y() + off;
                if (ry + ROW_H < clipTop || ry > clipBottom) continue;
                rows.render(g, font, expanded, r.row(), r.x(), ry, r.w(), ROW_H - 1, mouseX, mouseY);
            }
        }
        if (search.active() && cells.isEmpty()) g.centeredText(font, "§7Kein Cheat gefunden für \"" + search.query + "\"", width / 2, TOP + 20, 0xFFFFFFFF);

        // Kategorie-Knöpfe rechts
        g.fill(sideX - 3, TOP - 2, sideX + SIDE_W + 2, clipBottom, 0x90100820);
        g.text(font, "§7Kategorien", sideX + 2, TOP + 1, 0xFFFFFFFF, false);
        for (int i = 0; i <= Module.Category.values().length; i++) {
            Module.Category c = i == 0 ? null : Module.Category.values()[i - 1];
            int by = sideButtonY(i);
            boolean active = filter == c;
            boolean hover = in(mouseX, mouseY, sideX, by, SIDE_W, SIDE_BTN_H - 2);
            int col = c == null ? 0xFFB0A0D0 : catColor(c);
            g.fill(sideX, by, sideX + SIDE_W, by + SIDE_BTN_H - 2, active ? 0xFF6A24D8 : (hover ? 0xE0362060 : 0xD0281646));
            g.fill(sideX, by, sideX + 3, by + SIDE_BTN_H - 2, col);
            int n = 0, an = 0;
            for (Module m : TeizenClient.modules().all()) {
                if (!ModuleManager.shown(m) || (c != null && m.category != c)) continue;
                n++;
                if (m.isEnabled()) an++;
            }
            String label = (c == null ? "Alle" : c.title);
            g.text(font, (active ? "§f§l" : "§f") + label, sideX + 7, by + 4, 0xFFFFFFFF, false);
            String cnt = (an > 0 ? "§a" + an + "§7/" : "§7") + n;
            g.text(font, cnt, sideX + SIDE_W - 4 - font.width(cnt), by + 4, 0xFFFFFFFF, false);
        }

        // Bildlaufleiste
        if (maxS > 0) {
            int trackH = clipBottom - TOP;
            int thumbH = Math.max(16, trackH * trackH / Math.max(1, contentH));
            int thumbY = TOP + (int) ((trackH - thumbH) * (scroll / (double) maxS));
            g.fill(width - 5, TOP, width - 2, clipBottom, 0x40FFFFFF);
            g.fill(width - 5, thumbY, width - 2, thumbY + thumbH, 0xFF9C88C8);
        }

        // Kopf (über dem Inhalt)
        g.fill(0, 0, width, TOP - 3, 0xE0100820);
        g.text(font, "§5§lTEIZEN §7Übersicht", 6, 6, 0xFFFFFFFF, true);
        int sw = Math.max(80, Math.min(220, width - 2 * (font.width("TEIZEN Übersicht") + 60)));
        search.render(g, font, width / 2 - sw / 2, 4, sw, mouseX, mouseY);
        panicW = font.width("PANIK – alles aus") + 10;
        panicX = width - panicW - 6;
        panicY = 4;
        boolean ph = in(mouseX, mouseY, panicX, panicY, panicW, 13);
        g.fill(panicX, panicY, panicX + panicW, panicY + 13, ph ? 0xFFFF4040 : 0xFFC02020);
        g.text(font, "§lPANIK §r– alles aus", panicX + 5, panicY + 3, 0xFFFFFFFF, false);
        String opLabel = ModuleManager.hideOp ? "§6OP-Cheats: §cversteckt" : "§6OP-Cheats: §asichtbar";
        opW = font.width(opLabel) + 10;
        opX = width - opW - 6;
        boolean oh = in(mouseX, mouseY, opX, opY, opW, 13);
        g.fill(opX, opY, opX + opW, opY + 13, oh ? 0xFF5A3A10 : 0xFF3A2408);
        g.fill(opX, opY + 12, opX + opW, opY + 13, 0xFFE8A030);
        g.text(font, opLabel, opX + 5, opY + 3, 0xFFFFFFFF, false);
        if (oh) g.setTooltipForNextFrame(font, Component.literal("Cheats, die OP-Rechte brauchen (z.B. God, NoClip, NoCooldown), ein-/ausblenden"), mouseX, mouseY);
        String head = "§a" + on + "§7 von §f" + TeizenClient.modules().all().size() + "§7 an  §8|  §7Links: an/aus · Rechts: Einstellungen · Mitte/Shift: §6★ · §7Mausrad: scrollen";
        int room = width - 2 * (opW + 12);
        if (font.width(head) > room) head = "§a" + on + "§7 an §8| §7L: an/aus · R: Einst. · Mitte: §6★ §7· Mausrad";
        if (font.width(head) > room) head = "§a" + on + "§7 an §8| §7R: Einstellungen";
        g.centeredText(font, head, width / 2, 24, 0xFFFFFFFF);

        String foot;
        if (binding != null) foot = "§eDrück eine Taste für " + binding.name + " (Entf = keine)";
        else if (rows.hint() != null) foot = rows.hint();
        else foot = "§8Schließen: Esc oder " + KeyBinds.overviewName() + "  ·  Großes Menü: " + SettingsScreen.keyName(TeizenClient.menuKey)
                + "  ·  Tasten: Optionen → Steuerung → Teizen Client";
        g.fill(0, height - 14, width, height, 0xC0000000);
        if (font.width(foot) > width - 4) foot = font.plainSubstrByWidth(foot, width - 8);
        g.centeredText(font, foot, width / 2, height - 11, 0xFFFFFFFF);
    }

    private void button(GuiGraphicsExtractor g, int x, int y, int w, String label, boolean active, int mx, int my) {
        boolean hover = in(mx, my, x, y, w, 13);
        g.fill(x, y, x + w, y + 13, active ? 0xFF7A2CF0 : (hover ? 0xE0362060 : 0xD0281646));
        var font = getFont();
        String l = font.width(label) > w - 4 ? font.plainSubstrByWidth(label, w - 6) : label;
        g.text(font, l, x + (w - font.width(l)) / 2, y + 3, 0xFFFFFFFF, false);
    }

    private int sideButtonY(int i) { return TOP + 12 + i * SIDE_BTN_H; }

    /** Nur eine Kategorie zeigen (null = alle). */
    public void setFilter(Module.Category c) {
        filter = c;
        scroll = 0;
        if (expanded != null && c != null && expanded.category != c) expanded = null;
        if (search.active()) search.query = "";
    }

    /** Mitte eines Kategorie-Knopfs rechts (null = "Alle"), für den Selbsttest. */
    public double[] categoryButtonCenter(Module.Category c) {
        layout();
        int i = c == null ? 0 : c.ordinal() + 1;
        return new double[]{sideX + SIDE_W / 2.0, sideButtonY(i) + (SIDE_BTN_H - 2) / 2.0};
    }

    /** Mitte der Zelle eines Cheats (oder null, wenn nicht da). */
    public double[] cellCenter(Module target) {
        layout();
        for (Cell c : cells) {
            if (c.m() == target) return new double[]{c.x() + CELL_W / 2.0, c.y() + TOP - scroll + CELL_H / 2.0};
        }
        return null;
    }

    /** Scrollt, bis der Cheat zu sehen ist (Selbsttest / Suche). */
    public void scrollTo(Module target) {
        layout();
        for (Cell c : cells) {
            if (c.m() != target) continue;
            int visible = height - 16 - TOP;
            if (c.y() < scroll) scroll = c.y();
            else if (c.y() + CELL_H > scroll + visible) scroll = c.y() + CELL_H - visible;
            scroll = Math.max(0, Math.min(maxScroll(), scroll));
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        if (rows.isEditing() && !rows.finish(true)) return true;
        double mx = e.x(), my = e.y();
        if (in(mx, my, panicX, panicY, panicW, 13)) {
            int n = PanicButton.panic();
            Chat.ok("PANIK: " + n + " Cheats ausgeschaltet.");
            return true;
        }
        if (in(mx, my, opX, opY, opW, 13)) {
            toggleOpHidden();
            return true;
        }
        if (search.click(mx, my)) return true;
        if (my < TOP - 3 || my >= height - 16) return true;
        layout();
        // Kategorie-Knöpfe rechts
        for (int i = 0; i <= Module.Category.values().length; i++) {
            if (!in(mx, my, sideX, sideButtonY(i), SIDE_W, SIDE_BTN_H - 2)) continue;
            setFilter(i == 0 ? null : Module.Category.values()[i - 1]);
            return true;
        }
        if (mx >= sideX - 3) return true;
        int off = TOP - scroll;
        int button = e.button();
        // aufgeklappter Bereich
        if (expanded != null && panelY >= 0 && in(mx, my, gridX, panelY + off, gridW, panelH)) {
            int y = panelY + off + 2;
            if (in(mx, my, btnCloseX, y, 14, 13)) { toggleExpand(expanded); return true; }
            if (in(mx, my, btnOnX, y, btnW, 13)) { expanded.toggle(); Config.save(); return true; }
            if (in(mx, my, btnKeyX, y, btnW, 13)) { binding = binding == expanded ? null : expanded; return true; }
            if (in(mx, my, btnFavX, y, btnW, 13)) { expanded.favorite = !expanded.favorite; Config.save(); return true; }
            for (PanelRow r : panelRows) {
                if (in(mx, my, r.x(), r.y() + off, r.w(), ROW_H)) {
                    rows.click(expanded, r.row(), button);
                    return true;
                }
            }
            return true;
        }
        for (Cell c : cells) {
            if (!in(mx, my, c.x(), c.y() + off, CELL_W, CELL_H)) continue;
            Module m = c.m();
            boolean shift = (e.modifiers() & K.MOD_SHIFT) != 0;
            if (button == 2 || (button == 0 && shift)) {
                m.favorite = !m.favorite;
                Config.save();
            } else if (button == 0) {
                m.toggle();
                Config.save();
            } else if (button == 1) {
                toggleExpand(m);
            }
            return true;
        }
        return super.mouseClicked(e, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        layout();
        scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) (sy * 24)));
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent e) {
        if (rows.charTyped(e)) return true;
        if (binding == null && search.charTyped(e)) { scroll = 0; return true; }
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
            return true;
        }
        if (search.keyPressed(e)) return true;
        if (e.key() == K.ESCAPE && expanded != null) { toggleExpand(expanded); return true; }
        if (e.key() == TeizenClient.menuKey) {
            net.minecraft.client.Minecraft.getInstance().gui.setScreen(new ClickGui());
            return true;
        }
        if (KeyBinds.overview() != null && KeyBinds.overview().matches(e)) {
            onClose();
            return true;
        }
        if (e.key() == K.PAGE_DOWN || e.key() == K.DOWN) { scroll = Math.min(maxScroll(), scroll + (e.key() == K.DOWN ? 24 : height / 2)); return true; }
        if (e.key() == K.PAGE_UP || e.key() == K.UP) { scroll = Math.max(0, scroll - (e.key() == K.UP ? 24 : height / 2)); return true; }
        return super.keyPressed(e);
    }
}
