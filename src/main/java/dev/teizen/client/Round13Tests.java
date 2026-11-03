package dev.teizen.client;

import dev.teizen.client.core.Commands;
import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.modules.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

import java.util.List;
import java.util.Locale;

/** Selbsttest Runde 13: Sus-Chunk (Ambosse, Kisten-Häufung, Bauwerke ignorieren), NoClip fliegen, Bereichs-Miner. */
final class Round13Tests {
    private static final Minecraft mc = Minecraft.getInstance();
    private static BlockPos b;
    private static net.minecraft.world.phys.Vec3 p0;
    private static int n0, n1;
    private static boolean f1, f2;

    private Round13Tests() {}

    private static void cmd(String c) { SelfTest.cmd2(c); }
    private static boolean after(int t) { return SelfTest.after2(t); }
    private static int local() { return SelfTest.local(); }
    private static void next() { SelfTest.next2(); }
    private static void result(String r) { SelfTest.result(r); }
    private static void log(String s) { SelfTest.log2(s); }
    private static void t(String c) { Commands.handle(c); }
    private static String ok(boolean v) { return v ? "OK" : "FEHLER"; }
    private static String yes(boolean v) { return v ? "ja" : "nein"; }
    private static String blk(BlockPos p) { return p.getX() + " " + p.getY() + " " + p.getZ(); }
    private static <T extends Module> T get(Class<T> c) { return TeizenClient.modules().get(c); }
    private static void on(Module m) { if (!m.isEnabled()) m.setEnabled(true); }
    private static void off(Module m) { if (m.isEnabled()) m.setEnabled(false); }
    private static void tp(double x, double y, double z) { cmd(String.format(Locale.ROOT, "tp @s %.2f %.2f %.2f 0 0", x, y, z)); }
    private static void releaseKeys() {
        var o = mc.options;
        o.keyUp.setDown(false); o.keyJump.setDown(false); o.keyShift.setDown(false); o.keySprint.setDown(false);
    }

