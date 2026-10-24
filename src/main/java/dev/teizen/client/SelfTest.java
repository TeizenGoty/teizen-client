package dev.teizen.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.teizen.client.core.Commands;
import dev.teizen.client.core.Finder;
import dev.teizen.client.core.Module;
import dev.teizen.client.gui.ClickGui;
import dev.teizen.client.modules.AutoMine;
import dev.teizen.client.modules.KillAura;
import dev.teizen.client.modules.XRay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.block.Blocks;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Automatischer Test (nur in der Build-Pipeline, wenn TEIZEN_SELFTEST=1 gesetzt ist).
 * Läuft auf einem echten Server, schaltet alle Funktionen durch, macht Screenshots und beendet das Spiel.
 */
public final class SelfTest {
    public static final boolean ACTIVE = "1".equals(System.getenv("TEIZEN_SELFTEST"));
    private static final Minecraft mc = Minecraft.getInstance();

    private static int ticks;
    private static int worldTicks;
    private static int step;
    private static int stepStart;
    private static int moduleIndex;
    private static final List<String> results = new ArrayList<>();
    private static BlockPos origin;
    private static int diamondsBefore = -1;

    private SelfTest() {}

    private static void log(String s) { TeizenClient.LOG.info("SELFTEST: " + s); }

    private static void cmd(String c) {
        log("/" + c);
        mc.player.connection.sendCommand(c);
    }

