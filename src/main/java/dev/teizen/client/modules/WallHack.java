package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

/** WallHack: Spieler, Monster, Tiere und Items leuchten durch Wände (Umriss wie beim Leuchten-Effekt). */
public final class WallHack extends Module {
    private final Setting players = add(Setting.bool("spieler", "Spieler", true));
    private final Setting monsters = add(Setting.bool("monster", "Monster", true));
    private final Setting animals = add(Setting.bool("tiere", "Tiere und andere Mobs", false));
    private final Setting items = add(Setting.bool("items", "Gedroppte Items", false));
    private final Setting range = add(Setting.number("weite", "Bis zu so vielen Blöcken", 96, 8, 256));
    public int glowing;

    public WallHack() {
        super("WallHack", Category.SICHT, "Spieler, Monster und Items leuchten durch Wände.");
    }

    public boolean shouldGlow(Entity e) {
        if (!isEnabled() || mc.player == null || e == mc.player) return false;
        if (e.distanceToSqr(mc.player) > range.num() * range.num()) return false;
        boolean g = e instanceof Player ? players.on()
                : e instanceof Enemy ? monsters.on()
                : e instanceof ItemEntity ? items.on()
                : e instanceof net.minecraft.world.entity.LivingEntity && animals.on();
        if (g) glowing++;
        return g;
    }
}
