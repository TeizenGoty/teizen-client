package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.util.Chat;
import dev.teizen.client.util.Op;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Kein Schlag-Cooldown (z.B. beim Schwert): jeder Schlag hat sofort volle Kraft.
 * Geht über die Angriffsgeschwindigkeit (Attribut) – eigene Welt direkt, auf Servern braucht es OP.
 */
public final class NoCooldown extends Module {
    private int lastApply = -1000;
    private boolean warned;

    public NoCooldown() {
        super("NoCooldown", Category.KAMPF, "Kein Cooldown beim Schlagen – jeder Schlag mit voller Kraft.");
        opOnly();
    }

    @Override
    protected void onEnable() {
        lastApply = -1000;
        warned = false;
    }

    @Override
    protected void onDisable() {
        if (!inGame()) return;
        AttributeInstance a = mc.player.getAttribute(Attributes.ATTACK_SPEED);
        if (a != null) a.setBaseValue(4.0);
        if (Op.available()) Op.run("attribute @s minecraft:attack_speed base reset");
    }

    /** Volle Schlagkraft? (für KillAura/TriggerBot) */
    public static boolean active() {
        var mm = dev.teizen.client.TeizenClient.modules();
        if (mm == null) return false;
        NoCooldown n = mm.get(NoCooldown.class);
        return n != null && n.isEnabled();
    }

    @Override
    public void onTick() {
        if (!inGame()) return;
        AttributeInstance a = mc.player.getAttribute(Attributes.ATTACK_SPEED);
        // im Client sofort (Anzeige + Auto-Schlagen), auf dem Server per Befehl
        if (a != null && a.getBaseValue() < 1000) a.setBaseValue(1024);
        int now = mc.player.tickCount;
        if (now - lastApply < 100) return;
        lastApply = now;
        if (Op.available()) Op.run("attribute @s minecraft:attack_speed base set 1024");
        else if (!warned) {
            warned = true;
            Chat.error("NoCooldown: auf diesem Server ohne OP nur im Client – der Server rechnet den Cooldown trotzdem.");
        }
    }
}
