package dev.teizen.client.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.teizen.client.gui.SettingsScreen;

/** Mod Menu: Zahnrad bei "Teizen Client" öffnet die Einstellungen (Menü-Taste festlegen). */
public final class ModMenuCompat implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return SettingsScreen::new;
    }
}
