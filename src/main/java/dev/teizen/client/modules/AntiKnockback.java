package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;

/** Kein Rückstoß durch Schläge, Pfeile oder Explosionen (über Mixins in ClientPacketListener). */
public final class AntiKnockback extends Module {
    public final Setting strength = add(Setting.number("staerke", "Wie viel Rückstoß übrig bleibt (0 = keiner)", 0, 0, 1));

    public AntiKnockback() {
        super("AntiKnockback", Category.KAMPF, "Du fliegst bei Treffern nicht mehr weg.");
    }

    @Override
    public String info() { return Math.round(strength.num() * 100) + "%"; }
}
