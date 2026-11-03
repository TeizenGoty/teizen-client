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
 * BlockESP: markiert frei gewählte Blöcke durch Wände (wie StorageESP, aber du bestimmst die Blöcke selbst).
 * Befehl/Einstellung "bloecke": z.B. "spawner,budding_amethyst,chest". Gut, um bestimmte Blöcke in der Welt zu finden.
 */
public final class BlockESP extends Module {
    public final Setting blocks = add(Setting.text("bloecke", "Welche Blöcke (durch Komma getrennt), z.B. spawner,budding_amethyst,chest",
            "spawner,budding_amethyst"));
    private final Setting radius = add(Setting.number("radius", "Suchradius in Chunks", 6, 1, 16));
    private final Setting maxShown = add(Setting.number("anzahl", "Höchstens so viele Markierungen", 400, 20, 4000));
    private final Setting tracers = add(Setting.bool("linien", "Linien zu den nächsten Blöcken", false));
    private final Setting color = add(Setting.choice("farbe", "Farbe der Markierung", "türkis", "türkis", "rot", "grün", "gelb", "pink", "weiß"));

    private Set<Block> wanted = new HashSet<>();
    private String lastList = "";
    private final BlockScanner scanner = new BlockScanner(s -> wanted.contains(s.getBlock()), 6, 48).maxResults(8000);

    public BlockESP() {
        super("BlockESP", Category.SICHT, "Markiert selbst gewählte Blöcke durch Wände.");
    }

    private int argb() {
        return switch (color.str()) {
            case "rot" -> 0xFFFF4040;
            case "grün" -> 0xFF40FF60;
            case "gelb" -> 0xFFFFE030;
            case "pink" -> 0xFFFF5BD0;
            case "weiß" -> 0xFFFFFFFF;
            default -> 0xFF33EBFF;
        };
    }

    private void rebuild() {
        Set<Block> set = new HashSet<>();
        for (String part : blocks.str().split("[, ]+")) if (!part.isBlank()) set.addAll(Ids.resolve(part.trim()));
        wanted = set;
        lastList = blocks.str();
        scanner.restart();
    }

    @Override
    protected void onEnable() { rebuild(); scanner.clear(); }

    @Override
    protected void onDisable() { scanner.clear(); }

    @Override
    public void onTick() {
        if (!inGame()) return;
        if (!blocks.str().equals(lastList)) rebuild();
        scanner.setRadius(radius.integer());
        scanner.tick();
        if (mc.player.tickCount % 10 == 0) scanner.validate();
    }

    /** Für den Selbsttest: gefundene Stellen. */
    public List<BlockPos> results() { return scanner.results(); }

    @Override
    public void onHud(GuiGraphicsExtractor g, float partial) {
        if (!inGame()) return;
        List<BlockPos> list = new ArrayList<>(scanner.results());
        BlockPos me = mc.player.blockPosition();
        list.sort(Comparator.comparingDouble(p -> p.distSqr(me)));
        if (list.size() > maxShown.integer()) list = list.subList(0, maxShown.integer());
        int c = argb();
        for (int i = list.size() - 1; i >= 0; i--) {
            BlockPos p = list.get(i);
            Draw.worldBox(g, new AABB(p), c, true);
        }
        if (tracers.on()) {
            for (int i = Math.min(8, list.size()) - 1; i >= 0; i--) {
                BlockPos p = list.get(i);
                Draw.tracer(g, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, c);
            }
        }
    }

    @Override
    public String info() { return String.valueOf(scanner.results().size()); }
}
