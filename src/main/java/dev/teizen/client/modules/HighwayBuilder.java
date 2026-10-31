package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.util.Breaker;
import dev.teizen.client.util.Chat;
import dev.teizen.client.util.Inv;
import dev.teizen.client.util.Placer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * HighwayBuilder: baut in Blickrichtung (Nord/Süd/Ost/West) automatisch einen Highway – gräbt einen Tunnel frei,
 * legt einen durchgehenden Boden, schüttet Lava/Wasser daneben zu und läuft von selbst weiter.
 * Dafür im Inventar: Spitzhacke + Blöcke für den Boden (z.B. Obsidian oder Netherrack).
 */
public final class HighwayBuilder extends Module {
    private final Setting width = add(Setting.number("breite", "Breite des Highways", 3, 1, 7));
    private final Setting height = add(Setting.number("hoehe", "Höhe des Tunnels", 3, 2, 5));
    private final Setting floorBlock = add(Setting.choice("boden", "Blöcke für den Boden", "beliebig", "beliebig", "obsidian", "netherrack", "cobblestone"));
    private final Setting rails = add(Setting.bool("gelaender", "Am Rand eine Reihe Blöcke (Geländer)", false));
    private final Setting maxLen = add(Setting.number("laenge", "Nach so vielen Blöcken aufhören (0 = endlos)", 0, 0, 100000));

    private Direction dir;
    private BlockPos start;
    private int k;
    private BlockPos digging;
    private int stuck;
    public int built, dug, placed;

    public HighwayBuilder() {
        super("HighwayBuilder", Category.BAUEN, "Baut automatisch einen Highway in Blickrichtung (Tunnel + Boden).");
    }

    @Override
    protected void onEnable() {
        if (!inGame()) return;
        dir = mc.player.getDirection();
        start = mc.player.blockPosition();
        k = 1;
        digging = null;
        stuck = 0;
        Chat.info("HighwayBuilder: baue nach §f" + dir.getName() + "§7. Aus mit ///highwaybuilder.");
    }

    @Override
    protected void onDisable() {
        if (digging != null) Breaker.stop();
        digging = null;
        if (mc.options != null) mc.options.keyUp.setDown(false);
    }

    private boolean floorItem(ItemStack s) {
        if (s.isEmpty() || !(s.getItem() instanceof BlockItem bi)) return false;
        String id = BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
        if (!floorBlock.is("beliebig")) return id.equals(floorBlock.str());
        var st = bi.getBlock().defaultBlockState();
        return st.isCollisionShapeFullBlock(mc.level, BlockPos.ZERO) && !(bi.getBlock() instanceof net.minecraft.world.level.block.FallingBlock)
                && !id.contains("shulker") && !id.contains("chest") && !id.contains("ore");
    }

    /** Zellen der Scheibe k: Tunnel (frei machen) und Boden (setzen). */
    private BlockPos cell(int along, int side, int up) {
        Direction s = dir.getClockWise();
        return start.relative(dir, along).relative(s, side).above(up);
    }

    private int lo() { return -(width.integer() - 1) / 2; }
    private int hi() { return width.integer() / 2; }

    @Override
    public void onTick() {
        if (!inGame() || mc.gui.screen() != null || dir == null) return;
        if (maxLen.integer() > 0 && k > maxLen.integer()) { Chat.ok("HighwayBuilder fertig: " + (k - 1) + " Blöcke."); setEnabled(false); return; }
        Vec3 eye = mc.player.getEyePosition();
        double reach = mc.player.blockInteractionRange();
        // 1) Flüssigkeiten in/an der Scheibe zuschütten
        List<BlockPos> seal = new ArrayList<>();
        for (int w = lo() - 1; w <= hi() + 1; w++)
            for (int h = -1; h <= height.integer(); h++) {
                BlockPos p = cell(k, w, h);
                if (!mc.level.getFluidState(p).isEmpty() && eye.distanceToSqr(Vec3.atCenterOf(p)) <= reach * reach) seal.add(p);
            }
        BlockPos front = cell(k + 1, 0, 0);
        if (!mc.level.getFluidState(front).isEmpty()) seal.add(front);
        if (!seal.isEmpty()) {
            mc.options.keyUp.setDown(false);
            if (!Inv.ensureInHand(this::floorItem)) { noBlocks(); return; }
            if (Placer.place(seal.get(0), true)) placed++;
            return;
        }
        // 2) Tunnel frei machen
        if (digging != null && (mc.level.getBlockState(digging).isAir() || !Breaker.canBreak(digging))) { if (mc.level.getBlockState(digging).isAir()) dug++; digging = null; }
        if (digging == null) {
            for (int h = height.integer() - 1; h >= 0 && digging == null; h--)
                for (int w = lo(); w <= hi(); w++) {
                    BlockPos p = cell(k, w, h);
                    if (mc.level.getBlockState(p).getCollisionShape(mc.level, p).isEmpty()) continue;
                    if (!Breaker.canBreak(p)) { Chat.error("HighwayBuilder: unzerstörbarer Block bei " + p.toShortString() + " – gestoppt."); setEnabled(false); return; }
                    digging = p.immutable();
                    stuck = 0;
                    break;
                }
        }
        if (digging != null) {
            mc.options.keyUp.setDown(false);
            if (eye.distanceToSqr(Vec3.atCenterOf(digging)) > reach * reach) { walkTo(k - 1); return; }
            if (Breaker.tick(digging, true, true)) { dug++; digging = null; }
            if (++stuck > 600) { Chat.error("HighwayBuilder: komme nicht weiter."); setEnabled(false); }
            return;
        }
        // 3) Boden (+ Geländer)
        for (int w = lo(); w <= hi(); w++) {
            BlockPos f = cell(k, w, -1);
            if (!mc.level.getBlockState(f).getCollisionShape(mc.level, f).isEmpty()) continue;
            mc.options.keyUp.setDown(false);
            if (!Inv.ensureInHand(this::floorItem)) { noBlocks(); return; }
            if (eye.distanceToSqr(Vec3.atCenterOf(f)) > reach * reach) { walkTo(k - 1); return; }
            if (Placer.place(f, true)) placed++;
            return;
        }
        if (rails.on()) {
            for (int w : new int[]{lo() - 1, hi() + 1}) {
                BlockPos r = cell(k, w, 0);
                if (!mc.level.getBlockState(r).getCollisionShape(mc.level, r).isEmpty()) continue;
                if (!Inv.ensureInHand(this::floorItem)) { noBlocks(); return; }
                if (Placer.place(r, true)) placed++;
                return;
            }
        }
        // 4) Scheibe fertig -> weiter
        if (walkTo(k)) { k++; built++; }
    }

    private int noBlocksMsg;
    private void noBlocks() {
        if (noBlocksMsg++ % 200 == 0) Chat.error("HighwayBuilder: keine Blöcke für den Boden (" + floorBlock.str() + ") im Inventar.");
    }

    /** Zur Mitte der Scheibe laufen. @return true wenn angekommen */
    private boolean walkTo(int along) {
        BlockPos c = cell(along, 0, 0);
        Vec3 t = Vec3.atBottomCenterOf(c);
        double dx = t.x - mc.player.getX(), dz = t.z - mc.player.getZ();
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d < 0.35) { mc.options.keyUp.setDown(false); return true; }
        mc.player.setYRot((float) Math.toDegrees(Math.atan2(dz, dx)) - 90f);
        mc.options.keyUp.setDown(true);
        return false;
    }

    @Override
    public String info() { return dir == null ? "" : dir.getName() + " " + built; }
}
