package dev.teizen.client.modules;

import dev.teizen.client.TeizenClient;
import dev.teizen.client.core.Friends;
import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.mixin.MultiPlayerGameModeAccessor;
import dev.teizen.client.util.Chat;
import dev.teizen.client.util.Inv;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * AutoRescue: bei wenig Leben automatisch retten. Findet die Ursache und reagiert:
 * - Feuer / Lava: wirft Wasser unter dich (MLG), wenn ein Wassereimer da ist, sonst weg von der Lava.
 * - Gegner in der Nähe (Monster oder fremde Spieler): schaltet GodPvP an, bis alles um dich herum weg ist,
 *   und danach wieder aus (nur wenn AutoRescue es selbst angeschaltet hat).
 * Standardmäßig aus – schaltet im Notfall andere Cheats dazu.
 */
public final class AutoRescue extends Module {
    private final Setting hearts = add(Setting.number("herzen", "Ab wie vielen Herzen die Rettung startet", 6, 1, 10));
    private final Setting range = add(Setting.number("reichweite", "Wie nah ein Gegner sein muss (Blöcke)", 8, 3, 20));
    private final Setting useGod = add(Setting.bool("godpvp", "Bei Gegnern GodPvP anschalten, bis sie weg sind", true));
    private final Setting water = add(Setting.bool("wasser", "Bei Feuer/Lava Wasser werfen (braucht Wassereimer)", true));

    private boolean weEnabledGod;
    private int clearTicks, waterCooldown;
    /** Zähler für den Selbsttest. */
    public int godRescues, waterRescues;

    public AutoRescue() {
        super("AutoRescue", Category.SONSTIGES, "Bei wenig Leben: Wasser gegen Lava oder GodPvP gegen Gegner, automatisch.");
    }

    private float hp() { return mc.player.getHealth() + mc.player.getAbsorptionAmount(); }

    private boolean lavaDanger() {
        if (mc.player.isOnFire() || mc.player.isInLava()) return true;
        var feet = mc.player.blockPosition();
        for (var p : new net.minecraft.core.BlockPos[]{feet, feet.below(), feet.above(),
                feet.north(), feet.south(), feet.east(), feet.west()}) {
            if (mc.level.getFluidState(p).is(net.minecraft.tags.FluidTags.LAVA)) return true;
        }
        return false;
    }

    private LivingEntity nearestEnemy() {
        double r = range.num();
        LivingEntity best = null;
        double bd = r * r;
        for (var e : mc.level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity le) || le == mc.player || !le.isAlive()) continue;
            boolean enemy = le instanceof Monster
                    || (le instanceof Player p && !p.isSpectator() && !Friends.is(p.getGameProfile().name()));
            if (!enemy) continue;
            double d = le.distanceToSqr(mc.player);
            if (d < bd) { bd = d; best = le; }
        }
        return best;
    }

    private void throwWater() {
        if (waterCooldown > 0) return;
        if (!Inv.ensureInHand(s -> s.is(Items.WATER_BUCKET))) return;
        float pitch = mc.player.getXRot();
        mc.player.setXRot(90f); // nach unten schauen
        ((MultiPlayerGameModeAccessor) mc.gameMode).teizen$sendCarriedItem();
        mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        mc.player.setXRot(pitch);
        waterCooldown = 20;
        waterRescues++;
    }

    private void godOn() {
        GodPvP g = TeizenClient.modules().get(GodPvP.class);
        if (g == null || g.isEnabled()) return;
        g.setEnabled(true);
        if (g.isEnabled()) { weEnabledGod = true; godRescues++; Chat.ok("AutoRescue: GodPvP an – Gegner in der Nähe."); }
    }

    private void godOff() {
        if (!weEnabledGod) return;
        GodPvP g = TeizenClient.modules().get(GodPvP.class);
        if (g != null && g.isEnabled()) g.setEnabled(false);
        weEnabledGod = false;
        Chat.info("AutoRescue: GodPvP wieder aus – alles ruhig.");
    }

    @Override
    protected void onDisable() { godOff(); clearTicks = 0; }

    @Override
    public void onTick() {
        if (waterCooldown > 0) waterCooldown--;
        if (!inGame() || !mc.player.isAlive()) { godOff(); return; }

        boolean low = hp() <= hearts.num() * 2;

        // Feuer / Lava zuerst – das ist die Ursache, die man sofort stoppen muss
        if (low && water.on() && lavaDanger()) throwWater();

        // Gegner: GodPvP an, solange welche in der Nähe sind
        if (!useGod.on()) { godOff(); return; }
        LivingEntity enemy = nearestEnemy();
        if (low && enemy != null) {
            godOn();
            clearTicks = 0;
        } else if (weEnabledGod) {
            // niemand mehr in Reichweite -> kurz warten, dann GodPvP wieder aus
            if (enemy == null) {
                if (++clearTicks >= 40) godOff();
            } else {
                clearTicks = 0; // noch Gegner da (auch wenn Leben wieder ok) -> anlassen bis weg
            }
        }
    }

    @Override
    public String info() { return weEnabledGod ? "§cGodPvP" : (hp() <= hearts.num() * 2 ? "§e!" : ""); }
}
