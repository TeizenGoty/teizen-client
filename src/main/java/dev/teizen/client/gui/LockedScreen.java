package dev.teizen.client.gui;

import dev.teizen.client.core.Lock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Statt des Menüs, wenn die Version mit Key-Pflicht ohne Key-Mod läuft: nur eine Fehlermeldung. */
public final class LockedScreen extends Screen {
    private static final int BTN_W = 160, BTN_H = 20;

    private final Screen parent;

    public LockedScreen(Screen parent) {
        super(Component.literal("Teizen Client – gesperrt"));
        this.parent = parent;
    }

    private int boxTop() { return height / 2 - 50; }
    private int btnY() { return boxTop() + 72; }
    private int btnX() { return width / 2 - BTN_W / 2; }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        var font = getFont();
        g.fill(0, 0, width, height, 0xE0140A26);
        int w = Math.min(width - 20, 320), x = width / 2 - w / 2, y = boxTop();
        g.fill(x - 2, y - 2, x + w + 2, y + 102, 0xFFD02020);
        g.fill(x, y, x + w, y + 100, 0xFF2A0A0A);
        g.centeredText(font, "§c§lFEHLER: Teizen Client ist gesperrt", width / 2, y + 10, 0xFFFFFFFF);
        String kn = Lock.keyName;
        if (kn != null && !kn.equalsIgnoreCase(String.valueOf(Lock.myName()))) {
            g.centeredText(font, "§fDer Key gehört zu §e" + kn + "§f, du spielst als §e" + Lock.myName() + "§f.", width / 2, y + 28, 0xFFFFFFFF);
            g.centeredText(font, "§7Jeder braucht seinen eigenen Key (teizen-key-<Name>.jar).", width / 2, y + 42, 0xFFFFFFFF);
        } else {
            g.centeredText(font, "§fDie Key-Mod §e(teizen-key-<Name>.jar)§f fehlt.", width / 2, y + 28, 0xFFFFFFFF);
            g.centeredText(font, "§7Leg sie in den mods-Ordner und starte Minecraft neu.", width / 2, y + 42, 0xFFFFFFFF);
        }
        g.centeredText(font, "§7Ohne sie geht keine Funktion.", width / 2, y + 54, 0xFFFFFFFF);
        boolean hover = mouseX >= btnX() && mouseX < btnX() + BTN_W && mouseY >= btnY() && mouseY < btnY() + BTN_H;
        g.fill(btnX(), btnY(), btnX() + BTN_W, btnY() + BTN_H, hover ? 0xFF8A2020 : 0xFF5A1414);
        g.centeredText(font, "Schließen", width / 2, btnY() + 6, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        if (e.x() >= btnX() && e.x() < btnX() + BTN_W && e.y() >= btnY() && e.y() < btnY() + BTN_H) {
            onClose();
            return true;
        }
        return super.mouseClicked(e, doubleClick);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return parent != null; }

    /** Für den Selbsttest. */
    public static String text() { return Lock.message(); }
}
