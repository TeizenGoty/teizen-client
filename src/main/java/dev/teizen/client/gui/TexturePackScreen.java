package dev.teizen.client.gui;

import dev.teizen.client.util.TexturePacks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Texturpakete: Liste aus dem resourcepacks-Ordner, ein Klick = an/aus. */
public final class TexturePackScreen extends Screen {
    private final Screen parent;
    private List<String> packs = new ArrayList<>();
    private int scroll;
    private static final int TOP = 40, ROW = 18;

    public TexturePackScreen(Screen parent) {
        super(Component.literal("Texturpakete"));
        this.parent = parent;
        packs = TexturePacks.list();
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() { Minecraft.getInstance().gui.setScreen(parent); }

    private int bx() { return width / 2 - 150; }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partial) {
        Font font = getFont();
        g.fillGradient(0, 0, width, height, 0xF0140D26, 0xF0080510);
        MenuFont.active = true;
        try {
            int x = bx(), w = 300;
            MenuFont.text(g, font, "§lTexturpakete", x, 10, 0xFFEDE9FE, false);
            MenuFont.text(g, font, "Klick = an/aus · Pakete kommen aus dem resourcepacks-Ordner", x, 22, 0xFFA99BD1, false);
            // Knopf: Ordner öffnen
            int ox = x + w - 80;
            boolean oh = mx >= ox && mx < ox + 80 && my >= 8 && my < 20;
            ClickGui.round(g, ox, 8, ox + 80, 20, oh ? 0xFF7C3AED : 0xFF2B1F55);
            MenuFont.centered(g, font, "Ordner öffnen", ox + 40, 10, 0xFFFFFFFF);
            int y = TOP;
            if (packs.isEmpty())
                MenuFont.centered(g, font, "§7Keine Texturpakete gefunden. Leg die .zip in den Ordner (Knopf oben).", width / 2, TOP + 10, 0xFFFFFFFF);
            for (int i = scroll; i < packs.size() && y + ROW <= height - 20; i++) {
                String id = packs.get(i);
                boolean on = TexturePacks.on(id), h = mx >= x && mx < x + w && my >= y && my < y + ROW - 2;
                ClickGui.round(g, x, y, x + w, y + ROW - 2, on ? (h ? 0xFF1E4A30 : 0xFF14301F) : (h ? 0xFF1C1535 : 0xFF17112A));
                ClickGui.round(g, x + 6, y + 5, x + 12, y + 11, on ? 0xFF4ADE80 : 0xFF3A2F63);
                String t = (TexturePacks.isTeizen(id) ? "§d§l★ " : "") + TexturePacks.title(id);
                MenuFont.text(g, font, MenuFont.cut(font, t, w - 60), x + 18, y + 4, on ? 0xFFFFFFFF : 0xFFC9BFE6, false);
                String st = on ? "§aan" : "§7aus";
                MenuFont.text(g, font, st, x + w - 8 - MenuFont.width(font, st), y + 4, 0xFFFFFFFF, false);
                y += ROW;
            }
            MenuFont.centered(g, font, "§8Esc = zurück · Mausrad scrollen", width / 2, height - 12, 0xFFFFFFFF);
        } finally {
            MenuFont.active = false;
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        double mx = e.x(), my = e.y();
        int x = bx(), w = 300, ox = x + w - 80;
        if (mx >= ox && mx < ox + 80 && my >= 8 && my < 20) { TexturePacks.openFolder(); return true; }
        int y = TOP;
        for (int i = scroll; i < packs.size() && y + ROW <= height - 20; i++) {
            if (mx >= x && mx < x + w && my >= y && my < y + ROW - 2) { TexturePacks.toggle(packs.get(i)); return true; }
            y += ROW;
        }
        return super.mouseClicked(e, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        int rows = Math.max(1, (height - 20 - TOP) / ROW);
        scroll = Math.max(0, Math.min(Math.max(0, packs.size() - rows), scroll - (int) Math.signum(sy)));
        return true;
    }
}
