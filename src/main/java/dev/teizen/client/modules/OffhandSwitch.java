package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.util.Inv;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * OffhandSwitch: hält automatisch die beste Sache in der linken Hand. Standard "schlau": normalerweise ein Totem,
 * aber wenn gerade Gefahr droht (Gegner nah, Pfeil/Dreizack fliegt auf dich zu oder wenig Leben), ein Schild – danach
 * wieder das Totem. Oder fest auf Totem bzw. Schild stellen. Du brauchst Totem/Schild im Inventar.
 */
public final class OffhandSwitch extends Module {
    private final Setting mode = add(Setting.choice("modus", "schlau = Totem, bei Gefahr Schild; sonst fest auf Totem oder Schild",
            "schlau", "schlau", "totem", "schild"));
    private final Setting lowHp = add(Setting.number("schildableben", "Im Modus schlau: ab so wenig Leben lieber Schild", 8, 1, 19));
    private final Setting near = add(Setting.number("naehe", "Im Modus schlau: Gegner bis so nah = Schild", 5, 1, 12));
    private final Setting delay = add(Setting.number("verzoegerung", "Ticks zwischen zwei Tauschvorgängen", 2, 0, 20));
    private int wait;

    public OffhandSwitch() {
        super("OffhandSwitch", Category.KAMPF, "Hält automatisch das Beste in der linken Hand (schlau zwischen Totem und Schild).");
    }

    private static boolean isTotem(ItemStack s) { return s.is(Items.TOTEM_OF_UNDYING); }
    private static boolean isShield(ItemStack s) { return s.is(Items.SHIELD); }

    /** Droht gerade Gefahr? (Gegner nah, Geschoss auf dich zu, wenig Leben) */
    private boolean threatened() {
        if (mc.player.getHealth() <= lowHp.num()) return true;
        double r = near.num();
        var box = mc.player.getBoundingBox().inflate(Math.max(r, 12));
        for (var e : mc.level.getEntitiesOfClass(LivingEntity.class, mc.player.getBoundingBox().inflate(r),
                e -> e != mc.player && e.isAlive() && (e instanceof Enemy || (e instanceof Player p && !p.isCreative() && !p.isSpectator())))) {
            if (e.distanceToSqr(mc.player) <= r * r) return true;
        }
        // Geschoss (Pfeil/Dreizack/…), das sich dir nähert
        for (Projectile a : mc.level.getEntitiesOfClass(Projectile.class, box)) {
            if (a.getOwner() == mc.player) continue;
            Vec3 v = a.getDeltaMovement();
            if (v.lengthSqr() < 0.1) continue; // am Boden steckende Pfeile haben ~0 Tempo
            Vec3 toMe = mc.player.position().add(0, 1, 0).subtract(a.position());
            if (v.normalize().dot(toMe.normalize()) > 0.6 && toMe.lengthSqr() < 64) return true;
        }
        return false;
    }

    /** Was soll in die linke Hand? (Item-Prüfer oder null, wenn egal) */
    private java.util.function.Predicate<ItemStack> wanted() {
        if (mode.is("totem")) return OffhandSwitch::isTotem;
        if (mode.is("schild")) return OffhandSwitch::isShield;
        // schlau
        boolean danger = threatened();
        boolean haveShield = Inv.findAny(OffhandSwitch::isShield) >= 0 || isShield(mc.player.getOffhandItem());
        if (danger && haveShield) return OffhandSwitch::isShield;
        return OffhandSwitch::isTotem;
    }

    @Override
    public void onTick() {
        if (!inGame()) return;
        if (wait > 0) { wait--; return; }
        if (mc.gui.screen() != null && !(mc.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen)) return;
        var want = wanted();
        if (want.test(mc.player.getOffhandItem())) return; // passt schon
        int slot = Inv.findAny(want);
        if (slot < 0) return; // nichts Passendes dabei
        Inv.swapToOffhand(slot);
        wait = delay.integer();
    }

    @Override
    public String info() { return mode.str(); }
}
