package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.util.Arrows;
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
import net.minecraft.world.phys.Vec3;

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
    private final Setting tracers = add(Setting.bool("linien", "Linien zu den nächsten Erzen (das nächste dick)", false));
    private final Setting arrows = add(Setting.bool("pfeile", "Pfeile um das Fadenkreuz zum nächsten Erz jeder Sorte", true));
    private final Setting fade = add(Setting.bool("verblassen", "Weit entfernte Erze schwächer zeigen", true));
    private final Setting bright = add(Setting.bool("hell", "Dabei alles hell machen", true));
    public final Setting lava = add(Setting.bool("lava", "Auch Lava anzeigen (dezente orange Punkte)", false));
    public final Setting water = add(Setting.bool("wasser", "Auch Wasser unter der Erde anzeigen (dezente blaue Punkte)", false));
    public final Setting caves = add(Setting.bool("hoehlen", "Auch Höhlen anzeigen (blaue Punkte)", false));
    public final Setting brush = add(Setting.bool("brush", "Auch Suspicious Sand/Gravel anzeigen (für den Pinsel/AutoBrush)", false));
    public final Setting schem = add(Setting.bool("schematic", "Litematica: Fehler im Bau durch Wände zeigen (lila = falscher Block, rot = da fehlt ein Block)", false));
    public final Setting schemWhat = add(Setting.multi("schematicfehler", "Welche Fehler: falsch (lila) · fehlt (rot) · richtung (gelb, falsche Richtung/Zustand) · zuviel (orange, Block wo Luft sein soll)",
            "falsch,fehlt", "falsch", "fehlt", "richtung", "zuviel"));
    /** Welche Blöcke markiert werden: Liste der Blöcke, die im Bau gerade falsch sind/fehlen – einzeln abhaken. */
    public final Setting schemBlocks = add(Setting.dynamicMulti("schematicbloecke",
            "Welche Blöcke markiert werden – einzeln an/aus. In der Liste stehen die Blöcke, die in deinem Bau gerade falsch sind oder fehlen",
            this::schemBlockList).labels(XRay::blockName));
    private final Setting schemMax = add(Setting.number("schematicanzahl", "Höchstens so viele Bau-Fehler gleichzeitig zeigen (die nächsten). Weniger = weniger Lag", 150, 20, 1000));
    private final Setting schemRadius = add(Setting.number("schematicradius", "Wie weit um dich herum der Bau geprüft wird (Blöcke)", 48, 8, 160));
    public final dev.teizen.client.util.SchematicCheck schemCheck = new dev.teizen.client.util.SchematicCheck();

    private Set<Block> blocks = new HashSet<>();
    private String lastOres = "";
    private boolean lastBrush;
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
        if (brush.on()) {
            set.add(net.minecraft.world.level.block.Blocks.SUSPICIOUS_SAND);
            set.add(net.minecraft.world.level.block.Blocks.SUSPICIOUS_GRAVEL);
        }
        blocks = set;
        lastOres = ores.str();
        lastBrush = brush.on();
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
        if (!ores.str().equals(lastOres) || brush.on() != lastBrush) rebuild();
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
        if (schem.on() && dev.teizen.client.util.SchematicCheck.available()) schemCheck.tick(schemRadius.integer());
        else if (!schemCheck.results().isEmpty()) schemCheck.clear();
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

    /** Deckkraft nach Entfernung: nah = voll, ganz weit = etwa ein Drittel. */
    private double fadeFor(BlockPos p, double maxDist) {
        if (!fade.on()) return 1.0;
        double d = Math.sqrt(Vec3.atCenterOf(p).distanceToSqr(mc.player.getEyePosition()));
        if (d <= 8) return 1.0;
        return Math.max(0.3, 1.0 - (d - 8) / Math.max(8, maxDist - 8) * 0.7);
    }

    @Override
    public void onHud(GuiGraphicsExtractor g, float partial) {
        if (!inGame()) return;
        int max = maxShown.integer();
        double maxDist = radius.integer() * 16 + 8;
        // Höhlen zuerst (liegen hinten), als kleine Punkte
        if (caves.on()) {
            for (BlockPos p : nearest(caveScanner.results(), max)) {
                double[] s = Projector.project(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
                if (s == null) continue;
                g.fill((int) s[0] - 1, (int) s[1] - 1, (int) s[0] + 2, (int) s[1] + 2, Draw.alpha(0xB04080FF, fadeFor(p, maxDist)));
            }
        }
        // Lava/Wasser: nur dezente Punkte, damit man weiß, dass sie da ist – sieht nicht aus wie ein Erz
        if (lava.on() || water.on()) {
            for (BlockPos p : nearest(fluids.results(), max)) {
                boolean isLava = mc.level.getFluidState(p).is(FluidTags.LAVA);
                double[] s = Projector.project(p.getX() + 0.5, p.getY() + 0.85, p.getZ() + 0.5);
                if (s == null) continue;
                int c = Draw.alpha(isLava ? 0x90FF6A00 : 0x803070FF, fadeFor(p, maxDist));
                g.fill((int) s[0] - 1, (int) s[1] - 1, (int) s[0] + 1, (int) s[1] + 1, c);
            }
        }
        if (schem.on()) drawSchematic(g);
        List<BlockPos> list = nearest(max);
        // weit weg zuerst zeichnen, nah zuletzt (liegt dann oben)
        for (int i = list.size() - 1; i >= 0; i--) {
            BlockPos p = list.get(i);
            Block b = mc.level.getBlockState(p).getBlock();
            Draw.worldBox(g, new AABB(p), Draw.alpha(Ids.oreColor(b), fadeFor(p, maxDist)), true);
        }
        Vec3 eye = mc.player.getEyePosition();
        if (tracers.on() && !list.isEmpty()) {
            for (int i = Math.min(8, list.size()) - 1; i >= 1; i--) {
                BlockPos p = list.get(i);
                Draw.tracer(g, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, Ids.oreColor(mc.level.getBlockState(p).getBlock()));
            }
            BlockPos n = list.get(0);
            Block b = mc.level.getBlockState(n).getBlock();
            Draw.tracerNearest(g, n.getX() + 0.5, n.getY() + 0.5, n.getZ() + 0.5, Ids.oreColor(b), null);
        }
        if (arrows.on()) {
            // je Erz-Sorte ein Pfeil zum nächsten Erz dieser Sorte
            java.util.Map<String, BlockPos> best = new java.util.LinkedHashMap<>();
            for (BlockPos p : list) {
                String l = Ids.oreLabel(mc.level.getBlockState(p).getBlock());
                best.putIfAbsent(l, p);
            }
            for (var e : best.entrySet()) {
                BlockPos p = e.getValue();
                int c = Ids.oreColor(mc.level.getBlockState(p).getBlock());
                Arrows.draw(g, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, c, null, 44);
            }
            arrowCount = best.size();
        }
    }

    public static int schemColor(dev.teizen.client.util.SchematicCheck.Kind k) {
        return switch (k) {
            case FALSCH -> 0xFFB040FF;  // lila: falscher Block
            case FEHLT -> 0xFFFF3030;   // rot: hier muss noch ein Block hin
            case ZUSTAND -> 0xFFFFD020; // gelb: richtiger Block, falsche Richtung
            case ZUVIEL -> 0xFFFF8A20;  // orange: Block, wo Luft sein soll
        };
    }

    /** Block, um den es bei diesem Fehler geht: bei "zuviel" der, der da steht, sonst der, der hin soll. */
    private Block schemBlock(BlockPos p, dev.teizen.client.util.SchematicCheck.Kind k) {
        if (k == dev.teizen.client.util.SchematicCheck.Kind.ZUVIEL) return mc.level.getBlockState(p).getBlock();
        var want = dev.teizen.client.compat.LitematicaCompat.schematicState(p);
        return want == null ? mc.level.getBlockState(p).getBlock() : want.getBlock();
    }

    private static String blockId(Block b) {
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(b).getPath();
    }

    private static String blockName(String id) {
        var rl = net.minecraft.resources.Identifier.tryParse("minecraft:" + id);
        if (rl == null || !net.minecraft.core.registries.BuiltInRegistries.BLOCK.containsKey(rl)) return id;
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(rl).getName().getString();
    }

    private List<String> schemListCache = new ArrayList<>();
    private int schemListTick = -100;

    /** Blöcke, die gerade als Fehler gefunden sind (häufigste zuerst) – für die Abhak-Liste. */
    private List<String> schemBlockList() {
        if (!inGame() || !dev.teizen.client.util.SchematicCheck.available()) return schemListCache;
        if (mc.player.tickCount - schemListTick < 40 && mc.player.tickCount >= schemListTick) return schemListCache;
        schemListTick = mc.player.tickCount;
        java.util.Map<String, Integer> count = new java.util.HashMap<>();
        var res = schemCheck.results();
        int seen = 0;
        for (BlockPos p : schemCheck.nearest()) { // nur die nächsten 3000 ansehen (reicht für die Liste)
            var k = res.get(p);
            if (k == null || !schemShown(k)) continue;
            count.merge(blockId(schemBlock(p, k)), 1, Integer::sum);
            if (++seen >= 3000) break;
        }
        // ausgeschaltete Blöcke bleiben in der Liste (sonst kann man sie nicht wieder anmachen)
        for (String off : schemBlocks.selected()) count.putIfAbsent(off, 0);
        List<String> out = new ArrayList<>(count.keySet());
        out.sort((a, b) -> count.get(b) - count.get(a) != 0 ? count.get(b) - count.get(a) : a.compareTo(b));
        schemListCache = out;
        return out;
    }

    /** Ist der Block dieses Fehlers in "schematicbloecke" angehakt? */
    private boolean schemBlockOk(BlockPos p, dev.teizen.client.util.SchematicCheck.Kind k) {
        return schemBlocks.isOn(blockId(schemBlock(p, k)));
    }

    private boolean schemShown(dev.teizen.client.util.SchematicCheck.Kind k) {
        return switch (k) {
            case FALSCH -> schemWhat.isSelected("falsch");
            case FEHLT -> schemWhat.isSelected("fehlt");
            case ZUSTAND -> schemWhat.isSelected("richtung");
            case ZUVIEL -> schemWhat.isSelected("zuviel");
        };
    }

    /** Fehler im Litematica-Bau: Kästen durch Wände, die nächsten zuletzt (liegen oben). */
    private void drawSchematic(GuiGraphicsExtractor g) {
        if (!dev.teizen.client.util.SchematicCheck.available()) return;
        // schon sortierte Liste nehmen (nicht jedes Bild alles sortieren) und nur die nächsten zeichnen
        List<BlockPos> list = new ArrayList<>();
        int max = schemMax.integer();
        var res = schemCheck.results();
        for (BlockPos p : schemCheck.nearest()) {
            var k = res.get(p);
            if (k == null || !schemShown(k) || !schemBlockOk(p, k)) continue;
            list.add(p);
            if (list.size() >= max) break;
        }
        schemDrawn = list.size();
        for (int i = list.size() - 1; i >= 0; i--) {
            BlockPos p = list.get(i);
            var k = res.get(p);
            if (k == null) continue;
            AABB box = k == dev.teizen.client.util.SchematicCheck.Kind.FEHLT ? new AABB(p).deflate(0.15) : new AABB(p);
            Draw.worldBox(g, box, schemColor(k), true);
        }
    }

    /** Wie viele Bau-Fehler gerade gezeichnet werden (Selbsttest). */
    public int schemDrawn;

    /** Wie viele Pfeile gerade gezeigt werden (Selbsttest). */
    public int arrowCount;

    @Override
    public String info() {
        String o = ores.str().equals(DEFAULT_ORES) ? "" : ores.str() + " ";
        if (schem.on() && dev.teizen.client.util.SchematicCheck.available() && !schemCheck.results().isEmpty())
            return o + scanner.results().size() + " · Bau " + (schemCheck.results().size() >= 20000 ? "20000+" : schemCheck.results().size());
        return o + scanner.results().size();
    }
}
