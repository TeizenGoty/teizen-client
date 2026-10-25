package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;

/**
 * Elytra-Abbruch mit eigener Taste (Optionen → Steuerung → Teizen Client, Standard: Strg links):
 * im Gleitflug Taste = Elytra zu (du fällst, z.B. für einen Streitkolben-Schlag),
 * nochmal Taste in der Luft = Elytra wieder auf. Leertaste öffnet sie wie immer auch.
 */
public final class ElytraReset extends Module {
    private final Setting reopen = add(Setting.number("wiederauf", "Nach so vielen Ticks automatisch wieder öffnen (0 = nur mit der Taste)", 0, 0, 100));

    /** Ist die Elytra-Taste gedrückt? (Selbsttest kann das ersetzen) */
    public static java.util.function.BooleanSupplier keyDown = () -> dev.teizen.client.core.KeyBinds.elytra() != null && dev.teizen.client.core.KeyBinds.elytra().isDown();
    public int reopens;

    public int resets;
    /** Nach einem Reset: Elytra nicht wieder öffnen, bis die Leertaste losgelassen wurde. */
    private static boolean holdBlock;

    public static boolean blockRedeploy() { return holdBlock; }
    private boolean wasJump;
    private int reopenIn = -1;

    public ElytraReset() {
        super("ElytraReset", Category.BEWEGUNG, "Eigene Taste (Strg links) im Elytra-Flug: Elytra zu, nochmal: wieder auf.");
    }

    @Override
    protected void onDisable() { holdBlock = false; }

    @Override
    protected void onEnable() {
        wasJump = keyDown.getAsBoolean();
        reopenIn = -1;
    }

    @Override
    public void onTick() {
        if (!inGame()) return;
        boolean down = keyDown.getAsBoolean();
        boolean pressed = down && !wasJump;
        wasJump = down;
        if (holdBlock && --holdTicks <= 0) holdBlock = false;
        if (!pressed || mc.gui.screen() != null) {
            if (reopenIn > 0 && --reopenIn == 0) {
                reopenIn = -1;
                open();
            }
            return;
        }
        if (mc.player.isFallFlying()) {
            // Minecraft-Server: "Elytra öffnen" während man schon fliegt = Flug beenden
            mc.player.connection.send(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
            mc.player.stopFallFlying();
            holdBlock = true;
            holdTicks = 3;
            resets++;
            reopenIn = reopen.integer() > 0 ? reopen.integer() : -1;
        } else if (!mc.player.onGround()) {
            open();
        }
    }

    private int holdTicks;

    private void open() {
        holdBlock = false;
        if (!mc.player.onGround() && !mc.player.isFallFlying() && mc.player.tryToStartFallFlying()) {
            mc.player.connection.send(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
            reopens++;
        }
    }

    @Override
    public String info() { return resets > 0 ? String.valueOf(resets) : ""; }
}