    /** @return true wenn der Schritt hier behandelt wurde */
    static boolean step(int step, BlockPos o) {
        switch (step) {
            // ------------------------------------------------ Vorbereitung: frische Fläche (Chunk-genau)
            case 700 -> {
                releaseKeys();
                // Ecke eines Chunks + 4, damit alles in EINEM Chunk liegt
                b = new BlockPos(((o.getX() + 160) & ~15) + 4, o.getY() + 40, ((o.getZ() + 160) & ~15) + 4);
                cmd("forceload add " + (b.getX() - 40) + " " + (b.getZ() - 40) + " " + (b.getX() + 120) + " " + (b.getZ() + 40));
                tp(b.getX() + 0.5, b.getY() + 1, b.getZ() + 0.5);
                next();
            }
            case 701 -> {
                if (!after(60)) return true;
                cmd("fill " + blk(b.offset(-4, -1, -4)) + " " + blk(b.offset(100, -1, 11)) + " minecraft:stone");
                cmd("fill " + blk(b.offset(-4, 0, -4)) + " " + blk(b.offset(100, 8, 11)) + " minecraft:air");
                cmd("effect give @s minecraft:instant_health 1 10 true");
                cmd("effect give @s minecraft:resistance 600 4 true");
                cmd("effect give @s minecraft:saturation 600 1 true");
                // Basis: Amboss, Zaubertisch, 4 Fässer dicht beisammen
                cmd("setblock " + blk(b.offset(1, 0, 1)) + " minecraft:anvil");
                cmd("setblock " + blk(b.offset(3, 0, 1)) + " minecraft:enchanting_table");
                cmd("setblock " + blk(b.offset(1, 0, 4)) + " minecraft:barrel");
                cmd("setblock " + blk(b.offset(2, 0, 4)) + " minecraft:barrel");
                cmd("setblock " + blk(b.offset(3, 0, 4)) + " minecraft:barrel");
                cmd("setblock " + blk(b.offset(4, 0, 4)) + " minecraft:barrel");
                // "Trial Chamber" 4 Chunks weiter: Spawner + Kisten + Crafter -> soll NICHT gemeldet werden
                BlockPos tc = b.offset(64, 0, 0);
                cmd("setblock " + blk(tc) + " minecraft:trial_spawner");
                cmd("setblock " + blk(tc.offset(2, 0, 0)) + " minecraft:chest");
                cmd("setblock " + blk(tc.offset(4, 0, 0)) + " minecraft:chest");
                cmd("setblock " + blk(tc.offset(4, 0, 3)) + " minecraft:chest");
                cmd("setblock " + blk(tc.offset(6, 0, 3)) + " minecraft:crafter");
                cmd("setblock " + blk(tc.offset(7, 0, 3)) + " minecraft:hopper");
                cmd("setblock " + blk(tc.offset(7, 0, 5)) + " minecraft:dispenser");
                tp(b.getX() + 30.5, b.getY(), b.getZ() + 0.5);
                next();
            }
            case 702 -> {
                if (!after(20)) return true;
                off(get(ChunkFinder.class));
                on(get(ChunkFinder.class));
                next();
            }
            case 703 -> {
                if (!after(90)) return true;
                ChunkFinder cf = get(ChunkFinder.class);
                int cx = b.getX() >> 4, cz = b.getZ() >> 4;
                int tx = (b.getX() + 64) >> 4;
                var base = cf.found.values().stream().filter(f -> f.cx() == cx && f.cz() == cz).findFirst();
                var trial = cf.found.values().stream().filter(f -> f.cx() == tx && f.cz() == cz).findFirst();
                boolean anvil = base.isPresent() && base.get().what().contains("Amboss");
                boolean cluster = base.isPresent() && base.get().what().contains("dicht");
                SelfTest.shot2("90-suschunk");
                result("Sus-Chunk: Basis mit Amboss/Zaubertisch/4 Fässern gefunden=" + yes(base.isPresent())
                        + base.map(f -> " (" + Math.round(f.score()) + " Punkte: " + f.what() + ")").orElse("")
                        + ", Amboss erkannt=" + yes(anvil) + ", Fässer-Häufung erkannt=" + yes(cluster)
                        + ", Trial Chamber gemeldet=" + yes(trial.isPresent()) + trial.map(f -> " (" + f.what() + ")").orElse("") + " (soll nein) "
                        + ok(base.isPresent() && anvil && cluster && trial.isEmpty()));
                off(cf);
                cmd("fill " + blk(b.offset(-4, 0, -4)) + " " + blk(b.offset(100, 8, 11)) + " minecraft:air");
                next();
            }
            // ------------------------------------------------ NoClip fliegen: durch eine Wand, nichts wird abgebaut
            case 704 -> {
                if (!after(10)) return true;
                cmd("fill " + blk(b.offset(-1, 0, 3)) + " " + blk(b.offset(1, 2, 5)) + " minecraft:stone");
                tp(b.getX() + 0.5, b.getY(), b.getZ() + 0.5);
                t("///set noclip modus fliegen");
                t("///set noclip tempo 0.4");
                next();
            }
            case 705 -> {
                if (!after(20)) return true;
                if (local() == 20) {
                    p0 = mc.player.position();
                    mc.player.setYRot(0); mc.player.setXRot(0);
                    on(get(NoClip.class));
                    return true;
                }
                if (local() == 25) { mc.options.keyUp.setDown(true); return true; }
                if (!after(70)) return true;
                mc.options.keyUp.setDown(false);
                Freecam fc = get(Freecam.class);
                double camZ = fc.cam == null ? 0 : fc.cam.getZ();
                boolean through = camZ > b.getZ() + 6;
                int stone = 0;
                for (BlockPos q : BlockPos.betweenClosed(b.offset(-1, 0, 3), b.offset(1, 2, 5))) if (mc.level.getBlockState(q).is(Blocks.STONE)) stone++;
                double moved = p0.distanceTo(mc.player.position());
                boolean flying = get(NoClip.class).flying();
                SelfTest.shot2("91-noclip-fliegen");
                off(get(NoClip.class));
                boolean back = mc.getCameraEntity() == mc.player && !get(Freecam.class).isEnabled();
                result(String.format(Locale.ROOT, "NoClip fliegen: Kamera durch die Wand=%s (z %.1f), Wand heil %d/27, Spieler bewegt %.2f (soll 0), fliegt=%s, danach Kamera zurück=%s %s",
                        yes(through), camZ - b.getZ(), stone, moved, yes(flying), yes(back), ok(through && stone == 27 && moved < 0.3 && flying && back)));
                cmd("fill " + blk(b.offset(-1, 0, 3)) + " " + blk(b.offset(1, 2, 5)) + " minecraft:air");
                next();
            }
            // ------------------------------------------------ Bereichs-Miner: ///miner x1 y1 z1 x2 y2 z2
            case 706 -> {
                if (!after(10)) return true;
                cmd("fill " + blk(b.offset(2, 0, 2)) + " " + blk(b.offset(4, 1, 4)) + " minecraft:stone");
                tp(b.getX() + 0.5, b.getY(), b.getZ() + 0.5);
                cmd("clear @s");
                cmd("item replace entity @s hotbar.0 with minecraft:diamond_pickaxe");
                cmd("effect give @s minecraft:instant_health 1 10 true");
                next();
            }
            case 707 -> {
                if (!after(20)) return true;
                BlockPos p1 = b.offset(2, 1, 2), p2 = b.offset(4, 0, 4);
                n0 = 0;
                t("///miner " + blk(p1) + " " + blk(p2));
                next();
            }
            case 708 -> {
                int left = 0;
                for (BlockPos q : BlockPos.betweenClosed(b.offset(2, 0, 2), b.offset(4, 1, 4))) if (!mc.level.getBlockState(q).isAir()) left++;
                if (local() % 100 == 0) log("Bereichs-Miner " + local() + ": " + get(AutoMine.class).debugState());
                if (left > 0 && !after(1500)) return true;
                AutoMine am = get(AutoMine.class);
                int floor = 0;
                for (BlockPos q : BlockPos.betweenClosed(b.offset(2, -1, 2), b.offset(4, -1, 4))) if (!mc.level.getBlockState(q).isAir()) floor++;
                SelfTest.shot2("92-bereichs-miner");
                boolean stopped = !am.isEnabled() || left == 0;
                result("Bereichs-Miner: 18 Blöcke im Bereich, noch übrig " + left + ", Boden darunter heil " + floor + "/9, festgehangen " + am.unstuckCount + "x "
                        + ok(left == 0 && floor == 9 && stopped));
                off(am);
                SelfTest.jump(720);
            }
            // ------------------------------------------------ AutoMine-Schnellschalter: Helfer gehen mit an und wieder aus
            case 720 -> {
                if (!after(10)) return true;
                for (Class<? extends Module> c : List.of(XRay.class, Fullbright.class, NoFall.class, FastBreak.class, AutoTotem.class)) off(get(c));
                t("///set automine mitxray an");
                t("///set automine mithell an");
                t("///mine diamond");
                next();
            }
            case 721 -> {
                if (!after(20)) return true;
                boolean xr = get(XRay.class).isEnabled(), fb = get(Fullbright.class).isEnabled(), nf = get(NoFall.class).isEnabled();
                t("///set automine mitnofall an"); // während er läuft umschalten
                next();
                f1 = xr && fb && !nf;
            }
            case 722 -> {
                if (!after(20)) return true;
                boolean nf = get(NoFall.class).isEnabled();
                int helpers = get(AutoMine.class).helpersActive();
                t("///mine stop");
                next();
                f2 = nf && helpers == 3;
            }
            case 723 -> {
                if (!after(10)) return true;
                boolean allOff = !get(XRay.class).isEnabled() && !get(Fullbright.class).isEnabled() && !get(NoFall.class).isEnabled();
                result("AutoMine-Schalter: X-Ray + Fullbright gehen mit an=" + yes(f1) + ", NoFall im Laufen dazugeschaltet=" + yes(f2)
                        + ", beim Stoppen wieder aus=" + yes(allOff) + " " + ok(f1 && f2 && allOff));
                t("///set automine mitxray aus");
                t("///set automine mithell aus");
                t("///set automine mitnofall aus");
                next();
            }
            // ------------------------------------------------ GodPvP: beste Waffe + Mace beim Fliegen
            case 724 -> {
                if (!after(10)) return true;
                for (String m : new String[]{"killaura", "reach", "aimassist", "triggerbot", "criticals", "maceswap", "shieldbreaker", "fly", "godpvp"}) {
                    Module mm = TeizenClient.modules().byName(m);
                    if (mm != null && mm.isEnabled()) mm.setEnabled(false);
                }
                cmd("clear @s");
                cmd("item replace entity @s hotbar.0 with minecraft:wooden_sword");
                cmd("item replace entity @s hotbar.1 with minecraft:mace");
                cmd("item replace entity @s hotbar.2 with minecraft:diamond_sword");
                cmd("fill " + blk(b.offset(-4, 0, -4)) + " " + blk(b.offset(11, 8, 11)) + " minecraft:air");
                tp(b.getX() + 0.5, b.getY() + 2, b.getZ() + 0.5);
                cmd(String.format(Locale.ROOT, "summon minecraft:zombie %d %d %d {NoAI:1b,PersistenceRequired:1b,attributes:[{id:\"minecraft:max_health\",base:300}],Health:300f}",
                        b.getX() + 2, b.getY(), b.getZ()));
                dev.teizen.client.util.Inv.select(0);
                t("///set fly doppelsprung aus");
                next();
            }
            case 725 -> {
                if (!after(20)) return true;
                if (local() == 20) {
                    n0 = get(MaceSwap.class).fakeFalls;
                    n1 = get(GodPvP.class).weaponPicks;
                    on(get(Fly.class));
                    t("///godpvp");
                    return true;
                }
                if (!after(120)) return true;
                var zs = mc.level.getEntitiesOfClass(net.minecraft.world.entity.monster.zombie.Zombie.class, new net.minecraft.world.phys.AABB(b).inflate(8), e -> e.isAlive());
                float hp = zs.isEmpty() ? 0 : zs.get(0).getHealth();
                boolean mace = get(MaceSwap.class).isEnabled(), sb = get(ShieldBreaker.class).isEnabled();
                int ff = get(MaceSwap.class).fakeFalls - n0;
                int picks = get(GodPvP.class).weaponPicks - n1;
                boolean best = mc.player.getMainHandItem().is(net.minecraft.world.item.Items.DIAMOND_SWORD) || picks > 0;
                SelfTest.shot2("93-godpvp-fliegen");
                t("///godpvp");
                off(get(Fly.class));
                result(String.format(Locale.ROOT, "GodPvP: Mace+Schildbrecher an=%s, beste Waffe genommen=%s (%dx), beim Fliegen Mace-Sturz gezeigt=%dx, Zombie 300 -> %.0f Leben %s",
                        yes(mace && sb), yes(best), picks, ff, hp, ok(mace && sb && best && ff > 0 && hp < 300 - 30)));
                cmd("kill @e[type=minecraft:zombie,distance=..20]");
                SelfTest.jump(730);
            }
            // ------------------------------------------------ Neues Menü: AutoMine rechts, Kategorie Abbau, Schalter per Klick
            case 730 -> {
                if (!after(10)) return true;
                dev.teizen.client.gui.ClickGui gui = new dev.teizen.client.gui.ClickGui();
                mc.gui.setScreen(gui);
                gui.expand(get(AutoMine.class));
                next();
            }
            case 731 -> {
                if (!after(20)) return true;
                boolean open = mc.gui.screen() instanceof dev.teizen.client.gui.ClickGui;
                dev.teizen.client.gui.ClickGui gui = open ? (dev.teizen.client.gui.ClickGui) mc.gui.screen() : null;
                boolean sel = gui != null && gui.selected() == get(AutoMine.class);
                boolean listed = gui != null && gui.shownModules().contains(get(AutoMine.class)) && gui.shownModules().contains(get(XRay.class));
                SelfTest.shot2("94-neues-menue");
                // Einstellung rechts per Klick: "mitxray" umschalten
                Setting sx = get(AutoMine.class).setting("mitxray");
                boolean before = sx.on();
                if (gui != null) gui.clickSetting(get(AutoMine.class), sx, 0);
                boolean flipped = sx.on() != before;
                sx.setBool(before);
                result("Neues Menü: offen=" + yes(open) + ", AutoMine rechts gewählt=" + yes(sel) + ", Abbau-Liste zeigt AutoMine+XRay=" + yes(listed)
                        + ", Einstellung per Klick umgeschaltet=" + yes(flipped) + " " + ok(open && sel && listed && flipped));
                mc.gui.setScreen(null);
                SelfTest.jump(709);
            }
            case 709 -> {
                if (!after(10)) return true;
                cmd("forceload remove " + (b.getX() - 40) + " " + (b.getZ() - 40) + " " + (b.getX() + 120) + " " + (b.getZ() + 40));
                cmd("effect clear @s");
                SelfTest.jump(631);
            }
            default -> { return false; }
        }
        return true;
    }
}
