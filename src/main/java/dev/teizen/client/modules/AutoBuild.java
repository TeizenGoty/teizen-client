package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.util.Chat;
import dev.teizen.client.util.Draw;
import dev.teizen.client.util.Inv;
import dev.teizen.client.util.Rot;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EndRodBlock;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.LightningRodBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AutoBuild ("Printer"): baut das geladene Litematica-Hologramm automatisch nach.
 * Du brauchst nur die Blöcke im Inventar und läufst am Bau entlang.
 */
public final class AutoBuild extends Module {
    private final Setting speed = add(Setting.number("tempo", "Blöcke pro Tick", 2, 1, 10));
    private final Setting range = add(Setting.number("reichweite", "Wie weit um dich herum gebaut wird", 4.5, 2, 6));
    private final Setting delay = add(Setting.number("pause", "Ticks Pause zwischen Durchgängen", 0, 0, 10));
    private final Setting slot = add(Setting.number("slot", "Hotbar-Slot (1-9) für Blöcke aus dem Inventar", 9, 1, 9));
    private final Setting showMissing = add(Setting.bool("fehlend", "Fehlende Items im Chat melden", true));
    private final Setting airPlace = add(Setting.bool("luft", "Auch ohne Stütze bauen (in die Luft klicken)", true));
    private final Setting autoWalk = add(Setting.bool("laufen", "Von selbst zum Bau laufen (du musst nichts tun)", true));
    private final Setting dig = add(Setting.bool("abbauen", "Was nicht zur Schematic passt abbauen (Erde wo Luft sein soll = Loch graben, falsche Blöcke weg)", true));

    private BlockPos digging;
    private BlockPos walkTarget;
    private int walkScan;
    private int digTicks;
    public int dugTotal;
    private java.util.List<int[]> boxes = java.util.List.of();

    private final Map<BlockPos, Integer> cooldown = new HashMap<>();
    private final List<BlockPos> lastPlaced = new ArrayList<>();
    private int wait;
    private int missingMsg;
    private int placedTotal;

    public AutoBuild() {
        super("AutoBuild", Category.BAUEN, "Baut eine Litematica-Schematic automatisch nach.");
    }

    @Override
    protected void onEnable() {
        if (!FabricLoader.getInstance().isModLoaded("litematica")) {
            Chat.error("AutoBuild braucht die Mod Litematica.");
            setEnabled(false);
            return;
        }
        if (!dev.teizen.client.compat.LitematicaCompat.hasSchematic()) {
            Chat.info("Lade zuerst eine Schematic in Litematica (Taste M) und platziere sie.");
        }
        cooldown.clear();
        placedTotal = 0;
        dugTotal = 0;
        digging = null;
    }

    @Override
    protected void onDisable() {
        if (digging != null) dev.teizen.client.util.Breaker.stop();
        digging = null;
        walkTarget = null;
        releaseWalkKeys();
    }

    private void releaseWalkKeys() {
        if (mc.options == null) return;
        mc.options.keyUp.setDown(false);
        mc.options.keyJump.setDown(false);
    }

    private boolean inSchematic(BlockPos p) {
        for (int[] b : boxes)
            if (p.getX() >= b[0] && p.getX() <= b[3] && p.getY() >= b[1] && p.getY() <= b[4] && p.getZ() >= b[2] && p.getZ() <= b[5]) return true;
        return false;
    }

    /** Muss dieser Block weg? (Schematic will dort Luft oder einen anderen Block, der nicht einfach drüber gebaut werden kann) */
    private boolean mustDig(BlockPos p) {
        if (!inSchematic(p)) return false;
        BlockState have = mc.level.getBlockState(p);
        if (have.isAir() || !have.getFluidState().isEmpty()) return false;
        BlockState want = dev.teizen.client.compat.LitematicaCompat.schematicState(p);
        if (want == null || have == want) return false;
        if (!want.isAir()) {
            if (have.getBlock() == want.getBlock()) return false; // nur andere Drehung/Zustand -> nicht abreißen
            if (have.canBeReplaced() || needsSecondSlab(have, want)) return false;
        }
        if (!dev.teizen.client.util.Breaker.canBreak(p)) return false;
        // nicht unter den eigenen Füßen graben und nichts, wo Wasser/Lava nachlaufen würde
        if (p.equals(mc.player.blockPosition().below())) return false;
        return dev.teizen.client.util.MinePath.canClear(mc.level, p);
    }

