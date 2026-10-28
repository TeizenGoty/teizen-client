package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.mixin.MultiPlayerGameModeAccessor;
import dev.teizen.client.util.Inv;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.function.Predicate;

/**
 * AutoClutch: rettet dich beim Fallen automatisch, kurz bevor du aufkommst – mit dem, was du dabei hast:
 * Wassereimer (MLG), Boot, Schleimblock, Heublock, Pulverschnee, Spinnennetz oder (zur Not) ein beliebiger Block.
 * So überlebst du hohe Stürze. Standardmäßig aus.
 */
public final class AutoClutch extends Module {
    private final Setting minFall = add(Setting.number("ab", "Erst ab so vielen Blöcken Fallhöhe retten", 4, 2, 20));
    private final Setting dist = add(Setting.number("hoehe", "So viele Blöcke über dem Boden setzen", 3, 1, 6));
    private final Setting anyBlock = add(Setting.bool("notblock", "Zur Not irgendeinen Block setzen (wenn nichts Besseres da ist)", true));
    private final Setting pickup = add(Setting.bool("wasserweg", "Wasser danach wieder aufnehmen", true));

    private int cooldown, pickupWaterTick;
    private BlockPos waterAt;
    public int clutches;

    public AutoClutch() {
        super("AutoClutch", Category.BEWEGUNG, "Rettet dich beim Fallen automatisch (Wasser, Boot, Block ...).");
    }

    private boolean isBoat(ItemStack s) {
        return s.getItem() instanceof net.minecraft.world.item.BoatItem;
    }

    /** Fester „Clutch"-Block (landet man weich/sicher drauf). */
    private boolean clutchBlock(ItemStack s) {
        return s.is(Items.SLIME_BLOCK) || s.is(Items.HAY_BLOCK) || s.is(Items.COBWEB) || s.is(Items.POWDER_SNOW_BUCKET);
    }

    @Override
    public void onTick() {
        if (!inGame()) return;
        if (cooldown > 0) cooldown--;
        // Wasser wieder aufnehmen, kurz nachdem es gesetzt wurde
        if (waterAt != null && pickup.on() && mc.player.tickCount >= pickupWaterTick) {
            pickUpWater();
            waterAt = null;
        }
        var p = mc.player;
        if (cooldown > 0 || p.onGround() || p.isInWater() || p.isInLava() || p.isFallFlying()
                || p.getAbilities().flying || p.getDeltaMovement().y > -0.35 || p.isPassenger()) return;
        if (p.fallDistance < minFall.num()) return;

        // je schneller der Fall, desto früher auslösen (ein Tick kann mehrere Blöcke überspringen)
        int vy = (int) Math.ceil(Math.max(0, -p.getDeltaMovement().y));
        int window = Math.max(dist.integer(), 2) + vy;
        int ground = groundBelow(window + 2);
        if (ground < 0 || ground > window) return;

        // bestes Rettungsmittel wählen
        if (place(s -> s.is(Items.WATER_BUCKET), true, true)) { clutches++; cooldown = 10; return; }
        if (place(this::isBoat, true, false)) { clutches++; cooldown = 10; return; }
        if (place(this::clutchBlock, false, false)) { clutches++; cooldown = 10; return; }
        if (anyBlock.on() && place(s -> s.getItem() instanceof net.minecraft.world.item.BlockItem, false, false)) { clutches++; cooldown = 10; return; }
    }

    /** Abstand zum nächsten festen Block unter einem (in Blöcken), oder -1. */
    private int groundBelow(double depth) {
        Vec3 from = mc.player.position();
        Vec3 to = from.add(0, -depth, 0);
        BlockHitResult hit = mc.level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, mc.player));
        if (hit.getType() != HitResult.Type.BLOCK) return -1;
        return (int) Math.round(from.y - hit.getLocation().y);
    }

    /**
     * Rettungsmittel benutzen.
     * @param useItem true = per Rechtsklick nach unten (Eimer/Boot), false = Block auf den Boden setzen
     * @param isWater für das spätere Aufnehmen merken
     */
    private boolean place(Predicate<ItemStack> want, boolean useItem, boolean isWater) {
        if (!Inv.ensureInHand(want)) return false;
        float pitch = mc.player.getXRot(), yaw = mc.player.getYRot();
        mc.player.setXRot(90f); // nach unten
        ((MultiPlayerGameModeAccessor) mc.gameMode).teizen$sendCarriedItem();
        boolean ok;
        if (useItem) {
            ok = mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND).consumesAction();
            if (isWater && ok) { waterAt = mc.player.blockPosition(); pickupWaterTick = mc.player.tickCount + 8; }
        } else {
            ok = placeBelow();
        }
        mc.player.setXRot(pitch);
        mc.player.setYRot(yaw);
        return ok;
    }

    /** Einen Block direkt unter sich setzen (auf die Oberseite des Bodenblocks). */
    private boolean placeBelow() {
        BlockPos feet = mc.player.blockPosition();
        for (int d = 1; d <= dist.integer() + 2; d++) {
            BlockPos below = feet.below(d);
            BlockState s = mc.level.getBlockState(below);
            if (s.isAir() || !s.getFluidState().isEmpty() || s.canBeReplaced()) continue;
            BlockPos target = below.above();
            Vec3 hit = Vec3.atCenterOf(below).add(0, 0.5, 0);
            var res = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(hit, Direction.UP, below, false));
            if (res.consumesAction()) { dev.teizen.client.compat.V.swing(InteractionHand.MAIN_HAND); return true; }
            return false;
        }
        return false;
    }

    private void pickUpWater() {
        if (!Inv.ensureInHand(s -> s.is(Items.BUCKET))) return;
        float pitch = mc.player.getXRot();
        mc.player.setXRot(90f);
        ((MultiPlayerGameModeAccessor) mc.gameMode).teizen$sendCarriedItem();
        mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        mc.player.setXRot(pitch);
    }

    @Override
    public String info() { return clutches > 0 ? "§a" + clutches : ""; }
}
