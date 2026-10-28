package dev.teizen.client.gui;

import dev.teizen.client.TeizenClient;
import dev.teizen.client.core.Config;
import dev.teizen.client.core.KeyBinds;
import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.util.Keys;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import dev.teizen.client.compat.K;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Menü: Linksklick = an/aus, Rechtsklick = Einstellungen auf-/zuklappen,
 * Mittelklick = Taste festlegen, Shift+Klick = Favorit (steht dann ganz vorne).
 * Einstellungen: Zahl/Text anklicken und eintippen (Enter = übernehmen, Esc = abbrechen).
 * Oben die Lupe: einfach lostippen, um einen Cheat zu suchen. Überschrift anklicken = Spalte zuklappen.
 * Übersichts-Taste (Standard O) wechselt zur Übersicht.
 */
public final class ClickGui extends Screen {
    private static final int COL_W = 118, ROW_H = 13, HEAD_H = 16, TOP = 34;
    private static final int HEAD = 0xFF5B1FB8, FAV_HEAD = 0xFFB8861F, SEARCH_HEAD = 0xFF1F8AB8, ON = 0xFF7A2CF0, OFF = 0xC0201236;
    private static final Set<String> expanded = new HashSet<>();
    private static final Set<String> collapsed = new HashSet<>();

    private record Panel(String title, List<Module> mods, int color) {}

    private final SettingRows rows = new SettingRows();
    private final SearchBox search = new SearchBox();
    private Module binding;
    private int scroll;
    /** Unterkante des Inhalts (ohne Scrollen) – fürs Begrenzen des Mausrads. */
    private int contentBottom;
    private int panicX, panicW;
    private int opX, opW;

