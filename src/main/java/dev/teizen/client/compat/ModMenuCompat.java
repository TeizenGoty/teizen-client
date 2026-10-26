package dev.teizen.client.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.teizen.client.gui.SettingsScreen;

/** Mod Menu: Zahnrad bei "Teizen Client" öffnet die Einstellungen (Menü-Taste festlegen). */
public final class ModMenuCompat implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        // Version mit Key-Pflicht ohne Key-Mod: auch hier nur der Fehler
        return parent -> dev.teizen.client.core.Lock.ok() ? new SettingsScreen(parent) : new dev.teizen.client.gui.LockedScreen(parent);
    }
}
