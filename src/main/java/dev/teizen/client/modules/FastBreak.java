package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.mixin.MultiPlayerGameModeAccessor;
import dev.teizen.client.util.Op;

/**
 * Schneller abbauen: keine Pause zwischen zwei Blöcken, der Block bricht schon bei 70 % (so viel lässt der Server zu),
 * und mit OP / in der eigenen Welt zusätzlich schneller über das Abbau-Tempo-Attribut.
 */
public final class FastBreak extends Module {
    private final Setting factor = add(Setting.number("faktor", "Wie viel schneller (mit OP/eigene Welt, 1 = normal)", 6, 1, 50));
    private final Setting early = add(Setting.number("ab", "Block bricht schon ab diesem Fortschritt (0.7 = 70 %)", 0.7, 0.7, 1));
    private final Setting noDelay = add(Setting.bool("keinepause", "Keine Pause zwischen zwei Blöcken", true));

    private double applied = -1;
    private int lastApply = -1000;
    public int boosted;

    public FastBreak() {
        super("FastBreak", Category.ABBAU, "Baut Blöcke viel schneller ab.");
    }

    @Override
    protected void onEnable() { applied = -1; lastApply = -1000; }

    @Override
    protected void onDisable() {
        if (!inGame()) return;
        if (applied > 0 && Op.available()) Op.run("attribute @s minecraft:block_break_speed base reset");
        applied = -1;
    }

    @Override
    public void onTick() {
        if (!inGame()) return;
        var acc = (MultiPlayerGameModeAccessor) mc.gameMode;
        if (noDelay.on() && acc.teizen$getDestroyDelay() > 0) acc.teizen$setDestroyDelay(0);
        if (mc.gameMode.isDestroying()) {
            float p = acc.teizen$getDestroyProgress();
            if (p >= early.num() && p < 1f) {
                acc.teizen$setDestroyProgress(1f);
                boosted++;
            }
        }
        int now = mc.player.tickCount;
        if ((applied != factor.num() || now - lastApply > 200) && Op.available()) {
            applied = factor.num();
            lastApply = now;
            Op.run("attribute @s minecraft:block_break_speed base set " + factor.num());
        }
    }

    @Override
    public String info() { return Setting.fmt(factor.num()) + "x"; }
}
