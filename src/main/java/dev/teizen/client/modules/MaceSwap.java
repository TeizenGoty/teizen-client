package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.util.Inv;
import dev.teizen.client.util.WeaponSwap;

/**
 * MaceSwap: schlägst du im Fallen zu, wird für den Schlag kurz zum Streitkolben (Mace) gewechselt – der macht beim Fallen
 * riesigen Schaden. Danach hast du sofort wieder dein Schwert in der Hand. Du brauchst einen Streitkolben in der Hotbar.
 */
public final class MaceSwap extends Module {
    private final Setting minFall = add(Setting.number("fallhoehe", "Ab wie vielen Blöcken Fallhöhe gewechselt wird", 1.5, 0.5, 20));

    public int swaps;

    public MaceSwap() {
        super("MaceSwap", Category.KAMPF, "Schlag im Fallen: kurz zum Streitkolben wechseln für riesigen Schaden.");
    }

    /** Lohnt sich der Streitkolben gerade (genug Fallhöhe)? */
    public boolean wants() {
        return mc.player != null && mc.player.fallDistance >= minFall.num();
    }

    @Override
    public String info() { return mc.player != null && Inv.findHotbar(WeaponSwap::isMace) < 0 ? "§ckein Streitkolben" : ""; }
}
