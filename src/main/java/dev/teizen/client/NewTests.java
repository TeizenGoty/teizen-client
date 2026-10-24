package dev.teizen.client;

import dev.teizen.client.core.Commands;
import dev.teizen.client.core.Module;
import dev.teizen.client.gui.ClickGui;
import dev.teizen.client.gui.Overview;
import dev.teizen.client.modules.AimAssist;
import dev.teizen.client.modules.ArmorESP;
import dev.teizen.client.modules.AutoArmor;
import dev.teizen.client.modules.AutoCrystal;
import dev.teizen.client.modules.AutoFish;
import dev.teizen.client.modules.AutoReplant;
import dev.teizen.client.modules.AutoShield;
import dev.teizen.client.modules.Freecam;
import dev.teizen.client.modules.ItemESP;
import dev.teizen.client.modules.Nametags;
import dev.teizen.client.modules.PortalFinder;
import dev.teizen.client.modules.ShulkerESP;
import dev.teizen.client.modules.StructureFinder;
import dev.teizen.client.modules.XRay;
import dev.teizen.client.util.Inv;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;

/**
 * Selbsttest Runde 3: prüft die neuen Funktionen auf dem echten Server.
 * Jede Prüfung baut sich eine eigene freie Fläche ("Arena") und schreibt ein Ergebnis mit OK/FEHLER.
 */
final class NewTests {
    private static final Minecraft mc = Minecraft.getInstance();

    private static BlockPos area;
    private static Vec3 p0;
    private static double d1, d2, a0;
    private static int n0, kb0;
    private static boolean flag1, flag2;

    private NewTests() {}

    // ------------------------------------------------------------------ Helfer

    private static void cmd(String c) { SelfTest.cmd2(c); }
    private static boolean after(int t) { return SelfTest.after2(t); }
    private static void next() { SelfTest.next2(); }
    private static void result(String r) { SelfTest.result(r); }
    private static void log(String s) { SelfTest.log2(s); }
    private static void shot(String n) { SelfTest.shot2(n); }
    private static void t(String command) { Commands.handle(command); }

    private static String ok(boolean b) { return b ? "OK" : "FEHLER"; }
    private static String yes(boolean b) { return b ? "ja" : "nein"; }
    private static String f(double d) { return String.format(Locale.ROOT, "%.2f", d); }

    @SuppressWarnings("unchecked")
    private static <T extends Module> T mod(Class<T> c) { return TeizenClient.modules().get(c); }

    private static String xyz(BlockPos p, double dx, double dy, double dz) {
        return String.format(Locale.ROOT, "%.2f %.2f %.2f", p.getX() + dx, p.getY() + dy, p.getZ() + dz);
    }

    private static String blk(BlockPos p) { return p.getX() + " " + p.getY() + " " + p.getZ(); }

    private static void tp(BlockPos p, double dx, double dy, double dz, float yaw, float pitch) {
        cmd(String.format(Locale.ROOT, "tp @s %s %.1f %.1f", xyz(p, dx, dy, dz), yaw, pitch));
    }

    /** Freie Fläche: Steinboden, darüber Luft. */
    private static void arena(BlockPos c, int r, int h) {
        cmd("fill " + blk(c.offset(-r, -1, -r)) + " " + blk(c.offset(r, -1, r)) + " minecraft:stone");
        cmd("fill " + blk(c.offset(-r, 0, -r)) + " " + blk(c.offset(r, h, r)) + " minecraft:air");
    }

    private static List<LivingEntity> husks(double r) {
        return mc.level.getEntitiesOfClass(LivingEntity.class, new AABB(area).inflate(r),
                e -> e.getType() == EntityType.HUSK && e.isAlive() && !e.isDeadOrDying());
    }

    private static void summonHusk(BlockPos p, String extra) {
        cmd("summon minecraft:husk " + xyz(p, 0.5, 0, 0.5) + " {NoAI:1b,PersistenceRequired:1b,Silent:1b" + extra + "}");
    }

