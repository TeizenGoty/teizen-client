package dev.teizen.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Weg-Suche für den Auto-Miner. Der Weg darf durch Blöcke gehen – die werden unterwegs abgebaut.
 * Lava, Wasser, Grundgestein und tiefe Stürze werden vermieden.
 */
public final class MinePath {
    private static final Minecraft mc = Minecraft.getInstance();
    private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

    public enum Kind { WALK, UP, DOWN, DIG_DOWN, PILLAR }

    /** Ein Schritt; {@code place} = hier vorher einen Block hinsetzen (Brücke / unter sich beim Hochbauen), sonst null. */
    public record Step(BlockPos pos, Kind kind, List<BlockPos> clear, BlockPos place) {
        public Step(BlockPos pos, Kind kind, List<BlockPos> clear) { this(pos, kind, clear, null); }
    }

    private record Node(BlockPos pos, double g, double f) {}

    private MinePath() {}

    /** Kann der Spieler durch diesen Block (ohne abzubauen)? */
    public static boolean passable(ClientLevel l, BlockPos p) {
        BlockState s = l.getBlockState(p);
        return s.getCollisionShape(l, p).isEmpty() && s.getFluidState().isEmpty();
    }

    private static boolean dangerousFluid(ClientLevel l, BlockPos p) {
        FluidState f = l.getFluidState(p);
        return !f.isEmpty();
    }

    /** Kosten um einen Block frei zu machen, oder -1 wenn verboten. */
    private static double clearCost(ClientLevel l, BlockPos p) {
        BlockState s = l.getBlockState(p);
        if (!s.getFluidState().isEmpty()) return -1;
        if (s.getCollisionShape(l, p).isEmpty()) return 0;
        float h = s.getDestroySpeed(l, p);
        if (h < 0 || h > 30) return -1; // Grundgestein, Obsidian-artiges vermeiden
        // Nachbarn mit Flüssigkeit -> würde auslaufen
        for (Direction d : Direction.values()) {
            if (d == Direction.DOWN) continue;
            if (dangerousFluid(l, p.relative(d))) return -1;
        }
        double c = 1.5 + h * 2.5;
        if (l.getBlockState(p.above()).getBlock() instanceof FallingBlock) c += 6;
        return c;
    }

    /** Darf dieser Block für den Weg abgebaut werden? (kein Grundgestein, keine Flüssigkeit daneben) */
    public static boolean canClear(ClientLevel l, BlockPos p) {
        return clearCost(l, p) >= 0;
    }

    public static boolean solidFloor(ClientLevel l, BlockPos p) {
        BlockState s = l.getBlockState(p);
        if (!s.getFluidState().isEmpty()) return false;
        if (s.getBlock() instanceof net.minecraft.world.level.block.MagmaBlock) return false;
        return !s.getCollisionShape(l, p).isEmpty();
    }

    private static double sumClear(ClientLevel l, List<BlockPos> list) {
        double total = 0;
        for (BlockPos b : list) {
            double c = clearCost(l, b);
            if (c < 0) return -1;
            total += c;
        }
        return total;
    }

    /**
     * Sucht einen Weg von {@code start} (Füße) zu einer Position, von der aus {@code target} in Reichweite ist.
     */
    public static List<Step> find(BlockPos start, BlockPos target, double reach, int maxNodes) {
        return find(start, target, reach, maxNodes, false);
    }

