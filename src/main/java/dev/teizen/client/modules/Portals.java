package dev.teizen.client.modules;

import dev.teizen.client.TeizenClient;
import dev.teizen.client.core.Module;

/** Portals: im Nether-Portal Kisten, Inventar und Chat offen lassen (normal schließt Minecraft sie). */
public final class Portals extends Module {
    public int kept;

    public Portals() {
        super("Portals", Category.SONSTIGES, "Im Portal bleiben Inventar, Kisten und Chat offen.");
    }

    /** Mixin (LocalPlayer.handlePortalTransitionEffect): true = Menü offen lassen. */
    public static boolean keep() {
        if (TeizenClient.modules() == null || mc.gui == null || mc.gui.screen() == null) return false;
        Portals p = TeizenClient.modules().get(Portals.class);
        if (p == null || !p.isEnabled()) return false;
        p.kept++;
        return true;
    }
}
