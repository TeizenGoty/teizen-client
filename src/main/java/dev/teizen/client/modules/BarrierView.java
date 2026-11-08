package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.util.BlockScanner;
import dev.teizen.client.util.Draw;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Barrieren immer sichtbar: rote Rahmen um alle Barriere-Blöcke in der Nähe.
 * (Ein Texturpaket kann das nicht – Barrieren werden von Minecraft gar nicht gezeichnet, egal welche Textur.)
 */
public final class BarrierView extends Module {
    private final Setting radius = add(Setting.number("radius", "Suchradius in Chunks", 3, 1, 8));
    private final Setting maxShown = add(Setting.number("anzahl", "Höchstens so viele Rahmen", 600, 50, 4000));

    private final BlockScanner scanner = new BlockScanner(s -> s.is(Blocks.BARRIER), 3, 48).maxResults(8000);

    public BarrierView() {
        super("BarrierSicht", Category.SICHT, "Zeigt Barriere-Blöcke immer mit rotem Rahmen.");
    }

    @Override
    protected void onEnable() { scanner.clear(); scanner.restart(); }

    @Override
    protected void onDisable() { scanner.clear(); }

    @Override
    public void onTick() {
        if (!inGame()) return;
        scanner.setRadius(radius.integer());
        scanner.tick();
        if (mc.player.tickCount % 10 == 0) scanner.validate();
    }

    public List<BlockPos> results() { return scanner.results(); }

    @Override
    public void onHud(GuiGraphicsExtractor g, float partial) {
        if (!inGame()) return;
        List<BlockPos> list = new ArrayList<>(scanner.results());
        BlockPos me = mc.player.blockPosition();
        list.sort(Comparator.comparingDouble(p -> p.distSqr(me)));
        if (list.size() > maxShown.integer()) list = list.subList(0, maxShown.integer());
        for (int i = list.size() - 1; i >= 0; i--) Draw.worldBox(g, new AABB(list.get(i)), 0xFFFF2020, true);
    }

    @Override
    public String info() { return String.valueOf(scanner.results().size()); }
}
