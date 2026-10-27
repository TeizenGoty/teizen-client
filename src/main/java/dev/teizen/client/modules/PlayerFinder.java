package dev.teizen.client.modules;

import dev.teizen.client.core.FarPlayers;
import dev.teizen.client.core.Friends;
import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.util.Arrows;
import dev.teizen.client.util.Draw;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Pfeile zu allen Spielern (wie beim PortalFinder): um das Fadenkreuz zeigt ein Pfeil in die Richtung jedes Spielers,
 * mit Name, Entfernung und Höhe. Auch weit entfernte Spieler (eigene Welt direkt, auf Servern per /data mit OP).
 * Dazu: LogoutSpots (wo jemand offline ging), VisualRange (Meldung, wenn jemand in Sichtweite kommt/geht) und
 * unten links ein Zähler, wie viele Spieler im Blick sind.
 */
public final class PlayerFinder extends Module {
    private final Setting arrows = add(Setting.bool("pfeile", "Pfeile, die zu den Spielern zeigen", true));
    private final Setting style = add(Setting.choice("stil", "kreis = Pfeile um das Fadenkreuz, rand = am Bildschirmrand (nur wenn der Spieler nicht im Bild ist), beides",
            "kreis", "kreis", "rand", "beides"));
    private final Setting arrowSize = add(Setting.number("pfeilgroesse", "Größe der Pfeile (Pixel)", 6, 3, 24));
    private final Setting list = add(Setting.bool("liste", "Liste links oben: Name, Entfernung, Richtung", true));
    private final Setting boxes = add(Setting.bool("rahmen", "Rahmen und Name am Spieler (auch durch Wände)", true));
    private final Setting tracers = add(Setting.bool("linien", "Linien zu den Spielern (die nächste ist kräftiger)", false));
    private final Setting far = add(Setting.bool("weit", "Auch weit entfernte Spieler (eigene Welt direkt, Server: OP)", true));
    private final Setting ring = add(Setting.number("kreis", "Größe des Pfeil-Kreises (Pixel)", 60, 20, 200));
    private final Setting logout = add(Setting.bool("logout", "LogoutSpots: zeigt, wo ein Spieler offline gegangen ist", true));
    private final Setting visual = add(Setting.bool("meldung", "VisualRange: Meldung (mit Ton), wenn ein Spieler in deine Sichtweite kommt oder sie verlässt", true));
    private final Setting counter = add(Setting.bool("zaehler", "Unten links: wie viele Spieler du gerade im Blick hast", true));

    /** Wo jemand offline gegangen ist. */
    public record Spot(java.util.UUID id, String name, double x, double y, double z, String dim, long time) {}

    public final java.util.Map<java.util.UUID, Spot> logoutSpots = new java.util.LinkedHashMap<>();
    /** Wer gerade in Sichtweite ist (UUID -> letzter Stand). */
    private final java.util.Map<java.util.UUID, Spot> inRange = new java.util.HashMap<>();
    /** Gerade aus der Sichtweite verschwunden: warten, ob er auch aus der Tab-Liste geht (= offline). */
    private final java.util.Map<java.util.UUID, Integer> leaving = new java.util.HashMap<>();
    private int presenceTick;
    private Object lastLevel;
    /** Zähler für Anzeige und Selbsttest. */
    public int entered, left, loggedOut, cameBack;
    public int inView, nearby;

    public record Target(String name, double x, double y, double z, String dim, boolean sameDim, boolean near, boolean friend) {}

    public final List<Target> targets = new ArrayList<>();
    /** Nur für den Selbsttest: zusätzliche (gedachte) Spieler. */
    public static final List<Target> testTargets = new ArrayList<>();

    public PlayerFinder() {
        super("PlayerFinder", Category.SICHT, "Pfeile zu allen Spielern, auch weit weg.");
    }

    @Override
    protected void onDisable() {
        targets.clear();
        inRange.clear();
        leaving.clear();
    }

