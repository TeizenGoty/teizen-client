package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import net.minecraft.world.phys.Vec3;

/** TridentBoost: der Schwung vom Dreizack mit Sog (Riptide) ist viel stärker – du fliegst weiter. */
public final class TridentBoost extends Module {
    private final Setting factor = add(Setting.number("staerke", "Wie viel stärker der Schwung ist (1 = normal)", 2, 1, 5));

    private boolean wasSpinning;
    private int pending;
    public int boosts;
    public double lastSpeed;

    public TridentBoost() {
        super("TridentBoost", Category.BEWEGUNG, "Dreizack mit Sog schleudert dich viel weiter.");
    }

    @Override
    public void onTick() {
        if (!inGame()) return;
        boolean spin = mc.player.isAutoSpinAttack();
        if (spin && !wasSpinning) pending = 4;
        // den Schwung verstärken, sobald er wirklich da ist (kommt je nach Version 0-2 Ticks nach dem Start)
        if (spin && pending > 0) {
            Vec3 d = mc.player.getDeltaMovement();
            pending--;
            if (d.length() > 0.6 || pending == 0) {
                Vec3 v = d.scale(factor.num());
                mc.player.setDeltaMovement(v);
                lastSpeed = v.length();
                boosts++;
                pending = 0;
            }
        }
        if (!spin) pending = 0;
        wasSpinning = spin;
    }

    @Override
    public String info() { return "x" + Setting.fmt(factor.num()); }
}