    private static double horiz(Vec3 a, Vec3 b) {
        double dx = a.x - b.x, dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static void keys(boolean up, boolean use) {
        mc.options.keyUp.setDown(up);
        mc.options.keyUse.setDown(use);
    }

    // ------------------------------------------------------------------ Ablauf

    static void tick(int step) {
        BlockPos o = SelfTest.origin();
        switch (step) {
            // ---------------------------------------------- God
            case 29 -> {
                cmd("time set day");
                cmd("clear @s");
                t("///set god herzen 20");
                t("///god");
                next();
            }
            case 30 -> {
                if (!after(80)) return;
                var p = mc.player;
                boolean res = p.hasEffect(MobEffects.RESISTANCE), fire = p.hasEffect(MobEffects.FIRE_RESISTANCE);
                float hearts = p.getMaxHealth() / 2f;
                result("God an: Resistenz=" + yes(res) + ", Feuerschutz=" + yes(fire) + ", Herzen=" + f(hearts) + " (soll 20) "
                        + ok(res && fire && Math.abs(hearts - 20) < 0.1));
                next();
            }
            // ---------------------------------------------- AutoCrystal mit Kristallen
            case 31 -> {
                area = o.offset(0, 0, 24);
                tp(area, 0.5, 1, 0.5, -90, 0);
                next();
            }
            case 32 -> {
                if (!after(20)) return;
                arena(area, 7, 6);
                BlockPos tgt = area.offset(4, 0, 0);
                cmd("fill " + blk(tgt.offset(-2, -1, -2)) + " " + blk(tgt.offset(2, -1, 2)) + " minecraft:obsidian");
                summonHusk(tgt, "");
                cmd("give @s minecraft:end_crystal 16");
                tp(area, 0.5, 0, 0.5, -90, 20);
                t("///set autocrystal monster an");
                t("///set autocrystal spieler aus");
                next();
            }
            case 33 -> {
                if (!after(40)) return;
                n0 = husks(10).size();
                log("Husks vor AutoCrystal: " + n0);
                t("///autocrystal");
                next();
            }
            case 34 -> {
                if (SelfTest.local() == 25) shot("12-autocrystal");
                if (!after(200) && !husks(10).isEmpty()) return;
                AutoCrystal ac = mod(AutoCrystal.class);
                boolean dead = husks(10).isEmpty();
                result("AutoCrystal (Kristalle): Husk tot=" + yes(dead) + " (vorher " + n0 + ", gesetzt " + ac.placed + ", zerschlagen "
                        + ac.broken + ", " + SelfTest.local() + " Ticks) " + ok(dead && n0 == 1 && ac.placed > 0));
                t("///autocrystal");
                next();
            }
            // ---------------------------------------------- AutoCrystal mit Respawn-Ankern
            case 35 -> {
                area = o.offset(0, 0, 46);
                tp(area, 0.5, 1, 0.5, -90, 0);
                next();
            }
            case 36 -> {
                if (!after(20)) return;
                arena(area, 7, 6);
                summonHusk(area.offset(3, 0, 0), "");
                cmd("clear @s minecraft:end_crystal");
                cmd("give @s minecraft:respawn_anchor 4");
                cmd("give @s minecraft:glowstone 16");
                tp(area, 0.5, 0, 0.5, -90, 20);
                next();
            }
            case 37 -> {
                if (!after(40)) return;
                n0 = husks(10).size();
                mod(AutoCrystal.class).anchors = 0;
                t("///autocrystal");
                next();
            }
            case 38 -> {
                if (!after(240) && !husks(10).isEmpty()) return;
                AutoCrystal ac = mod(AutoCrystal.class);
                boolean dead = husks(10).isEmpty();
                result("AutoCrystal (Respawn-Anker): Husk tot=" + yes(dead) + " (vorher " + n0 + ", Anker gezündet " + ac.anchors + ", "
                        + SelfTest.local() + " Ticks) " + ok(dead && n0 == 1));
                t("///autocrystal");
                next();
            }
            // ---------------------------------------------- Velocity
            case 39 -> {
                area = o.offset(24, 0, 24);
                tp(area, 0.5, 1, 0.5, 180, 0);
                next();
            }
            case 40 -> {
                if (!after(20)) return;
                arena(area, 7, 6);
                summonHusk(area.offset(2, 0, 0), "");
                tp(area, 0.5, 0, 0.5, 180, 0);
                t("///velocity 0 0");
                next();
            }
            case 41 -> {
                if (!after(40)) return;
                p0 = mc.player.position();
                kb0 = SelfTest.knockbacks;
                cmd("damage @s 1 minecraft:mob_attack by @e[type=minecraft:husk,limit=1,sort=nearest]");
                next();
            }
            case 42 -> {
                if (!after(25)) return;
                d1 = horiz(p0, mc.player.position());
                n0 = SelfTest.knockbacks - kb0;
                t("///velocity"); // aus
                tp(area, 0.5, 0, 0.5, 180, 0);
                next();
            }
            case 43 -> {
                if (!after(30)) return;
                p0 = mc.player.position();
                cmd("damage @s 1 minecraft:mob_attack by @e[type=minecraft:husk,limit=1,sort=nearest]");
                next();
            }
            case 44 -> {
                if (!after(25)) return;
                d2 = horiz(p0, mc.player.position());
                result("Velocity: mit 0% weggeschoben " + f(d1) + " Blöcke, ohne Velocity " + f(d2) + " Blöcke (Pakete bearbeitet: " + n0 + ") "
                        + ok(d1 < 0.15 && d2 > 0.3));
                tp(area, 0.5, 0, 0.5, 180, 0);
                next();
            }
            // ---------------------------------------------- AimAssist
            case 45 -> {
                if (!after(20)) return;
                t("///set aimassist sichtfeld 360");
                t("///set aimassist staerke 10");
                t("///set aimassist monster an");
                next();
            }
            case 46 -> {
                if (!after(10)) return;
                List<LivingEntity> h = husks(6);
                a0 = h.isEmpty() ? -1 : AimAssist.angleTo(h.get(0).position().add(0, h.get(0).getBbHeight() * 0.7, 0));
                t("///aimassist");
                next();
            }
            case 47 -> {
                if (!after(40)) return;
                List<LivingEntity> h = husks(6);
                double a1 = h.isEmpty() ? -1 : AimAssist.angleTo(h.get(0).position().add(0, h.get(0).getBbHeight() * 0.7, 0));
                result("AimAssist: Winkel zum Gegner vorher " + Math.round(a0) + "°, nachher " + Math.round(a1) + "° (soll unter 10) "
                        + ok(a0 > 45 && a1 >= 0 && a1 < 10));
                t("///aimassist");
                next();
            }
            // ---------------------------------------------- AutoShield
            case 48 -> {
                cmd("give @s minecraft:shield");
                t("///set autoshield modus gegner");
                t("///set autoshield reichweite 4");
                next();
            }
            case 49 -> {
                if (!after(20)) return;
                t("///autoshield");
                next();
            }
            case 50 -> {
                if (!after(40)) return;
                AutoShield as = mod(AutoShield.class);
                boolean left = AutoShield.isShield(mc.player.getOffhandItem());
                shot("13-autoshield");
                result("AutoShield: Schild links=" + yes(left) + ", blockt bei Gegner=" + yes(as.blockedOnce) + " " + ok(left && as.blockedOnce));
                t("///autoshield");
                cmd("kill @e[type=minecraft:husk]");
                next();
            }
            // ---------------------------------------------- AutoArmor
            case 51 -> {
                cmd("give @s minecraft:iron_helmet");
                cmd("give @s minecraft:iron_chestplate");
                cmd("give @s minecraft:iron_leggings");
                cmd("give @s minecraft:iron_boots");
                next();
            }
            case 52 -> {
                if (!after(20)) return;
                t("///autoarmor");
                next();
            }
            case 53 -> {
                if (!after(40)) return;
                n0 = 0;
                if (mc.player.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET)) n0++;
                if (mc.player.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE)) n0++;
                if (mc.player.getItemBySlot(EquipmentSlot.LEGS).is(Items.IRON_LEGGINGS)) n0++;
                if (mc.player.getItemBySlot(EquipmentSlot.FEET).is(Items.IRON_BOOTS)) n0++;
                cmd("give @s minecraft:diamond_chestplate");
                next();
            }
            case 54 -> {
                if (!after(40)) return;
                boolean dia = mc.player.getItemBySlot(EquipmentSlot.CHEST).is(Items.DIAMOND_CHESTPLATE);
                result("AutoArmor: Eisen angezogen " + n0 + "/4, besserer Diamant-Brustpanzer getauscht=" + yes(dia) + " " + ok(n0 == 4 && dia));
                t("///autoarmor");
                next();
            }
            // ---------------------------------------------- NoSlowdown
            case 55 -> {
                area = o.offset(24, 0, -24);
                tp(area, 0.5, 1, 0.5, -90, 0);
                next();
            }
            case 56 -> {
                if (!after(20)) return;
                arena(area, 7, 5);
                cmd("give @s minecraft:shield");
                tp(area, -4.5, 0, 0.5, -90, 0);
                next();
            }
            case 57 -> {
                if (!after(20)) return;
                Inv.ensureInHand(AutoShield::isShield);
                keys(true, true);
                p0 = mc.player.position();
                next();
            }
            case 58 -> {
                if (!after(30)) return;
                flag1 = mc.player.isUsingItem();
                d1 = mc.player.getX() - p0.x;
                keys(false, false);
                tp(area, -4.5, 0, 0.5, -90, 0);
                next();
            }
            case 59 -> {
                if (!after(20)) return;
                t("///noslowdown");
                keys(true, true);
                p0 = mc.player.position();
                next();
            }
            case 60 -> {
                if (!after(30)) return;
                flag2 = mc.player.isUsingItem();
                d2 = mc.player.getX() - p0.x;
                keys(false, false);
                t("///noslowdown");
                result("NoSlowdown: Laufen mit erhobenem Schild ohne " + f(d1) + " Blöcke, mit NoSlowdown " + f(d2) + " Blöcke (Schild oben: "
                        + yes(flag1) + "/" + yes(flag2) + ") " + ok(flag1 && flag2 && d2 > 2.5 && d2 > d1 * 2));
                next();
            }
            // ---------------------------------------------- SafeWalk
            case 61 -> {
                if (!after(10)) return;
                cmd("fill " + blk(area.offset(2, -1, -7)) + " " + blk(area.offset(7, -6, 7)) + " minecraft:air");
                tp(area, -0.5, 0, 0.5, -90, 0);
                t("///safewalk");
                next();
            }
            case 62 -> {
                if (!after(20)) return;
                keys(true, false);
                next();
            }
            case 63 -> {
                if (!after(40)) return;
                keys(false, false);
                double x = mc.player.getX() - area.getX(), y = mc.player.getY() - area.getY();
                boolean onTop = y > -0.3;
                result("SafeWalk: an der Kante stehen geblieben=" + yes(onTop) + " (x=" + f(x) + ", y=" + f(y) + ") " + ok(onTop && x > 1.5));
                t("///safewalk");
                next();
            }
            // ---------------------------------------------- DolphinMode
            case 64 -> {
                area = o.offset(-24, 0, 0);
                tp(area, 0.5, 1, 0.5, -90, 0);
                next();
            }
            case 65 -> {
                if (!after(20)) return;
                arena(area, 7, 5);
                cmd("fill " + blk(area.offset(-3, -6, -3)) + " " + blk(area.offset(3, -1, 3)) + " minecraft:stone");
                cmd("fill " + blk(area.offset(-2, -5, -2)) + " " + blk(area.offset(2, -1, 2)) + " minecraft:water");
                tp(area, 0.5, -5, 0.5, -90, 0);
                next();
            }
            case 66 -> {
                if (!after(30)) return;
                d1 = mc.player.getEyeY() - area.getY();
                t("///dolphinmode");
                next();
            }
            case 67 -> {
                if (!after(80)) return;
                d2 = mc.player.getEyeY() - area.getY();
                result("DolphinMode: Augenhöhe zur Wasseroberfläche vorher " + f(d1) + ", nachher " + f(d2) + " (soll über -0.6) "
                        + ok(d1 < -1.5 && d2 > -0.6));
                t("///dolphinmode");
                next();
            }
            // ---------------------------------------------- AutoFish
            case 68 -> {
                cmd("give @s minecraft:fishing_rod[minecraft:enchantments={\"minecraft:lure\":5}]");
                cmd("give @s minecraft:fishing_rod");
                tp(area, -2.5, 0, 0.5, -90, 35);
                next();
            }
            case 69 -> {
                if (!after(30)) return;
                if (!Inv.ensureInHand(s -> s.getItem() instanceof FishingRodItem && s.isEnchanted()))
                    Inv.ensureInHand(s -> s.getItem() instanceof FishingRodItem);
                log("Angel in der Hand: " + mc.player.getMainHandItem() + " verzaubert=" + mc.player.getMainHandItem().isEnchanted());
                t("///autofish");
                next();
            }
            case 70 -> {
                AutoFish af = mod(AutoFish.class);
                if (SelfTest.local() % 100 == 0) log("AutoFish " + SelfTest.local() + ": Haken=" + (mc.player.fishing != null) + ", Fänge=" + af.catches);
                if (!after(900) && af.catches < 1) return;
                result("AutoFish: " + af.catches + " Fang/Fänge nach " + SelfTest.local() + " Ticks " + ok(af.catches >= 1));
                t("///autofish");
                next();
            }
            // ---------------------------------------------- AutoReplant
            case 71 -> {
                area = o.offset(-24, 0, -24);
                tp(area, 0.5, 1, 0.5, -90, 30);
                next();
            }
            case 72 -> {
                if (!after(20)) return;
                arena(area, 6, 4);
                cmd("fill " + blk(area.offset(1, -1, -1)) + " " + blk(area.offset(3, -1, 1)) + " minecraft:farmland");
                cmd("setblock " + blk(area.offset(2, -1, 0)) + " minecraft:water");
                for (int dx = 1; dx <= 3; dx++) for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 2 && dz == 0) continue;
                    cmd("setblock " + blk(area.offset(dx, 0, dz)) + " minecraft:wheat[age=7]");
                }
                cmd("give @s minecraft:wheat_seeds 16");
                tp(area, 0.5, 0, 0.5, -90, 30);
                next();
            }
            case 73 -> {
                if (!after(30)) return;
                n0 = wheat(true);
                t("///autoreplant");
                next();
            }
            case 74 -> {
                if (!after(100)) return;
                int young = wheat(false);
                AutoReplant ar = mod(AutoReplant.class);
                result("AutoReplant: reif vorher " + n0 + "/8, jetzt frisch gepflanzt " + young + "/8 (geerntet " + ar.harvested + ", gepflanzt "
                        + ar.planted + ") " + ok(n0 == 8 && young == 8));
                t("///autoreplant");
                next();
            }
            // ---------------------------------------------- Freecam + NoClip
            case 75 -> {
                area = o.offset(24, 0, 24);
                tp(area, 0.5, 0, 0.5, -90, 0);
                t("///set freecam reichweite 6");
                t("///set freecam tempo 0.5");
                next();
            }
            case 76 -> {
                if (!after(20)) return;
                p0 = mc.player.position();
                t("///freecam");
                next();
            }
            case 77 -> {
                if (!after(5)) return;
                keys(true, false);
                next();
            }
            case 78 -> {
                if (!after(40)) return;
                Freecam fc = mod(Freecam.class);
                double camD = fc.distance();
                double moved = p0.distanceTo(mc.player.position());
                boolean isCam = fc.cam != null && mc.getCameraEntity() == fc.cam;
                shot("14-freecam");
                result("Freecam: Kamera " + f(camD) + " Blöcke weg (Grenze 6), Spieler bewegt " + f(moved) + " (soll 0), Kamera aktiv=" + yes(isCam) + " "
                        + ok(isCam && camD > 3 && camD <= 6.2 && moved < 0.3));
                keys(false, false);
                next();
            }
            case 79 -> {
                if (!after(10)) return;
                t("///freecam");
                next();
            }
            case 80 -> {
                if (!after(5)) return;
                flag1 = mc.getCameraEntity() == mc.player;
                t("///noclip");
                next();
            }
            case 81 -> {
                if (!after(60)) return;
                flag2 = mc.gameMode.getPlayerMode() == GameType.SPECTATOR;
                t("///noclip");
                next();
            }
            case 82 -> {
                if (!after(60)) return;
                boolean surv = mc.gameMode.getPlayerMode() == GameType.SURVIVAL;
                result("Freecam aus: Kamera wieder beim Spieler=" + yes(flag1) + " " + ok(flag1));
                result("NoClip: Zuschauer-Modus=" + yes(flag2) + ", danach wieder Überleben=" + yes(surv) + " " + ok(flag2 && surv));
                next();
            }
            // ---------------------------------------------- Sicht: Portale, Items, Shulker, Namen, Rüstung
            case 83 -> {
                area = o.offset(-24, 0, 24);
                tp(area, 0.5, 1, 0.5, -90, 0);
                next();
            }
            case 84 -> {
                if (!after(20)) return;
                arena(area, 8, 7);
                BlockPos np = area.offset(5, 0, -4);
                cmd("fill " + blk(np) + " " + blk(np.offset(3, 4, 0)) + " minecraft:obsidian");
                cmd("fill " + blk(np.offset(1, 1, 0)) + " " + blk(np.offset(2, 3, 0)) + " minecraft:air");
                cmd("setblock " + blk(np.offset(1, 1, 0)) + " minecraft:fire");
                cmd("setblock " + blk(area.offset(5, 0, 3)) + " minecraft:end_portal_frame[facing=north,eye=true]");
                cmd("setblock " + blk(area.offset(6, 0, 3)) + " minecraft:end_portal_frame[facing=north,eye=false]");
                cmd("setblock " + blk(area.offset(7, 0, 0)) + " minecraft:purple_shulker_box");
                cmd("summon minecraft:item " + xyz(area.offset(3, 0, 1), 0.5, 0, 0.5) + " {Item:{id:\"minecraft:diamond\",count:3},PickupDelay:32767}");
                cmd("summon minecraft:item " + xyz(area.offset(3, 0, -1), 0.5, 0, 0.5) + " {Item:{id:\"minecraft:dirt\",count:5},PickupDelay:32767}");
                cmd("summon minecraft:item " + xyz(area.offset(4, 0, 0), 0.5, 0, 0.5) + " {Item:{id:\"minecraft:shulker_box\",count:1},PickupDelay:32767}");
                cmd("summon minecraft:armor_stand " + xyz(area.offset(2, 0, -2), 0.5, 0, 0.5));
                summonHusk(area.offset(2, 0, 2), ",CustomName:\"Testgegner\",CustomNameVisible:1b");
                tp(area, -3.5, 0, 0.5, -90, 10);
                next();
            }
            case 85 -> {
                if (!after(20)) return;
                cmd("item replace entity @e[type=minecraft:armor_stand,limit=1,sort=nearest] armor.head with minecraft:diamond_helmet");
                cmd("item replace entity @e[type=minecraft:armor_stand,limit=1,sort=nearest] armor.chest with minecraft:iron_chestplate");
                cmd("item replace entity @e[type=minecraft:armor_stand,limit=1,sort=nearest] weapon.mainhand with minecraft:diamond_sword");
                t("///set nametags mobs an");
                t("///set armoresp mobs an");
                t("///portalfinder");
                t("///itemesp");
                t("///shulkeresp");
                t("///nametags");
                t("///armoresp");
                next();
            }
            case 86 -> {
                if (!after(80)) return;
                shot("15-sicht");
                PortalFinder pf = mod(PortalFinder.class);
                boolean nether = false, frames = false;
                StringBuilder sb = new StringBuilder();
                for (PortalFinder.Portal p : pf.portals) {
                    sb.append(p.label()).append("; ");
                    if (p.label().startsWith("Netherportal")) nether = true;
                    if (p.label().startsWith("Endportal-Rahmen")) frames = true;
                }
                result("PortalFinder: " + pf.portals.size() + " Portale (" + sb + ") " + ok(nether && frames));
                ItemESP ie = mod(ItemESP.class);
                result("ItemESP: " + ie.total + " Items, hervorgehoben " + ie.highlighted + " (Diamant + Shulker) " + ok(ie.total >= 3 && ie.highlighted >= 2));
                ShulkerESP se = mod(ShulkerESP.class);
                result("ShulkerESP: aufgestellt " + se.blockCount + ", am Boden " + se.itemCount + " " + ok(se.blockCount >= 1 && se.itemCount >= 1));
                Nametags nt = mod(Nametags.class);
                result("Nametags: " + nt.drawn + " Namensschild(er) gezeichnet " + ok(nt.drawn >= 1));
                ArmorESP ae = mod(ArmorESP.class);
                result("ArmorESP: " + ae.drawn + " mit Rüstung gezeichnet " + ok(ae.drawn >= 1));
                next();
            }
            case 87 -> {
                PortalFinder pf = mod(PortalFinder.class);
                if (!after(600) && pf.located.isEmpty()) return;
                result("PortalFinder Festung (per /locate): " + (pf.located.isEmpty() ? "nicht gefunden FEHLER"
                        : "gefunden bei " + pf.located.get(0).pos().getX() + " " + pf.located.get(0).pos().getZ() + " OK"));
                t("///portalfinder");
                t("///itemesp");
                t("///shulkeresp");
                t("///nametags");
                t("///armoresp");
                cmd("kill @e[type=minecraft:husk]");
                next();
            }
            // ---------------------------------------------- XRay: nur bestimmte Erze, Lava, Wasser, Höhlen
            case 88 -> {
                cmd("setblock " + blk(area.offset(0, -10, 0)) + " minecraft:lava");
                cmd("fill " + blk(area.offset(3, -16, -2)) + " " + blk(area.offset(7, -13, 2)) + " minecraft:air");
                t("///xray nur diamond");
                t("///xray lava an");
                t("///xray wasser an");
                t("///xray hoehlen an");
                next();
            }
            case 89 -> {
                if (!after(140)) return;
                XRay x = mod(XRay.class);
                boolean only = x.ores.str().equals("diamond") && x.isEnabled();
                shot("16-xray-extra");
                result("XRay extra: nur diamond=" + yes(only) + ", Lava " + x.lavaCount() + ", Wasser " + x.waterCount() + ", Höhlen-Punkte "
                        + x.caveCount() + " " + ok(only && x.lavaCount() > 0 && x.caveCount() > 0));
                t("///xray alle");
                t("///xray lava aus");
                t("///xray wasser aus");
                t("///xray hoehlen aus");
                t("///xray");
                next();
            }
            // ---------------------------------------------- StructureFinder (per /locate auf dem Server)
            case 90 -> {
                if (!after(10)) return;
                t("///structurefinder village");
                next();
            }
            case 91 -> {
                StructureFinder sf = mod(StructureFinder.class);
                if (!after(600) && sf.found.isEmpty()) return;
                if (!sf.found.isEmpty()) shot("17-structurefinder");
                result("StructureFinder: Dorf " + (sf.found.isEmpty() ? "nicht gefunden FEHLER" : "gefunden bei " + sf.found.get(0).pos().getX()
                        + " " + sf.found.get(0).pos().getZ() + " OK"));
                t("///structurefinder aus");
                next();
            }
            // ---------------------------------------------- Favoriten
            case 92 -> {
                t("///fav killaura");
                t("///fav xray");
                mc.gui.setScreen(new ClickGui());
                next();
            }
            case 93 -> {
                if (!after(20)) return;
                shot("18-favoriten-menue");
                var mm = TeizenClient.modules();
                flag1 = mm.favorites().size() == 2 && mm.favoritesFirst().get(0).name.equals("KillAura") && mm.favoritesFirst().get(1).name.equals("XRay");
                boolean saved = false;
                try {
                    saved = java.nio.file.Files.readString(mc.gameDirectory.toPath().resolve("config/teizenclient.json")).contains("\"favorit\": true");
                } catch (Throwable ignored) {}
                flag2 = saved;
                mc.gui.setScreen(new Overview());
                next();
            }
            case 94 -> {
                if (!after(20)) return;
                shot("19-favoriten-uebersicht");
                result("Favoriten: KillAura + XRay oben=" + yes(flag1) + ", gespeichert=" + yes(flag2) + " " + ok(flag1 && flag2));
                mc.gui.setScreen(null);
                next();
            }
            // ---------------------------------------------- God aus
            case 95 -> {
                t("///god");
                next();
            }
            case 96 -> {
                if (!after(80)) return;
                boolean res = mc.player.hasEffect(MobEffects.RESISTANCE);
                float hearts = mc.player.getMaxHealth() / 2f;
                result("God aus: Resistenz weg=" + yes(!res) + ", Herzen wieder " + f(hearts) + " " + ok(!res && Math.abs(hearts - 10) < 0.1));
                next();
            }
            // ---------------------------------------------- AutoReconnect (Rauswurf per /kick, danach von selbst wieder rein)
            case 97 -> {
                t("///set autoreconnect sekunden 2");
                t("///autoreconnect");
                next();
            }
            case 98 -> {
                if (!after(20)) return;
                String name = mc.player.getGameProfile().name();
                SelfTest.startReconnect();
                cmd("kick " + name + " Teizen-Selbsttest");
            }
            default -> {}
        }
    }

    /** Weizen auf den 8 Feldern zählen: reif (Alter 7) oder frisch (jünger). */
    private static int wheat(boolean mature) {
        int n = 0;
        for (int dx = 1; dx <= 3; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (dx == 2 && dz == 0) continue;
            BlockState s = mc.level.getBlockState(area.offset(dx, 0, dz));
            if (!s.is(Blocks.WHEAT)) continue;
            boolean max = ((CropBlock) s.getBlock()).isMaxAge(s);
            if (max == mature) n++;
        }
        return n;
    }
}
