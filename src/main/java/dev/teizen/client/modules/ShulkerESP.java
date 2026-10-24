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
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Shulker-Kisten durch Wände: aufgestellte, am Boden liegende und solche, die andere Spieler in der Hand halten. */
public final class ShulkerESP extends Module {
    private final Setting radius = add(Setting.number("radius", "Radius in Chunks (aufgestellte Kisten)", 8, 1, 16));
    private final Setting items = add(Setting.bool("items", "Auch Shulker-Kisten, die am Boden liegen", true));
    private final Setting held = add(Setting.bool("spieler", "Zeigen, wenn Spieler eine Shulker-Kiste halten", true));
    private final Setting mobs = add(Setting.bool("mobs", "Auch Shulker-Mobs (Endsiedlungen)", false));
    private final Setting tracers = add(Setting.bool("linien", "Linien zu den Shulker-Kisten", true));

    private record Found(BlockPos pos, int color, String label) {}

    private final List<Found> blocks = new ArrayList<>();
    public int blockCount, itemCount;

    public ShulkerESP() {
        super("ShulkerESP", Category.SICHT, "Zeigt Shulker-Kisten durch Wände (auch am Boden und in Händen).");
    }

    static boolean isShulker(ItemStack s) {
        return s.getItem() instanceof BlockItem bi && bi.getBlock() instanceof ShulkerBoxBlock;
    }

    static int color(Block b) {
        if (b instanceof ShulkerBoxBlock sb) {
            DyeColor c = sb.getColor();
            if (c != null) return 0xFF000000 | c.getTextColor();
        }
        return 0xFFB080D0;
    }

    @Override
    public void onTick() {
        if (!inGame() || mc.player.tickCount % 20 != 0) return;
        blocks.clear();
        int r = radius.integer();
        int pcx = mc.player.blockPosition().getX() >> 4, pcz = mc.player.blockPosition().getZ() >> 4;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (!mc.level.hasChunk(pcx + dx, pcz + dz)) continue;
                LevelChunk ch = mc.level.getChunk(pcx + dx, pcz + dz);
                for (BlockEntity be : ch.getBlockEntities().values()) {
                    if (!(be instanceof ShulkerBoxBlockEntity)) continue;
                    Block b = be.getBlockState().getBlock();
                    blocks.add(new Found(be.getBlockPos(), color(b), b.getName().getString()));
                }
            }
        }
        blockCount = blocks.size();
    }

    @Override
    public void onHud(GuiGraphicsExtractor g, float partial) {
        if (!inGame()) return;
        Vec3 eye = mc.player.getEyePosition();
        for (Found f : blocks) {
            double[] r = Draw.worldBox(g, new AABB(f.pos()), f.color(), true);
            if (tracers.on()) Draw.tracer(g, f.pos().getX() + 0.5, f.pos().getY() + 0.5, f.pos().getZ() + 0.5, f.color());
            if (r != null) {
                String t = f.label() + " " + Math.round(eye.distanceTo(Vec3.atCenterOf(f.pos()))) + "m";
                g.text(mc.font, t, (int) ((r[0] + r[2]) / 2) - mc.font.width(t) / 2, (int) r[1] - 10, f.color(), true);
            }
        }
        int n = 0;
        if (items.on()) {
            for (ItemEntity e : mc.level.getEntitiesOfClass(ItemEntity.class, mc.player.getBoundingBox().inflate(radius.integer() * 16))) {
                ItemStack st = e.getItem();
                if (!isShulker(st)) continue;
                n++;
                int c = color(((BlockItem) st.getItem()).getBlock());
                Vec3 p = e.getPosition(partial);
                double[] r = Draw.worldBox(g, new AABB(p.x - 0.25, p.y, p.z - 0.25, p.x + 0.25, p.y + 0.5, p.z + 0.25), c, true);
                if (tracers.on()) Draw.tracer(g, p.x, p.y + 0.25, p.z, c);
                if (r != null) {
                    String t = st.getHoverName().getString() + " (am Boden) " + Math.round(eye.distanceTo(p)) + "m";
                    g.text(mc.font, t, (int) ((r[0] + r[2]) / 2) - mc.font.width(t) / 2, (int) r[1] - 10, c, true);
                }
            }
        }
        itemCount = n;
        if (held.on()) {
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
        if (mobs.on()) {
            for (Shulker s : mc.level.getEntitiesOfClass(Shulker.class, mc.player.getBoundingBox().inflate(radius.integer() * 16))) {
                Draw.worldBox(g, s.getBoundingBox(), 0xFFC080FF, false);
            }
        }
    }

    @Override
    public String info() { return String.valueOf(blockCount + itemCount); }
}
