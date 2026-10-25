package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.util.BlockScanner;
import dev.teizen.client.util.Draw;
import dev.teizen.client.util.Ids;
import dev.teizen.client.util.Projector;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * X-Ray: Erze werden durch alle Blöcke farbig markiert (funktioniert auch mit Sodium/Iris).
 * Welche Erze, steht in "erze" (z.B. nur diamond). Optional auch Lava, Wasser unter der Erde und Höhlen.
 */
public final class XRay extends Module {
    public static final String DEFAULT_ORES = "diamond,emerald,gold,iron,redstone,lapis,ancient_debris";

    public static final String[] ALL_ORES = {"diamond", "emerald", "gold", "iron", "redstone", "lapis", "copper", "coal", "quartz", "ancient_debris"};
    public final Setting ores = add(Setting.multi("erze", "Welche Erze: \"alle\" = alle an/aus, dann einzeln wählen", DEFAULT_ORES, ALL_ORES));
    private final Setting radius = add(Setting.number("radius", "Suchradius in Chunks", 4, 1, 12));
    private final Setting maxShown = add(Setting.number("anzahl", "Maximal angezeigte Markierungen je Art", 300, 20, 2000));
    private final Setting tracers = add(Setting.bool("linien", "Linien zu den nächsten Erzen", false));
    private final Setting bright = add(Setting.bool("hell", "Dabei alles hell machen", true));
    public final Setting lava = add(Setting.bool("lava", "Auch Lava anzeigen (orange)", false));
    public final Setting water = add(Setting.bool("wasser", "Auch Wasser unter der Erde anzeigen (blau)", false));
    public final Setting caves = add(Setting.bool("hoehlen", "Auch Höhlen anzeigen (blaue Punkte)", false));

    private Set<Block> blocks = new HashSet<>();
    private String lastOres = "";
    public final BlockScanner scanner = new BlockScanner(this::matches, 4, 48);
    /** Lava / Wasser (nur die Oberfläche einer Lache, Wasser nur unter der Erde). */
    public final BlockScanner fluids = new BlockScanner(this::fluidMatches, 4, 32)
            .posFilter(XRay::fluidSurface).maxResults(6000);
    /** Höhlen: Luft unter der Oberfläche, jeder 4. Block (sonst viel zu viele Punkte). */
    public final BlockScanner caveScanner = new BlockScanner(BlockState::isAir, 4, 64)
            .posFilter(XRay::underground).step(4).maxResults(8000);
    private boolean lastLava, lastWater, lastCaves;

    public XRay() {
        super("XRay", Category.ABBAU, "Zeigt Erze durch alle Blöcke an (optional Lava, Wasser, Höhlen).");
    }

    public boolean matches(BlockState s) {
        return blocks.contains(s.getBlock());
    }

    private boolean fluidMatches(BlockState s) {
        var f = s.getFluidState();
        if (f.isEmpty()) return false;
        return (lava.on() && f.is(FluidTags.LAVA)) || (water.on() && f.is(FluidTags.WATER));
    }

    /** Nur die oberste Schicht einer Flüssigkeit; Wasser nur unter der Erde (sonst wäre jedes Meer markiert). */
    private static boolean fluidSurface(LevelChunk chunk, int x, int y, int z, BlockState st) {
        BlockState above = chunk.getBlockState(new BlockPos(x, y + 1, z));
        boolean isLava = st.getFluidState().is(FluidTags.LAVA);
        var af = above.getFluidState();
        if (!af.isEmpty() && af.is(FluidTags.LAVA) == isLava) return false;
        if (!isLava) return y < chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15) - 2;
        return true;
    }

    private static boolean underground(LevelChunk chunk, int x, int y, int z, BlockState st) {
        return y < chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15) - 4;
    }

    private void rebuild() {
        Set<Block> set = new HashSet<>();
        for (String part : ores.str().split(",")) set.addAll(Ids.resolve(part.trim()));
        blocks = set;
        lastOres = ores.str();
        scanner.restart();
    }

    @Override
    protected void onEnable() {
        rebuild();
        scanner.clear();
        fluids.clear();
        caveScanner.clear();
    }

    @Override
    protected void onDisable() {
        scanner.clear();
        fluids.clear();
        caveScanner.clear();
        Fullbright fb = dev.teizen.client.TeizenClient.modules().get(Fullbright.class);
        if (bright.on() && fb != null && !fb.isEnabled() && inGame()) mc.player.removeEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION);
    }

    @Override
    public void onTick() {
        if (!inGame()) return;
        if (!ores.str().equals(lastOres)) rebuild();
        int r = radius.integer();
        scanner.setRadius(r);
        scanner.tick();
        if (lava.on() != lastLava || water.on() != lastWater) {
            lastLava = lava.on();
            lastWater = water.on();
            fluids.clear();
        }
        if (caves.on() != lastCaves) {
            lastCaves = caves.on();
            caveScanner.clear();
        }
        if (lava.on() || water.on()) { fluids.setRadius(r); fluids.tick(); }
        if (caves.on()) { caveScanner.setRadius(Math.min(r, 6)); caveScanner.tick(); }
        if (mc.player.tickCount % 10 == 0) {
            scanner.validate();
            if (lava.on() || water.on()) fluids.validate();
        }
        if (bright.on()) {
            var cur = mc.player.getEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION);
            if (cur == null || cur.getDuration() < 220)
                mc.player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.NIGHT_VISION, 420, 0, false, false, false));
        }
    }

    public List<BlockPos> nearest(int max) {
        return nearest(scanner.results(), max);
    }

    private List<BlockPos> nearest(List<BlockPos> all, int max) {
        List<BlockPos> list = new ArrayList<>(all);
        BlockPos me = mc.player.blockPosition();
        list.sort(Comparator.comparingDouble(p -> p.distSqr(me)));
        return list.size() > max ? list.subList(0, max) : list;
    }

    public int lavaCount() {
        int n = 0;
        for (BlockPos p : fluids.results()) if (mc.level.getFluidState(p).is(FluidTags.LAVA)) n++;
        return n;
    }

    public int waterCount() { return fluids.results().size() - lavaCount(); }

    public int caveCount() { return caveScanner.results().size(); }

    @Override
    public void onHud(GuiGraphicsExtractor g, float partial) {
        if (!inGame()) return;
        int max = maxShown.integer();
        // Höhlen zuerst (liegen hinten), als kleine Punkte
        if (caves.on()) {
            for (BlockPos p : nearest(caveScanner.results(), max)) {
                double[] s = Projector.project(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
                if (s == null) continue;
                g.fill((int) s[0] - 1, (int) s[1] - 1, (int) s[0] + 2, (int) s[1] + 2, 0xB04080FF);
            }
        }
        if (lava.on() || water.on()) {
            for (BlockPos p : nearest(fluids.results(), max)) {
                boolean isLava = mc.level.getFluidState(p).is(FluidTags.LAVA);
                Draw.worldBox(g, new AABB(p), isLava ? 0xFFFF7A00 : 0xFF2F6BFF, isLava);
            }
        }
        List<BlockPos> list = nearest(max);
        int i = 0;
        for (BlockPos p : list) {
            Block b = mc.level.getBlockState(p).getBlock();
            int c = Ids.oreColor(b);
            Draw.worldBox(g, new AABB(p), c, true);
            if (tracers.on() && i++ < 8) Draw.tracer(g, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, c);
        }
    }

    @Override
    public String info() {
        String o = ores.str().equals(DEFAULT_ORES) ? "" : ores.str() + " ";
        return o + scanner.results().size();
    }
}
