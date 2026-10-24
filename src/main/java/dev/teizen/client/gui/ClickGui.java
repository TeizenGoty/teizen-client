package dev.teizen.client.gui;

import dev.teizen.client.TeizenClient;
import dev.teizen.client.core.Config;
import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.util.Keys;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Menü: Linksklick = an/aus, Rechtsklick = Einstellungen auf-/zuklappen,
 * Mittelklick = Taste festlegen, Shift+Klick = Favorit (steht dann ganz vorne).
 * Klick auf eine Spalten-Überschrift klappt die Spalte zu/auf, Mausrad scrollt.
 * Bei Zahlen: Links = mehr, Rechts = weniger.
 */
public final class ClickGui extends Screen {
    private static final int COL_W = 112, ROW_H = 13, HEAD_H = 16;
    private static final int HEAD = 0xFF5B1FB8, FAV_HEAD = 0xFFB8861F, ON = 0xFF7A2CF0, OFF = 0xC0201236, SET = 0xD00E0718;
    private static final Set<String> expanded = new HashSet<>();
    /** Zugeklappte Spalten (Klick auf die Überschrift). */
    private static final Set<String> collapsed = new HashSet<>();

    private record Panel(String title, List<Module> mods, int color) {}

    private Module binding;
    private int scroll;