    /** Wie oben; mit {@code canPlace} darf der Weg auch Blöcke setzen (Lücken überbrücken, hochbauen). */
    public static List<Step> find(BlockPos start, BlockPos target, double reach, int maxNodes, boolean canPlace) {
        ClientLevel l = mc.level;
        Map<BlockPos, Double> best = new HashMap<>();
        Map<BlockPos, Step> came = new HashMap<>();
        Map<BlockPos, BlockPos> parent = new HashMap<>();
        PriorityQueue<Node> open = new PriorityQueue<>((a, b) -> Double.compare(a.f, b.f));
        open.add(new Node(start, 0, h(start, target)));
        best.put(start, 0.0);
        // Knoten, deren Boden ein geplanter (noch nicht gesetzter) Block ist
        java.util.Set<BlockPos> virtual = new java.util.HashSet<>();
        int expanded = 0;
        double reachSq = (reach - 0.3) * (reach - 0.3);

        while (!open.isEmpty() && expanded++ < maxNodes) {
            Node n = open.poll();
            if (n.g > best.getOrDefault(n.pos, Double.MAX_VALUE)) continue;
            // Ziel: Augen nah genug am Erz
            double ex = n.pos.getX() + 0.5 - (target.getX() + 0.5);
            double ey = n.pos.getY() + 1.62 - (target.getY() + 0.5);
            double ez = n.pos.getZ() + 0.5 - (target.getZ() + 0.5);
            if (ex * ex + ey * ey + ez * ez <= reachSq && !n.pos.equals(target) && !n.pos.above().equals(target)) {
                return rebuild(n.pos, came, parent);
            }
            for (Step s : neighbours(l, n.pos, canPlace, virtual.contains(n.pos))) {
                double c = sumClear(l, s.clear);
                if (c < 0) continue;
                double g = n.g + 1 + c + (s.kind == Kind.UP ? 0.5 : 0) + (s.place != null ? 3 : 0);
                if (g < best.getOrDefault(s.pos, Double.MAX_VALUE)) {
                    best.put(s.pos, g);
                    if (s.place != null) virtual.add(s.pos); else virtual.remove(s.pos);
                    came.put(s.pos, s);
                    parent.put(s.pos, n.pos);
                    open.add(new Node(s.pos, g, g + h(s.pos, target)));
                }
            }
        }
        return null;
    }

    /** Geschätzte Kosten: durch Stein graben kostet ~5 pro Block, darum stark gewichtet (sonst sucht A* ewig). */
    private static double h(BlockPos a, BlockPos b) {
        return (Math.abs(a.getX() - b.getX()) + Math.abs(a.getY() - b.getY()) * 1.5 + Math.abs(a.getZ() - b.getZ())) * 3.5;
    }

    private static List<Step> neighbours(ClientLevel l, BlockPos n, boolean canPlace, boolean virtualFloor) {
        List<Step> out = new ArrayList<>(10);
        boolean standing = virtualFloor || solidFloor(l, n.below());
        for (Direction d : HORIZONTAL) {
            BlockPos flat = n.relative(d);
            // geradeaus
            if (solidFloor(l, flat.below())) out.add(new Step(flat, Kind.WALK, List.of(flat, flat.above())));
            // Lücke: Block hinsetzen (an die Seite des Bodens, auf dem man steht) und drüber laufen
            else if (canPlace && standing && passable(l, flat.below()) && !dangerousFluid(l, flat.below()))
                out.add(new Step(flat, Kind.WALK, List.of(flat, flat.above()), flat.below()));
            // hoch
            BlockPos up = flat.above();
            if (solidFloor(l, flat) || !passable(l, flat)) {
                out.add(new Step(up, Kind.UP, List.of(n.above(2), up, up.above())));
            }
            // runter (eine Stufe)
            BlockPos down = flat.below();
            if (solidFloor(l, down.below())) out.add(new Step(down, Kind.DOWN, List.of(flat.above(), flat, down)));
        }
        // hochbauen: springen und einen Block unter sich setzen
        if (canPlace && standing) out.add(new Step(n.above(), Kind.PILLAR, List.of(n.above(), n.above(2)), n));
        // gerade nach unten graben (nur wenn darunter fester Boden ist)
        BlockPos below = n.below();
        if (solidFloor(l, below.below()) && !dangerousFluid(l, below.below())) {
            out.add(new Step(below, Kind.DIG_DOWN, List.of(below)));
        }
        return out;
    }

    private static List<Step> rebuild(BlockPos end, Map<BlockPos, Step> came, Map<BlockPos, BlockPos> parent) {
        List<Step> path = new ArrayList<>();
        BlockPos cur = end;
        while (came.containsKey(cur)) {
            path.add(came.get(cur));
            cur = parent.get(cur);
        }
        Collections.reverse(path);
        return path;
    }
}
