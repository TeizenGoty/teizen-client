package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.util.Inv;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;

/**
 * CombatLog: verlässt sofort das Spiel, wenn dein Leben zu tief fällt – aber nur, wenn du kein Totem mehr hast.
 * Danach schaltet es sich aus, und AutoReconnect verbindet in diesem Fall NICHT neu.
 */
public final class CombatLog extends Module {
    private final Setting hearts = add(Setting.number("herzen", "Bei so wenig Herzen raus (0.5 = halbes Herz)", 0.5, 0.5, 10));
    private final Setting needNoTotem = add(Setting.bool("nurohnetotem", "Nur rausgehen, wenn kein Totem mehr da ist", true));

    /** Gesetzt, wenn CombatLog die Verbindung getrennt hat (AutoReconnect wartet dann). */
    public static boolean loggedOut;
    public int triggered;

    public CombatLog() {
        super("CombatLog", Category.KAMPF, "Geht bei 0,5 Herzen raus (nur wenn kein Totem mehr da ist).");
    }

    public static boolean hasTotem() {
        return mc.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING) || mc.player.getMainHandItem().is(Items.TOTEM_OF_UNDYING)
                || Inv.findAny(s -> s.is(Items.TOTEM_OF_UNDYING)) >= 0;
    }

    @Override
    public void onTick() {
        if (!inGame() || mc.player.isCreative() || mc.player.isSpectator()) return;
        float hp = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        if (hp <= 0 || hp > hearts.num() * 2 + 0.01) return;
        if (needNoTotem.on() && hasTotem()) return;
        triggered++;
        loggedOut = true;
        String why = "Teizen CombatLog: nur noch " + String.format(java.util.Locale.ROOT, "%.1f", hp / 2f) + " Herzen und kein Totem – Spiel verlassen.";
        setEnabled(false);
        mc.player.connection.getConnection().disconnect(Component.literal(why));
    }

    @Override
    public String info() { return Setting.fmt(hearts.num()) + "❤"; }
}
