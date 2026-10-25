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
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * AutoCrystal. Modus "einfach" (Standard, wie die Crystal-Aura-Mod): mit Kristallen in der Hand wird auf den nächsten
 * Obsidian/Grundgestein gesetzt und jeder Kristall in 3 Blöcken Nähe sofort zerschlagen – kein Ziel nötig. Taste: J.
 * Modus "ziel": setzt End-Kristalle neben Gegner und zerschlägt sie sofort (Explosion).
 * Ohne Kristalle (oder als Ergänzung) auch mit Respawn-Ankern: setzen, mit Glowstone laden, zünden
 * (Anker explodieren in der Oberwelt und im End, nicht im Nether).
 * Dazu (einstellbar): Surround = Obsidian um die eigenen Füße, AutoTrap = Gegner mit Obsidian einbauen.
 */
public final class AutoCrystal extends Module {
    private final Setting mode = add(Setting.choice("modus", "einfach = wie die Crystal-Aura-Mod (nächster Obsidian), ziel = neben Gegner", "einfach", "einfach", "ziel"));
    private final Setting simpleBreak = add(Setting.number("schlagweite", "Modus einfach: Kristalle in dieser Nähe zerschlagen (Blöcke)", 3, 1, 6));
    private final Setting simpleHand = add(Setting.bool("nurinhand", "Modus einfach: nur wenn du Kristalle in der Hand hältst", true));
    private final Setting targetRange = add(Setting.number("zielreichweite", "Wie weit Gegner gesucht werden (Blöcke)", 10, 2, 20));
    private final Setting range = add(Setting.number("reichweite", "Wie weit Kristalle gesetzt/zerschlagen werden (Blöcke)", 4.5, 1, 6));
    private final Setting placeDelay = add(Setting.number("setzpause", "Ticks zwischen zwei Kristallen", 2, 0, 20));
    private final Setting breakDelay = add(Setting.number("schlagpause", "Ticks zwischen zwei Schlägen auf Kristalle", 1, 0, 20));
    private final Setting minDamage = add(Setting.number("minschaden", "Mindest-Schaden am Gegner (2 = 1 Herz)", 4, 0, 40));
    private final Setting maxSelf = add(Setting.number("maxselbst", "Höchstens so viel Schaden an dir selbst (2 = 1 Herz)", 20, 0, 100));
    private final Setting antiSuicide = add(Setting.bool("selbstschutz", "Nie so viel Schaden, dass du stirbst", true));
    private final Setting anchor = add(Setting.bool("anker", "Auch Respawn-Anker benutzen (Oberwelt/End)", true));
    private final Setting players = add(Setting.bool("spieler", "Spieler angreifen", true));
    private final Setting monsters = add(Setting.bool("monster", "Monster angreifen", false));
    private final Setting rotate = add(Setting.bool("drehen", "Unsichtbar zum Ziel drehen", true));
    private final Setting switchSlot = add(Setting.bool("wechseln", "Kristalle/Anker automatisch in die Hand nehmen", true));
    private final Setting surround = add(Setting.bool("surround", "Surround: Obsidian rund um deine Füße setzen (schützt vor Kristallen)", false));
    private final Setting trap = add(Setting.bool("autotrap", "AutoTrap: Gegner in deiner Nähe mit Obsidian einbauen", false));
    private final Setting trapWho = add(Setting.choice("trapziel", "Wen AutoTrap einbaut", "spieler", "spieler", "alle"));
    private final Setting trapRange = add(Setting.number("trapweite", "Wie weit AutoTrap Gegner sucht (Blöcke)", 4, 1, 5));

    public LivingEntity target;
    /** Zähler (Anzeige + Selbsttest). */
    public int placed, broken, anchors, anchorsPlaced;

    private int placeWait, breakWait;
    private BlockPos anchorPos;
    private int anchorStage, anchorTimeout;

    public AutoCrystal() {
        super("AutoCrystal", Category.KAMPF, "Setzt und zündet End-Kristalle automatisch (Taste J).");
        key = org.lwjgl.glfw.GLFW.GLFW_KEY_J;
    }

    /** Modus einfach: kürzlich belegte Plätze (10 Ticks Pause je Platz, wie bei der Mod). */
    private final java.util.Map<BlockPos, Integer> recent = new java.util.HashMap<>();

