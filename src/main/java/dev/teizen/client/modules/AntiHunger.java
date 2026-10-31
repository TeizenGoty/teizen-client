package dev.teizen.client.modules;

import dev.teizen.client.TeizenClient;
import dev.teizen.client.core.Module;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;

/** AntiHunger: der Server merkt nicht, dass du sprintest – Sprinten kostet keinen Hunger mehr. */
public final class AntiHunger extends Module {
    public int hidden;

    public AntiHunger() {
        super("AntiHunger", Category.SONSTIGES, "Sprinten kostet weniger Hunger (der Server sieht das Sprinten nicht).");
    }

    /** Mixin (Connection.send): true = Paket nicht schicken. */
    public static boolean drop(Packet<?> p) {
        if (!(p instanceof ServerboundPlayerCommandPacket c) || TeizenClient.modules() == null) return false;
        AntiHunger a = TeizenClient.modules().get(AntiHunger.class);
        if (a == null || !a.isEnabled()) return false;
        if (c.getAction() != ServerboundPlayerCommandPacket.Action.START_SPRINTING) return false;
        a.hidden++;
        return true;
    }

    @Override
    protected void onEnable() {
        // läuft man schon: dem Server einmal "Sprinten aus" sagen
        if (inGame() && mc.player.isSprinting())
            mc.player.connection.send(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.STOP_SPRINTING));
    }
}
