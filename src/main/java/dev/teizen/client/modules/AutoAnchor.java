package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.util.Explosions;
import dev.teizen.client.util.Inv;
import dev.teizen.client.util.Placer;
import dev.teizen.client.util.Rot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * AutoAnchor – Respawn-Anker als Waffe (Oberwelt/End; im Nether explodieren sie nicht).
 * Modus "makro" (Standard): Anker setzen wie immer – sobald du auf einen Anker schaust, lädt Teizen ihn mit Glowstone
 * und zündet ihn sofort (mit Totem/Schwert in der Hand), danach wieder Anker in die Hand.
 * Modus "ziel": setzt selbst Anker neben den nächsten Gegner, lädt und zündet sie.
 */
public final class AutoAnchor extends Module {
    private final Setting mode = add(Setting.choice("modus", "makro = Anker, auf den du schaust, laden + zünden · ziel = selbst neben Gegner setzen", "makro", "makro", "ziel"));
    private final Setting charges = add(Setting.number("ladung", "Mit wie viel Glowstone laden (1 reicht für volle Explosion)", 1, 1, 4));
    private final Setting delay = add(Setting.number("pause", "Ticks zwischen den Schritten (0 = so schnell wie möglich)", 0, 0, 10));
    private final Setting back = add(Setting.bool("zurück", "Nach dem Zünden wieder den Anker in die Hand", true));
    private final Setting totem = add(Setting.bool("totemhand", "Zum Zünden am liebsten das Totem in die Hand (überlebt eher)", true));
    private final Setting range = add(Setting.number("reichweite", "Modus ziel: wie weit Anker gesetzt werden", 4.5, 1, 6));
    private final Setting targetRange = add(Setting.number("zielreichweite", "Modus ziel: wie weit Gegner gesucht werden", 8, 2, 16));
    private final Setting minDamage = add(Setting.number("minschaden", "Modus ziel: Mindest-Schaden am Gegner (2 = 1 Herz)", 4, 0, 40));
    private final Setting players = add(Setting.bool("spieler", "Modus ziel: Spieler angreifen", true));
    private final Setting monsters = add(Setting.bool("monster", "Modus ziel: Monster angreifen", false));
    private final Setting antiSuicide = add(Setting.bool("selbstschutz", "Nicht zünden, wenn dich die Explosion töten würde (außer mit Totem)", true));
    private final Setting rotate = add(Setting.bool("drehen", "Unsichtbar zum Anker drehen", true));

    /** Zähler (Anzeige + Selbsttest). */
    public int charged, exploded, placedCount;

    private BlockPos work;
    private int wait, timeout, homeSlot = -1;
    private boolean warnedNether;

    public AutoAnchor() {
        super("AutoAnchor", Category.KAMPF, "Respawn-Anker automatisch laden und zünden (Oberwelt/End).");
    }

    @Override
    protected void onEnable() { work = null; wait = 0; homeSlot = -1; warnedNether = false; }

    @Override
    protected void onDisable() { work = null; }

    @Override
    public void onTick() {
        if (!inGame() || mc.gui.screen() != null) return;
        if (mc.level.dimension() == Level.NETHER) {
            if (!warnedNether) { dev.teizen.client.util.Chat.info("AutoAnchor: im Nether explodieren Anker nicht – hier pausiert."); warnedNether = true; }
            return;
        }
        warnedNether = false;
        if (wait > 0) { wait--; return; }

        if (work != null) {
            if (--timeout < 0 || !mc.level.getBlockState(work).is(Blocks.RESPAWN_ANCHOR)) { finish(); return; }
            step(work);
            return;
        }
        if (mode.is("makro")) {
            // Anker, auf den du schaust
            if (mc.hitResult instanceof BlockHitResult bh && mc.hitResult.getType() == HitResult.Type.BLOCK
                    && mc.level.getBlockState(bh.getBlockPos()).is(Blocks.RESPAWN_ANCHOR)) {
                start(bh.getBlockPos().immutable());
                step(work);
            }
            return;
        }
        // Modus ziel
        LivingEntity target = Targets.nearest(targetRange.num(), players.on(), monsters.on(), false, true);
        if (target == null) return;
        if (Inv.findAny(s -> s.is(Items.RESPAWN_ANCHOR)) < 0 || Inv.findAny(s -> s.is(Items.GLOWSTONE)) < 0) return;
        BlockPos p = placeNear(target);
        if (p != null) { start(p); placedCount++; wait = delay.integer(); }
    }