    private static void shot(String name) {
        try {
            Path dir = mc.gameDirectory.toPath().resolve("selftest-shots");
            Files.createDirectories(dir);
            Path file = dir.resolve(name + ".png");
            Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(), (NativeImage img) -> {
                try (img) {
                    img.writeToFile(file);
                    log("Screenshot " + file.getFileName());
                } catch (Exception e) {
                    log("Screenshot fehlgeschlagen: " + e);
                }
            });
        } catch (Throwable t) {
            log("Screenshot fehlgeschlagen: " + t);
        }
    }

    private static int countDiamondOre() {
        int n = 0;
        for (BlockPos p : BlockPos.betweenClosed(origin.offset(-12, -12, -12), origin.offset(12, 4, 12))) {
            if (mc.level.getBlockState(p).is(Blocks.DIAMOND_ORE)) n++;
        }
        return n;
    }

    private static int bx, by, bz;

    /** Vergleicht Welt und Hologramm. @return {richtig, falsch, gesamt} */
    private static int[] compareBuild(boolean report) {
        int ok = 0, bad = 0, total = 0;
        for (BlockPos p : BlockPos.betweenClosed(bx, by, bz, bx + 4, by + 1, bz + 4)) {
            var want = dev.teizen.client.compat.LitematicaCompat.schematicState(p);
            if (want == null || want.isAir()) continue;
            total++;
            var have = mc.level.getBlockState(p);
            if (have == want) ok++;
            else {
                if (!have.isAir()) bad++;
                if (report) log("  Unterschied bei " + (p.getX() - bx) + "," + (p.getY() - by) + "," + (p.getZ() - bz) + ": soll " + want + " ist " + have);
            }
        }
        return new int[]{ok, bad, total};
    }

    private static boolean after(int t) { return ticks - stepStart >= t; }

    private static void next() {
        step++;
        stepStart = ticks;
    }

    public static void tick() {
        if (!ACTIVE) return;
        ticks++;
        if (ticks % 200 == 0) log("tick " + ticks + " step " + step + " screen=" + (mc.gui.screen() == null ? "-" : mc.gui.screen().getClass().getSimpleName()));
        if (ticks > 20 * 60 * 12) { log("ZEITÜBERSCHREITUNG – beende"); finish(); return; }
        if (mc.player == null || mc.level == null) return;
        worldTicks++;
        if (worldTicks < 140) return;
        List<Module> mods = TeizenClient.modules().all();

        switch (step) {
            case 0 -> {
                origin = mc.player.blockPosition();
                log("in der Welt bei " + origin.toShortString());
                cmd("time set day");
                cmd("weather clear");
                cmd("difficulty easy");
                cmd("give @s minecraft:diamond_pickaxe");
                cmd("give @s minecraft:stone 64");
                cmd("give @s minecraft:totem_of_undying");
                cmd("give @s minecraft:cooked_beef 16");
                cmd("fill ~-1 ~-6 ~-1 ~1 ~-2 ~1 minecraft:stone");
                cmd("setblock ~2 ~-3 ~2 minecraft:diamond_ore");
                cmd("setblock ~3 ~-4 ~2 minecraft:diamond_ore");
                cmd("setblock ~-4 ~-5 ~3 minecraft:diamond_ore");
                cmd("setblock ~4 ~ ~4 minecraft:chest");
                next();
            }
            case 1 -> {
                if (!after(40)) return;
                shot("01-welt");
                Commands.handle("///help");
                Commands.handle("///list");
                next();
            }
            case 2 -> {
                if (!after(20)) return;
                mc.gui.setScreen(new ClickGui());
                next();
            }
            case 3 -> {
                if (!after(20)) return;
                shot("02-menue");
                mc.gui.setScreen(null);
                diamondsBefore = countDiamondOre();
                log("Diamant-Erz in der Nähe: " + diamondsBefore);
                Commands.handle("///xray");
                next();
            }
            case 4 -> {
                if (!after(80)) return;
                XRay x = TeizenClient.modules().get(XRay.class);
                results.add("XRay findet " + x.scanner.results().size() + " Erze");
                Commands.handle("///find block diamond 2");
                Commands.handle("///find chest 2");
                next();
            }
            case 5 -> {
                if (!after(60)) return;
                results.add("Find-Markierungen: " + Finder.markers().size());
                shot("03-xray-find");
                Commands.handle("///xray");
                Commands.handle("///find clear");
                cmd("summon minecraft:zombie ~3 ~ ~");
                Commands.handle("///killaura");
                next();
            }
            case 6 -> {
                if (!after(100)) return;
                List<Zombie> z = mc.level.getEntitiesOfClass(Zombie.class, mc.player.getBoundingBox().inflate(8));
                results.add("KillAura: Zombies übrig in 8 Blöcken = " + z.size());
                shot("04-killaura");
                Commands.handle("///killaura");
                Commands.handle("///mine diamond");
                next();
            }
            case 7 -> {
                int local = ticks - stepStart;
                if (local % 40 == 0) log("AutoMine " + local + ": " + TeizenClient.modules().get(AutoMine.class).debugState());
                if (local == 200) shot("05a-automine");
                if (!after(700) && countDiamondOre() > 0) return;
                int now = countDiamondOre();
                results.add("AutoMine: Diamant-Erz vorher " + diamondsBefore + ", nachher " + now + " (nach " + local + " Ticks)");
                shot("05-automine");
                Commands.handle("///mine stop");
                next();
            }
            // ------------------------------------------------ AutoBuild mit echter Litematica-Schematic
            case 8 -> {
                bx = origin.getX() + 12; by = origin.getY(); bz = origin.getZ();
                cmd("fill " + (bx - 3) + " " + (by - 1) + " " + (bz - 3) + " " + (bx + 7) + " " + (by - 1) + " " + (bz + 7) + " minecraft:stone");
                cmd("fill " + (bx - 3) + " " + by + " " + (bz - 3) + " " + (bx + 7) + " " + (by + 5) + " " + (bz + 7) + " minecraft:air");
                cmd("setblock " + (bx + 2) + " " + (by + 2) + " " + (bz + 2) + " minecraft:barrier");
                cmd("tp @s " + (bx + 2.5) + " " + (by + 3) + " " + (bz + 2.5) + " 0 60");
                cmd("attribute @s minecraft:block_interaction_range base set 6");
                cmd("give @s minecraft:stone_bricks 32");
                cmd("give @s minecraft:oak_stairs 8");
                cmd("give @s minecraft:oak_log 8");
                cmd("give @s minecraft:oak_slab 8");
                cmd("give @s minecraft:glass 8");
                cmd("give @s minecraft:chest 2");
                cmd("give @s minecraft:furnace 2");
                cmd("give @s minecraft:torch 8");
                next();
            }
            case 9 -> {
                if (!after(60)) return;
                if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("litematica")) {
                    results.add("AutoBuild: Litematica nicht geladen – übersprungen");
                    step = 12; stepStart = ticks;
                    return;
                }
                String err = dev.teizen.client.compat.LitematicaCompat.loadAndPlace("teizentest", new BlockPos(bx, by, bz));
                log("Schematic laden: " + (err == null ? "ok" : err) + ", Platzierungen=" + dev.teizen.client.compat.LitematicaCompat.placementCount());
                if (err != null) { results.add("AutoBuild: " + err); step = 12; stepStart = ticks; return; }
                next();
            }
            case 10 -> {
                if (!after(60)) return;
                int[] c = compareBuild(false);
                log("Schematic-Welt: " + c[2] + " Blöcke im Hologramm");
                shot("08a-hologramm");
                Commands.handle("///autobuild range 6");
                Commands.handle("///autobuild");
                next();
            }
            case 11 -> {
                int local = ticks - stepStart;
                if (local % 40 == 0) { int[] c = compareBuild(false); log("AutoBuild " + local + ": richtig " + c[0] + "/" + c[2] + ", falsch " + c[1]); }
                int[] c = compareBuild(false);
                if (!after(400) && c[0] < c[2]) return;
                c = compareBuild(true);
                results.add("AutoBuild: " + c[0] + "/" + c[2] + " Blöcke richtig, " + c[1] + " falsch (nach " + local + " Ticks)");
                shot("08-autobuild");
                Commands.handle("///autobuild");
                cmd("attribute @s minecraft:block_interaction_range base reset");
                next();
            }
            case 12 -> {
                if (!after(20)) return;
                moduleIndex = 0;
                next();
            }
            case 13 -> {
                // jede Funktion 30 Ticks an, dann aus
                if (moduleIndex >= mods.size()) { next(); return; }
                Module m = mods.get(moduleIndex);
                int local = ticks - stepStart;
                if (local == 1) {
                    log("teste " + m.name);
                    m.setEnabled(true);
                } else if (local == 25 && m.isEnabled() && moduleIndex % 6 == 0) {
                    shot("06-modul-" + m.name);
                } else if (local >= 30) {
                    results.add(m.name + (m.isEnabled() ? " ok" : " FEHLER (wurde abgeschaltet)"));
                    m.setEnabled(false);
                    moduleIndex++;
                    stepStart = ticks;
                }
            }
            case 14 -> {
                Commands.handle("///find biome plains 512");
                Commands.handle("///find structure village");
                Commands.handle("///set killaura reichweite 5");
                Commands.handle("///reach 6 7");
                next();
            }
            case 15 -> {
                if (!after(60)) return;
                shot("07-ende");
                next();
            }
            case 16 -> {
                if (!after(20)) return;
                finish();
            }
            default -> {}
        }
    }

    private static void finish() {
        log("ERGEBNISSE:");
        for (String r : results) log("  " + r);
        log("FERTIG");
        step = 99;
        mc.execute(mc::stop);
    }
}
