package dev.teizen.client.gui;

import dev.teizen.client.TeizenClient;
import dev.teizen.client.core.Config;
import dev.teizen.client.core.KeyBinds;
import dev.teizen.client.core.Module;
import dev.teizen.client.util.Keys;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Bearbeiten eines einzelnen Cheats (Rechtsklick in der Übersicht):
 * an/aus, Taste, Favorit und alle Einstellungen. Esc oder Rechtsklick daneben = zurück.
 */
public final class ModuleScreen extends Screen {
    private static final int W = 240, ROW_H = 15;

    private final Module m;
    private final Screen parent;
    private final SettingRows rows = new SettingRows();
    private boolean waitingKey;
    private int scroll;

    public ModuleScreen(Module m, Screen parent) {
        super(Component.literal(m.name));
        this.m = m;
        this.parent = parent;
    }

    public Module module() { return m; }

    @Override
    public boolean isPauseScreen() { return false; }

    private int x0() { return width / 2 - W / 2; }

    private int top() { return 46 - scroll; }

    private void button(GuiGraphicsExtractor g, int y, String label, int mouseX, int mouseY, boolean active) {
        int x = x0();
        boolean hover = mouseX >= x && mouseX < x + W && mouseY >= y && mouseY < y + ROW_H;
        g.fill(x, y, x + W, y + ROW_H - 1, active ? 0xFF7A2CF0 : (hover ? 0xE0362060 : 0xD0201236));
        g.text(getFont(), label, x + 8, y + 3, 0xFFFFFFFF, false);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        var font = getFont();
        g.fill(0, 0, width, height, 0x90000000);
        int x = x0();
        int y = top();
        button(g, y, "§l" + (m.isEnabled() ? "§aAN" : "§caus") + " §7– anklicken zum Umschalten", mouseX, mouseY, m.isEnabled());
        y += ROW_H;
        button(g, y, "§7Taste: " + (waitingKey ? "§eDrück eine Taste… (Entf = keine)" : "§f" + (m.key >= 0 ? Keys.name(m.key) : "keine")), mouseX, mouseY, waitingKey);
        y += ROW_H;
        button(g, y, "§7Favorit: " + (m.favorite ? "§6★ ja" : "§fnein"), mouseX, mouseY, false);
        y += ROW_H + 6;
        if (m.settings.isEmpty()) {
            g.centeredText(font, "§7Dieser Cheat hat keine Einstellungen.", width / 2, y + 4, 0xFFFFFFFF);
        }
        for (SettingRows.Row r : SettingRows.rows(m)) {
            rows.render(g, font, m, r, x, y, W, ROW_H - 1, mouseX, mouseY);
            y += ROW_H;
        }
        // Kopf
        g.fill(0, 0, width, 40, 0xE0100820);
        g.centeredText(font, "§5§l" + m.name + " §7(" + m.category.title + ")", width / 2, 7, 0xFFFFFFFF);
        String d = "§7" + m.description;
        if (font.width(d) > width - 8) d = font.plainSubstrByWidth(d, width - 12) + "…";
        g.centeredText(font, d, width / 2, 20, 0xFFFFFFFF);
        // Fuß
        g.fill(0, height - 14, width, height, 0xC0000000);
        String foot = rows.hint() != null ? rows.hint()
                : "§7Esc = zurück · Menü: Taste §f" + dev.teizen.client.gui.SettingsScreen.keyName(TeizenClient.menuKey) + " §7· Zahlen anklicken und eintippen";
        if (font.width(foot) > width - 4) foot = font.plainSubstrByWidth(foot, width - 8);
        g.centeredText(font, foot, width / 2, height - 11, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        if (rows.isEditing() && !rows.finish(true)) return true;
        double mx = e.x(), my = e.y();
        int x = x0();
        if (mx < x || mx >= x + W || my < 40) {
            if (e.button() == 1) { back(); return true; }
            return super.mouseClicked(e, doubleClick);
        }
        int y = top();
        if (my >= y && my < y + ROW_H) { m.toggle(); Config.save(); return true; }
        y += ROW_H;
        if (my >= y && my < y + ROW_H) { waitingKey = true; return true; }
        y += ROW_H;
        if (my >= y && my < y + ROW_H) { m.favorite = !m.favorite; Config.save(); return true; }
        y += ROW_H + 6;
        List<SettingRows.Row> list = SettingRows.rows(m);
        for (SettingRows.Row r : list) {
            if (my >= y && my < y + ROW_H) { rows.click(m, r, e.button()); return true; }
            y += ROW_H;
        }
        return super.mouseClicked(e, doubleClick);
    }

    /** Klick auf eine Einstellung (Selbsttest). */
    public void clickRow(SettingRows.Row r, int button) { rows.click(m, r, button); }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        int content = 3 * ROW_H + 6 + SettingRows.rows(m).size() * ROW_H + 60;
        scroll = Math.max(0, Math.min(Math.max(0, content - height + 40), scroll - (int) (sy * 12)));
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent e) {
        if (rows.charTyped(e)) return true;
        return super.charTyped(e);
    }

    private void back() {
        Minecraft.getInstance().gui.setScreen(parent);
    }

    @Override
    public void onClose() { back(); }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (rows.keyPressed(e)) return true;
        if (waitingKey) {
            int k = e.key();
            m.key = (k == GLFW.GLFW_KEY_DELETE || k == GLFW.GLFW_KEY_BACKSPACE || k == GLFW.GLFW_KEY_ESCAPE) ? -1 : k;
            waitingKey = false;
            Config.save();
            return true;
        }
        if (e.key() == TeizenClient.menuKey) {
            Minecraft.getInstance().gui.setScreen(new ClickGui());
            return true;
        }
        if (KeyBinds.overview() != null && KeyBinds.overview().matches(e)) {
            back();
            return true;
        }
        return super.keyPressed(e);
    }
}
