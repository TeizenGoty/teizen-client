package dev.teizen.client.modules;

import dev.teizen.client.core.Locator;
import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.util.Arrows;
import dev.teizen.client.util.BlockScanner;
import dev.teizen.client.util.Chat;
import dev.teizen.client.util.Draw;
import dev.teizen.client.util.Projector;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EndPortalFrameBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Findet Portale in der Nähe (Netherportale, Endportale, Endportal-Rahmen, End-Gateways)
 * und sucht die Festung (Stronghold) mit dem Endportal – auf Servern per /locate (braucht OP).
 */
public final class PortalFinder extends Module {
    private final Setting nether = add(Setting.bool("nether", "Netherportale", true));
    private final Setting end = add(Setting.bool("end", "Endportale und Endportal-Rahmen", true));
    private final Setting gateway = add(Setting.bool("gateway", "End-Gateways", true));
    private final Setting stronghold = add(Setting.bool("festung", "Festung mit Endportal suchen (Oberwelt)", true));
    private final Setting ruins = add(Setting.bool("ruinen", "Auch die nächste Portalruine suchen", false));
    private final Setting radius = add(Setting.number("radius", "Suchradius in Chunks", 8, 1, 16));
    private final Setting tracers = add(Setting.bool("linien", "Linien zu den Portalen", true));
    private final Setting arrows = add(Setting.bool("pfeile", "Pfeile um das Fadenkreuz, die zu den Portalen zeigen", true));

    public record Portal(BlockPos pos, AABB box, String label, int color) {}

    private final BlockScanner scanner = new BlockScanner(this::matches, 8, 64);
    public final List<Portal> portals = new ArrayList<>();
    public final List<StructureFinder.Found> located = new ArrayList<>();
    private int session;

    public PortalFinder() {
        super("PortalFinder", Category.SICHT, "Zeigt alle Portale und findet die Festung (Endportal).");
    }

    private boolean matches(BlockState s) {
        return (nether.on() && s.is(Blocks.NETHER_PORTAL))
                || (end.on() && (s.is(Blocks.END_PORTAL) || s.is(Blocks.END_PORTAL_FRAME)))
                || (gateway.on() && s.is(Blocks.END_GATEWAY));
    }

    @Override
    protected void onEnable() {
        scanner.clear();
        portals.clear();
        located.clear();
        if (!inGame()) return;
        final int my = ++session;
        if (stronghold.on() && mc.level.dimension() == Level.OVERWORLD) locate("stronghold", my);
        if (ruins.on()) locate("ruined_portal", my);
    }

    private void locate(String what, int my) {
        Locator.locate(what, new Locator.Callback() {
            @Override
            public void found(BlockPos pos, String label) {
                if (!isEnabled() || my != session) return;
                located.add(new StructureFinder.Found(pos, label));
                Chat.ok(label + " §7bei §f" + pos.getX() + " " + pos.getZ());
            }

            @Override
            public void failed(String why) {
                if (isEnabled() && my == session) Chat.error(why);
            }
        });
    }

    @Override
    protected void onDisable() {
        scanner.clear();
        portals.clear();
        located.clear();
        session++;
    }

    @Override
    public void onTick() {
        if (!inGame()) return;
        scanner.setRadius(radius.integer());
        scanner.tick();
        if (mc.player.tickCount % 20 == 0) rebuild();
    }

