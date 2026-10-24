package dev.teizen.client.gui;

import dev.teizen.client.TeizenClient;
import dev.teizen.client.core.Config;
import dev.teizen.client.util.Keys;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/**
 * Einstellungen von Teizen Client (wird über Mod Menu geöffnet: Mods → Teizen Client → Zahnrad).
 * Hier legt man fest, mit welcher Taste das Teizen-Menü aufgeht.
 */
public final class SettingsScreen extends Screen {
    private static final int BTN_W = 220, BTN_H = 20;
    private static final int BG = 0xE0140A26, BTN = 0xFF2A1650, BTN_HOVER = 0xFF5B1FB8, BTN_ACTIVE = 0xFF7A2CF0;

    private final Screen parent;
    private boolean waitingForKey;
    private String note = "";

    public SettingsScreen(Screen parent) {
        super(Component.literal("Teizen Client – Einstellungen"));
        this.parent = parent;
    }

    // ------------------------------------------------------------ Tastenname

    /** Lesbarer Name einer Taste, z.B. "M", "Rechte Shift", "F6". */
    public static String keyName(int code) {
        if (code < 0) return "keine";
        switch (code) {
            case GLFW.GLFW_KEY_RIGHT_SHIFT: return "Rechte Shift";
            case GLFW.GLFW_KEY_LEFT_SHIFT: return "Linke Shift";
            case GLFW.GLFW_KEY_RIGHT_CONTROL: return "Rechte Strg";
            case GLFW.GLFW_KEY_LEFT_CONTROL: return "Linke Strg";
            case GLFW.GLFW_KEY_RIGHT_ALT: return "Rechte Alt";
            case GLFW.GLFW_KEY_LEFT_ALT: return "Linke Alt";
            case GLFW.GLFW_KEY_RIGHT_SUPER: return "Rechte Cmd";
            case GLFW.GLFW_KEY_LEFT_SUPER: return "Linke Cmd";
            case GLFW.GLFW_KEY_TAB: return "Tab";
            case GLFW.GLFW_KEY_CAPS_LOCK: return "Feststelltaste";
            case GLFW.GLFW_KEY_SPACE: return "Leertaste";
            case GLFW.GLFW_KEY_ENTER: return "Enter";
            default: break;
        }
        try {
            String n = GLFW.glfwGetKeyName(code, 0);
            if (n != null && !n.isBlank()) return n.toUpperCase(Locale.ROOT);
        } catch (Throwable ignored) {}
        return Keys.name(code);
    }

    // ------------------------------------------------------------ Logik

    /** Neue Menü-Taste übernehmen und speichern. */
    public void applyKey(int code) {
        waitingForKey = false;
        if (code == GLFW.GLFW_KEY_ESCAPE) { note = "§7Abgebrochen."; return; }
        if (code == GLFW.GLFW_KEY_DELETE || code == GLFW.GLFW_KEY_BACKSPACE) {
            code = GLFW.GLFW_KEY_RIGHT_SHIFT;
            note = "§aZurückgesetzt auf Rechte Shift.";
        } else {
            note = "§aGespeichert! Das Menü geht jetzt mit §f" + keyName(code) + "§a auf.";
        }
        TeizenClient.menuKey = code;
        Config.save();
    }

    public void startListening() {
        waitingForKey = true;
        note = "";
    }

    // ------------------------------------------------------------ Aufbau

    private int bx() { return width / 2 - BTN_W / 2; }
    private int y0() { return height / 2 - 40; }
    private int keyY() { return y0(); }
    private int openY() { return y0() + 28; }
    private int doneY() { return y0() + 78; }

    private static boolean inside(double mx, double my, int x, int y) {
        return mx >= x && mx < x + BTN_W && my >= y && my < y + BTN_H;
    }

    private void button(GuiGraphicsExtractor g, int x, int y, String label, boolean hover, boolean active) {
        int col = active ? BTN_ACTIVE : hover ? BTN_HOVER : BTN;
        g.fill(x - 1, y - 1, x + BTN_W + 1, y + BTN_H + 1, 0xFF7A2CF0);
        g.fill(x, y, x + BTN_W, y + BTN_H, col);
        g.centeredText(getFont(), label, x + BTN_W / 2, y + 6, 0xFFFFFFFF);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        var font = getFont();
        g.fill(0, 0, width, height, BG);
        int x = bx();
        g.centeredText(font, "§5§lTEIZEN CLIENT §7– Einstellungen", width / 2, y0() - 40, 0xFFFFFFFF);
        g.centeredText(font, "§7Mit welcher Taste soll das Teizen-Menü aufgehen?", width / 2, y0() - 20, 0xFFFFFFFF);

        String keyLabel = waitingForKey ? "§e> Drück jetzt eine Taste <" : "Menü-Taste: §d" + keyName(TeizenClient.menuKey);
        button(g, x, keyY(), keyLabel, inside(mouseX, mouseY, x, keyY()), waitingForKey);

        boolean inWorld = Minecraft.getInstance().player != null;
        button(g, x, openY(), inWorld ? "Teizen-Menü jetzt öffnen" : "§8Teizen-Menü öffnen (nur in einer Welt)", inside(mouseX, mouseY, x, openY()), false);

        if (waitingForKey) {
            g.centeredText(font, "§7Esc = abbrechen · Entf = zurück auf Rechte Shift", width / 2, openY() + 26, 0xFFFFFFFF);
        } else if (!note.isEmpty()) {
            g.centeredText(font, note, width / 2, openY() + 26, 0xFFFFFFFF);
        } else {
            g.centeredText(font, "§7Geht auch per Befehl: §f///menutaste m", width / 2, openY() + 26, 0xFFFFFFFF);
        }

        button(g, x, doneY(), "Fertig", inside(mouseX, mouseY, x, doneY()), false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        double mx = e.x(), my = e.y();
        int x = bx();
        if (waitingForKey) {
            // Klick irgendwo = abbrechen
            waitingForKey = false;
            note = "§7Abgebrochen.";
            return true;
        }
        if (inside(mx, my, x, keyY())) { startListening(); return true; }
        if (inside(mx, my, x, openY())) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) mc.gui.setScreen(new ClickGui());
            else note = "§cDas Menü geht nur in einer Welt auf.";
            return true;
        }
        if (inside(mx, my, x, doneY())) { onClose(); return true; }
        return super.mouseClicked(e, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (waitingForKey) {
            applyKey(e.key());
            return true;
        }
        return super.keyPressed(e);
    }

    @Override
    public void onClose() {
        Config.save();
        Minecraft.getInstance().gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return parent != null; }
}
