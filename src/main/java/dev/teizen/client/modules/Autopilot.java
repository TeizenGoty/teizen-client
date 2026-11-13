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
    private final Setting tempo = add(Setting.number("tempo", "Tempo bei elytra/fly (Blöcke pro Tick, 1 = 20 Blöcke/s). Zu schnell = der Server setzt dich zurück", 1.5, 0.3, 4));
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
        if (!inGame()) return;
        if (!has && !resolve()) { setEnabled(false); return; }
        Chat.ok("Autopilot → §f" + targetName + " §7(" + Math.round(dist()) + " Blöcke, " + art.str() + ")");
    }

    @Override
    protected void onDisable() {
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
        if (blocked(dx, dz)) { vy = 0.8; sp = Math.min(sp, 0.3); }
        vel = new Vec3(dx * sp, vy, dz * sp);
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
        p.setYRot(yaw);
        double y = p.getY(), h = hoehe.num();
        boolean wall = blocked(dx, dz);
        float pitch = wall ? -45 : y < h - 12 ? -28 : y > h + 20 ? 12 : -3;
        p.setXRot(pitch);
        double speed = p.getDeltaMovement().length();
        boolean need = wall || y < h - 12 || speed < 1.0;
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
        p.setYRot(yaw);
        double sp = Math.min(tempo.num(), dist / 8 + 0.25);
        double vy = Math.max(-1, Math.min(1, (hoehe.num() - p.getY()) * 0.15));
        if (blocked(dx, dz)) { vy = 0.8; sp = Math.min(sp, 0.3); }
        p.setDeltaMovement(dx * sp, vy, dz * sp);
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
        g.text(mc.font, t, g.guiWidth() / 2 - mc.font.width(t) / 2, 16, 0xFFC4B5FD, true);
    }

    @Override
    public String info() { return has ? Math.round(dist()) + " m" : art.str(); }
}
