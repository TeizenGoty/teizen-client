package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.util.Chat;
import net.minecraft.client.gui.screens.DeathScreen;

/** Sofort wieder spawnen nach dem Tod (und Todes-Koordinaten in den Chat). */
public final class AutoRespawn extends Module {
    private boolean announced;

    public AutoRespawn() {
        super("AutoRespawn", Category.SONSTIGES, "Spawnt dich nach dem Tod sofort neu.");
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        if (mc.gui.screen() instanceof DeathScreen) {
            if (!announced) {
                Chat.info("Gestorben bei §f" + mc.player.blockPosition().toShortString());
                announced = true;
            }
            mc.player.respawn();
            mc.gui.setScreen(null);
        } else {
            announced = false;
        }
    }
}
