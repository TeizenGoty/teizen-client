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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Menü: Linksklick = an/aus, Rechtsklick = Einstellungen auf-/zuklappen,
 * Mittelklick = Taste festlegen. Bei Zahlen: Links = mehr, Rechts = weniger.
 */
public final class ClickGui extends Screen {
    private static final int COL_W = 112, ROW_H = 13, HEAD_H = 16;
    private static final int BG = 0xE0140A26, HEAD = 0xFF5B1FB8, ON = 0xFF7A2CF0, OFF = 0xC0201236, SET = 0xD00E0718;
    private static final Set<String> expanded = new HashSet<>();

    private Module binding;
    private int scroll;

    public ClickGui() {
        super(Component.literal("Teizen Client"));
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private static int panelHeight(Module.Category c) {
        int h = HEAD_H + 2;
        for (Module m : TeizenClient.modules().inCategory(c)) {
            h += ROW_H;
            if (expanded.contains(m.name)) h += ROW_H * m.settings.size();
        }
        return h;
    }

    /** Position jeder Kategorie – bricht in mehrere Reihen um, wenn das Fenster schmal ist. */
    private int[][] layout() {
        Module.Category[] cats = Module.Category.values();
        int cols = Math.max(1, Math.min(cats.length, (width - 8) / (COL_W + 6)));
        int total = cols * (COL_W + 6) - 6;
        int startX = Math.max(4, (width - total) / 2);
        int[][] pos = new int[cats.length][2];
        int rowTop = 32 - scroll;
        for (int row = 0; row * cols < cats.length; row++) {
            int rowH = 0;
            for (int col = 0; col < cols && row * cols + col < cats.length; col++) {
                int i = row * cols + col;
                pos[i][0] = startX + col * (COL_W + 6);
                pos[i][1] = rowTop;
                rowH = Math.max(rowH, panelHeight(cats[i]));
            }
            rowTop += rowH + 8;
        }
        return pos;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        var font = getFont();
        g.fill(0, 0, width, height, 0x60000000);
        String title = "§5§lTEIZEN CLIENT §7– Links: an/aus · Rechts: Einstellungen · Mitte: Taste";
        g.centeredText(font, title, width / 2, 6, 0xFFFFFFFF);
        if (binding != null) g.centeredText(font, "§eDrück eine Taste für " + binding.name + " (Entf = keine)", width / 2, 18, 0xFFFFFFFF);

        Module.Category[] cats = Module.Category.values();
        int[][] pos = layout();
        for (int c = 0; c < cats.length; c++) {
            int x = pos[c][0], y = pos[c][1];
            g.fill(x, y, x + COL_W, y + HEAD_H, HEAD);
            g.centeredText(font, "§l" + cats[c].title, x + COL_W / 2, y + 4, 0xFFFFFFFF);
            y += HEAD_H;
            for (Module m : TeizenClient.modules().inCategory(cats[c])) {
                boolean hover = mouseX >= x && mouseX < x + COL_W && mouseY >= y && mouseY < y + ROW_H;
                g.fill(x, y, x + COL_W, y + ROW_H, m.isEnabled() ? ON : (hover ? 0xD0301A50 : OFF));
                g.text(font, m.name, x + 4, y + 3, m.isEnabled() ? 0xFFFFFFFF : 0xFFB8A6D9, false);
                String right = m.key >= 0 ? Keys.name(m.key) : (m.settings.isEmpty() ? "" : (expanded.contains(m.name) ? "-" : "+"));
                g.text(font, right, x + COL_W - 4 - font.width(right), y + 3, 0xFF9C88C8, false);
                if (hover) g.setTooltipForNextFrame(font, Component.literal(m.description), mouseX, mouseY);
                y += ROW_H;
                if (expanded.contains(m.name)) {
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
            g.fill(x, y, x + COL_W, y + 2, HEAD);
        }
        g.centeredText(font, "§8Befehle im Chat: ///help", width / 2, height - 12, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        double mx = e.x(), my = e.y();
        int button = e.button();
        Module.Category[] cats = Module.Category.values();
        int[][] pos = layout();
        for (int c = 0; c < cats.length; c++) {
            int x = pos[c][0];
            if (mx < x || mx >= x + COL_W) continue;
            int y = pos[c][1] + HEAD_H;
            for (Module m : TeizenClient.modules().inCategory(cats[c])) {
                if (my >= y && my < y + ROW_H) {
                    if (button == 0) m.toggle();
                    else if (button == 1) {
                        if (!expanded.remove(m.name)) expanded.add(m.name);
                    } else if (button == 2) binding = m;
                    Config.save();
                    return true;
                }
                y += ROW_H;
                if (expanded.contains(m.name)) {
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
