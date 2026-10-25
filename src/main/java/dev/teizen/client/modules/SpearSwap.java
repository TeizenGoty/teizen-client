package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.util.Inv;
import dev.teizen.client.util.WeaponSwap;
import net.minecraft.world.entity.Entity;

/**
 * SpearSwap: ist der Gegner weiter weg als dein Schwert reicht, wird für den Schlag kurz zum Speer gewechselt und
 * zugestochen (der Speer reicht bis 4,5 Blöcke und trifft alle Gegner in einer Linie). Danach ist sofort wieder das
 * Schwert in der Hand. Der Server sticht in Kopf-Richtung: darum wirst du unsichtbar zum Gegner gedreht und der erste
 * Stich kommt 0,2 Sekunden später (danach beim selben Gegner sofort).
 * Du brauchst einen Speer in der Hotbar.
 */
public final class SpearSwap extends Module {
    private final Setting distance = add(Setting.number("abstand", "Ab welcher Entfernung zum Speer gewechselt wird (Blöcke)", 3, 0, 6));
    private final Setting always = add(Setting.bool("immer", "Immer mit dem Speer stechen (auch ganz nah)", false));

    public int swaps;

    public SpearSwap() {
        super("SpearSwap", Category.KAMPF, "Gegner zu weit weg: kurz zum Speer wechseln und zustechen.");
    }

    public boolean wants(Entity target) {
        if (target == null || mc.player == null) return false;
        return always.on() || Targets.distanceToBox(target) > distance.num();
    }

    @Override
    public void onTick() { WeaponSwap.tickSpear(); }

    @Override
    public String info() { return mc.player != null && Inv.findHotbar(WeaponSwap::isSpear) < 0 ? "§ckein Speer" : ""; }
}