    /**
     * LogoutSpots + VisualRange: wer ist in Sichtweite, wer ist gegangen, wer ist offline gegangen.
     * @param visible Spieler, die man gerade sieht (UUID -> Stand)
     * @param online  alle Spieler in der Tab-Liste
     */
    public void presence(java.util.Map<java.util.UUID, Spot> visible, java.util.Set<java.util.UUID> online, int now) {
        // neu in Sichtweite
        for (var e : visible.entrySet()) {
            if (inRange.containsKey(e.getKey())) continue;
            leaving.remove(e.getKey());
            entered++;
            if (visual.on() && now > 40) {
                double d = mc.player == null ? 0 : Math.sqrt(Math.pow(e.getValue().x() - mc.player.getX(), 2) + Math.pow(e.getValue().z() - mc.player.getZ(), 2));
                dev.teizen.client.util.Chat.info("§e" + e.getValue().name() + " §7kommt in deine Sichtweite (" + Math.round(d) + " m).");
                if (mc.player != null) mc.player.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(), 0.5f, 1.8f);
            }
        }
        // nicht mehr zu sehen -> kurz warten, ob er offline gegangen ist
        for (var e : inRange.entrySet()) if (!visible.containsKey(e.getKey())) leaving.putIfAbsent(e.getKey(), now);
        java.util.Map<java.util.UUID, Spot> last = new java.util.HashMap<>(inRange);
        inRange.clear();
        inRange.putAll(visible);
        for (var it = leaving.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            Spot sp = last.getOrDefault(e.getKey(), lastSeen.get(e.getKey()));
            if (sp != null) lastSeen.put(e.getKey(), sp);
            if (!online.contains(e.getKey())) {
                it.remove();
                if (sp == null) continue;
                loggedOut++;
                if (logout.on()) {
                    logoutSpots.put(e.getKey(), new Spot(sp.id(), sp.name(), sp.x(), sp.y(), sp.z(), sp.dim(), System.currentTimeMillis()));
                    dev.teizen.client.util.Chat.info("§c" + sp.name() + " §7ist bei §f" + Math.round(sp.x()) + " " + Math.round(sp.y()) + " " + Math.round(sp.z()) + " §7offline gegangen.");
                }
            } else if (now - e.getValue() >= 10) {
                it.remove();
                left++;
                if (visual.on() && sp != null) dev.teizen.client.util.Chat.info("§7" + sp.name() + " hat deine Sichtweite verlassen.");
            }
        }
        // wieder online -> Logout-Stelle weg
        for (var it = logoutSpots.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            if (online.contains(e.getKey())) {
                it.remove();
                cameBack++;
                if (logout.on()) dev.teizen.client.util.Chat.info("§a" + e.getValue().name() + " §7ist wieder online.");
            }
        }
    }

    private final java.util.Map<java.util.UUID, Spot> lastSeen = new java.util.HashMap<>();

    /** Wie viele dieser Punkte gerade im Bild sind (für den Zähler unten links). */
    public static int countInView(java.util.List<Vec3> points, int w, int h) {
        int n = 0;
        for (Vec3 p : points) {
            double[] s = dev.teizen.client.util.Projector.project(p.x, p.y, p.z);
            if (s != null && s[0] >= 0 && s[0] <= w && s[1] >= 0 && s[1] <= h) n++;
        }
        return n;
    }

    @Override
    public void onTick() {
        if (!inGame()) return;
        // LogoutSpots / VisualRange (neue Welt/Dimension: von vorne anfangen, keine Meldungs-Flut)
        if (mc.level != lastLevel) { lastLevel = mc.level; presenceTick = 0; inRange.clear(); leaving.clear(); }
        presenceTick++;
        java.util.Map<java.util.UUID, Spot> vis = new java.util.HashMap<>();
        String dimName = FarPlayers.dimName(mc.level.dimension());
        for (Player p : mc.level.players()) {
            if (p == mc.player || p.isSpectator()) continue;
            vis.put(p.getUUID(), new Spot(p.getUUID(), p.getGameProfile().name(), p.getX(), p.getY(), p.getZ(), dimName, 0));
        }
        java.util.Set<java.util.UUID> online = new java.util.HashSet<>();
        if (mc.getConnection() != null) for (var info : mc.getConnection().getOnlinePlayers()) online.add(info.getProfile().id());
        presence(vis, online, presenceTick);
        if (far.on()) FarPlayers.want();
        List<Target> out = new ArrayList<>();
        for (Player p : mc.level.players()) {
            if (p == mc.player || p.isSpectator()) continue;
            String n = p.getGameProfile().name();
            out.add(new Target(n, p.getX(), p.getY(), p.getZ(), FarPlayers.dimName(mc.level.dimension()), true, true, Friends.is(n)));
        }
        if (far.on()) {
            for (FarPlayers.P p : FarPlayers.all()) {
                boolean same = p.dim() == mc.level.dimension();
                out.add(new Target(p.name(), p.x(), p.y(), p.z(), FarPlayers.dimName(p.dim()), same, false, Friends.is(p.name())));
            }
        }
        out.addAll(testTargets);
        Vec3 me = mc.player.position();
        out.sort(Comparator.comparingDouble((Target t) -> t.sameDim() ? 0 : 1).thenComparingDouble(t -> dist2(t, me)));
        targets.clear();
        targets.addAll(out);
    }

    private static double dist2(Target t, Vec3 me) {
        double dx = t.x() - me.x, dy = t.y() - me.y, dz = t.z() - me.z;
        return dx * dx + dy * dy + dz * dz;
    }

    private static int color(Target t) {
        if (t.friend()) return 0xFF55FF55;
        return t.near() ? 0xFFFF5050 : 0xFFFFA040;
    }

    @Override
    public void onHud(GuiGraphicsExtractor g, float partial) {
        if (!inGame()) return;
        drawLogoutSpots(g);
        drawCounter(g, partial);
        drawTargets(g, partial);
    }

    private void drawLogoutSpots(GuiGraphicsExtractor g) {
        if (!logout.on() || logoutSpots.isEmpty()) return;
        String dim = FarPlayers.dimName(mc.level.dimension());
        long now = System.currentTimeMillis();
        for (Spot sp : logoutSpots.values()) {
            if (!dim.equals(sp.dim())) continue;
            double[] r = Draw.worldBox(g, new AABB(sp.x() - 0.3, sp.y(), sp.z() - 0.3, sp.x() + 0.3, sp.y() + 1.8, sp.z() + 0.3), 0xFFB0B0B0, true);
            if (r == null) continue;
            long min = (now - sp.time()) / 60000;
            String t = "§7" + sp.name() + " §8(offline " + (min < 1 ? "gerade eben" : min + " min") + ")";
            g.text(mc.font, t, (int) ((r[0] + r[2]) / 2) - mc.font.width(t) / 2, (int) r[1] - 10, 0xFFFFFFFF, true);
        }
    }

    private void drawCounter(GuiGraphicsExtractor g, float partial) {
        if (!counter.on()) return;
        java.util.List<Vec3> pts = new java.util.ArrayList<>();
        for (Player p : mc.level.players()) {
            if (p == mc.player || p.isSpectator()) continue;
            pts.add(p.getPosition(partial).add(0, 1.0, 0));
        }
        nearby = pts.size();
        inView = countInView(pts, g.guiWidth(), g.guiHeight());
        // nur ein kleines Feld: Augen-Zeichen + Zahl der Spieler im Blick (passt immer links neben die Hotbar)
        String t = "§d👁 §f" + inView;
        int w = mc.font.width(t);
        int maxRight = g.guiWidth() / 2 - 91 - 4; // linker Rand der Hotbar
        if (w + 5 > maxRight && !dev.teizen.client.gui.HudLayout.moved("zaehler")) return;
        dev.teizen.client.gui.HudLayout.report("zaehler", 1, g.guiHeight() - 12, w + 5, g.guiHeight() - 1);
        dev.teizen.client.gui.HudLayout.push(g, "zaehler");
        g.fill(1, g.guiHeight() - 12, w + 5, g.guiHeight() - 1, 0x80140A26);
        g.text(mc.font, t, 3, g.guiHeight() - 10, 0xFFFFFFFF, true);
        dev.teizen.client.gui.HudLayout.pop(g);
    }

    private void drawTargets(GuiGraphicsExtractor g, float partial) {
        if (targets.isEmpty()) {
            // sonst denkt man, es geht nicht
            if (list.on()) {
                int y0 = dev.teizen.client.compat.XaeroCompat.minimapInstalled() ? Math.max(14, g.guiHeight() / 3) : 14;
                String l1 = "§7PlayerFinder: kein anderer Spieler gefunden", l2 = "§8(Server ohne OP: nur Spieler in Sichtweite)";
                dev.teizen.client.gui.HudLayout.report("spielerliste", 4, y0, 4 + Math.max(mc.font.width(l1), mc.font.width(l2)), y0 + 19);
                dev.teizen.client.gui.HudLayout.push(g, "spielerliste");
                g.text(mc.font, l1, 4, y0, 0xFFFFFFFF, true);
                g.text(mc.font, l2, 4, y0 + 10, 0xFFFFFFFF, true);
                dev.teizen.client.gui.HudLayout.pop(g);
            }
            return;
        }
        Vec3 eye = mc.player.getEyePosition();
        int line = 0;
        listW = 0;
        boolean first = true;
        // Liste links oben – mit Xaero's Minimap weiter unten, damit sie nicht unter der Karte liegt
        int listY = dev.teizen.client.compat.XaeroCompat.minimapInstalled() ? Math.max(14, g.guiHeight() / 3) : 14;
        for (Target t : targets) {
            int c = color(t);
            double cy = t.y() + 1.0;
            long dist = Math.round(Math.sqrt(dist2(t, mc.player.position())));
            if (t.sameDim()) {
                // Spieler, die man sieht: weich mitbewegen
                double x = t.x(), y = t.y(), z = t.z();
                if (t.near()) {
                    for (Player p : mc.level.players()) {
                        if (p.getGameProfile().name().equals(t.name())) {
                            Vec3 v = p.getPosition(partial);
                            x = v.x; y = v.y; z = v.z;
                            break;
                        }
                    }
                    cy = y + 1.0;
                }
                if (arrows.on()) {
                    String lbl = t.name() + " " + dist + "m" + Arrows.height(y);
                    int sz = arrowSize.integer();
                    if (style.is("kreis") || style.is("beides")) Arrows.draw(g, x, cy, z, c, null, ring.num(), sz);
                    if (style.is("rand") || style.is("beides")) Arrows.drawEdge(g, x, cy, z, c, null, sz);
                }
                if (tracers.on()) {
                    if (first) Draw.tracerNearest(g, x, cy, z, c, null);
                    else Draw.tracer(g, x, cy, z, c);
                }
                if (boxes.on()) {
                    double[] r = Draw.worldBox(g, new AABB(x - 0.3, y, z - 0.3, x + 0.3, y + 1.8, z + 0.3), c, false);
                    if (r != null) {
                        String s = t.name() + " §f" + dist + "m";
                        g.text(mc.font, s, (int) ((r[0] + r[2]) / 2) - mc.font.width(s) / 2, (int) r[1] - 10, c, true);
                    }
                }
                first = false;
            }
            if (list.on() && line < 12) {
                String s;
                if (t.sameDim()) {
                    double dx = t.x() - eye.x, dz = t.z() - eye.z;
                    s = "§f" + t.name() + " §7" + dist + " m " + StructureFinder.direction(dx, dz) + Arrows.height(t.y());
                } else {
                    s = "§f" + t.name() + " §8im " + t.dim() + " §7(" + Math.round(t.x()) + " " + Math.round(t.z()) + ")";
                }
                dev.teizen.client.gui.HudLayout.push(g, "spielerliste");
                g.text(mc.font, s, 4, listY + line * 10, c, true);
                dev.teizen.client.gui.HudLayout.pop(g);
                listW = Math.max(listW, mc.font.width(s));
                line++;
            }
        }
        if (line > 0) dev.teizen.client.gui.HudLayout.report("spielerliste", 4, listY, 4 + listW, listY + line * 10 - 1);
    }

    private int listW;

    @Override
    public String info() { return String.valueOf(targets.size()); }
}
