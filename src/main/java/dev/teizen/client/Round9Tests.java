package dev.teizen.client;

import dev.teizen.client.core.Commands;
import dev.teizen.client.core.Module;
import dev.teizen.client.modules.*;
import dev.teizen.client.util.Inv;
import dev.teizen.client.util.WeaponSwap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.StringDecomposer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;

/** Selbsttest Runde 9: die 19 neuen Funktionen (Waffenwechsel, Boot, Elytra, ESPs, Zeit/Wetter, Inventar, Name/Skin). */
final class Round9Tests {
    private static final Minecraft mc = Minecraft.getInstance();

    private static BlockPos area;
    private static double y0, y1, hp0;
    private static Vec3 p0;
    private static boolean flag1, flag2;
    private static int n0;
    private static String text = "";

    private Round9Tests() {}

    private static void cmd(String c) { SelfTest.cmd2(c); }
    private static boolean after(int t) { return SelfTest.after2(t); }
    private static void next() { SelfTest.next2(); }
    private static void result(String r) { SelfTest.result(r); }
    private static void log(String s) { SelfTest.log2(s); }
    private static void shot(String n) { SelfTest.shot2(n); }
    private static void t(String c) { Commands.handle(c); }
    private static String ok(boolean b) { return b ? "OK" : "FEHLER"; }
    private static String yes(boolean b) { return b ? "ja" : "nein"; }
    private static String f(double d) { return String.format(Locale.ROOT, "%.2f", d); }

    private static <T extends Module> T mod(Class<T> c) { return TeizenClient.modules().get(c); }

    private static String xyz(BlockPos p, double dx, double dy, double dz) {
        return String.format(Locale.ROOT, "%.2f %.2f %.2f", p.getX() + dx, p.getY() + dy, p.getZ() + dz);
    }

    private static String blk(BlockPos p) { return p.getX() + " " + p.getY() + " " + p.getZ(); }

    private static void tp(BlockPos p, double dx, double dy, double dz, float yaw, float pitch) {
        cmd(String.format(Locale.ROOT, "tp @s %s %.1f %.1f", xyz(p, dx, dy, dz), yaw, pitch));
    }

    private static void arena(BlockPos c, int r, int h) {
        cmd("fill " + blk(c.offset(-r, -1, -r)) + " " + blk(c.offset(r, -1, r)) + " minecraft:stone");
        cmd("fill " + blk(c.offset(-r, 0, -r)) + " " + blk(c.offset(r, h, r)) + " minecraft:air");
    }

    private static void husk(BlockPos p) {
        cmd("summon minecraft:husk " + xyz(p, 0.5, 0, 0.5) + " {NoAI:1b,PersistenceRequired:1b,Silent:1b}");
    }

    /** Husk, der am nächsten an dieser Stelle steht. */
    private static LivingEntity huskAt(BlockPos p) {
        List<LivingEntity> l = mc.level.getEntitiesOfClass(LivingEntity.class, new AABB(p).inflate(1.5),
                e -> e instanceof net.minecraft.world.entity.monster.zombie.Husk && e.isAlive());
        return l.isEmpty() ? null : l.get(0);
    }

    private static int count(net.minecraft.world.item.Item item) {
        int n = 0;
        for (int i = 0; i < 36; i++) if (mc.player.getInventory().getItem(i).is(item)) n += mc.player.getInventory().getItem(i).getCount();
        return n;
    }

    private static void allKeysUp() {
        mc.options.keyUp.setDown(false);
        mc.options.keyJump.setDown(false);
        mc.options.keyUse.setDown(false);
        mc.options.keySprint.setDown(false);
    }

