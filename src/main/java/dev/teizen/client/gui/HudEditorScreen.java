package dev.teizen.client.gui;

import dev.teizen.client.core.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.Map;

/** HUD verschieben: Teile mit der linken Maustaste ziehen, Rechtsklick = zurück an den Standard-Platz. */
public final class HudEditorScreen extends Screen {
    private static final int BTN_W = 90, BTN_H = 16;

    private final Screen parent;
    private String dragging;
    private double grabX, grabY;
    private int startDx, startDy;

    public HudEditorScreen(Screen parent) {
        super(Component.literal("HUD verschieben"));
        this.parent = parent;
    }

    private int doneX() { return width / 2 - BTN_W - 4; }
    private int resetX() { return width / 2 + 4; }
    private int btnY() { return height / 2 - BTN_H / 2; }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** Ganz oben liegendes Teil unter der Maus. */
    public String at(double mx, double my) {
        String hit = null;
        for (String id : HudLayout.NAMES.keySet()) {
            HudLayout.Box b = HudLayout.screenBox(id);
            if (mx >= b.x1() - 1 && mx <= b.x2() + 1 && my >= b.y1() - 1 && my <= b.y2() + 1) hit = id;
        }
        return hit;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        // kein Unschärfe-Hintergrund: man soll das HUD sehen
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        var font = getFont();
        g.fill(0, 0, width, height, 0x30000000);
        String hover = dragging != null ? dragging : at(mouseX, mouseY);
        for (Map.Entry<String, String> e : HudLayout.NAMES.entrySet()) {
            String id = e.getKey();
            HudLayout.Box b = HudLayout.screenBox(id);
            boolean on = HudLayout.visible(id);
            boolean h = id.equals(hover);
            int col = h ? 0xFFFFE040 : (on ? 0xFFB07CFF : 0xFF6A5A80);
            if (!on) g.fill(b.x1(), b.y1(), b.x2(), b.y2(), 0x50281646);
            if (h) g.fill(b.x1(), b.y1(), b.x2(), b.y2(), 0x30FFFFFF);
            g.fill(b.x1() - 1, b.y1() - 1, b.x2() + 1, b.y1(), col);
            g.fill(b.x1() - 1, b.y2(), b.x2() + 1, b.y2() + 1, col);
            g.fill(b.x1() - 1, b.y1(), b.x1(), b.y2(), col);
            g.fill(b.x2(), b.y1(), b.x2() + 1, b.y2(), col);
            String label = (on ? "§f" : "§7") + e.getValue() + (on ? "" : " §8(gerade aus)");
            int lw = font.width(label);
            int lx = Math.max(1, Math.min(width - lw - 1, b.x1()));
            int ly = b.y1() > 11 ? b.y1() - 10 : b.y2() + 2;
            g.text(font, label, lx, ly, 0xFFFFFFFF, true);
        }
        g.centeredText(font, "§5§lHUD verschieben §7– ziehen = verschieben · Rechtsklick = zurück · Esc = fertig", width / 2, height / 2 - 26, 0xFFFFFFFF);
        boolean hd = in(mouseX, mouseY, doneX(), btnY(), BTN_W, BTN_H);
        boolean hr = in(mouseX, mouseY, resetX(), btnY(), BTN_W, BTN_H);
        g.fill(doneX(), btnY(), doneX() + BTN_W, btnY() + BTN_H, hd ? 0xFF7A2CF0 : 0xE0362060);
        g.centeredText(font, "Fertig", doneX() + BTN_W / 2, btnY() + 4, 0xFFFFFFFF);
        g.fill(resetX(), btnY(), resetX() + BTN_W, btnY() + BTN_H, hr ? 0xFF8A2020 : 0xE0401818);
        g.centeredText(font, "Alles zurück", resetX() + BTN_W / 2, btnY() + 4, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        double mx = e.x(), my = e.y();
        if (in(mx, my, doneX(), btnY(), BTN_W, BTN_H)) { onClose(); return true; }
        if (in(mx, my, resetX(), btnY(), BTN_W, BTN_H)) { HudLayout.resetAll(); Config.save(); return true; }
        String id = at(mx, my);
        if (id == null) return super.mouseClicked(e, doubleClick);
        if (e.button() == 1) { HudLayout.reset(id); Config.save(); return true; }
        if (e.button() == 0) startDrag(id, mx, my);
        return true;
    }

    /** Ziehen beginnen (auch für den Selbsttest). */
    public void startDrag(String id, double mx, double my) {
        Minecraft mc = Minecraft.getInstance();
        int[] o = HudLayout.offset(id, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
        dragging = id;
        grabX = mx;
        grabY = my;
        startDx = o[0];
        startDy = o[1];
    }

    /** Ziehen bis zur Maus-Stelle (auch für den Selbsttest). */
    public void dragTo(double mx, double my) {
        if (dragging == null) return;
        HudLayout.set(dragging, startDx + (int) Math.round(mx - grabX), startDy + (int) Math.round(my - grabY));
    }

    public void stopDrag() {
        if (dragging == null) return;
        dragging = null;
        Config.save();
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (dragging != null) { dragTo(e.x(), e.y()); return true; }
        return super.mouseDragged(e, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        if (dragging != null) { dragTo(e.x(), e.y()); stopDrag(); return true; }
        return super.mouseReleased(e);
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (e.key() == dev.teizen.client.compat.K.ESCAPE) { onClose(); return true; }
        return super.keyPressed(e);
    }

    @Override
    public void onClose() {
        stopDrag();
        Config.save();
        Minecraft.getInstance().gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