    private void simpleTick() {
        recent.replaceAll((k, v) -> v - 1);
        recent.values().removeIf(v -> v <= 0);
        Vec3 eye = mc.player.getEyePosition();
        double br = simpleBreak.num();
        // 1) alle Kristalle in der Nähe sofort zerschlagen
        java.util.List<EndCrystal> list = mc.level.getEntitiesOfClass(EndCrystal.class,
                new AABB(eye.x - br, eye.y - br, eye.z - br, eye.x + br, eye.y + br, eye.z + br),
                c -> c.isAlive() && c.position().distanceTo(eye) <= br);
        list.sort(java.util.Comparator.comparingDouble(c -> c.distanceToSqr(mc.player)));
        for (EndCrystal c : list) {
            if (rotate.on()) { float[] rr = Rot.to(c.getBoundingBox().getCenter()); Rot.sendSilent(rr[0], rr[1]); }
            mc.gameMode.attack(mc.player, c);
            mc.player.swing(InteractionHand.MAIN_HAND);
            recent.remove(c.blockPosition().below());
            broken++;
        }
        // 2) Kristall auf den nächsten freien Obsidian/Grundgestein setzen
        InteractionHand hand = mc.player.getMainHandItem().is(Items.END_CRYSTAL) ? InteractionHand.MAIN_HAND
                : mc.player.getOffhandItem().is(Items.END_CRYSTAL) ? InteractionHand.OFF_HAND : null;
        if (hand == null && !simpleHand.on()) hand = takeInHand(Items.END_CRYSTAL);
        if (hand == null) return;
        double r = range.num();
        int ri = (int) Math.ceil(r);
        BlockPos c = mc.player.blockPosition();
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-ri, -ri, -ri), c.offset(ri, ri, ri))) {
            BlockState st = mc.level.getBlockState(p);
            if (!st.is(Blocks.OBSIDIAN) && !st.is(Blocks.BEDROCK)) continue;
            if (!mc.level.getBlockState(p.above()).isAir()) continue;
            // nie direkt neben deine Füße (eigener Surround) – das würde dir selbst schaden
            if (p.getY() >= c.getY() - 1 && Math.abs(p.getX() - c.getX()) <= 1 && Math.abs(p.getZ() - c.getZ()) <= 1) continue;
            double d = eye.distanceToSqr(p.getX() + 0.5, p.getY() + 1.0, p.getZ() + 0.5);
            if (d > r * r || d >= bestD) continue;
            if (recent.containsKey(p)) continue;
            BlockPos up = p.above();
            if (!mc.level.getEntitiesOfClass(EndCrystal.class, new AABB(up)).isEmpty()) continue;
            best = p.immutable();
            bestD = d;
        }
        if (best == null) return;
        Vec3 hit = new Vec3(best.getX() + 0.5, best.getY() + 1.0, best.getZ() + 0.5);
        if (rotate.on()) { float[] rr = Rot.to(hit); Rot.sendSilent(rr[0], rr[1]); }
        mc.gameMode.useItemOn(mc.player, hand, new BlockHitResult(hit, Direction.UP, best, false));
        mc.player.swing(hand);
        recent.put(best, 10);
        placed++;
    }

    @Override
    protected void onDisable() {
        target = null;
        anchorPos = null;
    }

    private boolean safe(double selfDamage) {
        if (selfDamage > maxSelf.num()) return false;
        if (!antiSuicide.on()) return true;
        if (mc.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING) || mc.player.getMainHandItem().is(Items.TOTEM_OF_UNDYING)) return true;
        return selfDamage < mc.player.getHealth() + mc.player.getAbsorptionAmount() - 1.0;
    }

    /** Zähler für Surround / AutoTrap (Selbsttest). */
    public int surroundPlaced, trapPlaced;
    public LivingEntity trapTarget;

    private static boolean obsidian(net.minecraft.world.item.ItemStack s) {
        return s.is(Items.OBSIDIAN) || s.is(Items.CRYING_OBSIDIAN);
    }

    /** Surround: die 4 Blöcke um die Füße (und darunter, falls Luft) mit Obsidian füllen. */
    private int surroundTick(int budget) {
        if (!surround.on() || !mc.player.onGround() || budget <= 0) return 0;
        BlockPos feet = BlockPos.containing(mc.player.getX(), mc.player.getY() + 0.2, mc.player.getZ());
        int n = 0;
        BlockPos[] spots = {feet.below(), feet.north(), feet.south(), feet.east(), feet.west()};
        for (BlockPos p : spots) {
            if (n >= budget) break;
            if (!Placer.replaceable(p)) continue;
            if (dev.teizen.client.util.BlockSwap.place(p, AutoCrystal::obsidian)) { n++; surroundPlaced++; }
        }
        return n;
    }

    /** AutoTrap: Gegner mit Obsidian einbauen (Füße + Kopf rundherum, dann oben drauf). */
    private int trapTick(int budget) {
        if (!trap.on() || budget <= 0) { trapTarget = null; return 0; }
        trapTarget = Targets.nearest(trapRange.num(), true, trapWho.is("alle"), false, true);
        if (trapTarget == null) return 0;
        BlockPos f = trapTarget.blockPosition();
        AABB me = mc.player.getBoundingBox().inflate(0.1);
        java.util.List<BlockPos> spots = new java.util.ArrayList<>();
        for (Direction d : Direction.Plane.HORIZONTAL) spots.add(f.relative(d));
        for (Direction d : Direction.Plane.HORIZONTAL) spots.add(f.above().relative(d));
        spots.add(f.above(2));
        int n = 0;
        for (BlockPos p : spots) {
            if (n >= budget) break;
            if (!Placer.replaceable(p) || me.intersects(new AABB(p))) continue;
            if (Vec3.atCenterOf(p).distanceTo(mc.player.getEyePosition()) > 5.0) continue;
            if (dev.teizen.client.util.BlockSwap.place(p, AutoCrystal::obsidian)) { n++; trapPlaced++; }
        }
        return n;
    }

    @Override
    public void onTick() {
        if (!inGame() || mc.gui.screen() != null) { target = null; return; }
        // erst schützen / einbauen (höchstens 2 Blöcke pro Tick), dann Kristalle
        int used = surroundTick(2);
        trapTick(2 - used);
        if (mode.is("einfach")) { target = null; simpleTick(); return; }
        target = Targets.nearest(targetRange.num(), players.on(), monsters.on(), false, true);
        if (target == null) {
            // Anker, der schon steht, trotzdem fertig zünden (sonst bleibt ein geladener Anker liegen)
            if (anchorPos != null && anchorStage >= 2 && --anchorTimeout >= 0) anchorTick();
            else anchorPos = null;
            return;
        }
        if (breakWait > 0) breakWait--;
        if (placeWait > 0) placeWait--;

        if (breakWait == 0 && breakCrystal()) { breakWait = breakDelay.integer(); return; }
        if (anchorPos != null) { anchorTick(); return; }
        if (placeWait > 0) return;
        if (placeCrystal()) { placeWait = placeDelay.integer(); return; }
        if (anchor.on() && mc.level.dimension() != Level.NETHER && startAnchor()) placeWait = placeDelay.integer();
    }

    // ------------------------------------------------------------------ Kristalle

    private boolean breakCrystal() {
        double r = range.num();
        EndCrystal best = null;
        double bestScore = -1;
        for (EndCrystal c : mc.level.getEntitiesOfClass(EndCrystal.class, mc.player.getBoundingBox().inflate(r + 2))) {
            if (!c.isAlive() || Targets.distanceToBox(c) > r) continue;
            Vec3 boom = c.position();
            double td = Explosions.damage(target, boom, 6f, null);
            if (td < minDamage.num() && td < target.getHealth() + target.getAbsorptionAmount()) continue;
            double sd = Explosions.damage(mc.player, boom, 6f, null);
            if (!safe(sd)) continue;
            double score = td - sd * 0.5;
            if (score > bestScore) { bestScore = score; best = c; }
        }
        if (best == null) return false;
        if (rotate.on()) { float[] rr = Rot.to(best.getBoundingBox().getCenter()); Rot.sendSilent(rr[0], rr[1]); }
        mc.gameMode.attack(mc.player, best);
        mc.player.swing(InteractionHand.MAIN_HAND);
        broken++;
        return true;
    }

    private boolean placeCrystal() {
        if (Inv.findAny(s -> s.is(Items.END_CRYSTAL)) < 0 && !mc.player.getOffhandItem().is(Items.END_CRYSTAL)) return false;
        double r = range.num();
        int ri = (int) Math.ceil(r);
        Vec3 eye = mc.player.getEyePosition();
        BlockPos c = mc.player.blockPosition();
        BlockPos best = null;
        double bestScore = -1;
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-ri, -ri, -ri), c.offset(ri, ri, ri))) {
            BlockState st = mc.level.getBlockState(p);
            if (!st.is(Blocks.OBSIDIAN) && !st.is(Blocks.BEDROCK)) continue;
            BlockPos up = p.above();
            if (!mc.level.getBlockState(up).isAir()) continue;
            if (eye.distanceToSqr(p.getX() + 0.5, p.getY() + 1.0, p.getZ() + 0.5) > r * r) continue;
            AABB space = new AABB(up.getX(), up.getY(), up.getZ(), up.getX() + 1, up.getY() + 2, up.getZ() + 1);
            if (!mc.level.getEntities((Entity) null, space).isEmpty()) continue;
            Vec3 boom = new Vec3(p.getX() + 0.5, p.getY() + 1.0, p.getZ() + 0.5);
            double td = Explosions.damage(target, boom, 6f, null);
            if (td < minDamage.num()) continue;
            double sd = Explosions.damage(mc.player, boom, 6f, null);
            if (!safe(sd)) continue;
            double score = td - sd * 0.5;
            if (score > bestScore) { bestScore = score; best = p.immutable(); }
        }
        if (best == null) return false;
        InteractionHand hand = takeInHand(Items.END_CRYSTAL);
        if (hand == null) return false;
        Vec3 hit = new Vec3(best.getX() + 0.5, best.getY() + 1.0, best.getZ() + 0.5);
        if (rotate.on()) { float[] rr = Rot.to(hit); Rot.sendSilent(rr[0], rr[1]); }
        InteractionResult res = mc.gameMode.useItemOn(mc.player, hand, new BlockHitResult(hit, Direction.UP, best, false));
        if (res.consumesAction()) {
            mc.player.swing(hand);
            placed++;
            return true;
        }
        return false;
    }

    private InteractionHand takeInHand(Item item) {
        if (mc.player.getMainHandItem().is(item)) return InteractionHand.MAIN_HAND;
        if (mc.player.getOffhandItem().is(item)) return InteractionHand.OFF_HAND;
        if (!switchSlot.on()) return null;
        return Inv.ensureInHand(s -> s.is(item)) ? InteractionHand.MAIN_HAND : null;
    }

    // ------------------------------------------------------------------ Respawn-Anker

    private boolean startAnchor() {
        if (Inv.findAny(s -> s.is(Items.RESPAWN_ANCHOR)) < 0 || Inv.findAny(s -> s.is(Items.GLOWSTONE)) < 0) return false;
        double r = range.num();
        Vec3 eye = mc.player.getEyePosition();
        BlockPos t = target.blockPosition();
        BlockPos best = null;
        Direction bestSupport = null;
        double bestScore = -1;
        for (int dy = 0; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos p = t.offset(dx, dy, dz);
                    if (!mc.level.getBlockState(p).canBeReplaced()) continue;
                    if (eye.distanceToSqr(Vec3.atCenterOf(p)) > r * r) continue;
                    AABB box = new AABB(p);
                    if (!mc.level.getEntities((Entity) null, box, e -> e instanceof LivingEntity).isEmpty()) continue;
                    Direction support = Placer.findSupport(p);
                    if (support == null) continue;
                    Vec3 boom = Vec3.atCenterOf(p);
                    double td = Explosions.damage(target, boom, 5f, p);
                    if (td < minDamage.num()) continue;
                    double sd = Explosions.damage(mc.player, boom, 5f, p);
                    if (!safe(sd)) continue;
                    double score = td - sd * 0.5;
                    if (score > bestScore) { bestScore = score; best = p; bestSupport = support; }
                }
            }
        }
        if (best == null) return false;
        if (!Inv.ensureInHand(s -> s.is(Items.RESPAWN_ANCHOR))) return false;
        if (Placer.placeAgainst(best, bestSupport, 0.5, rotate.on())) {
            anchorPos = best;
            anchorStage = 1;
            anchorTimeout = 30;
            anchorsPlaced++;
            return true;
        }
        return false;
    }

    private void anchorTick() {
        if (--anchorTimeout < 0) { anchorPos = null; return; }
        BlockState st = mc.level.getBlockState(anchorPos);
        if (!st.is(Blocks.RESPAWN_ANCHOR)) {
            anchorPos = null;
            placeWait = placeDelay.integer();
            return;
        }
        Vec3 hitVec = Vec3.atCenterOf(anchorPos).add(0, 0.5, 0);
        BlockHitResult hit = new BlockHitResult(hitVec, Direction.UP, anchorPos, false);
        if (rotate.on()) { float[] rr = Rot.to(hitVec); Rot.sendSilent(rr[0], rr[1]); }
        int charge = st.getValue(RespawnAnchorBlock.CHARGE);
        if (charge == 0) {
            if (anchorStage >= 2) return; // Laden ist unterwegs
            if (!Inv.ensureInHand(s -> s.is(Items.GLOWSTONE))) { anchorPos = null; return; }
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
            mc.player.swing(InteractionHand.MAIN_HAND);
            anchorStage = 2;
            return;
        }
        if (mc.player.isShiftKeyDown()) return;
        // Geladen: mit etwas anderem als Glowstone anklicken = Explosion
        int slot = Inv.findHotbar(s -> !s.is(Items.GLOWSTONE));
        if (slot < 0) return;
        Inv.select(slot);
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
        mc.player.swing(InteractionHand.MAIN_HAND);
        if (anchorStage < 3) anchors++;
        anchorStage = 3;
    }

    @Override
    public String info() { return mode.is("einfach") ? "einfach" : target == null ? "" : target.getName().getString(); }
}
