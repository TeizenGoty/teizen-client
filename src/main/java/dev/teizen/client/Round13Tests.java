package dev.teizen.client;

import dev.teizen.client.core.Commands;
import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.modules.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;

/** Selbsttest Runde 13: Sus-Chunk (Ambosse, Kisten-Häufung, Bauwerke ignorieren), NoClip fliegen, Bereichs-Miner. */
final class Round13Tests {
    private static final Minecraft mc = Minecraft.getInstance();
    private static BlockPos b;
    private static net.minecraft.world.phys.Vec3 p0;
    private static int n0, n1;
    private static BlockPos h;
    private static java.util.Map<BlockPos, net.minecraft.world.level.block.state.BlockState> house;
    private static double walked;

    /** Testhaus 7x5x7: Boden, Wände mit Stamm-Ecken, Tür, Fenster, Fackel, Balken, Dach aus Treppen und oberen Stufen. */
    private static java.util.Map<BlockPos, net.minecraft.world.level.block.state.BlockState> buildHouse(BlockPos o) {
        var P = net.minecraft.world.level.block.state.properties.BlockStateProperties.class;
        java.util.Map<BlockPos, net.minecraft.world.level.block.state.BlockState> m = new java.util.HashMap<>();
        var air = Blocks.AIR.defaultBlockState();
        var planks = Blocks.OAK_PLANKS.defaultBlockState();
        for (int x = 0; x < 7; x++) for (int z = 0; z < 7; z++) for (int y = 0; y <= 4; y++) {
            BlockPos p = o.offset(x, y, z);
            boolean edgeX = x == 0 || x == 6, edgeZ = z == 0 || z == 6, edge = edgeX || edgeZ, corner = edgeX && edgeZ;
            net.minecraft.world.level.block.state.BlockState st;
            if (y == 0) st = planks;
            else if (y <= 3) {
                if (!edge) st = air;
                else if (corner) st = Blocks.OAK_LOG.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS, net.minecraft.core.Direction.Axis.Y);
                else if (z == 0 && x == 3 && y <= 2) st = Blocks.OAK_DOOR.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, net.minecraft.core.Direction.NORTH)
                        .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF,
                                y == 1 ? net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER : net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER);
                else if (z == 0 && y == 3 && x >= 2 && x <= 4) st = Blocks.OAK_LOG.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS, net.minecraft.core.Direction.Axis.X);
                else if (y == 2 && (x == 1 || x == 5) && (z == 0 || z == 6)) st = Blocks.GLASS.defaultBlockState();
                else st = planks;
            } else {
                if (corner) st = planks;
                else if (edge) {
                    net.minecraft.core.Direction f = z == 0 ? net.minecraft.core.Direction.SOUTH : z == 6 ? net.minecraft.core.Direction.NORTH
                            : x == 0 ? net.minecraft.core.Direction.EAST : net.minecraft.core.Direction.WEST;
                    st = Blocks.OAK_STAIRS.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, f);
                } else st = Blocks.OAK_SLAB.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.SLAB_TYPE,
                        net.minecraft.world.level.block.state.properties.SlabType.TOP);
            }
            m.put(p, st);
        }
        // Fackel innen an der Nordwand
        m.put(o.offset(1, 2, 1), Blocks.WALL_TORCH.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, net.minecraft.core.Direction.SOUTH));
        // Trichter (zeigt nach Osten), Beobachter (nach oben), stehendes Schild (Drehung 4) innen
        m.put(o.offset(2, 1, 4), Blocks.HOPPER.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING_HOPPER, net.minecraft.core.Direction.EAST));
        m.put(o.offset(4, 1, 4), Blocks.OBSERVER.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, net.minecraft.core.Direction.UP));
        m.put(o.offset(4, 1, 2), Blocks.OAK_SIGN.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.ROTATION_16, 4));
        return m;
    }

    /** Gleicher Block und gleiche Ausrichtung (Scharnier/Treppenform/Verbindungen zählen nicht). */
    private static boolean same(net.minecraft.world.level.block.state.BlockState have, net.minecraft.world.level.block.state.BlockState want) {
        if (have.getBlock() != want.getBlock()) return false;
        for (var prop : new net.minecraft.world.level.block.state.properties.Property<?>[]{
                net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING,
                net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING,
                net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS,
                net.minecraft.world.level.block.state.properties.BlockStateProperties.HALF,
                net.minecraft.world.level.block.state.properties.BlockStateProperties.SLAB_TYPE,
                net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF,
                net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING,
                net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING_HOPPER,
                net.minecraft.world.level.block.state.properties.BlockStateProperties.ROTATION_16}) {
            if (want.hasProperty(prop) && !java.util.Objects.equals(have.getValue(prop), want.getValue(prop))) return false;
        }
        return true;
    }

    /** [richtig, falsch, gesamt] – nur Stellen, wo ein Block hin soll. */
    private static int[] houseCheck() {
        int good = 0, bad = 0, total = 0;
        for (var e : house.entrySet()) {
            if (e.getValue().isAir()) continue;
            total++;
            var have = mc.level.getBlockState(e.getKey());
            if (have.isAir() || have.canBeReplaced()) continue;
            if (same(have, e.getValue())) good++; else bad++;
        }
        return new int[]{good, bad, total};
    }

    private static String invSummary() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 36; i++) { var st = mc.player.getInventory().getItem(i); if (!st.isEmpty()) sb.append(st.getCount()).append('x').append(st.getItem()).append(' '); }
        return sb.toString();
    }

    private static void logWrong() {
        int n = 0;
        for (var e : house.entrySet()) {
            if (e.getValue().isAir()) continue;
            var have = mc.level.getBlockState(e.getKey());
            if (same(have, e.getValue())) continue;
            if (n++ < 25) log("AutoBuild Haus falsch/fehlt bei " + e.getKey().subtract(h).toShortString() + ": soll " + e.getValue() + " ist " + have);
        }
        log("AutoBuild Haus: " + n + " Stellen falsch/fehlen");
    }
    private static boolean f1, f2;
    private static float maxY;
    private static double d1;
    private static String f(float v) { return String.format(java.util.Locale.ROOT, "%.2f", v); }

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
                mc.options.guiScale().set(2); // große GUI wie beim Spieler -> Menü wird verkleinert
                mc.resizeGui();
                SelfTest.jump(732);
            }
            case 732 -> {
                if (!after(20)) return true;
                dev.teizen.client.gui.ClickGui gui = new dev.teizen.client.gui.ClickGui();
                mc.gui.setScreen(gui);
                gui.expand(get(AutoMine.class));
                next();
            }
            case 733 -> {
                if (!after(10)) return true;
                if (mc.gui.screen() instanceof dev.teizen.client.gui.ClickGui gui) {
                    int w = gui.width, h = gui.height;
                    for (int i = 0; i < 60; i++) gui.mouseScrolled(w / 2.0, h / 2.0, 0, -1); // Mitte ganz runter
                    for (int i = 0; i < 60; i++) gui.mouseScrolled(w - 5.0, h / 2.0, 0, -1); // rechts ganz runter
                }
                next();
            }
            case 734 -> {
                if (!after(10)) return true;
                SelfTest.shot2("94b-menue-skaliert-unten");
                result("Menü skaliert + ganz runtergescrollt: Bild 94b");
                mc.gui.setScreen(null);
                mc.options.guiScale().set(0);
                mc.resizeGui();
                SelfTest.jump(735);
            }
            case 735 -> {
                // AutoAnchor (makro): Anker vor dir -> laden + zünden
                if (local() == 1) {
                    if (Round13Tests.b == null) Round13Tests.b = mc.player.blockPosition(); // Kurztest ab 730
                    BlockPos b = mc.player.blockPosition();
                    cmd("fill " + (b.getX() - 2) + " " + b.getY() + " " + (b.getZ() + 1) + " " + (b.getX() + 2) + " " + (b.getY() + 3) + " " + (b.getZ() + 5) + " air");
                    cmd("setblock " + b.getX() + " " + (b.getY() + 1) + " " + (b.getZ() + 4) + " minecraft:respawn_anchor");
                    cmd("item replace entity @s hotbar.0 with minecraft:respawn_anchor 4");
                    cmd("item replace entity @s hotbar.1 with minecraft:glowstone 16");
                    cmd("item replace entity @s hotbar.2 with minecraft:totem_of_undying");
                    cmd("item replace entity @s weapon.offhand with minecraft:totem_of_undying");
                    cmd("effect give @s minecraft:resistance 30 255 true");
                    return true;
                }
                if (local() == 30) {
                    mc.player.setYRot(0); mc.player.setXRot(0);
                    mc.player.setPos(Math.floor(mc.player.getX()) + 0.5, mc.player.getY(), Math.floor(mc.player.getZ()) + 0.5);
                    dev.teizen.client.util.Inv.select(0);
                    on(get(AutoAnchor.class));
                    return true;
                }
                if (local() < 90) return true;
                AutoAnchor aa = get(AutoAnchor.class);
                BlockPos b = mc.player.blockPosition();
                boolean gone = !mc.level.getBlockState(new BlockPos(b.getX(), b.getY() + 1, b.getZ() + 4)).is(net.minecraft.world.level.block.Blocks.RESPAWN_ANCHOR);
                log("AutoAnchor: geladen=" + aa.charged + " gezündet=" + aa.exploded + " weg=" + gone);
                result("AutoAnchor makro: geladen=" + aa.charged + " gezündet=" + aa.exploded + " Anker weg=" + yes(gone) + " " + ok(aa.charged >= 1 && aa.exploded >= 1 && gone));
                off(aa);
                SelfTest.jump(740);
            }
            // ------------------------------------------------ AutoBuild realistisch: Haus (Überleben, laufen, Treppen/Stämme/Stufen/Tür/Fackel)
            case 740 -> {
                if (!after(10)) return true;
                if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("litematica")) { result("AutoBuild Haus: Litematica fehlt – übersprungen"); SelfTest.jump(750); return true; }
                h = b.offset(40, 0, 1);
                house = buildHouse(h);
                dev.teizen.client.compat.LitematicaCompat.testOverride = house;
                cmd("fill " + blk(h.offset(-12, 0, -12)) + " " + blk(h.offset(18, 8, 12)) + " minecraft:air");
                cmd("fill " + blk(h.offset(-12, -1, -12)) + " " + blk(h.offset(18, -1, 12)) + " minecraft:stone");
                t("///set autobuild laufen an"); // ältere Tests schalten Laufen aus
                t("///set autobuild abbauen an");
                cmd("gamemode survival");
                cmd("clear @s");
                cmd("give @s minecraft:oak_planks 256");
                cmd("give @s minecraft:oak_log 16");
                cmd("give @s minecraft:oak_stairs 64");
                cmd("give @s minecraft:oak_slab 64");
                cmd("give @s minecraft:glass 8");
                cmd("give @s minecraft:oak_door 1");
                cmd("give @s minecraft:torch 4");
                cmd("give @s minecraft:hopper 1");
                cmd("give @s minecraft:observer 1");
                cmd("give @s minecraft:oak_sign 1");
                cmd("effect give @s minecraft:instant_health 1 10 true");
                tp(h.getX() + 3.5, h.getY(), h.getZ() - 9.5);
                next();
            }
            case 741 -> {
                if (!after(20)) return true;
                off(get(AutoBuild.class));
                on(get(AutoBuild.class));
                p0 = mc.player.position();
                walked = 0;
                next();
            }
            case 742 -> {
                Vec3 now = mc.player.position();
                walked += now.distanceTo(p0);
                p0 = now;
                int[] c = houseCheck();
                if (local() % 200 == 0) log("AutoBuild Haus " + local() + ": richtig " + c[0] + "/" + c[2] + ", falsch " + c[1] + ", gesetzt " + get(AutoBuild.class).placedTotal
                        + ", pos " + mc.player.blockPosition().toShortString() + ", gelaufen " + Math.round(walked));
                if (c[0] + c[1] < c[2] && !after(3600)) return true;
                SelfTest.shot2("95-autobuild-haus");
                off(get(AutoBuild.class));
                releaseKeys();
                logWrong();
                // wo sind Bretter gelandet, die nicht zum Haus gehören?
                int stray = 0;
                for (BlockPos q : BlockPos.betweenClosed(h.offset(-12, 0, -12), h.offset(18, 8, 12))) {
                    var st = mc.level.getBlockState(q);
                    if (st.isAir()) continue;
                    var want = house.get(q.immutable());
                    if (want != null && same(st, want)) continue;
                    if (stray++ < 15) log("AutoBuild Haus fremder Block bei " + q.subtract(h).toShortString() + ": " + st + " (soll " + want + ")");
                }
                log("AutoBuild Haus: " + stray + " fremde Blöcke; Inventar: " + invSummary());
                // wichtig ist: keine falschen/fremden Blöcke, nicht ziellos herumgelaufen, Großteil gebaut
                result(String.format(Locale.ROOT, "AutoBuild Haus (Überleben, laufen): richtig %d/%d, falsch %d, fremde %d, nach %d Ticks, %.0f Blöcke gelaufen %s",
                        c[0], c[2], c[1], stray, local(), walked, ok(c[1] == 0 && stray == 0 && c[0] >= c[2] * 40 / 100 && walked < 120)));
                dev.teizen.client.compat.LitematicaCompat.testOverride = null;
                cmd("fill " + blk(h.offset(-12, 0, -12)) + " " + blk(h.offset(18, 8, 12)) + " minecraft:air");
                cmd("gamemode survival");
                SelfTest.jump(750);
            }
            // ------------------------------------------------ OffhandSwitch, FastSneak, SpawnerProtect
            case 750 -> {
                if (!after(10)) return true;
                releaseKeys();
                cmd("gamemode survival");
                cmd("clear @s");
                cmd("give @s minecraft:totem_of_undying");
                cmd("give @s minecraft:shield");
                cmd("effect give @s minecraft:instant_health 1 10 true");
                tp(b.getX() + 0.5, b.getY(), b.getZ() + 0.5);
                cmd("kill @e[type=!minecraft:player,distance=..30]");
                t("///set offhandswitch modus schlau");
                on(get(OffhandSwitch.class));
                next();
            }
            case 751 -> {
                if (!after(20)) return true;
                boolean totem = mc.player.getOffhandItem().is(net.minecraft.world.item.Items.TOTEM_OF_UNDYING);
                // Gegner direkt daneben -> sollte auf Schild wechseln
                cmd(String.format(Locale.ROOT, "summon minecraft:zombie %.1f %d %.1f {NoAI:1b,PersistenceRequired:1b,Silent:1b}", b.getX() + 1.5, b.getY(), b.getZ() + 0.5));
                f1 = totem;
                next();
            }
            case 752 -> {
                if (!after(25)) return true;
                boolean shield = mc.player.getOffhandItem().is(net.minecraft.world.item.Items.SHIELD);
                cmd("kill @e[type=minecraft:zombie,distance=..20]");
                f2 = shield;
                next();
            }
            case 753 -> {
                if (!after(30)) return true;
                boolean backToTotem = mc.player.getOffhandItem().is(net.minecraft.world.item.Items.TOTEM_OF_UNDYING);
                off(get(OffhandSwitch.class));
                result("OffhandSwitch (schlau): erst Totem=" + yes(f1) + ", bei Gegner Schild=" + yes(f2) + ", danach wieder Totem=" + yes(backToTotem) + " "
                        + ok(f1 && f2 && backToTotem));
                next();
            }
            // FastSneak: Schleich-Tempo = 1.0
            case 754 -> {
                if (!after(5)) return true;
                var a = mc.player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.SNEAKING_SPEED);
                double before = a == null ? -1 : a.getBaseValue();
                on(get(FastSneak.class));
                maxY = (float) before;
                next();
            }
            case 755 -> {
                if (!after(5)) return true;
                var a = mc.player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.SNEAKING_SPEED);
                double now = a == null ? -1 : a.getBaseValue();
                off(get(FastSneak.class));
                result("FastSneak: Schleich-Tempo " + f(maxY) + " -> " + f((float) now) + " (soll 1.0) " + ok(Math.abs(now - 1.0) < 1e-6));
                next();
            }
            // SpawnerProtect: Spawner wird nicht abgebaut
            case 756 -> {
                if (!after(5)) return true;
                cmd("setblock " + blk(b.offset(2, 0, 2)) + " minecraft:spawner");
                tp(b.getX() + 2.5, b.getY(), b.getZ() + 0.5);
                on(get(SpawnerProtect.class));
                next();
            }
            case 757 -> {
                if (!after(10)) return true;
                BlockPos sp = b.offset(2, 0, 2);
                boolean prot = dev.teizen.client.util.Breaker.canBreak(sp) == false;
                // manuell abbauen versuchen
                for (int i = 0; i < 60; i++) { mc.gameMode.startDestroyBlock(sp, net.minecraft.core.Direction.UP); mc.gameMode.continueDestroyBlock(sp, net.minecraft.core.Direction.UP); }
                boolean still = mc.level.getBlockState(sp).is(net.minecraft.world.level.block.Blocks.SPAWNER);
                off(get(SpawnerProtect.class));
                boolean nowBreakable = dev.teizen.client.util.Breaker.canBreak(sp);
                cmd("setblock " + blk(sp) + " minecraft:air");
                result("SpawnerProtect: Spawner geschützt (canBreak=nein)=" + yes(prot) + ", nach 60 Versuchen noch da=" + yes(still) + ", aus -> wieder abbaubar=" + yes(nowBreakable) + " "
                        + ok(prot && still && nowBreakable));
                SelfTest.jump(760);
            }
            // ------------------------------------------------ BlockESP, Chunks (aktive Chunks), GodPvP+Crystal
            case 760 -> {
                if (!after(10)) return true;
                releaseKeys();
                cmd("setblock " + blk(b.offset(3, 0, 3)) + " minecraft:spawner");
                cmd("setblock " + blk(b.offset(3, 0, 5)) + " minecraft:spawner");
                tp(b.getX() + 0.5, b.getY(), b.getZ() + 0.5);
                t("///set blockesp bloecke spawner");
                on(get(BlockESP.class));
                next();
            }
            case 761 -> {
                if (!after(40)) return true;
                int found = get(BlockESP.class).results().size();
                off(get(BlockESP.class));
                cmd("setblock " + blk(b.offset(3, 0, 3)) + " minecraft:air");
                cmd("setblock " + blk(b.offset(3, 0, 5)) + " minecraft:air");
                result("BlockESP: 2 Spawner gesetzt, gefunden " + found + " " + ok(found >= 2));
                next();
            }
            case 762 -> {
                if (!after(5)) return true;
                on(get(Chunks.class));
                // hoch spawnen, damit die Perle noch fliegt, wenn wir gleich nachschauen (am Boden wird sie sofort entfernt)
                cmd(String.format(Locale.ROOT, "summon minecraft:ender_pearl %.1f %d %.1f", b.getX() + 2.5, b.getY() + 40, b.getZ() + 2.5));
                next();
            }
            case 763 -> {
                if (!after(8)) return true;
                int act = get(Chunks.class).activeCount;
                off(get(Chunks.class));
                cmd("kill @e[type=minecraft:ender_pearl,distance=..20]");
                result("Chunks: aktiver Chunk durch Ender-Perle erkannt=" + yes(act > 0) + " (" + act + ") " + ok(act > 0));
                next();
            }
            case 764 -> {
                if (!after(5)) return true;
                for (String m : new String[]{"autocrystal", "killaura", "godpvp"}) { Module mm = TeizenClient.modules().byName(m); if (mm != null && mm.isEnabled()) mm.setEnabled(false); }
                t("///set godpvp crystal an");
                on(get(GodPvP.class));
                boolean crystalOn = get(AutoCrystal.class).isEnabled();
                t("///godpvp");
                t("///set godpvp crystal aus");
                boolean crystalOff = !get(AutoCrystal.class).isEnabled();
                result("GodPvP+Crystal: AutoCrystal mit an=" + yes(crystalOn) + ", nach aus wieder aus=" + yes(crystalOff) + " " + ok(crystalOn && crystalOff));
                SelfTest.jump(766);
            }
            // ------------------------------------------------ TreeBot: kleiner Baum wird gefällt
            case 766 -> {
                if (!after(10)) return true;
                cmd("gamemode survival");
                cmd("clear @s");
                cmd("item replace entity @s hotbar.0 with minecraft:diamond_axe");
                cmd("fill " + blk(b.offset(5, 0, 5)) + " " + blk(b.offset(5, 3, 5)) + " minecraft:air");
                cmd("setblock " + blk(b.offset(5, -1, 5)) + " minecraft:dirt");
                cmd("setblock " + blk(b.offset(5, 0, 5)) + " minecraft:oak_log");
                cmd("setblock " + blk(b.offset(5, 1, 5)) + " minecraft:oak_log");
                cmd("setblock " + blk(b.offset(5, 2, 5)) + " minecraft:oak_log");
                cmd("effect give @s minecraft:instant_health 1 10 true");
                tp(b.getX() + 5.5, b.getY(), b.getZ() + 3.5);
                next();
            }
            case 767 -> {
                if (!after(20)) return true;
                if (local() == 20) { dev.teizen.client.util.Inv.select(0); t("///tree"); return true; }
                if (local() % 100 == 0) log("TreeBot " + local() + ": " + get(AutoMine.class).debugState());
                int logs = 0;
                for (int y = 0; y <= 2; y++) if (mc.level.getBlockState(b.offset(5, y, 5)).is(net.minecraft.world.level.block.Blocks.OAK_LOG)) logs++;
                // Stämme im Inventar (der TreeBot nimmt auch echte Bäume in der Nähe – große zuerst)
                int inv = 0;
                for (int i = 0; i < 36; i++) {
                    var st = mc.player.getInventory().getItem(i);
                    if (!st.isEmpty() && net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem()).getPath().endsWith("_log")) inv += st.getCount();
                }
                if (logs > 0 && inv < 3 && !after(1800)) return true;
                boolean farm = get(AutoMine.class).farmMode;
                t("///tree stop");
                result("TreeBot: Stämme gefällt und eingesammelt=" + inv + " (Testbaum noch " + logs + "/3), Farm-Modus=" + yes(farm) + " " + ok(farm && (inv >= 3 || logs == 0)));
                cmd("fill " + blk(b.offset(5, -1, 5)) + " " + blk(b.offset(5, 3, 5)) + " minecraft:air");
                SelfTest.jump(768);
            }
            // ------------------------------------------------ NoClip-Freecam: Interagieren frei + TP zur Kamera
            case 768 -> {
                if (!after(10)) return true;
                cmd("gamemode creative");
                cmd("fill " + blk(b.offset(-2, 0, -2)) + " " + blk(b.offset(2, 4, 12)) + " minecraft:air");
                tp(b.getX() + 0.5, b.getY(), b.getZ() + 0.5);
                t("///set noclip modus fliegen");
                t("///set noclip tempo 0.6");
                get(Freecam.class).interact.setBool(false); // Ausgangszustand
                next();
            }
            case 769 -> {
                if (!after(10)) return true;
                if (local() == 10) { p0 = mc.player.position(); on(get(NoClip.class)); mc.player.setYRot(0); mc.player.setXRot(0); return true; }
                if (local() < 45) { mc.options.keyUp.setDown(true); return true; }
                mc.options.keyUp.setDown(false);
                Freecam fc = get(Freecam.class);
                boolean interactOn = fc.interact.on();
                boolean flying = get(NoClip.class).flying();
                double camDist = fc.cam == null ? 0 : fc.cam.distanceTo(mc.player);
                f1 = interactOn && flying && camDist > 4;
                d1 = fc.cam == null ? 0 : fc.cam.getZ();
                t("///camtp");
                next();
            }
            case 770 -> {
                if (!after(15)) return true;
                double movedZ = Math.abs(mc.player.getZ() - (b.getZ() + 0.5));
                boolean tped = Math.abs(mc.player.getZ() - d1) < 2.5; // Spieler jetzt ~bei der Kamera
                off(get(NoClip.class));
                boolean restored = !get(Freecam.class).interact.on(); // war vorher aus
                boolean camGone = mc.getCameraEntity() == mc.player;
                result("NoClip-Freecam: Interagieren frei + fliegt=" + yes(f1) + ", per ///camtp zur Kamera (Δz " + f((float) movedZ) + ")=" + yes(tped)
                        + ", danach interagieren zurück=" + yes(restored) + ", Kamera weg=" + yes(camGone) + " " + ok(f1 && tped && restored && camGone));
                cmd("gamemode survival");
                SelfTest.jump(771);
            }
            // ------------------------------------------------ PhantomMarket (nur Link, im Test nicht geöffnet)
            case 771 -> {
                if (!after(5)) return true;
                dev.teizen.client.util.ExternalLinks.lastUrl = null;
                t("///phantom");
                String pm = dev.teizen.client.util.ExternalLinks.lastUrl;
                boolean pmOk = "https://market.phantom-node.com".equals(pm);
                // Schematic-Fenster öffnen (Knöpfe werden gezeichnet)
                mc.gui.setScreen(new dev.teizen.client.gui.SchematicsScreen(null));
                result("Link: PhantomMarket=" + yes(pmOk) + " " + ok(pmOk));
                next();
            }
            case 772 -> {
                if (!after(10)) return true;
                boolean open = mc.gui.screen() instanceof dev.teizen.client.gui.SchematicsScreen;
                SelfTest.shot2("96-schematics-knoepfe");
                mc.gui.setScreen(null);
                result("Schematic-Fenster mit PhantomMarket-Knopf offen=" + yes(open) + " " + ok(open));
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