    /** Benachbarte Portal-Blöcke zu einem Portal zusammenfassen. */
    private void rebuild() {
        Set<BlockPos> all = new HashSet<>(scanner.results());
        Set<BlockPos> seen = new HashSet<>();
        List<Portal> out = new ArrayList<>();
        for (BlockPos start : all) {
            if (!seen.add(start)) continue;
            List<BlockPos> group = new ArrayList<>();
            Deque<BlockPos> todo = new ArrayDeque<>();
            todo.add(start);
            while (!todo.isEmpty() && group.size() < 4000) {
                BlockPos p = todo.poll();
                group.add(p);
                for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                    BlockPos n = p.offset(dx, dy, dz);
                    if (all.contains(n) && seen.add(n)) todo.add(n);
                }
            }
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
            int frames = 0, eyes = 0;
            boolean netherP = false, endP = false, gate = false;
            for (BlockPos p : group) {
                minX = Math.min(minX, p.getX()); minY = Math.min(minY, p.getY()); minZ = Math.min(minZ, p.getZ());
                maxX = Math.max(maxX, p.getX()); maxY = Math.max(maxY, p.getY()); maxZ = Math.max(maxZ, p.getZ());
                BlockState st = mc.level.getBlockState(p);
                if (st.is(Blocks.NETHER_PORTAL)) netherP = true;
                else if (st.is(Blocks.END_PORTAL)) endP = true;
                else if (st.is(Blocks.END_GATEWAY)) gate = true;
                else if (st.is(Blocks.END_PORTAL_FRAME)) {
                    frames++;
                    if (st.getValue(EndPortalFrameBlock.HAS_EYE)) eyes++;
                }
            }
            String label;
            int color;
            if (netherP) { label = "Netherportal"; color = 0xFFB040FF; }
            else if (endP) { label = "Endportal (offen)"; color = 0xFF30FFB0; }
            else if (gate) { label = "End-Gateway"; color = 0xFF60D0FF; }
            else { label = "Endportal-Rahmen " + eyes + "/" + frames + " Augen"; color = 0xFF30C890; }
            out.add(new Portal(group.get(0), new AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1), label, color));
        }
        portals.clear();
        portals.addAll(out);
    }

    @Override
    public void onHud(GuiGraphicsExtractor g, float partial) {
        if (!inGame()) return;
        var eye = mc.player.getEyePosition();
        if (arrows.on()) {
            List<Portal> near = new ArrayList<>(portals);
            near.sort(java.util.Comparator.comparingDouble(p -> p.box().getCenter().distanceToSqr(eye)));
            for (int i = 0; i < Math.min(5, near.size()); i++) {
                Portal p = near.get(i);
                var c = p.box().getCenter();
                Arrows.draw(g, c.x, c.y, c.z, p.color(), null, 56);
            }
        }
        Portal nearestP = null;
        for (Portal p : portals)
            if (nearestP == null || p.box().getCenter().distanceToSqr(eye) < nearestP.box().getCenter().distanceToSqr(eye)) nearestP = p;
        for (Portal p : portals) {
            double[] r = Draw.worldBox(g, p.box(), p.color(), true);
            var c = p.box().getCenter();
            if (tracers.on()) {
                if (p == nearestP) Draw.tracerNearest(g, c.x, c.y, c.z, p.color(), null);
                else Draw.tracer(g, c.x, c.y, c.z, p.color());
            }
            if (r != null) {
                String t = p.label() + " " + Math.round(eye.distanceTo(c)) + "m";
                g.text(mc.font, t, (int) ((r[0] + r[2]) / 2) - mc.font.width(t) / 2, (int) r[1] - 10, p.color(), true);
            }
        }
        int line = 0;
        for (StructureFinder.Found f : located) {
            double x = f.pos().getX() + 0.5, z = f.pos().getZ() + 0.5;
            double dx = x - eye.x, dz = z - eye.z;
            long dist = Math.round(Math.sqrt(dx * dx + dz * dz));
            if (tracers.on()) Draw.tracer(g, x, eye.y, z, 0xFF30FFB0);
            if (arrows.on()) Arrows.draw(g, x, eye.y, z, 0xFF30FFB0, f.label() + " " + dist + "m", 62);
            double[] s = Projector.project(x, eye.y, z);
            if (s != null) {
                String t = "§a" + f.label() + " §f" + dist + "m";
                g.text(mc.font, t, (int) s[0] - mc.font.width(t) / 2, (int) s[1] - 4, 0xFFFFFFFF, true);
            }
            String t = "§a" + f.label() + "§7: §f" + dist + " m §7Richtung §f" + StructureFinder.direction(dx, dz);
            g.text(mc.font, t, g.guiWidth() / 2 - mc.font.width(t) / 2, 46 + line * 10, 0xFFFFFFFF, true);
            line++;
        }
    }

    @Override
    public String info() { return String.valueOf(portals.size()); }
}