    public ClickGui() {
        super(Component.literal("Teizen Client"));
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private static List<Panel> panels() {
        List<Panel> out = new ArrayList<>();
        List<Module> fav = TeizenClient.modules().favorites();
        if (!fav.isEmpty()) out.add(new Panel("★ Favoriten", fav, FAV_HEAD));
        for (Module.Category c : Module.Category.values()) out.add(new Panel(c.title, TeizenClient.modules().inCategory(c), HEAD));
        return out;
    }

    private static String key(Panel p, Module m) { return p.title() + "/" + m.name; }

    private static int panelHeight(Panel p) {
        int h = HEAD_H + 2;
        if (collapsed.contains(p.title())) return h;
        for (Module m : p.mods()) {
            h += ROW_H;
            if (expanded.contains(key(p, m))) h += ROW_H * m.settings.size();
        }
        return h;
    }

    /** Position jeder Spalte – bricht in mehrere Reihen um, wenn das Fenster schmal ist. */
    private int[][] layout(List<Panel> panels) {
        int cols = Math.max(1, Math.min(panels.size(), (width - 8) / (COL_W + 6)));
        int total = cols * (COL_W + 6) - 6;
        int startX = Math.max(4, (width - total) / 2);
        int[][] pos = new int[panels.size()][2];
        int rowTop = 32 - scroll;
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
        String title = "§5§lTEIZEN CLIENT §7– Links: an/aus · Rechts: Einstellungen · Mitte: Taste · Shift+Klick: §6★ Favorit";
        if (font.width(title) > width - 8) title = "§5§lTEIZEN §7– L: an/aus · R: Einst. · Mitte: Taste · Shift: §6★";
        g.centeredText(font, title, width / 2, 6, 0xFFFFFFFF);
        if (binding != null) g.centeredText(font, "§eDrück eine Taste für " + binding.name + " (Entf = keine)", width / 2, 18, 0xFFFFFFFF);

        List<Panel> panels = panels();
        int[][] pos = layout(panels);
        for (int c = 0; c < panels.size(); c++) {
            Panel p = panels.get(c);
            int x = pos[c][0], y = pos[c][1];
            g.fill(x, y, x + COL_W, y + HEAD_H, p.color());
            boolean closed = collapsed.contains(p.title());
            g.centeredText(font, "§l" + p.title() + (closed ? " §7(" + p.mods().size() + ") ▸" : ""), x + COL_W / 2, y + 4, 0xFFFFFFFF);
            y += HEAD_H;
            for (Module m : closed ? List.<Module>of() : p.mods()) {
                boolean hover = mouseX >= x && mouseX < x + COL_W && mouseY >= y && mouseY < y + ROW_H;
                g.fill(x, y, x + COL_W, y + ROW_H, m.isEnabled() ? ON : (hover ? 0xD0301A50 : OFF));
                String name = (m.favorite ? "§6★ §r" : "") + m.name;
                g.text(font, name, x + 4, y + 3, m.isEnabled() ? 0xFFFFFFFF : 0xFFB8A6D9, false);
                String right = m.key >= 0 ? Keys.name(m.key) : (m.settings.isEmpty() ? "" : (expanded.contains(key(p, m)) ? "-" : "+"));
                g.text(font, right, x + COL_W - 4 - font.width(right), y + 3, 0xFF9C88C8, false);
                if (hover) g.setTooltipForNextFrame(font, Component.literal(m.description + (m.favorite ? " (★ Favorit)" : "")), mouseX, mouseY);
                y += ROW_H;
                if (expanded.contains(key(p, m))) {
                    for (Setting s : m.settings.values()) {
                        g.fill(x, y, x + COL_W, y + ROW_H, SET);
                        String label = "§7" + s.name + ": " + (s.type == Setting.Type.BOOL ? (s.on() ? "§aan" : "§caus") : "§f" + s.display());
                        if (font.width(label) > COL_W - 10) label = font.plainSubstrByWidth(label, COL_W - 14) + "…";
                        g.text(font, label, x + 8, y + 3, 0xFFFFFFFF, false);
                        boolean sh = mouseX >= x && mouseX < x + COL_W && mouseY >= y && mouseY < y + ROW_H;
                        if (sh) g.setTooltipForNextFrame(font, Component.literal(s.description + (s.type == Setting.Type.NUMBER ? " (Links +, Rechts -)" : "")), mouseX, mouseY);
                        y += ROW_H;
                    }
                }
            }
            g.fill(x, y, x + COL_W, y + 2, p.color());
        }
        g.fill(0, height - 14, width, height, 0xA0000000);
        String foot = "§7Überschrift anklicken = zuklappen · Mausrad = scrollen · Befehle: ///help · Favorit: ///fav <Funktion>";
        if (font.width(foot) > width - 4) foot = "§7Überschrift = zuklappen · Mausrad = scrollen · ///help";
        g.centeredText(font, foot, width / 2, height - 11, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        double mx = e.x(), my = e.y();
        int button = e.button();
        boolean shift = (e.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
        List<Panel> panels = panels();
        int[][] pos = layout(panels);
        for (int c = 0; c < panels.size(); c++) {
            Panel p = panels.get(c);
            int x = pos[c][0];
            if (mx < x || mx >= x + COL_W) continue;
            if (my >= pos[c][1] && my < pos[c][1] + HEAD_H && button == 0) {
                if (!collapsed.remove(p.title())) collapsed.add(p.title());
                return true;
            }
            int y = pos[c][1] + HEAD_H;
            if (collapsed.contains(p.title())) continue;
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
                    for (Setting s : m.settings.values()) {
                        if (my >= y && my < y + ROW_H) {
                            clickSetting(s, button);
                            Config.save();
                            return true;
                        }
                        y += ROW_H;
                    }
                }
            }
        }
        return super.mouseClicked(e, doubleClick);
    }

    private static void clickSetting(Setting s, int button) {
        switch (s.type) {
            case BOOL -> s.setBool(!s.on());
            case NUMBER -> {
                double range = s.max - s.min;
                double step = range <= 2 ? 0.05 : range <= 20 ? 0.5 : range <= 200 ? 5 : 50;
                if (s.max - s.min >= 1 && s.num() == Math.rint(s.num()) && range > 2 && range <= 20) step = 1;
                s.setNumber(Math.round((s.num() + (button == 1 ? -step : step)) * 100.0) / 100.0);
            }
            case CHOICE -> {
                List<String> c = s.choices;
                int i = c.indexOf(s.str());
                s.setText(c.get((i + (button == 1 ? c.size() - 1 : 1)) % c.size()));
            }
            case TEXT -> dev.teizen.client.util.Chat.info("Ändern mit: ///set <Funktion> " + s.name + " <Text>");
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        scroll = Math.max(0, scroll - (int) (sy * 12));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (binding != null) {
            int k = e.key();
            binding.key = (k == GLFW.GLFW_KEY_DELETE || k == GLFW.GLFW_KEY_BACKSPACE || k == GLFW.GLFW_KEY_ESCAPE) ? -1 : k;
            binding = null;
            Config.save();
            return true;
        }
        if (e.key() == TeizenClient.menuKey) {
            onClose();
            return true;
        }
        return super.keyPressed(e);
    }
}
