package dev.teizen.client.modules;

import dev.teizen.client.core.Locator;
import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.util.Chat;
import dev.teizen.client.util.Draw;
import dev.teizen.client.util.Projector;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Findet jede Struktur (Dorf, Festung, Antike Stadt, ...). In der eigenen Welt direkt,
 * auf Servern per /locate (braucht OP). Zeigt Richtung, Entfernung und eine Linie dorthin.
 */
public final class StructureFinder extends Module {
    public final Setting structures = add(Setting.text("strukturen", "Welche Strukturen (Komma-getrennt), z.B. village,stronghold,ancient_city", "village"));
    private final Setting tracers = add(Setting.bool("linien", "Linie zur Struktur", true));
    private final Setting compass = add(Setting.bool("kompass", "Richtung und Entfernung oben in der Mitte", true));

    public record Found(BlockPos pos, String label) {}

    public final List<Found> found = new ArrayList<>();
    private int session;

    public StructureFinder() {
        super("StructureFinder", Category.SICHT, "Findet Dörfer, Festungen und jede andere Struktur.");
    }

    @Override
    protected void onEnable() {
        found.clear();
        if (!inGame()) return;
        final int my = ++session;
        for (String part : structures.str().split(",")) {
            String name = part.trim();
            if (name.isEmpty()) continue;
            Chat.info("Suche §f" + Locator.label(name) + "§7...");
            Locator.locate(name, new Locator.Callback() {
                @Override
                public void found(BlockPos pos, String label) {
                    if (!isEnabled() || my != session) return;
                    found.add(new Found(pos, label));
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

    @Override
    protected void onDisable() {
        found.clear();
        session++;
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
        for (Found f : found) {
            double x = f.pos().getX() + 0.5, z = f.pos().getZ() + 0.5;
            double dx = x - eye.x, dz = z - eye.z;
            long dist = Math.round(Math.sqrt(dx * dx + dz * dz));
            // Markierung immer auf Augenhöhe, damit sie am Horizont zu sehen ist
            double y = eye.y;
            if (tracers.on()) Draw.tracer(g, x, y, z, 0xFFFFAA00);
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
    public String info() { return structures.str(); }
}
