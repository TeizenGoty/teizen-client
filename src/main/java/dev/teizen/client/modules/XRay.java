package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.util.BlockScanner;
import dev.teizen.client.util.Draw;
import dev.teizen.client.util.Ids;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * X-Ray: alle Erze werden durch Wände farbig markiert (funktioniert auch mit Sodium/Iris).
 * Dazu wird automatisch alles hell.
 */
public final class XRay extends Module {
    public final Setting ores = add(Setting.text("erze", "Welche Erze (Komma-getrennt)",
            "diamond,emerald,gold,iron,redstone,lapis,ancient_debris"));
    private final Setting radius = add(Setting.number("radius", "Suchradius in Chunks", 4, 1, 12));
    private final Setting maxShown = add(Setting.number("anzahl", "Maximal angezeigte Erze", 300, 20, 2000));
    private final Setting tracers = add(Setting.bool("linien", "Linien zu den nächsten Erzen", false));
    private final Setting bright = add(Setting.bool("hell", "Dabei alles hell machen", true));

    private Set<Block> blocks = new HashSet<>();
    private String lastOres = "";
    public final BlockScanner scanner = new BlockScanner(this::matches, 4, 48);

    public XRay() {
        super("XRay", Category.ABBAU, "Zeigt alle Erze durch Wände an.");
    }

    public boolean matches(BlockState s) {
        return blocks.contains(s.getBlock());
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
    }

    @Override
    protected void onDisable() {
        scanner.clear();
        Fullbright fb = dev.teizen.client.TeizenClient.modules().get(Fullbright.class);
        if (bright.on() && fb != null && !fb.isEnabled() && inGame()) mc.player.removeEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION);
    }

    @Override
    public void onTick() {
        if (!inGame()) return;
        if (!ores.str().equals(lastOres)) rebuild();
        scanner.setRadius(radius.integer());
        scanner.tick();
        if (mc.player.tickCount % 10 == 0) scanner.validate();
        if (bright.on()) {
            var cur = mc.player.getEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION);
            if (cur == null || cur.getDuration() < 220)
                mc.player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.NIGHT_VISION, 420, 0, false, false, false));
        }
    }

    public List<BlockPos> nearest(int max) {
        List<BlockPos> list = new ArrayList<>(scanner.results());
        BlockPos me = mc.player.blockPosition();
        list.sort(Comparator.comparingDouble(p -> p.distSqr(me)));
        return list.size() > max ? list.subList(0, max) : list;
    }

    @Override
    public void onHud(GuiGraphicsExtractor g, float partial) {
        if (!inGame()) return;
        List<BlockPos> list = nearest(maxShown.integer());
        int i = 0;
        for (BlockPos p : list) {
            Block b = mc.level.getBlockState(p).getBlock();
            int c = Ids.oreColor(b);
            Draw.worldBox(g, new AABB(p), c, true);
            if (tracers.on() && i++ < 8) Draw.tracer(g, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, c);
        }
    }

    @Override
    public String info() { return String.valueOf(scanner.results().size()); }
}