    public ClickGui() {
        super(Component.literal("Teizen Client"));
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private List<Panel> panels() {
        List<Panel> out = new ArrayList<>();
        if (search.active()) {
            List<Module> hits = SearchBox.search(search.query);
            out.add(new Panel("Suche: " + hits.size() + " Treffer", hits, SEARCH_HEAD));
            return out;
        }
        List<Module> fav = new ArrayList<>();
        for (Module m : TeizenClient.modules().favorites()) if (dev.teizen.client.core.ModuleManager.shown(m)) fav.add(m);
        if (!fav.isEmpty()) out.add(new Panel("★ Favoriten", fav, FAV_HEAD));
        for (Module.Category c : Module.Category.values()) {
            List<Module> l = new ArrayList<>();
            for (Module m : TeizenClient.modules().inCategory(c)) if (dev.teizen.client.core.ModuleManager.shown(m)) l.add(m);
            if (!l.isEmpty()) out.add(new Panel(c.title, l, HEAD));
        }
        return out;
    }

    private static String key(Panel p, Module m) { return (p.title().startsWith("Suche") ? "Suche" : p.title()) + "/" + m.name; }

    private boolean isCollapsed(Panel p) { return !search.active() && collapsed.contains(p.title()); }

    private int panelHeight(Panel p) {
        int h = HEAD_H + 2;
        if (isCollapsed(p)) return h;
        for (Module m : p.mods()) {
            h += ROW_H;
            if (expanded.contains(key(p, m))) h += ROW_H * SettingRows.rows(m).size();
        }
        return h;
    }

    /** Position jeder Spalte – bricht in mehrere Reihen um, wenn das Fenster schmal ist. */
    private int[][] layout(List<Panel> panels) {
        int cols = Math.max(1, Math.min(panels.size(), (width - 8) / (COL_W + 6)));
        int total = cols * (COL_W + 6) - 6;
        int startX = Math.max(4, (width - total) / 2);
        int[][] pos = new int[panels.size()][2];
        int rowTop = TOP - scroll;
        for (int row = 0; row * cols < panels.size(); row++) {
            int rowH = 0;
            for (int col = 0; col < cols && row * cols + col < panels.size(); col++) {
                int i = row * cols + col;
                pos[i][0] = startX + col * (COL_W + 6);
                pos[i][1] = rowTop;
                rowH = Math.max(rowH, panelHeight(panels.get(i)));
            }
            rowTop += rowH + 8;
        }
        return pos;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        var font = getFont();
        g.fill(0, 0, width, height, 0x60000000);

        List<Panel> panels = panels();
        int[][] pos = layout(panels);
        contentBottom = 0;
        for (int c = 0; c < panels.size(); c++) {
            Panel p = panels.get(c);
            int x = pos[c][0], y = pos[c][1];
            g.fill(x, y, x + COL_W, y + HEAD_H, p.color());
            boolean closed = isCollapsed(p);
            g.centeredText(font, "§l" + p.title() + (closed ? " §7(" + p.mods().size() + ") ▸" : ""), x + COL_W / 2, y + 4, 0xFFFFFFFF);
            y += HEAD_H;
            if (!closed) {
                for (Module m : p.mods()) {
                    boolean hover = mouseX >= x && mouseX < x + COL_W && mouseY >= y && mouseY < y + ROW_H && mouseY >= TOP;
                    g.fill(x, y, x + COL_W, y + ROW_H, m.isEnabled() ? ON : (hover ? 0xD0301A50 : OFF));
                    String name = (m.favorite ? "§6★ §r" : "") + m.name;
                    g.text(font, name, x + 4, y + 3, m.isEnabled() ? 0xFFFFFFFF : 0xFFB8A6D9, false);
                    String right = m.key >= 0 ? Keys.name(m.key) : (m.settings.isEmpty() ? "" : (expanded.contains(key(p, m)) ? "-" : "+"));
                    if (m.needsOp()) right = "§6OP §r" + right;
                    g.text(font, right, x + COL_W - 4 - font.width(right), y + 3, 0xFF9C88C8, false);
                    if (hover) g.setTooltipForNextFrame(font, Component.literal(m.description + (m.favorite ? " (★ Favorit)" : "")), mouseX, mouseY);
                    y += ROW_H;
                    if (expanded.contains(key(p, m))) {
                        for (SettingRows.Row r : SettingRows.rows(m)) {
                            rows.render(g, font, m, r, x, y, COL_W, ROW_H, mouseX, mouseY);
                            y += ROW_H;
                        }
                    }
                }
            }
            g.fill(x, y, x + COL_W, y + 2, p.color());
            contentBottom = Math.max(contentBottom, y + 2 + scroll);
        }
        if (search.active() && panels.get(0).mods().isEmpty())
            g.centeredText(font, "§7Kein Cheat gefunden für \"" + search.query + "\"", width / 2, TOP + 26, 0xFFFFFFFF);

        // Kopf: Titel + Lupe (über den Spalten, damit nichts überdeckt wird)
        g.fill(0, 0, width, TOP - 3, 0xC0100820);
        g.text(font, "§5§lTEIZEN CLIENT", 6, 6, 0xFFFFFFFF, true);
        panicW = font.width("PANIK") + 10;
        panicX = width - panicW - 6;
        boolean ph = mouseX >= panicX && mouseX < panicX + panicW && mouseY >= 4 && mouseY < 17;
        g.fill(panicX, 4, panicX + panicW, 17, ph ? 0xFFFF4040 : 0xFFC02020);
        g.text(font, "§lPANIK", panicX + 5, 7, 0xFFFFFFFF, false);
        int sw = Math.max(80, Math.min(220, width - 2 * (font.width("TEIZEN CLIENT") + 16)));
        search.render(g, font, width / 2 - sw / 2, 4, sw, mouseX, mouseY);
        String opLabel = dev.teizen.client.core.ModuleManager.hideOp ? "§6OP: §cversteckt" : "§6OP: §asichtbar";
        opW = font.width(opLabel) + 10;
        opX = width - opW - 6;
        boolean oh = mouseX >= opX && mouseX < opX + opW && mouseY >= 19 && mouseY < 31;
        g.fill(opX, 19, opX + opW, 31, oh ? 0xFF5A3A10 : 0xFF3A2408);
        g.fill(opX, 30, opX + opW, 31, 0xFFE8A030);
        g.text(font, opLabel, opX + 5, 21, 0xFFFFFFFF, false);
        if (oh) g.setTooltipForNextFrame(font, Component.literal("Cheats, die OP-Rechte brauchen, ein-/ausblenden"), mouseX, mouseY);
        String sub = "§7Links: an/aus · Rechts: Einstellungen · Mitte: Taste · Shift+Klick: §6★ Favorit";
        int room = width - 2 * (opW + 12);
        if (font.width(sub) > room) sub = "§7L: an/aus · R: Einst. · Mitte: Taste · Shift: §6★";
        if (font.width(sub) > room) sub = "§7R: Einstellungen";
        g.centeredText(font, sub, width / 2, 20, 0xFFFFFFFF);

        // Fuß: Hinweise
        g.fill(0, height - 14, width, height, 0xC0000000);
        String foot;
        if (binding != null) foot = "§eDrück eine Taste für " + binding.name + " (Entf = keine)";
        else if (rows.hint() != null) foot = rows.hint();
        else foot = "§7Übersicht: Taste §f" + KeyBinds.overviewName() + " §7· Überschrift = zuklappen · Mausrad = scrollen · ///help";
        if (font.width(foot) > width - 4) foot = font.plainSubstrByWidth(foot, width - 8);
        g.centeredText(font, foot, width / 2, height - 11, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        // Klick woanders hin = Eingabe übernehmen
        if (rows.isEditing() && !rows.finish(true)) return true;
        double mx = e.x(), my = e.y();
        if (mx >= opX && mx < opX + opW && my >= 19 && my < 31) {
            dev.teizen.client.core.ModuleManager.hideOp = !dev.teizen.client.core.ModuleManager.hideOp;
            scroll = 0;
            Config.save();
            dev.teizen.client.util.Chat.info(dev.teizen.client.core.ModuleManager.hideOp ? "OP-Cheats ausgeblendet" : "OP-Cheats werden wieder gezeigt");
            return true;
        }
        if (mx >= panicX && mx < panicX + panicW && my >= 4 && my < 17) {
            int n = dev.teizen.client.modules.PanicButton.panic();
            dev.teizen.client.util.Chat.ok("PANIK: " + n + " Cheats ausgeschaltet.");
            return true;
        }
        if (search.click(mx, my)) return true;
        if (my < TOP - 3) return true;
        int button = dev.teizen.client.compat.V.mouse(e.button());
        boolean shift = (e.modifiers() & K.MOD_SHIFT) != 0;
        List<Panel> panels = panels();
        int[][] pos = layout(panels);
        for (int c = 0; c < panels.size(); c++) {
            Panel p = panels.get(c);
            int x = pos[c][0];
            if (mx < x || mx >= x + COL_W) continue;
            if (my >= pos[c][1] && my < pos[c][1] + HEAD_H && button == 0) {
                if (!search.active() && !collapsed.remove(p.title())) collapsed.add(p.title());
                return true;
            }
            int y = pos[c][1] + HEAD_H;
            if (isCollapsed(p)) continue;
            for (Module m : p.mods()) {
                if (my >= y && my < y + ROW_H) {
                    if (shift && button == 0) m.favorite = !m.favorite;
                    else if (button == 0) m.toggle();
                    else if (button == 1) {
                        if (!expanded.remove(key(p, m))) expanded.add(key(p, m));
                    } else if (button == 2) binding = m;
                    Config.save();
                    return true;
                }
                y += ROW_H;
                if (expanded.contains(key(p, m))) {
                    for (SettingRows.Row r : SettingRows.rows(m)) {
                        if (my >= y && my < y + ROW_H) {
                            rows.click(m, r, button);
                            return true;
                        }
                        y += ROW_H;
                    }
                }
            }
        }
        return super.mouseClicked(e, doubleClick);
    }

    /** Wie ein Klick auf eine Einstellung (auch für den Selbsttest). */
    public void clickSetting(Module m, Setting s, int button) {
        rows.click(m, new SettingRows.Row(SettingRows.Kind.SETTING, s, null), button);
    }

    public boolean isEditing() { return rows.isEditing(); }

    /** Suche setzen (Selbsttest). */
    public void setSearch(String q) { search.query = q; search.focused = !q.isEmpty(); }

    /** Cheats, die gerade (mit Suche) angezeigt werden. */
    public List<Module> shownModules() {
        List<Module> out = new ArrayList<>();
        for (Panel p : panels()) out.addAll(p.mods());
        return out;
    }

    /** Einstellungen eines Cheats aufklappen (Selbsttest / XRay-Erze direkt zeigen). */
    public void expand(Module m) {
        for (Panel p : panels()) if (p.mods().contains(m)) expanded.add(key(p, m));
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        int max = Math.max(0, contentBottom - (height - 20));
        scroll = Math.max(0, Math.min(max, scroll - (int) (sy * 16)));
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
            net.minecraft.client.Minecraft.getInstance().gui.setScreen(new Overview());
            return true;
        }
        return super.keyPressed(e);
    }
}
