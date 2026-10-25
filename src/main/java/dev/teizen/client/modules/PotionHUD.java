package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.mixin.LivingEntityAccessor;
import dev.teizen.client.util.Projector;
import dev.teizen.client.util.ServerQuery;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PotionHUD: zeigt die Trank-Effekte ANDERER Spieler – als Liste und über ihrem Kopf.
 * Eigene Welt: genau (Stufe + Zeit). Server mit OP: genau per /data. Sonst: aus den Effekt-Partikeln erkannt (ohne Zeit).
 */
public final class PotionHUD extends Module {
    private final Setting overHead = add(Setting.bool("kopf", "Effekte über dem Kopf anzeigen", true));
    private final Setting list = add(Setting.bool("liste", "Liste am Bildschirmrand", true));
    private final Setting range = add(Setting.number("reichweite", "Spieler in dieser Entfernung (Blöcke)", 64, 8, 256));
    private final Setting self = add(Setting.bool("selbst", "Auch deine eigenen Effekte in der Liste", false));

    /** Ein Effekt: Name, Stufe (0 = I), Restzeit in Ticks (-1 = unendlich, -2 = unbekannt), Farbe. */
    public record Eff(String name, int amp, int ticks, int color) {}

    public final Map<UUID, List<Eff>> effects = new HashMap<>();
    private volatile Map<UUID, List<Eff>> fromServer = new HashMap<>();
    private final Map<UUID, Integer> lastAsked = new HashMap<>();
    private static Map<Integer, Holder<MobEffect>> byColor;
    private static Map<ParticleType<?>, Holder<MobEffect>> byType;
    private static final Pattern ID = Pattern.compile("id:\\s*\"([^\"]+)\"");
    private static final Pattern AMP = Pattern.compile("amplifier:\\s*(-?\\d+)");
    private static final Pattern DUR = Pattern.compile("duration:\\s*(-?\\d+)");
    /** Wie die Effekte erkannt wurden (Anzeige + Selbsttest): server / data / partikel. */
    public String source = "";

    public PotionHUD() {
        super("PotionHUD", Category.SICHT, "Zeigt die Trank-Effekte anderer Spieler (auch über dem Kopf).");
    }

    private static void buildLookup() {
        if (byColor != null) return;
        Map<Integer, Holder<MobEffect>> c = new HashMap<>();
        Map<ParticleType<?>, Holder<MobEffect>> t = new HashMap<>();
        BuiltInRegistries.MOB_EFFECT.listElements().forEach(h -> {
            try {
                ParticleOptions po = h.value().createParticleOptions(new MobEffectInstance(h));
                if (po instanceof ColorParticleOption cp) c.putIfAbsent(rgb(cp), h);
                else if (po != null) t.putIfAbsent(po.getType(), h);
            } catch (Throwable ignored) {
            }
        });
        byColor = c;
        byType = t;
    }

    private static int rgb(ColorParticleOption cp) {
        int r = Math.round(cp.getRed() * 255), g = Math.round(cp.getGreen() * 255), b = Math.round(cp.getBlue() * 255);
        return (r << 16) | (g << 8) | b;
    }

    static String roman(int amp) { return EnchantPreview.roman(amp + 1); }

    private static Eff eff(Holder<MobEffect> h, int amp, int ticks) {
        return new Eff(h.value().getDisplayName().getString(), amp, ticks, 0xFF000000 | h.value().getColor());
    }

    @Override
    protected void onDisable() { effects.clear(); }

    @Override
    public void onTick() {
        if (!inGame()) return;
        buildLookup();
        IntegratedServer server = mc.getSingleplayerServer();
        double r = range.num();
        List<Player> players = new ArrayList<>();
        for (Player p : mc.level.players()) {
            if (p == mc.player && !self.on()) continue;
            if (p.isSpectator() || p.distanceTo(mc.player) > r) continue;
            players.add(p);
        }
        if (server != null) {
            source = "server";
            if (mc.player.tickCount % 10 == 0) {
                List<UUID> ids = new ArrayList<>();
                for (Player p : players) ids.add(p.getUUID());
                server.execute(() -> {
                    Map<UUID, List<Eff>> m = new HashMap<>();
                    for (UUID id : ids) {
                        ServerPlayer sp = server.getPlayerList().getPlayer(id);
                        if (sp == null) continue;
                        List<Eff> l = new ArrayList<>();
                        for (MobEffectInstance i : sp.getActiveEffects()) l.add(eff(i.getEffect(), i.getAmplifier(), i.isInfiniteDuration() ? -1 : i.getDuration()));
                        m.put(id, l);
                    }
                    fromServer = m;
                });
            }
        } else if (ServerQuery.available()) {
            source = "data";
            int now = mc.player.tickCount;
            for (Player p : players) {
                UUID id = p.getUUID();
                if (now - lastAsked.getOrDefault(id, -1000) < 40 || ServerQuery.busy() > 6) continue;
                lastAsked.put(id, now);
                String name = p.getGameProfile().name();
                ServerQuery.data(name, "active_effects", txt -> {
                    Map<UUID, List<Eff>> m = new HashMap<>(fromServer);
                    m.put(id, parse(txt));
                    fromServer = m;
                }, () -> {
                    // keine Effekte -> Minecraft meldet "nichts gefunden"
                    Map<UUID, List<Eff>> m = new HashMap<>(fromServer);
                    m.put(id, List.of());
                    fromServer = m;
                });
            }
        } else {
            source = "partikel";
        }
        effects.clear();
        for (Player p : players) {
            List<Eff> l = fromServer.get(p.getUUID());
            if (l == null || source.equals("partikel")) l = fromParticles(p);
            if (!l.isEmpty()) effects.put(p.getUUID(), l);
        }
    }

