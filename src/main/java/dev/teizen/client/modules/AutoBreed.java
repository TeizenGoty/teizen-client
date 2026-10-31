package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.util.Inv;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.phys.EntityHitResult;

import java.util.HashMap;
import java.util.Map;

/** AutoBreed: füttert Tiere in deiner Nähe automatisch zum Züchten (das passende Futter muss im Inventar sein). */
public final class AutoBreed extends Module {
    private final Setting delay = add(Setting.number("pause", "Ticks zwischen zwei Tieren", 4, 0, 20));
    private final Map<Integer, Integer> fed = new HashMap<>();
    private int wait;
    public int feeds;

    public AutoBreed() {
        super("AutoBreed", Category.SONSTIGES, "Füttert Tiere in der Nähe automatisch zum Züchten.");
    }

    @Override
    protected void onEnable() { fed.clear(); }

    @Override
    public void onTick() {
        if (!inGame() || mc.gui.screen() != null) return;
        fed.replaceAll((k, v) -> v - 1);
        fed.values().removeIf(v -> v <= 0);
        if (wait > 0) { wait--; return; }
        double r = mc.player.entityInteractionRange();
        for (Animal a : mc.level.getEntitiesOfClass(Animal.class, mc.player.getBoundingBox().inflate(r))) {
            if (!a.isAlive() || a.isBaby() || a.isInLove() || !a.canFallInLove() || fed.containsKey(a.getId())) continue;
            if (mc.player.distanceTo(a) > r) continue;
            if (Inv.findHotbar(a::isFood) < 0 && Inv.findAny(a::isFood) < 0) continue;
            if (!Inv.ensureInHand(a::isFood)) continue;
            mc.gameMode.interact(mc.player, a, new EntityHitResult(a), InteractionHand.MAIN_HAND);
            dev.teizen.client.compat.V.swing(InteractionHand.MAIN_HAND);
            fed.put(a.getId(), 600); // ~30 s nicht nochmal (Liebes-Pause)
            feeds++;
            wait = delay.integer();
            return;
        }
    }

    @Override
    public String info() { return String.valueOf(feeds); }
}
