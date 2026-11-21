package dev.teizen.client.modules;

import dev.teizen.client.TeizenClient;
import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.util.Chat;
import dev.teizen.client.util.Inv;
import dev.teizen.client.util.Waypoints;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Autopilot: fliegt von selbst zu einem Ziel (Koordinaten oder Wegpunkt von Xaero's Karte).
 * Art "elytra" = Elytra ohne Raketen, "raketen" = Elytra mit Raketen (echter Flug), "fly" = mit Fly.
 * Ist die Elytra fast kaputt, wird eine Ersatz-Elytra angezogen – gibt es keine, landet der Autopilot und geht aus.
 */
public final class Autopilot extends Module {
    private final Setting art = add(Setting.choice("art", "elytra = Elytra ohne Raketen · raketen = Elytra und verbraucht Raketen · fly = fliegen mit Fly", "elytra", "elytra", "raketen", "fly"));
    private final Setting ziel = add(Setting.text("ziel", "Ziel als \"X Z\" (z.B. 1200 -340). Leer lassen = Wegpunkt von Xaero's Karte nehmen", ""));
    private final Setting wegpunkt = add(Setting.dynamicChoice("wegpunkt", "Welchen Xaero-Wegpunkt anfliegen: in der Liste darunter anklicken (auch temporäre). \"neuester\" = der oberste. Gilt nur, wenn \"ziel\" leer ist.",
            "neuester", dev.teizen.client.util.Waypoints::names));
    private final Setting tempo = add(Setting.number("tempo", "Geschwindigkeit in Blöcken pro Tick (1 = 20 Blöcke/s) – gilt für alle Arten, bei raketen wird nachgezündet, wenn es langsamer wird. Zu schnell = der Server setzt dich zurück", 1.5, 0.1, 6));
    private final Setting umweg = add(Setting.number("umweg", "Wie weit er bei einem Hindernis zur Seite ausweicht (Blöcke), bevor er es wieder direkt versucht", 10, 3, 40));
    private final Setting abbauen = add(Setting.bool("abbauen", "Nur wenn es GAR NICHT weiter geht (nicht drüber, kein Umweg, nicht zurück raus): den Block im Weg abbauen", true));
    private final Setting hoehe = add(Setting.number("hoehe", "Flughöhe (Y)", 180, -60, 400));
    private final Setting minHalt = add(Setting.number("minhaltbarkeit", "Elytra unter so viel Prozent: tauschen oder landen", 5, 1, 50));
    private final Setting landen = add(Setting.bool("landen", "Am Ziel von selbst landen", true));
    @SuppressWarnings("unused")
    private final Setting liste = add(Setting.action("wegpunkte", "Zeigt die gefundenen Xaero-Wegpunkte im Chat", "Wegpunkte anzeigen", Autopilot::listWaypoints));

    private double tx, tz;
    private boolean has, landing, flyByUs, noRockets, tookOff;
    private String targetName = "";
    private int rocketCd, spin;
    private Vec3 vel = Vec3.ZERO;
    /** Für den Selbsttest. */
    public int rockets;
    public boolean arrived;

    public Autopilot() {
        super("Autopilot", Category.BEWEGUNG, "Fliegt von selbst zu Koordinaten oder einem Xaero-Wegpunkt (Elytra, Elytra + Raketen oder Fly).");
    }

    private static void listWaypoints() {
        var l = Waypoints.all();
        if (l.isEmpty()) { Chat.error("Keine Xaero-Wegpunkte gefunden. Erstell einen auf der Karte (Rechtsklick). §8[" + Waypoints.source + "]"); return; }
        Chat.info("Xaero-Wegpunkte (" + l.size() + ") §8[" + Waypoints.source + "]§7:");
        int n = 0;
        for (var w : l) { Chat.raw("§7- §f" + w.name() + " §7(" + w.x() + ", " + w.z() + ")"); if (++n >= 15) break; }
    }

    /** Ziel direkt setzen (Befehl / Selbsttest). */
    public void target(double x, double z, String name) {
        tx = x; tz = z; has = true; targetName = name;
    }

    private boolean resolve() {
        String z = ziel.str().trim();
        if (!z.isEmpty()) {
            String[] a = z.replace(",", " ").trim().split("\\s+");
            try {
                double x = Double.parseDouble(a[0]), zz = Double.parseDouble(a[a.length >= 3 ? 2 : 1]);
                target(x, zz, Math.round(x) + " " + Math.round(zz));
                return true;
            } catch (RuntimeException e) {
                Chat.error("Autopilot: Ziel bitte als \"X Z\" eintragen, z.B. 1200 -340.");
                return false;
            }
        }
        String wp = wegpunkt.str();
        if (wp == null || wp.equalsIgnoreCase("neuester")) wp = "";
        var w = Waypoints.find(wp);
        if (w == null) {
            Chat.error(wp.isBlank() ? "Autopilot: kein Ziel. Trag \"ziel\" ein oder erstell einen Wegpunkt auf Xaero's Karte."
                    : "Autopilot: Wegpunkt \"" + wp + "\" nicht gefunden.");
            return false;
        }
        target(w.x() + 0.5, w.z() + 0.5, w.name());
        return true;
    }

    @Override
    protected void onEnable() {
        landing = false; noRockets = false; tookOff = false; arrived = false; rocketCd = 0; spin = 0; vel = Vec3.ZERO; flyByUs = false;
        detTicks = 0; backTicks = 0; stuck = 0; around = ""; mined = 0; detours = 0; backTries = 0; bestDist = Double.MAX_VALUE; side = 0; digging = false;
        if (!inGame()) return;
        if (!has && !resolve()) { setEnabled(false); return; }
        Chat.ok("Autopilot → §f" + targetName + " §7(" + Math.round(dist()) + " Blöcke, " + art.str() + ")");
    }

    @Override
    protected void onDisable() {
        dev.teizen.client.util.Breaker.stop();
        has = false;
        vel = Vec3.ZERO;
        if (flyByUs && inGame()) {
            Fly fly = TeizenClient.modules().get(Fly.class);
            // nur am Boden ausmachen – in der Luft weiterfliegen lassen, damit du nicht abstürzt
            if (fly != null && fly.isEnabled() && mc.player.onGround()) fly.setEnabled(false);
        }
        flyByUs = false;
    }

    private double dist() {
        return Math.hypot(tx - mc.player.getX(), tz - mc.player.getZ());
    }

    private static int elytraPercent(ItemStack s) {
        if (!s.is(Items.ELYTRA) || s.getMaxDamage() <= 0) return 0;
        return 100 * (s.getMaxDamage() - s.getDamageValue()) / s.getMaxDamage();
    }

    /** Ersatz-Elytra anziehen. @return true wenn getauscht */
    private boolean swapElytra() {
        int best = -1, bp = (int) minHalt.num();
        for (int i = 0; i < 36; i++) {
            int pc = elytraPercent(Inv.inv().getItem(i));
            if (pc > bp) { bp = pc; best = i; }
        }
        if (best < 0 || mc.gui.screen() != null) return false;
        var menu = mc.player.inventoryMenu;
        int from = Inv.menuSlot(best);
        mc.gameMode.handleContainerInput(menu.containerId, from, 0, ContainerInput.PICKUP, mc.player);
        mc.gameMode.handleContainerInput(menu.containerId, 6, 0, ContainerInput.PICKUP, mc.player); // 6 = Brust
        if (!menu.getCarried().isEmpty()) mc.gameMode.handleContainerInput(menu.containerId, from, 0, ContainerInput.PICKUP, mc.player);
        Chat.ok("Autopilot: Ersatz-Elytra angezogen (" + bp + " %).");
        return true;
    }

    private void stop(String msg) {
        Chat.ok("Autopilot: " + msg);
        setEnabled(false);
    }

    // ------------------------------------------------------------ Hindernisse: drüber, Umweg, zurück, erst dann abbauen

    private double detX, detZ;
    private int detTicks, backTicks, stuck, mineMsg, backTries;
    private double bestDist = Double.MAX_VALUE;
    /** Was er gerade wegen eines Hindernisses macht (Anzeige). */
    private String around = "";
    /** Für den Selbsttest: wie oft abgebaut / ausgewichen. */
    public int mined, detours;

    /** Kurz voraus (1–3 Blöcke) etwas im Weg? */
    private boolean blockedNear(double dx, double dz) {
        for (double d : new double[]{0.9, 2.0, 3.0}) if (blockedAt(dx, dz, d)) return true;
        return false;
    }

    private boolean blockedAt(double dx, double dz, double d) {
        return !mc.level.noCollision(mc.player, mc.player.getBoundingBox().move(dx * d, 0, dz * d));
    }

    /** Kann er nach oben über das Hindernis? (frei bis 4 Blöcke hoch und dort vorne frei) */
    private boolean canRise(double dx, double dz) {
        var box = mc.player.getBoundingBox();
        for (int up = 1; up <= 4; up++) {
            if (!mc.level.noCollision(mc.player, box.move(0, up, 0))) return false;
            if (mc.level.noCollision(mc.player, box.move(dx * 1.5, up, dz * 1.5))) return true;
        }
        return false;
    }

    private static final int[] ANGLES = {35, 70, 100, 135};
    /** Seite für den Umweg: 1 = links, -1 = rechts, 0 = noch nicht gewählt. */
    private int side;
    private boolean digging;
    private net.minecraft.core.BlockPos lastMine;

    private static double[] rot(double dx, double dz, int deg) {
        double r = Math.toRadians(deg);
        return new double[]{dx * Math.cos(r) - dz * Math.sin(r), dx * Math.sin(r) + dz * Math.cos(r)};
    }

    /** Kleinster freier Winkel auf dieser Seite, oder -1. */
    private int freeAngle(double dx, double dz, int sgn) {
        for (int a : ANGLES) { double[] d = rot(dx, dz, a * sgn); if (!blockedNear(d[0], d[1])) return a; }
        return -1;
    }

    /**
     * Richtung bei Hindernissen. Reihenfolge: direkt · drüber · Umweg an der Mauer entlang (eine Seite) ·
     * zurück raus · erst dann abbauen.
     * @return {dx, dz, vy (NaN = normal), Tempo-Faktor}
     */
    private double[] steer(net.minecraft.client.player.LocalPlayer p, double dx, double dz, boolean mayMine) {
        if (lastMine != null && dev.teizen.client.util.MinePath.passable(mc.level, lastMine)) { mined++; lastMine = null; }
        double dist = Math.hypot(tx - p.getX(), tz - p.getZ());
        if (dist < bestDist - 3) { bestDist = dist; if (!digging) { stuck = 0; backTries = 0; side = 0; } }
        if (digging) {
            if (!blockedNear(dx, dz)) { digging = false; side = 0; backTries = 0; around = ""; return new double[]{dx, dz, Double.NaN, 1}; }
            if (blockedAt(dx, dz, 0.9)) { mineAhead(p, dx, dz); return new double[]{0, 0, 0, 0}; }
            around = "zur Wand";
            return new double[]{dx, dz, 0, 0.3}; // bis an den Block ran, dann abbauen
        }
        if (backTicks > 0) {
            backTicks--;
            around = "fliegt zurück raus";
            if (!blockedNear(-dx, -dz)) return new double[]{-dx, -dz, 0.3, 0.5};
            backTicks = 0;
        }
        if (detTicks > 0) {
            if (!blockedNear(dx, dz)) detTicks = 0; // direkt ist wieder frei
            else if (!blockedNear(detX, detZ)) { detTicks--; around = "Umweg"; return new double[]{detX, detZ, Double.NaN, 0.7}; }
            else detTicks = 0;
        }
        if (!blockedNear(dx, dz)) { around = ""; return new double[]{dx, dz, Double.NaN, 1}; }
        stuck++;
        if (canRise(dx, dz)) { around = "drüber"; return new double[]{dx * 0.15, dz * 0.15, 0.8, 0.3}; }
        // Umweg: einmal eine Seite wählen und an der Mauer entlang, bis es direkt wieder frei ist
        if (side == 0) {
            int l = freeAngle(dx, dz, 1), r = freeAngle(dx, dz, -1);
            side = l < 0 && r < 0 ? 0 : (r >= 0 && (l < 0 || r < l)) ? -1 : 1;
        }
        if (side != 0) {
            int a = freeAngle(dx, dz, side);
            if (a < 0) { side = -side; a = freeAngle(dx, dz, side); }
            if (a >= 0) {
                double[] d = rot(dx, dz, a * side);
                detX = d[0]; detZ = d[1];
                detTicks = (int) Math.min(400, umweg.num() / Math.max(0.1, tempo.num() * 0.7));
                detours++;
                around = "Umweg";
                return new double[]{detX, detZ, Double.NaN, 0.7};
            }
        }
        // zurück raus (höchstens 2x am selben Hindernis), vielleicht geht es von dort drüber oder vorbei
        if (backTries < 2 && !blockedNear(-dx, -dz)) { backTries++; backTicks = 25; around = "fliegt zurück raus"; return new double[]{-dx, -dz, 0.3, 0.5}; }
        // gar kein Weg: erst jetzt abbauen
        if (mayMine && abbauen.on()) { digging = true; return steer(p, dx, dz, true); }
        around = "steckt fest";
        if (mc.player.tickCount - mineMsg > 200) { mineMsg = mc.player.tickCount; Chat.error("Autopilot: kein Weg vorbei. Schalt \"abbauen\" an, dann baut er den Block im Weg ab."); }
        return new double[]{0, 0, 0, 0};
    }

    /** Den Block direkt vor ihm (Füße oder Kopf) abbauen. */
    private void mineAhead(net.minecraft.client.player.LocalPlayer p, double dx, double dz) {
        around = "baut ab";
        net.minecraft.core.BlockPos feet = net.minecraft.core.BlockPos.containing(p.getX() + dx * 0.9, p.getY() + 0.2, p.getZ() + dz * 0.9);
        for (net.minecraft.core.BlockPos b : new net.minecraft.core.BlockPos[]{feet, feet.above()}) {
            if (dev.teizen.client.util.MinePath.passable(mc.level, b)) continue;
            if (!dev.teizen.client.util.Breaker.canBreak(b)) { around = "steckt fest (Block nicht abbaubar)"; return; }
            dev.teizen.client.util.Breaker.tick(b, true, true);
            lastMine = b;
            return;
        }
    }

    /** Ist ein Stück voraus etwas im Weg? */
    private boolean blocked(double dx, double dz) {
        var box = mc.player.getBoundingBox();
        for (double d : new double[]{2, 5, 9})
            if (!mc.level.noCollision(mc.player, box.move(dx * d, 0, dz * d))) return true;
        return false;
    }

    @Override
    public void onTick() {
        if (!inGame()) return;
        if (!has) { if (!resolve()) { setEnabled(false); return; } }
        var p = mc.player;
        double ddx = tx - p.getX(), ddz = tz - p.getZ(), dist = Math.hypot(ddx, ddz);
        double dx = dist < 1e-3 ? 0 : ddx / dist, dz = dist < 1e-3 ? 0 : ddz / dist;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        if (rocketCd > 0) rocketCd--;
        if (dist < 4 && !landing) {
            arrived = true;
            if (!landen.on()) { stop("angekommen bei " + targetName + "."); return; }
            landing = true;
            Chat.ok("Autopilot: angekommen – lande.");
        }
        if (landing && p.onGround()) { stop("gelandet."); return; }

        if (art.is("fly")) { tickFly(p, dx, dz, dist, yaw); return; }

        // ---- Elytra
        ItemStack chest = p.getItemBySlot(EquipmentSlot.CHEST);
        if (!chest.is(Items.ELYTRA)) {
            if (!swapElytra()) stop("keine Elytra an. Zieh eine an oder nimm Art \"fly\".");
            return;
        }
        if (!landing && elytraPercent(chest) < minHalt.num()) {
            if (!swapElytra()) { landing = true; Chat.error("Autopilot: Elytra fast kaputt (" + elytraPercent(chest) + " %) und keine Ersatz-Elytra – lande."); }
            else return;
        }
        if (!p.isFallFlying()) {
            vel = Vec3.ZERO;
            if (p.onGround()) {
                if (tookOff && landing) { stop("gelandet."); return; }
                p.jumpFromGround();
            } else if (!p.isInWater() && !p.isInLava() && p.getDeltaMovement().y < 0.1) {
                p.connection.send(new net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket(
                        p, net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
            }
            return;
        }
        tookOff = true;
        if (art.is("raketen")) tickRockets(p, dx, dz, dist, yaw);
        else tickGlide(p, dx, dz, dist, yaw);
    }

    /** Elytra ohne Raketen: wir geben die Geschwindigkeit direkt vor (wie ElytraFly). */
    private void tickGlide(net.minecraft.client.player.LocalPlayer p, double dx, double dz, double dist, float yaw) {
        if (landing) { vel = new Vec3(0, -0.5, 0); return; }
        p.setYRot(yaw);
        double sp = Math.min(tempo.num(), dist / 8 + 0.25);
        double vy = Math.max(-0.8, Math.min(0.8, (hoehe.num() - p.getY()) * 0.15));
        double[] st = steer(p, dx, dz, true);
        if (!Double.isNaN(st[2])) vy = st[2];
        sp *= st[3];
        if (st[0] != dx || st[1] != dz) p.setYRot((float) Math.toDegrees(Math.atan2(-st[0], st[1])));
        vel = new Vec3(st[0] * sp, vy, st[1] * sp);
    }

    /** Elytra mit Raketen: echter Gleitflug, Blick steuert, Rakete wenn es zu langsam oder zu tief wird. */
    private void tickRockets(net.minecraft.client.player.LocalPlayer p, double dx, double dz, double dist, float yaw) {
        vel = null;
        if (landing) {
            // sanft im Kreis runter
            spin++;
            p.setYRot(p.getYRot() + 5);
            p.setXRot(12);
            return;
        }
        double y = p.getY(), h = hoehe.num();
        // bei Hindernis: drüber oder seitlich vorbei (mit Raketen wird nie abgebaut)
        double[] st = steer(p, dx, dz, false);
        p.setYRot(st[3] == 0 ? yaw : (float) Math.toDegrees(Math.atan2(-st[0], st[1])));
        boolean wall = blocked(dx, dz) && around.equals("drüber");
        float pitch = wall ? -45 : y < h - 12 ? -28 : y > h + 20 ? 12 : -3;
        p.setXRot(pitch);
        double speed = p.getDeltaMovement().length();
        boolean need = wall || y < h - 12 || speed < tempo.num();
        if (need && rocketCd == 0) {
            if (fireRocket(p)) { rocketCd = 28; rockets++; }
            else if (!noRockets) { noRockets = true; Chat.error("Autopilot: keine Raketen mehr – gleite weiter, so weit es geht."); }
        }
    }

    private boolean fireRocket(net.minecraft.client.player.LocalPlayer p) {
        if (mc.gui.screen() != null) return false;
        if (p.getOffhandItem().is(Items.FIREWORK_ROCKET)) { mc.gameMode.useItem(p, InteractionHand.OFF_HAND); return true; }
        int slot = Inv.findHotbar(s -> s.is(Items.FIREWORK_ROCKET));
        if (slot < 0) {
            int any = Inv.findAny(s -> s.is(Items.FIREWORK_ROCKET));
            if (any < 0) return false;
            Inv.swapToHotbar(any, Inv.selected());
            slot = Inv.selected();
        }
        Inv.select(slot);
        mc.gameMode.useItem(p, InteractionHand.MAIN_HAND);
        noRockets = false;
        return true;
    }

    private void tickFly(net.minecraft.client.player.LocalPlayer p, double dx, double dz, double dist, float yaw) {
        Fly fly = TeizenClient.modules().get(Fly.class);
        if (fly != null && !fly.isEnabled()) { fly.setEnabled(true); flyByUs = true; }
        p.getAbilities().mayfly = true;
        p.getAbilities().flying = true;
        if (landing) { p.setDeltaMovement(0, -0.5, 0); return; }
        double sp = Math.min(tempo.num(), dist / 8 + 0.25);
        double vy = Math.max(-1, Math.min(1, (hoehe.num() - p.getY()) * 0.15));
        double[] st = steer(p, dx, dz, true);
        if (!Double.isNaN(st[2])) vy = st[2];
        sp *= st[3];
        p.setYRot(st[3] == 0 ? yaw : (float) Math.toDegrees(Math.atan2(-st[0], st[1])));
        p.setDeltaMovement(st[0] * sp, vy, st[1] * sp);
    }

    /** Mixin (Elytra-Physik): unsere Geschwindigkeit, oder null = normale Physik. */
    public static Vec3 velocity(LivingEntity e) {
        if (TeizenClient.modules() == null || e != mc.player) return null;
        Autopilot a = TeizenClient.modules().get(Autopilot.class);
        if (a == null || !a.isEnabled() || !a.has || a.art.is("raketen") || a.art.is("fly")) return null;
        return a.vel;
    }

    @Override
    public void onHud(GuiGraphicsExtractor g, float partial) {
        if (!inGame() || !has) return;
        String t = "Autopilot → " + targetName + "  " + Math.round(dist()) + " m";
        ItemStack chest = mc.player.getItemBySlot(EquipmentSlot.CHEST);
        if (!art.is("fly") && chest.is(Items.ELYTRA)) t += "  ·  Elytra " + elytraPercent(chest) + " %";
        if (landing) t += "  ·  landet";
        else if (!around.isEmpty()) t += "  ·  " + around;
        g.text(mc.font, t, g.guiWidth() / 2 - mc.font.width(t) / 2, 16, 0xFFC4B5FD, true);
    }

    @Override
    public String info() { return has ? Math.round(dist()) + " m" : art.str(); }
}
