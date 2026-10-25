package dev.teizen.client.gui;

import dev.teizen.client.TeizenClient;
import dev.teizen.client.core.Config;
import dev.teizen.client.core.KeyBinds;
import dev.teizen.client.core.Module;
import dev.teizen.client.util.Keys;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Übersicht: alle Cheats auf einen Blick, mit an/aus und Taste. Favoriten stehen ganz oben.
 * Linksklick = an/aus, Rechtsklick = Cheat bearbeiten, Mittelklick oder Shift+Klick = Favorit.
 * Lupe oben: einfach lostippen zum Suchen. Menü-Taste (Standard Rechte Shift) = großes Menü.
 */
public final class Overview extends Screen {
    private static final int CELL_W = 124, CELL_H = 24, GAP = 4;
    private static final int BG = 0xD0100820, ON = 0xFF6A24D8, OFF = 0xC0201236, HOVER = 0xD0362060;

    private int scroll;
    private final SearchBox search = new SearchBox();

    private List<Module> shown() {
        return search.active() ? SearchBox.search(search.query) : TeizenClient.modules().favoritesFirst();
    }

    /** Suche setzen (Selbsttest). */
    public void setSearch(String q) { search.query = q; search.focused = !q.isEmpty(); }

    public List<Module> shownModules() { return shown(); }

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

    private static final int TOP = 40;

    /** Spalten, Zellgröße, Abstand. Kompakt = eine Zeile pro Cheat, notfalls mehr und schmalere Spalten. */
    private record Layout(int cols, int cellW, int cellH, int stepX, int stepY, boolean compact) {}

    private Layout layout() {
        int n = Math.max(1, shown().size());
        int cols = Math.max(1, Math.min(6, (width - 16 + GAP) / (CELL_W + GAP)));
        int rows = (n + cols - 1) / cols;
        if (TOP + rows * (CELL_H + GAP) <= height - 18) return new Layout(cols, CELL_W, CELL_H, CELL_W + GAP, CELL_H + GAP, false);
        int maxRows = Math.max(1, (height - 18 - TOP) / 14);
        int need = (n + maxRows - 1) / maxRows;
        int c = Math.max(cols, Math.min(8, need));
        int w = Math.min(CELL_W, (width - 8 - (c - 1) * 3) / c);
        if (w < 70) { c = cols; w = CELL_W; } // zu schmal -> lieber scrollen
        return new Layout(c, w, 12, w + 3, 14, true);
    }

