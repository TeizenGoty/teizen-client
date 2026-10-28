package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.util.Op;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Höher springen. */
public final class HighJump extends Module {
    private final Setting power = add(Setting.number("hoehe", "Sprunghöhe in Blöcken (normal 1.25)", 3, 0.5, 30));

    public HighJump() {
        super("HighJump", Category.BEWEGUNG, "Sprunghöhe in Blöcken einstellen.");
    }

    @Override
    public void onTick() {
        if (!inGame()) return;
        AttributeInstance a = mc.player.getAttribute(Attributes.JUMP_STRENGTH);
        double want = strengthFor(power.num());
        if (a != null && Math.abs(a.getBaseValue() - want) > 1e-6) a.setBaseValue(want);
        // ohne OP setzt der Server die Sprungkraft zurück und zieht dich runter ("rubberband").
        // Mit OP / in der eigenen Welt die Sprungkraft auch auf dem Server setzen, dann springst du wirklich hoch.
        if (Op.available() && (appliedWant != want || mc.player.tickCount - lastApply > 100)) {
            appliedWant = want;
            lastApply = mc.player.tickCount;
            Op.run(String.format(java.util.Locale.ROOT, "attribute @s minecraft:jump_strength base set %.5f", want));
        }
    }

    private double appliedWant = -1;
    private int lastApply = -1000;

    @Override
    protected void onEnable() { appliedWant = -1; lastApply = -1000; }

    @Override
    protected void onDisable() {
        if (!inGame()) return;
        AttributeInstance a = mc.player.getAttribute(Attributes.JUMP_STRENGTH);
        if (a != null) a.setBaseValue(0.42);
        if (appliedWant > 0 && Op.available()) Op.run("attribute @s minecraft:jump_strength base reset");
        appliedWant = -1;
    }

    /** Wie hoch kommt man mit dieser Sprungkraft? (Minecraft-Physik: Schwerkraft 0.08, Luftwiderstand 0.98) */
    static double heightFor(double v) {
        double y = 0;
        for (int i = 0; i < 400 && v > 0; i++) { y += v; v = (v - 0.08) * 0.98; }
        return y;
    }

    /** Sprungkraft, mit der man genau so hoch springt. */
    static double strengthFor(double blocks) {
        double lo = 0.05, hi = 10;
        for (int i = 0; i < 60; i++) {
            double mid = (lo + hi) / 2;
            if (heightFor(mid) < blocks) lo = mid; else hi = mid;
        }
        return hi;
    }

    @Override
    public String info() { return Setting.fmt(power.num()) + " Blöcke"; }
}