    /** Einen Tick abbauen. @return true wenn gerade abgebaut wird (dann diesen Tick nicht bauen) */
    private boolean digTick(double r, BlockPos me, int ri, Vec3 eye) {
        if (digging != null) {
            if (!mustDig(digging) || eye.distanceToSqr(Vec3.atCenterOf(digging)) > r * r || ++digTicks > 200) {
                if (mc.level.getBlockState(digging).isAir()) dugTotal++;
                dev.teizen.client.util.Breaker.stop();
                digging = null;
            }
        }
        if (digging == null) {
            BlockPos best = null;
            double bd = Double.MAX_VALUE;
            for (BlockPos p : BlockPos.betweenClosed(me.offset(-ri, -ri, -ri), me.offset(ri, ri + 1, ri))) {
                if (eye.distanceToSqr(Vec3.atCenterOf(p)) > r * r || !mustDig(p)) continue;
                // von oben nach unten graben, dann der nächste
                double score = -p.getY() * 1000.0 + p.distSqr(me);
                if (score < bd) { bd = score; best = p.immutable(); }
            }
            if (best == null) return false;
            digging = best;
            digTicks = 0;
        }
        boolean gone = dev.teizen.client.util.Breaker.tick(digging, true, true);
        if (gone) {
            dugTotal++;
            lastPlaced.add(digging);
            digging = null;
        }
        return true;
    }

    @Override
    public void onTick() {
        if (!inGame() || mc.gui.screen() != null) return;
        if (wait > 0) { wait--; return; }
        cooldown.replaceAll((k, v) -> v - 1);
        cooldown.values().removeIf(v -> v <= 0);
        lastPlaced.clear();

        double r = range.num();
        BlockPos me = mc.player.blockPosition();
        int ri = (int) Math.ceil(r);
        Vec3 eye = mc.player.getEyePosition();
        if (dig.on()) {
            try {
                boxes = dev.teizen.client.compat.LitematicaCompat.regionBoxes();
            } catch (Throwable t) {
                boxes = java.util.List.of();
            }
            if (digTick(r, me, ri, eye)) return;
        }
        List<BlockPos> todo = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(me.offset(-ri, -ri, -ri), me.offset(ri, ri + 1, ri))) {
            if (eye.distanceToSqr(Vec3.atCenterOf(p)) > r * r) continue;
            if (cooldown.containsKey(p)) continue;
            BlockState want = dev.teizen.client.compat.LitematicaCompat.schematicState(p);
            if (want == null || want.isAir()) continue;
            BlockState have = mc.level.getBlockState(p);
            if (have == want) continue;
            if (!have.canBeReplaced() && !needsSecondSlab(have, want)) continue; // falscher Block steht da -> nicht anfassen
            if (!placeable(want)) continue;
            todo.add(p.immutable());
        }
        // von unten nach oben bauen, damit Stützen zuerst kommen
        todo.sort((a, b) -> a.getY() != b.getY() ? Integer.compare(a.getY(), b.getY())
                : Double.compare(a.distSqr(me), b.distSqr(me)));

