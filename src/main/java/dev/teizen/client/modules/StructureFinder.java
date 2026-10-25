package dev.teizen.client.modules;

import dev.teizen.client.core.Locator;
import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.util.Arrows;
import dev.teizen.client.util.Chat;
import dev.teizen.client.util.Draw;
import dev.teizen.client.util.Projector;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Findet jede Struktur (Dorf, Festung, Antike Stadt, Bastion, ...): im Menü einfach anhaken.
 * In der eigenen Welt direkt, auf Servern per /locate (braucht OP). Zeigt Pfeil, Richtung, Entfernung und eine Linie.
 */
public final class StructureFinder extends Module {
    public final Setting structures = add(Setting.multi("strukturen",
            "Welche Strukturen gesucht werden (anhaken; Rechtsklick = Namen eintippen)", "village",
            Locator.names().toArray(new String[0])).labels(Locator::label));
    private final Setting arrows = add(Setting.bool("pfeile", "Pfeile um das Fadenkreuz, die zur Struktur zeigen", true));
    private final Setting tracers = add(Setting.bool("linien", "Linie zur Struktur", true));
    private final Setting compass = add(Setting.bool("kompass", "Richtung und Entfernung oben in der Mitte", true));

    public record Found(BlockPos pos, String label, String key) {
        public Found(BlockPos pos, String label) { this(pos, label, ""); }
    }

    public final List<Found> found = new ArrayList<>();
    /** Strukturen, die schon gesucht werden (Hauptname). */
    private final Set<String> searched = new LinkedHashSet<>();
    private String lastQuery = "";
    private ResourceKey<Level> lastDim;
    private int session;

    public StructureFinder() {
        super("StructureFinder", Category.SICHT, "Findet Dörfer, Festungen und jede andere Struktur.");
        opOnly();
    }

    @Override
    protected void onEnable() {
        found.clear();
        searched.clear();
        session++;
        lastQuery = "";
        lastDim = null;
        if (inGame()) sync();
    }

    @Override
    protected void onDisable() {
        found.clear();
        searched.clear();
        session++;
    }

    @Override
    public void onTick() {
        if (!inGame()) return;
        if (lastDim != mc.level.dimension()) {
            // andere Dimension: alles neu suchen
            found.clear();
            searched.clear();
            session++;
            sync();
        } else if (!structures.str().equals(lastQuery)) {
            sync();
        }
    }

    /** Neu angehakte Strukturen suchen, abgehakte entfernen. */
    private void sync() {
        lastQuery = structures.str();
        lastDim = mc.level.dimension();
        List<String> want = new ArrayList<>();
        for (String n : structures.selected()) {
            String p = Locator.primary(n);
            if (!want.contains(p)) want.add(p);
        }
        searched.removeIf(n -> {
            if (want.contains(n)) return false;
            found.removeIf(f -> f.key().equals(n));
            return true;
        });
        final int my = session;
        for (String name : want) {
            if (searched.contains(name)) continue;
            searched.add(name);
            if (!Locator.inDimension(name, lastDim)) {
                Chat.info(Locator.label(name) + " §7gibt es nur im §f" + Locator.dimensionText(name) + "§7.");
                continue;
            }
            Chat.info("Suche §f" + Locator.label(name) + "§7...");
            Locator.locate(name, new Locator.Callback() {
                @Override
                public void found(BlockPos pos, String label) {
                    if (!isEnabled() || my != session || !searched.contains(name)) return;
                    found.removeIf(f -> f.key().equals(name));
                    found.add(new Found(pos, label, name));
                    BlockPos me = mc.player.blockPosition();
                    long d = Math.round(Math.sqrt(Math.pow(pos.getX() - me.getX(), 2) + Math.pow(pos.getZ() - me.getZ(), 2)));
                    Chat.ok(label + " §7bei §f" + pos.getX() + " " + pos.getZ() + " §7(" + d + " Blöcke)");
                }

                @Override
                public void failed(String why) {
                    if (isEnabled() && my == session) Chat.error(why);
                }
            });
        }
    }

    private static double flat(Found f, net.minecraft.world.phys.Vec3 eye) {
        double dx = f.pos().getX() + 0.5 - eye.x, dz = f.pos().getZ() + 0.5 - eye.z;
        return dx * dx + dz * dz;
    }

    static String direction(double dx, double dz) {
        double ang = Math.toDegrees(Math.atan2(dz, dx)); // 0 = Osten, 90 = Süden
        String[] dirs = {"O", "SO", "S", "SW", "W", "NW", "N", "NO"};
        int i = (int) Math.floorMod(Math.round(ang / 45.0), 8);
        return dirs[i];
    }

    @Override
    public void onHud(GuiGraphicsExtractor g, float partial) {
        if (!inGame() || found.isEmpty()) return;
        var eye = mc.player.getEyePosition();
        int line = 0;
        Found nearestF = null;
        for (Found f : found)
            if (nearestF == null || flat(f, eye) < flat(nearestF, eye)) nearestF = f;
        for (Found f : found) {
            double x = f.pos().getX() + 0.5, z = f.pos().getZ() + 0.5;
            double dx = x - eye.x, dz = z - eye.z;
            long dist = Math.round(Math.sqrt(dx * dx + dz * dz));
            // Markierung immer auf Augenhöhe, damit sie am Horizont zu sehen ist
            double y = eye.y;
            if (tracers.on()) {
                if (f == nearestF) Draw.tracerNearest(g, x, y, z, 0xFFFFAA00, f.label() + " " + dist + "m");
                else Draw.tracer(g, x, y, z, 0xFFFFAA00);
            }
            if (arrows.on()) Arrows.draw(g, x, y, z, 0xFFFFAA00, f.label() + " " + dist + "m", 70);
            double[] s = Projector.project(x, y, z);
            if (s != null) {
                String t = "§6" + f.label() + " §f" + dist + "m";
                g.text(mc.font, t, (int) s[0] - mc.font.width(t) / 2, (int) s[1] - 4, 0xFFFFFFFF, true);
            }
            if (compass.on()) {
                String t = "§6" + f.label() + "§7: §f" + dist + " m §7Richtung §f" + direction(dx, dz);
                g.text(mc.font, t, g.guiWidth() / 2 - mc.font.width(t) / 2, 16 + line * 10, 0xFFFFFFFF, true);
                line++;
            }
        }
    }

    @Override
    public String info() {
        List<String> sel = structures.selected();
        if (sel.size() == 1) return Locator.label(sel.get(0));
        return found.size() + "/" + sel.size();
    }
}
