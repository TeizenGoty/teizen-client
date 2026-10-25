package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.util.Draw;
import dev.teizen.client.util.Projector;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Kisten-ESP: Kisten (Doppelkisten als EINE Box), Fässer, Endertruhen, Spawner und Shulker-Kisten durch Wände.
 * Shulker auch am Boden und wenn andere Spieler sie halten. Linien zu allem, die nächste Kiste dick hervorgehoben.
 */
public final class StorageESP extends Module {
    private final Setting radius = add(Setting.number("radius", "Radius in Chunks", 6, 1, 16));
    private final Setting tracers = add(Setting.bool("linien", "Linien zu den Kisten (die nächste dick)", true));
    private final Setting chests = add(Setting.bool("kisten", "Kisten und Doppelkisten", true));
    private final Setting barrels = add(Setting.bool("faesser", "Fässer", true));
    private final Setting ender = add(Setting.bool("ender", "Endertruhen", true));
    private final Setting shulker = add(Setting.bool("shulker", "Shulker-Kisten (aufgestellt)", true));
    private final Setting spawner = add(Setting.bool("spawner", "Spawner", true));
    private final Setting shulkerItems = add(Setting.bool("shulkerboden", "Shulker-Kisten, die am Boden liegen", true));
    private final Setting shulkerHeld = add(Setting.bool("shulkerspieler", "Zeigen, wenn Spieler eine Shulker-Kiste halten", true));
    private final Setting shulkerMobs = add(Setting.bool("shulkermobs", "Shulker-Mobs (Endsiedlungen)", false));
    private final Setting names = add(Setting.bool("namen", "Name und Entfernung über den Kisten", false));

    public record Found(AABB box, int color, String label, boolean doubleChest) {}

    public final List<Found> found = new ArrayList<>();
    /** Zähler für Anzeige und Selbsttest. */
    public int doubleChests, shulkerBlocks, shulkerItemCount;

    public StorageESP() {
        super("StorageESP", Category.SICHT, "Kisten, Shulker, Fässer und Spawner durch Wände (mit Linien).");
    }

    public static int color(BlockEntity be) {
        if (be instanceof EnderChestBlockEntity) return 0xFFB44CFF;
        if (be instanceof ShulkerBoxBlockEntity) return shulkerColor(be.getBlockState().getBlock());
        if (be instanceof BarrelBlockEntity) return 0xFFC08A4A;
        if (be instanceof SpawnerBlockEntity) return 0xFFFF3030;
        if (be instanceof ChestBlockEntity) return 0xFFFFB22E;
        return 0;
    }

    static boolean isShulker(ItemStack s) {
        return s.getItem() instanceof BlockItem bi && bi.getBlock() instanceof ShulkerBoxBlock;
    }

    static int shulkerColor(Block b) {
        if (b instanceof ShulkerBoxBlock sb) {
            DyeColor c = sb.getColor();
            if (c != null) return 0xFF000000 | c.getTextColor();
        }
        return 0xFFFF6EC7;
    }

    private boolean wanted(BlockEntity be) {
        if (be instanceof EnderChestBlockEntity) return ender.on();
        if (be instanceof ShulkerBoxBlockEntity) return shulker.on();
        if (be instanceof BarrelBlockEntity) return barrels.on();
        if (be instanceof SpawnerBlockEntity) return spawner.on();
        if (be instanceof ChestBlockEntity) return chests.on();
        return false;
    }

    @Override
    protected void onDisable() { found.clear(); }

