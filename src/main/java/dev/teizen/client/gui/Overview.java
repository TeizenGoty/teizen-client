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
 * Linksklick = an/aus, Rechtsklick = Einstellungen (großes Menü), Mittelklick oder Shift+Klick = Favorit.
 */
public final class Overview extends Screen {
    private static final int CELL_W = 124, CELL_H = 24, GAP = 4;
    private static final int BG = 0xD0100820, ON = 0xFF6A24D8, OFF = 0xC0201236, HOVER = 0xD0362060;

    private int scroll;

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
        int n = TeizenClient.modules().all().size();
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
        List<Module> mods = TeizenClient.modules().favoritesFirst();
        int on = 0;
        for (Module m : mods) if (m.isEnabled()) on++;

        g.fill(0, 0, width, height, 0x80000000);
        g.centeredText(font, "§5§lTEIZEN CLIENT §7– Übersicht", width / 2, 8, 0xFFFFFFFF);
        g.centeredText(font, "§a" + on + "§7 von §f" + mods.size() + "§7 an   §8|   §7Links: an/aus · Rechts: Einstellungen · Mitte/Shift: §6★ Favorit", width / 2, 22, 0xFFFFFFFF);

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
        g.centeredText(font, "§8Schließen: Esc oder " + KeyBinds.overviewName() + "  ·  Tasten ändern: Optionen → Steuerung → Teizen Client",
                width / 2, height - 12, 0xFFFFFFFF);
    }

    private Module at(double mx, double my) {
        List<Module> mods = TeizenClient.modules().favoritesFirst();
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
                net.minecraft.client.Minecraft.getInstance().gui.setScreen(new ClickGui());
            }
            return true;
        }
        return super.mouseClicked(e, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        Layout l = layout();
        int rows = (TeizenClient.modules().all().size() + l.cols() - 1) / l.cols();
        int max = Math.max(0, TOP + rows * l.stepY() - (height - 18));
        scroll = Math.max(0, Math.min(max, scroll - (int) (sy * 12)));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (KeyBinds.overview() != null && KeyBinds.overview().matches(e)) {
            onClose();
            return true;
        }
        return super.keyPressed(e);
    }
}
