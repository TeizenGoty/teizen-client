package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;

/**
 * Elytra-Reset mit der Leertaste: im Gleitflug Leertaste = Elytra zu (du fällst, z.B. für einen Streitkolben-Schlag),
 * nochmal Leertaste in der Luft = Elytra wieder auf (das macht Minecraft dann selbst).
 */
public final class ElytraReset extends Module {
    private final Setting reopen = add(Setting.number("wiederauf", "Nach so vielen Ticks automatisch wieder öffnen (0 = nur mit Leertaste)", 0, 0, 100));

    public int resets;
    private boolean wasJump;
    private int reopenIn = -1;

    public ElytraReset() {
        super("ElytraReset", Category.BEWEGUNG, "Leertaste im Elytra-Flug: Elytra zu, nochmal: wieder auf.");
    }

    @Override
    protected void onEnable() {
        wasJump = mc.options.keyJump.isDown();
        reopenIn = -1;
    }

    @Override
    public void onTick() {
        if (!inGame()) return;
        boolean jump = mc.options.keyJump.isDown();
        boolean pressed = jump && !wasJump;
        wasJump = jump;
        if (pressed && mc.player.isFallFlying() && mc.gui.screen() == null) {
            // Minecraft-Server: "Elytra öffnen" während man schon fliegt = Flug beenden
            mc.player.connection.send(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
            mc.player.stopFallFlying();
            resets++;
            reopenIn = reopen.integer() > 0 ? reopen.integer() : -1;
            return;
        }
        if (reopenIn > 0 && --reopenIn == 0) {
            reopenIn = -1;
            if (!mc.player.onGround() && !mc.player.isFallFlying() && mc.player.tryToStartFallFlying())
                mc.player.connection.send(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
        }
    }

    @Override
    public String info() { return resets > 0 ? String.valueOf(resets) : ""; }
}