    private int startX(Layout l) {
        return (width - (l.cols() * l.stepX() - (l.stepX() - l.cellW()))) / 2;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        var font = getFont();
        List<Module> mods = shown();
        int on = 0;
        for (Module m : TeizenClient.modules().all()) if (m.isEnabled()) on++;

        g.fill(0, 0, width, height, 0x80000000);
        g.text(font, "§5§lTEIZEN §7Übersicht", 6, 6, 0xFFFFFFFF, true);
        int sw = Math.max(80, Math.min(220, width - 2 * (font.width("TEIZEN Übersicht") + 16)));
        search.render(g, font, width / 2 - sw / 2, 4, sw, mouseX, mouseY);
        String head = "§a" + on + "§7 von §f" + TeizenClient.modules().all().size() + "§7 an  §8|  §7Links: an/aus · Rechts: bearbeiten · Mitte/Shift: §6★ Favorit";
        if (font.width(head) > width - 8) head = "§a" + on + "§7 an §8| §7L: an/aus · R: bearbeiten · Mitte: §6★";
        g.centeredText(font, head, width / 2, 22, 0xFFFFFFFF);
        if (search.active() && mods.isEmpty()) g.centeredText(font, "§7Kein Cheat gefunden für \"" + search.query + "\"", width / 2, TOP + 10, 0xFFFFFFFF);

        Layout l = layout();
        int cols = l.cols(), x0 = startX(l), ch = l.cellH(), cw = l.cellW();
        boolean compact = l.compact();
        for (int i = 0; i < mods.size(); i++) {
            Module m = mods.get(i);
            int x = x0 + (i % cols) * l.stepX();
            int y = TOP + (i / cols) * l.stepY() - scroll;
            if (y < TOP - 4 || y + ch > height - 16) continue;
            boolean hover = mouseX >= x && mouseX < x + cw && mouseY >= y && mouseY < y + ch;
            g.fill(x, y, x + cw, y + ch, m.isEnabled() ? ON : (hover ? HOVER : OFF));
            g.fill(x, y, x + 3, y + ch, catColor(m.category));
            if (m.favorite) g.fill(x, y, x + cw, y + 1, 0xFFE8B030);
            String state = m.isEnabled() ? "§aAN" : "§8aus";
            if (compact && m.key >= 0 && cw >= 110) state = "§7" + Keys.name(m.key) + " " + state;
            String name = (m.favorite ? "§6★ §r" : "") + m.name;
            int room = cw - 7 - font.width(state) - 6;
            if (font.width(name) > room) name = font.plainSubstrByWidth(name, room - font.width("…")) + "…";
            g.text(font, name, x + 7, y + 2, m.isEnabled() ? 0xFFFFFFFF : 0xFFC8B8E8, false);
            g.text(font, state, x + cw - 4 - font.width(state), y + 2, 0xFFFFFFFF, false);
            if (hover) g.setTooltipForNextFrame(font, Component.literal(m.description), mouseX, mouseY);
            if (compact) continue;
            String sub = "§7" + m.category.title + (m.key >= 0 ? " §8· §fTaste " + Keys.name(m.key) : "");
            String info = m.isEnabled() ? m.info() : "";
            if (info != null && !info.isEmpty()) sub += " §8· §b" + info;
            if (font.width(sub) > cw - 10) sub = font.plainSubstrByWidth(sub, cw - 14) + "…";
            g.text(font, sub, x + 7, y + 13, 0xFFFFFFFF, false);
        }
        String foot = "§8Schließen: Esc oder " + KeyBinds.overviewName() + "  ·  Großes Menü: " + SettingsScreen.keyName(TeizenClient.menuKey)
                + "  ·  Tasten ändern: Optionen → Steuerung → Teizen Client";
        if (font.width(foot) > width - 4) foot = "§8Esc/" + KeyBinds.overviewName() + " = zu · Menü: " + SettingsScreen.keyName(TeizenClient.menuKey);
        g.centeredText(font, foot, width / 2, height - 12, 0xFFFFFFFF);
    }

    /** Mitte der Zelle eines Cheats (oder null, wenn nicht sichtbar). */
    public double[] cellCenter(Module target) {
        List<Module> mods = shown();
        Layout l = layout();
        int x0 = startX(l);
        int i = mods.indexOf(target);
        if (i < 0) return null;
        int x = x0 + (i % l.cols()) * l.stepX();
        int y = TOP + (i / l.cols()) * l.stepY() - scroll;
        return new double[]{x + l.cellW() / 2.0, y + l.cellH() / 2.0};
    }

    private Module at(double mx, double my) {
        List<Module> mods = shown();
        Layout l = layout();
        int x0 = startX(l);
        for (int i = 0; i < mods.size(); i++) {
            int x = x0 + (i % l.cols()) * l.stepX();
            int y = TOP + (i / l.cols()) * l.stepY() - scroll;
            if (mx >= x && mx < x + l.cellW() && my >= y && my < y + l.cellH()) return mods.get(i);
        }
        return null;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        if (search.click(e.x(), e.y())) return true;
        if (e.y() < TOP - 4) return true;
        Module m = at(e.x(), e.y());
        if (m != null) {
            boolean shift = (e.modifiers() & org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT) != 0;
            if (e.button() == 2 || (e.button() == 0 && shift)) {
                m.favorite = !m.favorite;
                Config.save();
            } else if (e.button() == 0) {
                m.toggle();
                Config.save();
            } else if (e.button() == 1) {
                net.minecraft.client.Minecraft.getInstance().gui.setScreen(new ModuleScreen(m, this));
            }
            return true;
        }
        return super.mouseClicked(e, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        Layout l = layout();
        int rows = (shown().size() + l.cols() - 1) / l.cols();
        int max = Math.max(0, TOP + rows * l.stepY() - (height - 18));
        scroll = Math.max(0, Math.min(max, scroll - (int) (sy * 12)));
        return true;
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent e) {
        if (search.charTyped(e)) { scroll = 0; return true; }
        return super.charTyped(e);
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (search.keyPressed(e)) return true;
        if (e.key() == TeizenClient.menuKey) {
            net.minecraft.client.Minecraft.getInstance().gui.setScreen(new ClickGui());
            return true;
        }
        if (KeyBinds.overview() != null && KeyBinds.overview().matches(e)) {
            onClose();
            return true;
        }
        return super.keyPressed(e);
    }
}