    /** Antwort von "/data get entity <Name> active_effects" auswerten: [{amplifier: 1b, duration: 2400, id: "minecraft:speed", ...}, ...] */
    static List<Eff> parse(String txt) {
        List<Eff> out = new ArrayList<>();
        for (String entry : entries(txt)) {
            // versteckte Unter-Effekte ({...} im Eintrag) ignorieren
            String flat = entry.replaceAll("\\{[^{}]*\\}", "");
            Matcher m = ID.matcher(flat);
            if (!m.find()) continue;
            Identifier id = Identifier.tryParse(m.group(1));
            if (id == null) continue;
            var ref = BuiltInRegistries.MOB_EFFECT.get(id);
            if (ref.isEmpty()) continue;
            Matcher a = AMP.matcher(flat), d = DUR.matcher(flat);
            int amp = a.find() ? Integer.parseInt(a.group(1)) : 0;
            int dur = d.find() ? Integer.parseInt(d.group(1)) : -2;
            out.add(eff(ref.get(), amp, dur));
        }
        return out;
    }

    /** Die einzelnen {...}-Einträge der obersten Liste. */
    private static List<String> entries(String txt) {
        List<String> out = new ArrayList<>();
        int depth = 0, start = -1;
        for (int i = 0; i < txt.length(); i++) {
            char c = txt.charAt(i);
            if (c == '{') {
                if (depth == 0) start = i;
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && start >= 0) {
                    out.add(txt.substring(start + 1, i));
                    start = -1;
                }
            }
        }
        return out;
    }

    /** Ohne OP: aus den Effekt-Partikeln (jeder Effekt hat seine eigene Farbe). */
    public List<Eff> fromParticles(Player p) {
        buildLookup();
        List<Eff> out = new ArrayList<>();
        List<ParticleOptions> parts;
        try {
            parts = p.getEntityData().get(LivingEntityAccessor.teizen$effectParticles());
        } catch (Throwable t) {
            return out;
        }
        if (parts == null) return out;
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (ParticleOptions po : parts) {
            Holder<MobEffect> h = null;
            if (po instanceof ColorParticleOption cp) {
                int c = rgb(cp);
                h = byColor.get(c);
                if (h == null) {
                    // Farben können minimal abweichen
                    for (var e : byColor.entrySet()) if (close(e.getKey(), c)) { h = e.getValue(); break; }
                }
            } else if (po != null) {
                h = byType.get(po.getType());
            }
            if (h != null && seen.add(h.value().getDescriptionId())) out.add(eff(h, -1, -2));
        }
        return out;
    }

    private static boolean close(int a, int b) {
        return Math.abs(((a >> 16) & 255) - ((b >> 16) & 255)) <= 3 && Math.abs(((a >> 8) & 255) - ((b >> 8) & 255)) <= 3
                && Math.abs((a & 255) - (b & 255)) <= 3;
    }

    public static String text(Eff e) {
        String lvl = e.amp() >= 1 ? " " + roman(e.amp()) : "";
        String time = e.ticks() == -1 ? " ∞" : e.ticks() >= 0 ? " " + (e.ticks() / 20 / 60) + ":" + String.format("%02d", (e.ticks() / 20) % 60) : "";
        return e.name() + lvl + time;
    }

    @Override
    public void onHud(GuiGraphicsExtractor g, float partial) {
        if (!inGame() || effects.isEmpty()) return;
        int y = g.guiHeight() / 2 - 40;
        for (Player p : mc.level.players()) {
            List<Eff> l = effects.get(p.getUUID());
            if (l == null) continue;
            if (overHead.on() && p != mc.player) {
                Vec3 pos = p.getPosition(partial).add(0, p.getBbHeight() + 0.9, 0);
                double[] s = Projector.project(pos.x, pos.y, pos.z);
                if (s != null) {
                    int ly = (int) s[1] - l.size() * 9;
                    for (Eff e : l) {
                        String t = text(e);
                        int w = mc.font.width(t);
                        g.fill((int) s[0] - w / 2 - 2, ly - 1, (int) s[0] + w / 2 + 2, ly + 8, 0x90000000);
                        g.text(mc.font, t, (int) s[0] - w / 2, ly, e.color(), true);
                        ly += 9;
                    }
                }
            }
            if (list.on()) {
                String head = "§f" + p.getGameProfile().name() + "§7:";
                g.text(mc.font, head, g.guiWidth() - mc.font.width(head) - 4, y, 0xFFFFFFFF, true);
                y += 10;
                for (Eff e : l) {
                    String t = text(e);
                    g.text(mc.font, t, g.guiWidth() - mc.font.width(t) - 8, y, e.color(), true);
                    y += 9;
                }
                y += 3;
            }
        }
    }

    @Override
    public String info() {
        int n = 0;
        for (List<Eff> l : effects.values()) n += l.size();
        return n > 0 ? String.valueOf(n) : "";
    }
}
