package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.util.Draw;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.phys.AABB;

import java.util.HashSet;
import java.util.Set;

/**
 * Chunks: zeigt die Chunk-Grenzen an und hebt "aktive" Chunks hervor – also Chunks, die geladen bleiben, weil z.B. eine
 * geworfene Ender-Perle drin liegt (oder, wenn eingestellt, liegende Items/Loren, die oft auf einen Chunkloader deuten).
 * Hinweis: erkennt nur, was in deiner Sichtweite geladen ist.
 */
public final class Chunks extends Module {
    private final Setting borders = add(Setting.bool("gitter", "Chunk-Grenzen als Gitter zeigen", true));
    private final Setting radius = add(Setting.number("radius", "Wie viele Chunks weit das Gitter geht", 4, 1, 12));
    private final Setting pearls = add(Setting.bool("perlen", "Chunks mit geworfener Ender-Perle hervorheben (bleiben geladen)", true));
    private final Setting items = add(Setting.bool("items", "Chunks mit liegenden Items/Loren hervorheben (möglicher Chunkloader)", false));
    private final Setting height = add(Setting.number("hoehe", "Höhe der aktiven Markierung (Blöcke um dich)", 24, 4, 128));

    /** Für den Selbsttest: Anzahl gerade erkannter aktiver Chunks. */
    public int activeCount;
    private Set<Long> activeChunks = new HashSet<>();

    public Chunks() {
        super("Chunks", Category.SICHT, "Chunk-Grenzen und aktive Chunks (z.B. durch Ender-Perle geladen).");
    }

    private static long key(int cx, int cz) { return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL); }

    private void collect(Set<Long> out, Iterable<? extends Entity> es) {
        for (Entity e : es) out.add(key(e.blockPosition().getX() >> 4, e.blockPosition().getZ() >> 4));
    }

    @Override
    public void onTick() {
        if (!inGame()) return;
        Set<Long> active = new HashSet<>();
        double reach = 16 * (radius.integer() + 2);
        if (pearls.on())
            collect(active, mc.level.getEntitiesOfClass(ThrownEnderpearl.class, mc.player.getBoundingBox().inflate(reach)));
        if (items.on())
            collect(active, mc.level.getEntitiesOfClass(ItemEntity.class, mc.player.getBoundingBox().inflate(reach)));
        activeChunks = active;
        activeCount = active.size();
    }

    @Override
    public void onHud(GuiGraphicsExtractor g, float partial) {
        if (!inGame()) return;
        int mx = mc.player.blockPosition().getX() >> 4, mz = mc.player.blockPosition().getZ() >> 4;
        double py = mc.player.getY();
        Set<Long> active = activeChunks;

        int r = radius.integer();
        double h = height.num();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                int cx = mx + dx, cz = mz + dz;
                double x0 = cx * 16, z0 = cz * 16;
                boolean act = active.contains(key(cx, cz));
                if (act) {
                    AABB box = new AABB(x0, py - h, z0, x0 + 16, py + h, z0 + 16);
                    Draw.worldBox(g, box, 0x5500E5FF, true);
                    Draw.worldBox(g, box, 0xFF00E5FF, false);
                } else if (borders.on()) {
                    // nur ein flaches Gitter-Band um die eigene Höhe (nicht die ganze Welt)
                    AABB box = new AABB(x0, py - 1, z0, x0 + 16, py + 2, z0 + 16);
                    Draw.worldBox(g, box, 0x3055C0FF, false);
                }
            }
        }
    }

    @Override
    public String info() { return activeCount > 0 ? "§b" + activeCount : ""; }
}
