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

    public static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        if (mc.player == null || mc.level == null) return;
        if (mc.gui.hud.isHidden()) return;
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
        int y = 2;
        int w = g.guiWidth();
        g.text(mc.font, "§5§lTeizen §7Client", 3, 3, 0xFFFFFFFF, true);
        for (Module m : on) {
            String t = label(m);
            int tw = mc.font.width(t);
            g.fill(w - tw - 5, y, w, y + 11, 0x80140A26);
            g.fill(w - 2, y, w, y + 11, 0xFF9D52FF);
            g.text(mc.font, t, w - tw - 3, y + 2, 0xFFE8D2FF, true);
            y += 11;
        }
    }

    private static String label(Module m) {
        String i = m.info();
        return i == null || i.isEmpty() ? m.name : m.name + " §7" + i;
    }
}
