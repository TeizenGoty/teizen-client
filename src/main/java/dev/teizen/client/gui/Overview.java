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
 * Übersicht: alle Cheats auf einen Blick, mit an/aus und Taste.
 * Linksklick = an/aus, Rechtsklick = Einstellungen (großes Menü).
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

    private int cols() {
        return Math.max(1, Math.min(6, (width - 16 + GAP) / (CELL_W + GAP)));
    }

    private int startX() {
        int c = cols();
        return (width - (c * (CELL_W + GAP) - GAP)) / 2;
    }

    private static final int TOP = 40;

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        var font = getFont();
        List<Module> mods = TeizenClient.modules().all();
        int on = 0;
        for (Module m : mods) if (m.isEnabled()) on++;

        g.fill(0, 0, width, height, 0x80000000);
        g.centeredText(font, "§5§lTEIZEN CLIENT §7– Übersicht", width / 2, 8, 0xFFFFFFFF);
        g.centeredText(font, "§a" + on + "§7 von §f" + mods.size() + "§7 an   §8|   §7Links: an/aus · Rechts: Einstellungen", width / 2, 22, 0xFFFFFFFF);

        int cols = cols(), x0 = startX();
        for (int i = 0; i < mods.size(); i++) {
            Module m = mods.get(i);
            int x = x0 + (i % cols) * (CELL_W + GAP);
            int y = TOP + (i / cols) * (CELL_H + GAP) - scroll;
            if (y + CELL_H < TOP - 4 || y > height - 18) continue;
            boolean hover = mouseX >= x && mouseX < x + CELL_W && mouseY >= y && mouseY < y + CELL_H;
            g.fill(x, y, x + CELL_W, y + CELL_H, m.isEnabled() ? ON : (hover ? HOVER : OFF));
            g.fill(x, y, x + 3, y + CELL_H, catColor(m.category));
            g.text(font, m.name, x + 7, y + 3, m.isEnabled() ? 0xFFFFFFFF : 0xFFC8B8E8, false);
            String state = m.isEnabled() ? "§aAN" : "§8aus";
            g.text(font, state, x + CELL_W - 4 - font.width(state), y + 3, 0xFFFFFFFF, false);
            String sub = "§7" + m.category.title + (m.key >= 0 ? " §8· §fTaste " + Keys.name(m.key) : "");
            String info = m.isEnabled() ? m.info() : "";
            if (info != null && !info.isEmpty()) sub += " §8· §b" + info;
            if (font.width(sub) > CELL_W - 10) sub = font.plainSubstrByWidth(sub, CELL_W - 14) + "…";
            g.text(font, sub, x + 7, y + 13, 0xFFFFFFFF, false);
            if (hover) g.setTooltipForNextFrame(font, Component.literal(m.description), mouseX, mouseY);
        }
        g.centeredText(font, "§8Schließen: Esc oder " + KeyBinds.overviewName() + "  ·  Tasten ändern: Optionen → Steuerung → Teizen Client",
                width / 2, height - 12, 0xFFFFFFFF);
    }

    private Module at(double mx, double my) {
        List<Module> mods = TeizenClient.modules().all();
        int cols = cols(), x0 = startX();
        for (int i = 0; i < mods.size(); i++) {
            int x = x0 + (i % cols) * (CELL_W + GAP);
            int y = TOP + (i / cols) * (CELL_H + GAP) - scroll;
            if (mx >= x && mx < x + CELL_W && my >= y && my < y + CELL_H) return mods.get(i);
        }
        return null;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        Module m = at(e.x(), e.y());
        if (m != null) {
            if (e.button() == 0) {
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
        int rows = (TeizenClient.modules().all().size() + cols() - 1) / cols();
        int max = Math.max(0, TOP + rows * (CELL_H + GAP) - (height - 20));
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