        int done = 0;
        for (BlockPos p : todo) {
            if (done >= speed.integer()) break;
            BlockState want = dev.teizen.client.compat.LitematicaCompat.schematicState(p);
            if (tryPlace(p, want)) {
                done++;
                placedTotal++;
                lastPlaced.add(p);
                cooldown.put(p, 10);
            } else {
                cooldown.put(p, 6);
            }
        }
        if (done > 0) wait = delay.integer();
        // nichts in Reichweite gebaut -> von selbst zur nächsten Baustelle laufen
        if (autoWalk.on() && done == 0) walkToWork(me, eye);
        else releaseWalkKeys();
    }

    /** Zur nächsten noch offenen Schematic-Stelle laufen, damit man selbst nichts tun muss. */
    private void walkToWork(BlockPos me, Vec3 eye) {
        if (mc.gui.screen() != null) { releaseWalkKeys(); return; }
        if (walkTarget == null || !isWork(walkTarget) || --walkScan <= 0) {
            walkTarget = findWork(me);
            walkScan = 10;
        }
        if (walkTarget == null) { releaseWalkKeys(); return; }
        Vec3 c = Vec3.atBottomCenterOf(walkTarget);
        double dx = c.x - mc.player.getX(), dz = c.z - mc.player.getZ();
        double horiz = Math.sqrt(dx * dx + dz * dz);
        if (horiz < 0.6) { releaseWalkKeys(); return; } // nah genug – der Bau-/Grab-Teil macht den Rest
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        mc.player.setYRot(yaw);
        mc.player.connection.send(new ServerboundMovePlayerPacket.Rot(yaw, mc.player.getXRot(), mc.player.onGround(), mc.player.horizontalCollision));
        mc.options.keyUp.setDown(true);
        // hoch, wenn die Stelle über uns liegt oder etwas im Weg ist
        boolean stepUp = c.y > mc.player.getY() + 0.6 || mc.player.horizontalCollision;
        mc.options.keyJump.setDown(stepUp && mc.player.onGround());
        mc.player.setSprinting(horiz > 2);
    }

    /** Ist an dieser Stelle noch etwas zu tun (bauen oder – wenn an – abbauen)? */
    private boolean isWork(BlockPos p) {
        BlockState want = dev.teizen.client.compat.LitematicaCompat.schematicState(p);
        if (want == null) return false;
        BlockState have = mc.level.getBlockState(p);
        if (!want.isAir()) return have != want && (have.canBeReplaced() || needsSecondSlab(have, want)) && placeable(want);
        return dig.on() && !have.isAir() && have.getFluidState().isEmpty() && !p.equals(mc.player.blockPosition().below());
    }

    /** Nächste offene Stelle im Umkreis (bis 14 Blöcke), oder null. */
    private BlockPos findWork(BlockPos me) {
        BlockPos best = null;
        double bd = Double.MAX_VALUE;
        int R = 14;
        for (BlockPos p : BlockPos.betweenClosed(me.offset(-R, -R, -R), me.offset(R, R, R))) {
            if (!isWork(p)) continue;
            double d = p.distSqr(me);
            if (d < bd) { bd = d; best = p.immutable(); }
        }
        return best;
    }

    private static boolean needsSecondSlab(BlockState have, BlockState want) {
        return want.getBlock() instanceof SlabBlock && have.getBlock() == want.getBlock()
                && have.getValue(SlabBlock.TYPE) != SlabType.DOUBLE && want.getValue(SlabBlock.TYPE) == SlabType.DOUBLE;
    }

    private static boolean placeable(BlockState s) {
        if (s.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && s.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER)
            return false;
        if (s.hasProperty(BlockStateProperties.BED_PART) && s.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD) return false;
        if (!s.getFluidState().isEmpty() && s.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock) return false;
        return s.getBlock().asItem() != Items.AIR;
    }

    private boolean ensureItem(Item item) {
        if (mc.player.getMainHandItem().is(item)) return true;
        int h = Inv.findHotbar(s -> s.is(item));
        if (h >= 0) { Inv.select(h); return true; }
        if (mc.player.isCreative()) {
            int target = slot.integer() - 1;
            mc.gameMode.handleCreativeModeItemAdd(new ItemStack(item), 36 + target);
            Inv.inv().setItem(target, new ItemStack(item));
            Inv.select(target);
            return true;
        }
        int any = Inv.findAny(s -> s.is(item));
        if (any < 0) {
            if (showMissing.on() && ++missingMsg % 100 == 1) Chat.info("AutoBuild: dir fehlt §f" + item.getName(new ItemStack(item)).getString());
            return false;
        }
        int target = slot.integer() - 1;
        Inv.swapToHotbar(any, target);
        Inv.select(target);
        return mc.player.getMainHandItem().is(item);
    }

    /** Kann man an diesen Block etwas dranbauen? (Kisten, Öfen, Türen usw. nicht – die würden sich öffnen) */
    private boolean solid(BlockPos p) {
        BlockState s = mc.level.getBlockState(p);
        if (s.canBeReplaced() || s.isAir()) return false;
        Block b = s.getBlock();
        if (b instanceof DoorBlock || b instanceof TrapDoorBlock || b instanceof FenceGateBlock || b instanceof ButtonBlock
                || b instanceof LeverBlock || b instanceof BedBlock || b instanceof net.minecraft.world.level.block.NoteBlock
                || b instanceof net.minecraft.world.level.block.RepeaterBlock || b instanceof net.minecraft.world.level.block.ComparatorBlock
                || b instanceof net.minecraft.world.level.block.CakeBlock || b instanceof net.minecraft.world.level.block.BellBlock
                || b instanceof net.minecraft.world.level.block.FlowerPotBlock || b instanceof net.minecraft.world.level.block.DaylightDetectorBlock)
            return false;
        try {
            if (s.getMenuProvider(mc.level, p) != null) return false;
        } catch (Throwable ignored) {}
        return !s.hasBlockEntity() || b instanceof net.minecraft.world.level.block.SpawnerBlock;
    }

    /** Direkt in den leeren Block klicken (geht auch ohne Nachbarblock). */
    private boolean airClick(BlockPos pos, Direction face, double hitY, float yaw, float pitch) {
        if (!airPlace.on()) return false;
        Vec3 hit = Vec3.atCenterOf(pos).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
        if (face.getAxis() != Direction.Axis.Y) hit = new Vec3(hit.x, pos.getY() + hitY, hit.z);
        return click(pos, face, hit, yaw, pitch);
    }

    private boolean tryPlace(BlockPos pos, BlockState want) {
        Block block = want.getBlock();
        Item item = block.asItem();
        if (!ensureItem(item)) return false;

        BlockState have = mc.level.getBlockState(pos);
        // Doppelte Stufe: zweite Hälfte draufklicken
        if (needsSecondSlab(have, want)) {
            boolean top = have.getValue(SlabBlock.TYPE) == SlabType.BOTTOM;
            Direction face = top ? Direction.UP : Direction.DOWN;
            Vec3 hit = Vec3.atCenterOf(pos).add(0, top ? 0.5 : -0.5, 0);
            return click(pos, face, hit, mc.player.getYRot(), top ? 90 : -90);
        }

        Direction facing = null;
        if (want.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) facing = want.getValue(BlockStateProperties.HORIZONTAL_FACING);
        else if (want.hasProperty(BlockStateProperties.FACING)) facing = want.getValue(BlockStateProperties.FACING);
        else if (want.hasProperty(BlockStateProperties.FACING_HOPPER)) facing = want.getValue(BlockStateProperties.FACING_HOPPER);

        // 1) Blöcke, die an einer Wand/Fläche hängen -> genau dort anklicken
        boolean wallAttached = block instanceof WallTorchBlock || block instanceof WallSignBlock || block instanceof LadderBlock
                || block instanceof WallBannerBlock || block instanceof net.minecraft.world.level.block.TripWireHookBlock;
        if (block instanceof FaceAttachedHorizontalDirectionalBlock && want.hasProperty(BlockStateProperties.ATTACH_FACE)) {
            AttachFace af = want.getValue(BlockStateProperties.ATTACH_FACE);
            if (af == AttachFace.WALL) wallAttached = true;
            else {
                Direction sup = af == AttachFace.FLOOR ? Direction.DOWN : Direction.UP;
                if (!solid(pos.relative(sup))) return false;
                return clickNeighbor(pos, sup, 0.5, facing != null ? facing.toYRot() : mc.player.getYRot(), af == AttachFace.FLOOR ? 60 : -60);
            }
        }
        if (wallAttached && facing != null) {
            Direction sup = facing.getOpposite();
            if (!solid(pos.relative(sup))) return false;
            return clickNeighbor(pos, sup, 0.5, sup.toYRot(), 0);
        }
        if ((block instanceof EndRodBlock || block instanceof LightningRodBlock || block instanceof net.minecraft.world.level.block.AmethystClusterBlock) && facing != null) {
            Direction sup = facing.getOpposite();
            if (solid(pos.relative(sup))) return clickNeighbor(pos, sup, 0.5, mc.player.getYRot(), 0);
        }
        if (block instanceof HopperBlock && facing != null) {
            if (facing == Direction.DOWN) {
                Direction sup = supportAny(pos);
                return sup != null && clickNeighbor(pos, sup, 0.5, mc.player.getYRot(), 0) ;
            }
            if (solid(pos.relative(facing))) return clickNeighbor(pos, facing, 0.5, mc.player.getYRot(), 0);
        }

        // 2) Achse (Stämme, Säulen, Ketten, Heuballen): die Achse kommt von der angeklickten Seite
        if (want.hasProperty(BlockStateProperties.AXIS)) {
            Direction.Axis axis = want.getValue(BlockStateProperties.AXIS);
            for (Direction d : new Direction[]{Direction.get(Direction.AxisDirection.NEGATIVE, axis), Direction.get(Direction.AxisDirection.POSITIVE, axis)}) {
                if (solid(pos.relative(d))) return clickNeighbor(pos, d, 0.5, mc.player.getYRot(), 0);
            }
            // kein passender Nachbar -> direkt mit der richtigen Seite in die Luft klicken
            return airClick(pos, Direction.get(Direction.AxisDirection.POSITIVE, axis), 0.5, mc.player.getYRot(), 0);
        }

        // 3) Oben/unten (Stufen, Treppen, Falltüren)
        double hitY = 0.5;
        Direction preferSupport = null;
        if (want.hasProperty(BlockStateProperties.SLAB_TYPE)) {
            boolean top = want.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.TOP;
            hitY = top ? 0.8 : 0.2;
            preferSupport = top ? Direction.UP : Direction.DOWN;
        }
        if (want.hasProperty(BlockStateProperties.HALF)) {
            boolean top = want.getValue(BlockStateProperties.HALF) == Half.TOP;
            hitY = top ? 0.8 : 0.2;
            preferSupport = top ? Direction.UP : Direction.DOWN;
        }
        if (block instanceof TrapDoorBlock && facing != null) {
            if (solid(pos.relative(facing.getOpposite()))) return clickNeighbor(pos, facing.getOpposite(), hitY, mc.player.getYRot(), 0);
            return airClick(pos, facing, hitY, mc.player.getYRot(), 0);
        }

        // 4) Blickrichtung für ausgerichtete Blöcke
        float yaw = mc.player.getYRot(), pitch = 0;
        if (facing != null) {
            if (facing.getAxis() == Direction.Axis.Y) {
                // Kolben, Spender, Beobachter...: nach oben zeigen = nach unten schauen
                pitch = facing == Direction.UP ? 90 : -90;
            } else {
                Direction look;
                if (block instanceof StairBlock || block instanceof DoorBlock || block instanceof FenceGateBlock || block instanceof BedBlock) look = facing;
                else if (block instanceof AnvilBlock) look = facing.getCounterClockWise();
                else look = facing.getOpposite();
                yaw = look.toYRot();
            }
        }
        Direction sup = null;
        if (preferSupport != null && solid(pos.relative(preferSupport))) sup = preferSupport;
        if (sup == null) sup = supportAny(pos);
        if (sup != null && sup.getAxis() == Direction.Axis.Y && preferSupport != null && sup != preferSupport) {
            // Klick von oben/unten würde die falsche Hälfte ergeben -> Seitenfläche oder direkt in die Luft
            sup = supportSide(pos);
        }
        if (sup == null) {
            Direction face = preferSupport == Direction.UP ? Direction.NORTH : Direction.UP;
            return airClick(pos, face, hitY, yaw, pitch);
        }
        return clickNeighbor(pos, sup, hitY, yaw, pitch);
    }

    private Direction supportAny(BlockPos pos) {
        for (Direction d : new Direction[]{Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP})
            if (solid(pos.relative(d))) return d;
        return null;
    }

    private Direction supportSide(BlockPos pos) {
        for (Direction d : Direction.Plane.HORIZONTAL) if (solid(pos.relative(d))) return d;
        return null;
    }

    /** Klickt auf die Seite des Nachbarn in Richtung {@code sup}. */
    private boolean clickNeighbor(BlockPos pos, Direction sup, double hitY, float yaw, float pitch) {
        BlockPos n = pos.relative(sup);
        Direction face = sup.getOpposite();
        Vec3 hit = Vec3.atCenterOf(n).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
        if (face.getAxis() != Direction.Axis.Y) hit = new Vec3(hit.x, pos.getY() + hitY, hit.z);
        return click(n, face, hit, yaw, pitch);
    }

    private boolean click(BlockPos clicked, Direction face, Vec3 hit, float yaw, float pitch) {
        float oldYaw = mc.player.getYRot(), oldPitch = mc.player.getXRot();
        mc.player.connection.send(new ServerboundMovePlayerPacket.Rot(yaw, pitch, mc.player.onGround(), mc.player.horizontalCollision));
        Rot.look(yaw, pitch);
        InteractionResult res = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(hit, face, clicked, false));
        Rot.look(oldYaw, oldPitch);
        mc.player.connection.send(new ServerboundMovePlayerPacket.Rot(oldYaw, oldPitch, mc.player.onGround(), mc.player.horizontalCollision));
        if (res.consumesAction()) {
            dev.teizen.client.compat.V.swing(InteractionHand.MAIN_HAND);
            return true;
        }
        return false;
    }

    @Override
    public void onHud(GuiGraphicsExtractor g, float partial) {
        for (BlockPos p : lastPlaced) Draw.worldBox(g, new AABB(p), 0xFF55FF55, false);
    }

    @Override
    public String info() { return String.valueOf(placedTotal); }
}
