package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Größere Reichweite zum Schlagen und Bauen.
 * In der eigenen Welt / wenn du hostest wird es auch auf der Server-Seite gesetzt, damit es wirklich zählt.
 */
public final class Reach extends Module {
    public final Setting fight = add(Setting.number("kampf", "Reichweite beim Schlagen (normal 3)", 6, 3, 12));
    public final Setting build = add(Setting.number("bauen", "Reichweite beim Bauen/Abbauen (normal 4.5)", 7, 4.5, 12));

    private double oldFight = -1, oldBuild = -1;

    public Reach() {
        super("Reach", Category.KAMPF, "Größere Reichweite beim Kämpfen und Bauen.");
    }

    @Override
    protected void onEnable() {
        if (!inGame()) return;
        oldFight = base(mc.player, Attributes.ENTITY_INTERACTION_RANGE, 3.0);
        oldBuild = base(mc.player, Attributes.BLOCK_INTERACTION_RANGE, 4.5);
    }

    @Override
    protected void onDisable() {
        if (!inGame()) return;
        apply(oldFight > 0 ? oldFight : 3.0, oldBuild > 0 ? oldBuild : 4.5);
    }

    @Override
    public void onTick() {
        if (!inGame()) return;
        apply(fight.num(), build.num());
    }

    private void apply(double f, double b) {
        set(mc.player, Attributes.ENTITY_INTERACTION_RANGE, f);
        set(mc.player, Attributes.BLOCK_INTERACTION_RANGE, b);
        IntegratedServer server = mc.getSingleplayerServer();
        if (server != null) {
            java.util.UUID id = mc.player.getUUID();
            server.execute(() -> {
                ServerPlayer sp = server.getPlayerList().getPlayer(id);
                if (sp != null) {
                    set(sp, Attributes.ENTITY_INTERACTION_RANGE, f);
                    set(sp, Attributes.BLOCK_INTERACTION_RANGE, b);
                }
            });
        }
    }

    private static double base(LivingEntity e, Holder<Attribute> a, double def) {
        AttributeInstance inst = e.getAttribute(a);
        return inst == null ? def : inst.getBaseValue();
    }

    private static void set(LivingEntity e, Holder<Attribute> a, double v) {
        AttributeInstance inst = e.getAttribute(a);
        if (inst != null && inst.getBaseValue() != v) inst.setBaseValue(v);
    }

    @Override
    public String info() { return Setting.fmt(fight.num()); }
}
