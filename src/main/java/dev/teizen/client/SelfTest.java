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
import net.minecraft.world.entity.monster.Zombie;
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

    private static boolean after(int t) { return ticks - stepStart >= t; }

    private static void next() {
        step++;
        stepStart = ticks;
    }

    public static void tick() {
        if (!ACTIVE) return;
        ticks++;
        if (ticks % 200 == 0) log("tick " + ticks + " step " + step + " screen=" + (mc.gui.screen() == null ? "-" : mc.gui.screen().getClass().getSimpleName()));
        if (ticks > 20 * 60 * 8) { log("ZEITÜBERSCHREITUNG – beende"); finish(); return; }
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
                if (!after(400)) return;
                int now = countDiamondOre();
                results.add("AutoMine: Diamant-Erz vorher " + diamondsBefore + ", nachher " + now);
                shot("05-automine");
                Commands.handle("///mine stop");
                moduleIndex = 0;
                next();
            }
            case 8 -> {
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
            case 9 -> {
                Commands.handle("///find biome plains 512");
                Commands.handle("///find structure village");
                Commands.handle("///set killaura reichweite 5");
                Commands.handle("///reach 6 7");
                next();
            }
            case 10 -> {
                if (!after(60)) return;
                shot("07-ende");
                next();
            }
            case 11 -> {
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
