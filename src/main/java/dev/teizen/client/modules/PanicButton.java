package dev.teizen.client.modules;

import dev.teizen.client.TeizenClient;
import dev.teizen.client.core.Config;
import dev.teizen.client.core.Module;
import dev.teizen.client.util.Chat;

/** PanicButton: ein Klick (oder Taste) und ALLE Cheats sind sofort aus. */
public final class PanicButton extends Module {
    public PanicButton() {
        super("PanicButton", Category.SONSTIGES, "Ein Klick: alle Cheats sofort aus.");
    }

    /** Alles aus (auch über ///panic und den roten Knopf im Menü). */
    public static int panic() {
        int n = 0;
        for (Module m : TeizenClient.modules().all()) {
            if (m.isEnabled() && !(m instanceof PanicButton)) {
                m.setEnabled(false);
                n++;
            }
        }
        // auch ein laufendes Shulker-Ausräumen abbrechen
        if (ShulkerUnpacker.busy()) { ShulkerUnpacker.stop(); n++; }
        Config.save();
        return n;
    }

    @Override
    protected void onEnable() {
        int n = panic();
        Chat.ok("PANIK: " + n + " Cheats ausgeschaltet.");
        setEnabled(false);
    }
}
