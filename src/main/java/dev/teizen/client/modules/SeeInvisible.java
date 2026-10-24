package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/** Unsichtbare Spieler (Trank der Unsichtbarkeit) werden durchsichtig angezeigt – wie bei Teamkameraden. */
public final class SeeInvisible extends Module {
    private final Setting mobs = add(Setting.bool("mobs", "Auch unsichtbare Mobs und Rüstungsständer zeigen", false));
    public int seen;

    public SeeInvisible() {
        super("SeeInvisible", Category.SICHT, "Du siehst unsichtbare Spieler (durchsichtig).");
    }

    /** Aus dem Entity-Mixin: soll dieses Wesen für uns sichtbar sein? */
    public boolean reveal(Entity e) {
        if (!isEnabled() || e == mc.player) return false;
        return e instanceof Player || mobs.on();
    }

    @Override
    public void onTick() {
        if (!inGame() || mc.player.tickCount % 20 != 0) return;
        int n = 0;
        for (Entity e : mc.level.entitiesForRendering()) if (e != mc.player && e.isInvisible() && reveal(e)) n++;
        seen = n;
    }

    @Override
    public String info() { return seen > 0 ? String.valueOf(seen) : ""; }
}