    static void step(int step, BlockPos o) {
        switch (step) {
            // ---------------------------------------------- ShieldBreaker / MaceSwap / SpearSwap
            case 300 -> {
                area = o.offset(100, 0, -60);
                tp(area, 0.5, 1, 0.5, -90, 0);
                next();
            }
            case 301 -> {
                if (!after(20)) return;
                arena(area, 7, 5);
                cmd("kill @e[type=minecraft:husk]");
                cmd("clear @s");
                cmd("effect give @s minecraft:instant_health 1 5");
                cmd("item replace entity @s hotbar.0 with minecraft:iron_sword");
                cmd("item replace entity @s hotbar.1 with minecraft:iron_axe");
                cmd("item replace entity @s hotbar.2 with minecraft:mace");
                cmd("item replace entity @s hotbar.3 with minecraft:iron_spear");
                husk(area.offset(1, 0, -2));
                husk(area.offset(4, 0, 1));
                tp(area, 0.5, 0, 0.5, -90, 0);
                for (String m : new String[]{"killaura", "triggerbot", "aimassist", "shieldbreaker", "maceswap", "spearswap", "criticals"}) {
                    Module mm = TeizenClient.modules().byName(m);
                    if (mm != null && mm.isEnabled()) mm.setEnabled(false);
                }
                t("///shieldbreaker");
                next();
            }
            case 302 -> {
                if (!after(25)) return;
                Inv.select(0);
                LivingEntity h = huskAt(area.offset(1, 0, -2));
                boolean axe = false, back = false;
                int before = mod(ShieldBreaker.class).swaps;
                if (h != null) {
                    ShieldBreaker.testBlocking.add(h.getId());
                    WeaponSwap.lastWeapon = null;
                    mc.gameMode.attack(mc.player, h);
                    axe = WeaponSwap.lastWeapon != null && WeaponSwap.lastWeapon.getDefaultInstance().is(ItemTags.AXES);
                    back = Inv.selected() == 0;
                    ShieldBreaker.testBlocking.clear();
                }
                result("ShieldBreaker: Gegner blockt -> für den Schlag zur Axt=" + yes(axe) + ", danach wieder Schwert=" + yes(back) + " "
                        + ok(h != null && axe && back && mod(ShieldBreaker.class).swaps == before + 1));
                t("///shieldbreaker");
                t("///maceswap");
                next();
            }
            case 303 -> {
                if (!after(25)) return;
                Inv.select(0);
                LivingEntity h = huskAt(area.offset(1, 0, -2));
                boolean mace = false, back = false;
                if (h != null) {
                    mc.player.fallDistance = 3;
                    WeaponSwap.lastWeapon = null;
                    mc.gameMode.attack(mc.player, h);
                    mace = WeaponSwap.lastWeapon == Items.MACE;
                    back = Inv.selected() == 0;
                    mc.player.fallDistance = 0;
                }
                // ohne Fallen: kein Wechsel
                WeaponSwap.lastWeapon = null;
                if (h != null && h.isAlive()) mc.gameMode.attack(mc.player, h);
                boolean noSwapOnGround = WeaponSwap.lastWeapon == null;
                result("MaceSwap: im Fallen zum Streitkolben=" + yes(mace) + ", danach wieder Schwert=" + yes(back) + ", am Boden kein Wechsel=" + yes(noSwapOnGround)
                        + " " + ok(h != null && mace && back && noSwapOnGround));
                t("///maceswap");
                t("///spearswap");
                next();
            }
            case 304 -> {
                if (!after(25)) return;
                Inv.select(0);
                LivingEntity h = huskAt(area.offset(4, 0, 1));
                flag1 = false;
                hp0 = h == null ? -1 : h.getHealth();
                n0 = h == null ? -1 : h.getId();
                if (h != null) {
                    WeaponSwap.lastWeapon = null;
                    mc.gameMode.attack(mc.player, h);
                    flag1 = WeaponSwap.lastWeapon != null && WeaponSwap.isSpear(WeaponSwap.lastWeapon.getDefaultInstance()) && Inv.selected() == 0;
                }
                text = h == null ? "-" : f(Targets.distanceToBox(h));
                next();
            }
            case 305 -> {
                if (!after(20)) return;
                var e = mc.level.getEntity(n0);
                double hp = e instanceof LivingEntity le && le.isAlive() ? le.getHealth() : 0;
                boolean hit = hp0 > 0 && hp < hp0;
                result("SpearSwap: Gegner " + text + " Blöcke weg -> zum Speer + zugestochen=" + yes(flag1) + ", getroffen=" + yes(hit) + " (Leben " + f(hp0) + "->" + f(hp)
                        + "), danach wieder Schwert " + ok(flag1 && hit));
                t("///spearswap");
                cmd("kill @e[type=minecraft:husk]");
                next();
            }
            // ---------------------------------------------- TargetESP
            case 306 -> {
                if (!after(10)) return;
                husk(area.offset(2, 0, 0));
                tp(area, 0.5, 0, 0.5, -90, 10);
                t("///targetesp");
                next();
            }
            case 307 -> {
                if (!after(30)) return;
                TargetESP te = mod(TargetESP.class);
                boolean ok = te.current instanceof net.minecraft.world.entity.monster.zombie.Husk;
                shot("40-targetesp");
                result("TargetESP: angeschauter Husk ist markiert=" + yes(ok) + ", gezeichnet " + te.drawn + "x " + ok(ok && te.drawn > 0));
                t("///targetesp");
                cmd("kill @e[type=minecraft:husk]");
                next();
            }
            // ---------------------------------------------- HoleESP
            case 308 -> {
                area = o.offset(100, 0, -30);
                tp(area, 0.5, 1, 0.5, -90, 30);
                next();
            }
            case 309 -> {
                if (!after(20)) return;
                arena(area, 6, 4);
                BlockPos b = area.offset(2, 0, 0);
                cmd("setblock " + blk(b.below()) + " minecraft:bedrock");
                for (BlockPos s : new BlockPos[]{b.east(), b.west(), b.north(), b.south()}) cmd("setblock " + blk(s) + " minecraft:bedrock");
                BlockPos ob = area.offset(-2, 0, 0);
                cmd("setblock " + blk(ob.below()) + " minecraft:obsidian");
                for (BlockPos s : new BlockPos[]{ob.east(), ob.west(), ob.north(), ob.south()}) cmd("setblock " + blk(s) + " minecraft:obsidian");
                tp(area, 0.5, 0, 0.5, -90, 30);
                t("///holeesp");
                next();
            }
            case 310 -> {
                if (!after(30)) return;
                HoleESP he = mod(HoleESP.class);
                BlockPos b = area.offset(2, 0, 0), ob = area.offset(-2, 0, 0);
                boolean bed = he.holes.stream().anyMatch(h -> h.pos().equals(b) && h.bedrock());
                boolean obs = he.holes.stream().anyMatch(h -> h.pos().equals(ob) && !h.bedrock());
                shot("41-holeesp");
                result("HoleESP: Grundgestein-Loch (grün)=" + yes(bed) + ", Obsidian-Loch (gelb)=" + yes(obs) + ", gefunden " + he.holes.size() + " " + ok(bed && obs));
                t("///holeesp");
                next();
            }
            // ---------------------------------------------- LowHealthWarning
            case 311 -> {
                cmd("effect clear @s");
                cmd("effect give @s minecraft:instant_health 1 5");
                t("///lowhealthwarning");
                next();
            }
            case 312 -> {
                if (!after(15)) return;
                y0 = mc.player.getHealth();
                cmd("damage @s 14 minecraft:generic");
                next();
            }
            case 313 -> {
                if (!after(20)) return;
                LowHealthWarning lh = mod(LowHealthWarning.class);
                flag1 = lh.low;
                shot("42-wenig-leben");
                result("LowHealthWarning: Leben " + f(y0) + " -> " + f(mc.player.getHealth()) + ", Warnung an=" + yes(flag1) + " " + ok(flag1 && lh.warnings > 0));
                cmd("effect give @s minecraft:instant_health 1 5");
                next();
            }
            case 314 -> {
                if (!after(15)) return;
                LowHealthWarning lh = mod(LowHealthWarning.class);
                result("LowHealthWarning: nach Heilen wieder aus=" + yes(!lh.low) + " " + ok(!lh.low));
                t("///lowhealthwarning");
                next();
            }
            // ---------------------------------------------- TimeChanger + WeatherChanger
            case 315 -> {
                TimeChanger.hits = 0;
                t("///set timechanger zeit mitternacht");
                t("///timechanger");
                t("///set weatherchanger wetter gewitter");
                t("///weatherchanger");
                tp(area, 0.5, 0, 0.5, -90, -30);
                next();
            }
            case 316 -> {
                if (!after(30)) return;
                flag1 = TimeChanger.hits > 0;
                flag2 = mc.level.getRainLevel(1f) > 0.99f && mc.level.getThunderLevel(1f) > 0.99f;
                shot("43-mitternacht-gewitter");
                t("///set weatherchanger wetter klar");
                t("///set timechanger zeit mittag");
                next();
            }
            case 317 -> {
                if (!after(20)) return;
                boolean clear = mc.level.getRainLevel(1f) < 0.01f && mc.level.getThunderLevel(1f) < 0.01f;
                shot("44-mittag-klar");
                result("TimeChanger: eigene Uhrzeit benutzt=" + yes(flag1) + " (" + TimeChanger.hits + "x); WeatherChanger: Gewitter=" + yes(flag2) + ", dann klar=" + yes(clear)
                        + " " + ok(flag1 && flag2 && clear));
                t("///timechanger");
                t("///weatherchanger");
                next();
            }
            // ---------------------------------------------- ChunkFinder
            case 318 -> {
                area = o.offset(100, 0, 40);
                tp(area, 0.5, 1, -3.5, 0, 20);
                next();
            }
            case 319 -> {
                if (!after(20)) return;
                arena(area, 6, 4);
                cmd("setblock " + blk(area.offset(1, 0, 1)) + " minecraft:shulker_box");
                cmd("setblock " + blk(area.offset(2, 0, 1)) + " minecraft:shulker_box");
                cmd("setblock " + blk(area.offset(1, 0, 2)) + " minecraft:ender_chest");
                cmd("setblock " + blk(area.offset(2, 0, 2)) + " minecraft:hopper");
                tp(area, 0.5, 0, -3.5, 0, 20);
                t("///chunkfinder");
                next();
            }
            case 320 -> {
                if (!after(60)) return;
                ChunkFinder cf = mod(ChunkFinder.class);
                int cx = area.offset(1, 0, 1).getX() >> 4, cz = area.offset(1, 0, 1).getZ() >> 4;
                var hit = cf.found.values().stream().filter(fd -> fd.cx() == cx && fd.cz() == cz).findFirst();
                shot("45-chunkfinder");
                result("ChunkFinder: Chunk mit Shulkern/Endertruhe gefunden=" + yes(hit.isPresent()) + (hit.map(h -> " (" + Math.round(h.score()) + " Punkte: " + h.what() + ")").orElse(""))
                        + " " + ok(hit.isPresent() && hit.get().score() >= 10));
                t("///chunkfinder");
                cmd("fill " + blk(area.offset(1, 0, 1)) + " " + blk(area.offset(2, 0, 2)) + " minecraft:air");
                next();
            }
            // ---------------------------------------------- ClickTP
            case 321 -> {
                area = o.offset(100, 0, 70);
                tp(area, 0.5, 1, 0.5, -90, 0);
                next();
            }
            case 322 -> {
                if (!after(20)) return;
                arena(area, 8, 5);
                cmd("setblock " + blk(area.offset(6, 0, 0)) + " minecraft:gold_block");
                cmd("clear @s");
                tp(area, 0.5, 0, 0.5, -90, 10.6f);
                t("///clicktp");
                next();
            }
            case 323 -> {
                if (!after(20)) return;
                Inv.select(5);
                flag1 = ClickTP.onUse();
                next();
            }
            case 324 -> {
                if (!after(20)) return;
                Vec3 want = new Vec3(area.getX() + 6.5, area.getY() + 1, area.getZ() + 0.5);
                double d = mc.player.position().distanceTo(want);
                result("ClickTP: Rechtsklick auf Goldblock 6 Blöcke weit -> oben drauf (Abstand " + f(d) + ") " + ok(flag1 && d < 1.0 && mod(ClickTP.class).teleports > 0));
                t("///clicktp");
                next();
            }
            // ---------------------------------------------- Spider
            case 325 -> {
                area = o.offset(130, 0, -60);
                tp(area, 0.5, 1, 0.5, -90, 0);
                next();
            }
            case 326 -> {
                if (!after(20)) return;
                arena(area, 5, 10);
                cmd("fill " + blk(area.offset(2, 0, -2)) + " " + blk(area.offset(2, 9, 2)) + " minecraft:stone");
                tp(area, 0.5, 0, 0.5, -90, 0);
                t("///spider");
                next();
            }
            case 327 -> {
                if (!after(15)) return;
                y0 = mc.player.getY();
                mc.options.keyUp.setDown(true);
                next();
            }
            case 328 -> {
                if (!after(30)) return;
                double up = mc.player.getY() - y0;
                allKeysUp();
                result("Spider: gegen die Wand laufen -> " + f(up) + " Blöcke hochgeklettert " + ok(up >= 2));
                t("///spider");
                tp(area, 0.5, 0, 0.5, -90, 0);
                next();
            }
            // ---------------------------------------------- ElytraFly
            case 329 -> {
                if (!after(10)) return;
                area = o.offset(130, 0, -30);
                cmd("item replace entity @s armor.chest with minecraft:elytra");
                tp(area, 0.5, 40, 0.5, -90, 0);
                next();
            }
            case 330 -> {
                if (!after(8)) return;
                if (mc.player.tryToStartFallFlying())
                    mc.player.connection.send(new net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket(mc.player,
                            net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
                t("///elytrafly");
                next();
            }
            case 331 -> {
                if (!after(10)) return;
                flag1 = mc.player.isFallFlying();
                y0 = mc.player.getY();
                next();
            }
            case 332 -> {
                if (!after(30)) return;
                flag2 = Math.abs(mc.player.getY() - y0) < 1.0 && mc.player.isFallFlying();
                y1 = mc.player.getY() - y0;
                p0 = mc.player.position();
                mc.options.keyUp.setDown(true);
                next();
            }
            case 333 -> {
                if (!after(20)) return;
                allKeysUp();
                next();
            }
            case 334 -> {
                if (!after(20)) return;
                double moved = Math.sqrt(Math.pow(mc.player.getX() - p0.x, 2) + Math.pow(mc.player.getZ() - p0.z, 2));
                shot("46-elytrafly");
                result("ElytraFly: Elytra offen=" + yes(flag1) + ", ohne Taste in der Luft stehen (Höhe " + f(y1) + ")=" + yes(flag2) + ", W gedrückt -> " + f(moved)
                        + " Blöcke geflogen (vom Server angenommen) " + ok(flag1 && flag2 && moved >= 15));
                t("///elytrafly");
                cmd("item replace entity @s armor.chest with minecraft:air");
                arena(area, 4, 3);
                tp(area, 0.5, 0, 0.5, -90, 0);
                next();
            }
            // ---------------------------------------------- BoatFly + BoatNoclip
            case 335 -> {
                if (!after(20)) return;
                area = o.offset(130, 0, 0);
                tp(area, 0.5, 1, 0.5, -90, 0);
                next();
            }
            case 336 -> {
                if (!after(20)) return;
                arena(area, 8, 10);
                cmd("kill @e[type=minecraft:oak_boat]");
                cmd("summon minecraft:oak_boat " + xyz(area, 0.5, 0, 0.5));
                tp(area, 1.5, 0, 0.5, -90, 0);
                next();
            }
            case 337 -> {
                if (!after(10)) return;
                cmd("ride @s mount @e[type=minecraft:oak_boat,limit=1,sort=nearest]");
                next();
            }
            case 338 -> {
                if (!after(15)) return;
                flag1 = mc.player.getVehicle() instanceof AbstractBoat;
                y0 = flag1 ? mc.player.getVehicle().getY() : mc.player.getY();
                t("///boatfly");
                mc.options.keyJump.setDown(true);
                next();
            }
            case 339 -> {
                if (!after(10)) return;
                allKeysUp();
                y1 = mc.player.getVehicle() != null ? mc.player.getVehicle().getY() : mc.player.getY();
                next();
            }
            case 340 -> {
                if (!after(30)) return;
                double y2 = mc.player.getVehicle() != null ? mc.player.getVehicle().getY() : mc.player.getY();
                boolean up = y1 - y0 >= 3;
                boolean hover = Math.abs(y2 - y1) < 1.0;
                shot("47-boatfly");
                result("BoatFly: im Boot=" + yes(flag1) + ", Leertaste -> " + f(y1 - y0) + " Blöcke hoch, danach in der Luft stehen (vom Server angenommen)=" + yes(hover) + " "
                        + ok(flag1 && up && hover));
                t("///boatfly");
                t("///boatnoclip");
                next();
            }
            case 341 -> {
                if (!after(10)) return;
                BoatNoclip bn = mod(BoatNoclip.class);
                String req = bn.requirement();
                boolean boatNoPhys = mc.player.getVehicle() != null && mc.player.getVehicle().noPhysics;
                result("BoatNoclip: Boot fliegt durch Blöcke (Client)=" + yes(boatNoPhys) + ", Hinweis auf fremdem Server = \"" + req + "\" "
                        + ok(boatNoPhys && req != null && mc.getSingleplayerServer() == null));
                t("///boatnoclip");
                cmd("ride @s dismount");
                next();
            }
            case 342 -> {
                if (!after(10)) return;
                cmd("kill @e[type=minecraft:oak_boat]");
                tp(area, 0.5, 0, 0.5, -90, 0);
                next();
            }
            // ---------------------------------------------- TridentBoost
            case 343 -> {
                if (!after(10)) return;
                area = o.offset(130, 0, 30);
                tp(area, 0.5, 1, 0.5, -90, 0);
                next();
            }
            case 344 -> {
                if (!after(20)) return;
                arena(area, 5, 8);
                cmd("fill " + blk(area.offset(-1, -2, -1)) + " " + blk(area.offset(1, -2, 1)) + " minecraft:stone");
                cmd("fill " + blk(area.offset(-1, -1, -1)) + " " + blk(area.offset(1, 0, 1)) + " minecraft:water");
                cmd("clear @s");
                cmd("item replace entity @s hotbar.0 with minecraft:trident[minecraft:enchantments={\"minecraft:riptide\":3}]");
                tp(area, 0.5, -1, 0.5, -90, -90);
                t("///tridentboost");
                next();
            }
            case 345 -> {
                if (!after(20)) return;
                Inv.select(0);
                n0 = mod(TridentBoost.class).boosts;
                mc.options.keyUse.setDown(true);
                next();
            }
            case 346 -> {
                if (!after(20)) return;
                mc.options.keyUse.setDown(false);
                next();
            }
            case 347 -> {
                if (!after(15)) return;
                TridentBoost tb = mod(TridentBoost.class);
                result("TridentBoost: Dreizack mit Sog im Wasser -> verstärkt=" + yes(tb.boosts > n0) + " (Tempo " + f(tb.lastSpeed) + " Blöcke/Tick) "
                        + ok(tb.boosts > n0 && tb.lastSpeed > 1.5));
                t("///tridentboost");
                allKeysUp();
                arena(area, 5, 8);
                tp(area, 0.5, 0, 0.5, -90, 0);
                next();
            }
            // ---------------------------------------------- AutoReplenish
            case 348 -> {
                if (!after(40)) return;
                cmd("clear @s");
                cmd("item replace entity @s hotbar.3 with minecraft:cobblestone 5");
                cmd("item replace entity @s inventory.10 with minecraft:cobblestone 64");
                t("///autoreplenish");
                next();
            }
            case 349 -> {
                if (!after(25)) return;
                int hot = mc.player.getInventory().getItem(3).is(Items.COBBLESTONE) ? mc.player.getInventory().getItem(3).getCount() : 0;
                int total = count(Items.COBBLESTONE);
                result("AutoReplenish: Hotbar-Stapel 5 -> " + hot + " aufgefüllt, insgesamt " + total + " (nichts verloren) " + ok(hot == 64 && total == 69));
                t("///autoreplenish");
                next();
            }
            // ---------------------------------------------- FastUse
            case 350 -> {
                cmd("clear @s");
                cmd("item replace entity @s hotbar.0 with minecraft:snowball 64");
                tp(area, 0.5, 0, 0.5, -90, -40);
                t("///set fastuse protick 3");
                t("///fastuse");
                next();
            }
            case 351 -> {
                if (!after(15)) return;
                Inv.select(0);
                n0 = count(Items.SNOWBALL);
                mc.options.keyUse.setDown(true);
                next();
            }
            case 352 -> {
                if (!after(20)) return;
                mc.options.keyUse.setDown(false);
                next();
            }
            case 353 -> {
                if (!after(10)) return;
                int used = n0 - count(Items.SNOWBALL);
                result("FastUse: Schneebälle in 20 Ticks geworfen: " + used + " (normal ca. 5) " + ok(used >= 25));
                t("///fastuse");
                cmd("clear @s");
                cmd("kill @e[type=minecraft:snowball]");
                next();
            }
            // ---------------------------------------------- NameProtect
            case 354 -> {
                t("///set nameprotect name Geheim");
                t("///nameprotect");
                next();
            }
            case 355 -> {
                if (!after(5)) return;
                String real = mc.getUser().getName();
                String shown = StringDecomposer.getPlainText(Component.literal("Hallo " + real + "!"));
                boolean ok = shown.contains("Geheim") && !shown.contains(real);
                dev.teizen.client.util.Chat.info("Test NameProtect: " + real);
                text = shown;
                flag1 = ok;
                next();
            }
            case 356 -> {
                if (!after(10)) return;
                shot("48-nameprotect");
                result("NameProtect: \"Hallo " + mc.getUser().getName().charAt(0) + "...!\" wird angezeigt als \"" + text + "\" " + ok(flag1));
                t("///nameprotect");
                next();
            }
            // ---------------------------------------------- SkinChanger
            case 357 -> {
                String def = net.minecraft.client.resources.DefaultPlayerSkin.get(mc.player.getUUID()).body().id().getPath();
                text = def.endsWith("/efe") ? "kai" : "efe";
                t("///set skinchanger skin " + text);
                t("///skinchanger");
                next();
            }
            case 358 -> {
                if (!after(15)) return;
                String path = mc.player.getSkin().body().id().getPath();
                flag1 = path.endsWith("/" + text);
                cmd("tp @s ~ ~ ~ 90 0");
                mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
                next();
            }
            case 359 -> {
                if (!after(10)) return;
                shot("49-skinchanger");
                mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                t("///skinchanger");
                next();
            }
            case 360 -> {
                if (!after(5)) return;
                boolean back = !mc.player.getSkin().body().id().getPath().endsWith("/" + text);
                result("SkinChanger: Skin \"" + text + "\" bei dir=" + yes(flag1) + ", aus -> wieder dein Skin=" + yes(back) + " " + ok(flag1 && back));
                SelfTest.jump(152);
            }
            default -> {}
        }
    }
}