    private void start(BlockPos p) {
        work = p;
        timeout = 40;
        if (homeSlot < 0) homeSlot = Inv.selected();
    }

    /** Einen Schritt: laden oder zünden. */
    private void step(BlockPos p) {
        BlockState st = mc.level.getBlockState(p);
        int charge = st.getValue(RespawnAnchorBlock.CHARGE);
        Vec3 hv = Vec3.atCenterOf(p).add(0, 0.5, 0);
        BlockHitResult hit = new BlockHitResult(hv, Direction.UP, p, false);
        if (Vec3.atCenterOf(p).distanceTo(mc.player.getEyePosition()) > 5.5) { finish(); return; }
        if (rotate.on()) { float[] rr = Rot.to(hv); Rot.sendSilent(rr[0], rr[1]); }
        if (charge < Math.min(4, charges.integer())) {
            if (!Inv.ensureInHand(s -> s.is(Items.GLOWSTONE))) { finish(); return; }
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
            dev.teizen.client.compat.V.swing(InteractionHand.MAIN_HAND);
            charged++;
            wait = delay.integer();
            return;
        }
        // Geladen -> zünden (mit etwas, das kein Glowstone ist)
        if (mc.player.isShiftKeyDown()) return;
        if (antiSuicide.on() && !hasTotem() && Explosions.damage(mc.player, Vec3.atCenterOf(p), 5f, p)
                >= mc.player.getHealth() + mc.player.getAbsorptionAmount()) return;
        int slot = -1;
        if (totem.on()) slot = Inv.findHotbar(s -> s.is(Items.TOTEM_OF_UNDYING));
        if (slot < 0) slot = Inv.findHotbar(s -> !s.is(Items.GLOWSTONE) && !s.is(Items.RESPAWN_ANCHOR));
        if (slot < 0) slot = Inv.findHotbar(s -> !s.is(Items.GLOWSTONE));
        if (slot < 0) { finish(); return; }
        Inv.select(slot);
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
        dev.teizen.client.compat.V.swing(InteractionHand.MAIN_HAND);
        exploded++;
        finish();
        wait = delay.integer();
    }

    private boolean hasTotem() {
        return mc.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING) || mc.player.getMainHandItem().is(Items.TOTEM_OF_UNDYING);
    }

    private void finish() {
        work = null;
        if (back.on()) {
            int a = Inv.findHotbar(s -> s.is(Items.RESPAWN_ANCHOR));
            if (a >= 0) Inv.select(a);
            else if (homeSlot >= 0) Inv.select(homeSlot);
        }
        homeSlot = -1;
    }

    /** Modus ziel: Anker neben/über den Gegner setzen. */
    private BlockPos placeNear(LivingEntity target) {
        double r = range.num();
        Vec3 eye = mc.player.getEyePosition();
        BlockPos t = target.blockPosition();
        BlockPos best = null;
        Direction bestSupport = null;
        double bestScore = -1;
        for (int dy = 0; dy <= 2; dy++)
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos p = t.offset(dx, dy, dz);
                    if (!mc.level.getBlockState(p).canBeReplaced()) continue;
                    if (eye.distanceToSqr(Vec3.atCenterOf(p)) > r * r) continue;
                    if (!mc.level.getEntities((Entity) null, new AABB(p), e -> e instanceof LivingEntity).isEmpty()) continue;
                    Direction support = Placer.findSupport(p);
                    if (support == null) continue;
                    Vec3 boom = Vec3.atCenterOf(p);
                    double td = Explosions.damage(target, boom, 5f, p);
                    if (td < minDamage.num()) continue;
                    double sd = Explosions.damage(mc.player, boom, 5f, p);
                    if (antiSuicide.on() && !hasTotem() && sd >= mc.player.getHealth() + mc.player.getAbsorptionAmount()) continue;
                    double score = td - sd * 0.5;
                    if (score > bestScore) { bestScore = score; best = p; bestSupport = support; }
                }
        if (best == null) return null;
        if (homeSlot < 0) homeSlot = Inv.selected();
        if (!Inv.ensureInHand(s -> s.is(Items.RESPAWN_ANCHOR))) return null;
        return Placer.placeAgainst(best, bestSupport, 0.5, rotate.on()) ? best : null;
    }

    @Override
    public String info() { return mode.str(); }
}
