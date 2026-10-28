package dev.teizen.client.gui;

import dev.teizen.client.TeizenClient;
import dev.teizen.client.core.Finder;
import dev.teizen.client.core.Module;
import dev.teizen.client.util.Projector;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** HUD: Liste aktiver Funktionen oben rechts + alle 2D-Markierungen (ESP, X-Ray, Find). */
public final class Hud {
    private static final Minecraft mc = Minecraft.getInstance();

    private Hud() {}

    /** Für den Selbsttest: wie lange das HUD (mit allen ESPs) zum Zeichnen braucht. */
    public static long renderNanos, renderFrames;

    public static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        long t0 = System.nanoTime();
        try {
            renderInner(g, delta);
        } finally {
            renderNanos += System.nanoTime() - t0;
            renderFrames++;
        }
    }

    private static void renderInner(GuiGraphicsExtractor g, DeltaTracker delta) {
        if (mc.player == null || mc.level == null) return;
        if (mc.gui.hud.isHidden()) return;
        HudLayout.frame();
        float partial = delta.getGameTimeDeltaPartialTick(true);

        if (Projector.begin(g)) {
            for (Module m : TeizenClient.modules().all()) {
                if (!m.isEnabled()) continue;
                try {
                    m.onHud(g, partial);
                } catch (Throwable t) {
                    TeizenClient.LOG.error("HUD-Fehler in " + m.name, t);
                }
            }
            Finder.hud(g);
        }

        // Modul-Liste
        List<Module> on = new ArrayList<>();
        for (Module m : TeizenClient.modules().all()) if (m.isEnabled()) on.add(m);
        on.sort(Comparator.comparingInt((Module m) -> -mc.font.width(label(m))));
        // mit Xaero's Minimap ist oben links die Karte -> Schriftzug weglassen
        if (!dev.teizen.client.compat.XaeroCompat.minimapInstalled()) {
            String logo = "§5§lTeizen §7Client";
            HudLayout.report("logo", 2, 2, 4 + mc.font.width(logo), 12);
            HudLayout.push(g, "logo");
            g.text(mc.font, logo, 3, 3, 0xFFFFFFFF, true);
            HudLayout.pop(g);
        }
        if (scale <= 0.01f || on.isEmpty()) return;
        // Liste oben rechts, verkleinert (///hud <Größe>)
        float sc = scale;
        int w = (int) Math.floor(g.guiWidth() / sc);
        int y = 1;
        int maxTw = 0;
        for (Module m : on) maxTw = Math.max(maxTw, mc.font.width(label(m)));
        HudLayout.report("liste", (int) Math.floor((w - maxTw - 5) * sc), (int) Math.floor(sc), g.guiWidth(), (int) Math.ceil((1 + on.size() * 10) * sc));
        HudLayout.push(g, "liste");
        g.pose().pushMatrix();
        g.pose().scale(sc, sc);
        for (Module m : on) {
            String t = label(m);
            int tw = mc.font.width(t);
            g.fill(w - tw - 5, y, w, y + 10, 0x80140A26);
            g.fill(w - 2, y, w, y + 10, 0xFF9D52FF);
            g.text(mc.font, t, w - tw - 3, y + 1, 0xFFE8D2FF, true);
            y += 10;
        }
        g.pose().popMatrix();
        HudLayout.pop(g);
    }

    /** Größe der Liste oben rechts (1 = normal, 0 = ausblenden). */
    public static float scale = 0.6f;

    private static String label(Module m) {
        String i = m.info();
        return i == null || i.isEmpty() ? m.name : m.name + " §7" + i;
    }
}