    @Override
    public void onTick() {
        if (!inGame() || mc.player.tickCount % 20 != 0) return;
        List<Found> out = new ArrayList<>();
        Set<BlockPos> done = new HashSet<>();
        int dbl = 0, sh = 0;
        int r = radius.integer();
        int pcx = mc.player.blockPosition().getX() >> 4, pcz = mc.player.blockPosition().getZ() >> 4;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (!mc.level.hasChunk(pcx + dx, pcz + dz)) continue;
                LevelChunk ch = mc.level.getChunk(pcx + dx, pcz + dz);
                for (BlockEntity be : ch.getBlockEntities().values()) {
                    if (!wanted(be)) continue;
                    BlockPos pos = be.getBlockPos();
                    if (!done.add(pos)) continue;
                    int c = color(be);
                    BlockState st = be.getBlockState();
                    if (be instanceof ChestBlockEntity && st.getBlock() instanceof ChestBlock && st.hasProperty(ChestBlock.TYPE)
                            && st.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
                        // Doppelkiste: beide Hälften zu einer Box zusammenfassen
                        BlockPos other = ChestBlock.getConnectedBlockPos(pos, st);
                        done.add(other);
                        AABB box = chestBox(pos).minmax(chestBox(other));
                        out.add(new Found(box, c, "Doppelkiste", true));
                        dbl++;
                        continue;
                    }
                    String label = be instanceof ChestBlockEntity ? "Kiste" : st.getBlock().getName().getString();
                    if (be instanceof ShulkerBoxBlockEntity) sh++;
                    out.add(new Found(be instanceof ChestBlockEntity || be instanceof EnderChestBlockEntity ? chestBox(pos) : new AABB(pos), c, label, false));
                }
            }
        }
        found.clear();
        found.addAll(out);
        doubleChests = dbl;
        shulkerBlocks = sh;
    }

    /** Kisten sind etwas kleiner als ein ganzer Block. */
    private static AABB chestBox(BlockPos p) {
        return new AABB(p.getX() + 1 / 16.0, p.getY(), p.getZ() + 1 / 16.0, p.getX() + 15 / 16.0, p.getY() + 14 / 16.0, p.getZ() + 15 / 16.0);
    }

    @Override
    public void onHud(GuiGraphicsExtractor g, float partial) {
        if (!inGame()) return;
        Vec3 eye = mc.player.getEyePosition();
        Found nearest = null;
        double nd = Double.MAX_VALUE;
        for (Found f : found) {
            double d = f.box().getCenter().distanceTo(eye);
            if (d < nd) { nd = d; nearest = f; }
        }
        for (Found f : found) {
            double[] r = Draw.worldBox(g, f.box(), f.color(), true);
            Vec3 c = f.box().getCenter();
            if (tracers.on() && f != nearest) Draw.tracer(g, c.x, c.y, c.z, f.color());
            if (r != null && names.on()) {
                String t = f.label() + " " + Math.round(eye.distanceTo(c)) + "m";
                g.text(mc.font, t, (int) ((r[0] + r[2]) / 2) - mc.font.width(t) / 2, (int) r[1] - 10, f.color(), true);
            }
        }
        if (tracers.on() && nearest != null) {
            Vec3 c = nearest.box().getCenter();
            Draw.tracerNearest(g, c.x, c.y, c.z, nearest.color(), nearest.label() + " " + Math.round(nd) + "m");
        }
        int n = 0;
        if (shulkerItems.on()) {
            for (ItemEntity e : mc.level.getEntitiesOfClass(ItemEntity.class, mc.player.getBoundingBox().inflate(radius.integer() * 16))) {
                ItemStack st = e.getItem();
                if (!isShulker(st)) continue;
                n++;
                int c = shulkerColor(((BlockItem) st.getItem()).getBlock());
                Vec3 p = e.getPosition(partial);
                double[] r = Draw.worldBox(g, new AABB(p.x - 0.25, p.y, p.z - 0.25, p.x + 0.25, p.y + 0.5, p.z + 0.25), c, true);
                if (tracers.on()) Draw.tracer(g, p.x, p.y + 0.25, p.z, c);
                if (r != null) {
                    String t = st.getHoverName().getString() + " (am Boden) " + Math.round(eye.distanceTo(p)) + "m";
                    g.text(mc.font, t, (int) ((r[0] + r[2]) / 2) - mc.font.width(t) / 2, (int) r[1] - 10, c, true);
                }
            }
        }
        shulkerItemCount = n;
        if (shulkerHeld.on()) {
            for (Player p : mc.level.players()) {
                if (p == mc.player) continue;
                ItemStack st = isShulker(p.getMainHandItem()) ? p.getMainHandItem() : isShulker(p.getOffhandItem()) ? p.getOffhandItem() : ItemStack.EMPTY;
                if (st.isEmpty()) continue;
                Vec3 pos = p.getPosition(partial).add(0, p.getBbHeight() + 1.2, 0);
                double[] s = Projector.project(pos.x, pos.y, pos.z);
                if (s == null) continue;
                String t = "§d" + p.getGameProfile().name() + " hält " + st.getHoverName().getString();
                g.text(mc.font, t, (int) s[0] - mc.font.width(t) / 2, (int) s[1], 0xFFFFFFFF, true);
            }
        }
        if (shulkerMobs.on()) {
            for (Shulker s : mc.level.getEntitiesOfClass(Shulker.class, mc.player.getBoundingBox().inflate(radius.integer() * 16))) {
                Draw.worldBox(g, s.getBoundingBox(), 0xFFC080FF, false);
            }
        }
    }

    @Override
    public String info() { return String.valueOf(found.size() + shulkerItemCount); }
}
