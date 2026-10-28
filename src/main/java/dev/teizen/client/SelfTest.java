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
    /** Wie oft Velocity/AntiKnockback einen Rückstoß verändert haben (aus dem Mixin). */
    public static int knockbacks;
    private static final Minecraft mc = Minecraft.getInstance();

    private static int ticks;
    private static int worldTicks;
    private static int step;
    private static int lastRespawn = -1000, deaths;
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

    private static int mineDone = -1;

    private static int countDiamondOre() {
        int n = 0;
        for (BlockPos p : BlockPos.betweenClosed(origin.offset(-12, -12, -12), origin.offset(12, 4, 12))) {
            if (mc.level.getBlockState(p).is(Blocks.DIAMOND_ORE)) n++;
        }
        return n;
    }

    private static int bx, by, bz;
    /** Wo die Test-Schematic (5x2x5) liegt. */
    static BlockPos buildOrigin() { return new BlockPos(bx, by, bz); }

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
        if (ticks > 20 * 60 * 20) { log("ZEITÜBERSCHREITUNG – beende"); finish(); return; }
        if (step == RECONNECT_STEP) { reconnectTick(); return; }
        if (step == COMBATLOG_STEP) { combatLogTick(); return; }
        if (mc.player == null || mc.level == null) return;
        // Sicherheitsnetz: stirbt der Testspieler (z.B. Monster in der Testwelt), sofort wiederbeleben und weitermachen
        if (mc.gui.screen() instanceof net.minecraft.client.gui.screens.DeathScreen || (mc.player.isDeadOrDying() && mc.player.deathTime > 10)) {
            if (ticks - lastRespawn > 40) {
                lastRespawn = ticks;
                deaths++;
                log("Testspieler gestorben (Schritt " + step + ") – wiederbeleben");
                mc.player.respawn();
                mc.gui.setScreen(null);
            }
            return;
        }
        if (deaths > 0 && ticks - lastRespawn == 40) cmd("effect give @s minecraft:instant_health 1 5");
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
                // keine natürlichen Monster in der Testwelt (sonst greifen Husks/Zombies den Testspieler an)
                cmd("gamerule spawn_mobs false");
                cmd("gamerule doMobSpawning false");
                cmd("kill @e[type=minecraft:husk]");
                cmd("kill @e[type=minecraft:zombie]");
                cmd("kill @e[type=minecraft:skeleton]");
                cmd("kill @e[type=minecraft:creeper]");
                cmd("kill @e[type=minecraft:spider]");
                cmd("give @s minecraft:diamond_pickaxe");
                cmd("give @s minecraft:stone 64");
                cmd("give @s minecraft:totem_of_undying");
                cmd("give @s minecraft:cooked_beef 16");
                cmd("fill ~-1 ~-6 ~-1 ~1 ~-2 ~1 minecraft:stone");
                cmd("setblock ~2 ~-3 ~2 minecraft:diamond_ore");
                cmd("setblock ~3 ~-4 ~2 minecraft:diamond_ore");
                cmd("setblock ~-4 ~-5 ~3 minecraft:diamond_ore");
                cmd("setblock ~4 ~ ~4 minecraft:chest");
                // Nur für schnelle Versuche: direkt bei einem bestimmten Schritt anfangen
                String from = System.getenv("TEIZEN_SELFTEST_FROM");
                if (from != null && !from.isBlank()) { log("starte bei Schritt " + from.trim()); jump(Integer.parseInt(from.trim())); return; }
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
                // flache, freie Fläche, damit der Zombie nicht in Bäumen/am Hang landet
                cmd("fill ~-5 ~-1 ~-5 ~5 ~-1 ~5 minecraft:stone");
                cmd("fill ~-5 ~ ~-5 ~5 ~4 ~5 minecraft:air");
                cmd("summon minecraft:zombie ~3 ~ ~");
                Commands.handle("///killaura");
                next();
            }
            case 6 -> {
                if (!after(100)) return;
                List<Zombie> z = mc.level.getEntitiesOfClass(Zombie.class, mc.player.getBoundingBox().inflate(8), e -> e.isAlive() && !e.isDeadOrDying());
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
                AutoMine am = TeizenClient.modules().get(AutoMine.class);
                if (!after(900) && countDiamondOre() > 0) { mineDone = -1; return; }
                int now = countDiamondOre();
                int dias = 0;
                for (int i = 0; i < 36; i++) if (mc.player.getInventory().getItem(i).is(net.minecraft.world.item.Items.DIAMOND)) dias += mc.player.getInventory().getItem(i).getCount();
                // nach dem letzten Erz noch kurz Zeit zum Einsammeln lassen
                if (mineDone < 0) mineDone = local;
                if (dias < 1 && local - mineDone < 160) return;
                log("AutoMine fertig: " + am.debugState());
                results.add("AutoMine: Diamant-Erz vorher " + diamondsBefore + ", nachher " + now + ", eingesammelt " + am.collected + " (Diamanten im Inventar " + dias
                        + ", nach " + local + " Ticks) " + (now < diamondsBefore && dias >= 1 ? "OK" : "FEHLER"));
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
                Commands.handle("///set autobuild laufen aus");
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
                    results.add(m.name + (m.isEnabled() ? " ok"
                            : TeizenClient.modules().crashed.contains(m.name) ? " FEHLER (Absturz, wurde abgeschaltet)" : " ok (schaltet sich ohne Aufgabe selbst aus)"));
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
                // Einstellungen über Mod Menu: Menü-Taste ändern
                if (!after(10)) return;
                boolean modMenu = net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("modmenu");
                net.minecraft.client.gui.screens.Screen s = null;
                try {
                    Class<?> mm = Class.forName("com.terraformersmc.modmenu.ModMenu");
                    s = (net.minecraft.client.gui.screens.Screen) mm.getMethod("getConfigScreen", String.class, net.minecraft.client.gui.screens.Screen.class)
                            .invoke(null, "teizenclient", null);
                } catch (Throwable t) {
                    log("Mod Menu Zugriff fehlgeschlagen: " + t);
                }
                results.add("Mod Menu geladen=" + modMenu + ", Einstellungen-Knopf=" + (s instanceof dev.teizen.client.gui.SettingsScreen ? "ok" : "FEHLT"));
                if (s == null) s = new dev.teizen.client.gui.SettingsScreen(null);
                mc.gui.setScreen(s);
                next();
            }
            case 17 -> {
                if (!after(20)) return;
                if (mc.gui.screen() instanceof dev.teizen.client.gui.SettingsScreen st) {
                    st.startListening();
                    st.applyKey(dev.teizen.client.compat.K.M);
                    boolean ok = TeizenClient.menuKey == dev.teizen.client.compat.K.M;
                    boolean saved = false;
                    try { saved = java.nio.file.Files.readString(mc.gameDirectory.toPath().resolve("config/teizenclient.json")).contains("\"menuTaste\": " + dev.teizen.client.compat.K.toGlfw(dev.teizen.client.compat.K.M)); } catch (Throwable ignored) {}
                    results.add("Menü-Taste ändern: " + (ok ? "ok (jetzt " + dev.teizen.client.gui.SettingsScreen.keyName(TeizenClient.menuKey) + ")" : "FEHLER") + ", gespeichert=" + saved);
                } else {
                    results.add("Menü-Taste ändern: FEHLER (Einstellungen nicht offen: " + mc.gui.screen() + ")");
                }
                next();
            }
            case 18 -> {
                if (!after(10)) return;
                shot("09-einstellungen");
                next();
            }
            case 19 -> {
                if (!after(20)) return;
                // zurück auf Standard
                TeizenClient.menuKey = dev.teizen.client.compat.K.RIGHT_SHIFT;
                dev.teizen.client.core.Config.save();
                mc.gui.setScreen(null);
                next();
            }
            case 20 -> {
                // Befehls-Vorschläge wie im normalen Spiel
                if (!after(10)) return;
                List<String> all = dev.teizen.client.core.CommandSuggestions.debugSuggest("///");
                log("Vorschläge für '///': " + all);
                List<String> set = dev.teizen.client.core.CommandSuggestions.debugSuggest("///set killaura ");
                log("Vorschläge für '///set killaura ': " + set);
                List<String> mine = dev.teizen.client.core.CommandSuggestions.debugSuggest("///mine di");
                log("Vorschläge für '///mine di': " + mine);
                List<String> fly = dev.teizen.client.core.CommandSuggestions.debugSuggest("///fl");
                log("Vorschläge für '///fl': " + fly);
                boolean ok = all.contains("//help") && all.contains("//killaura") && set.contains("reichweite")
                        && mine.contains("diamond") && fly.contains("//fly");
                results.add("Befehls-Vorschläge: " + (ok ? "ok (" + all.size() + " Befehle)" : "FEHLER"));
                // Ausführen über den echten Chat-Weg: genau einmal?
                int before = Commands.executed;
                mc.player.connection.sendCommand("//fly");
                mc.player.connection.sendCommand("//toggle fly");
                boolean flyOff = !TeizenClient.modules().byName("fly").isEnabled();
                mc.player.connection.sendCommand("//set fly tempo 3");
                double tempo = TeizenClient.modules().byName("fly").setting("tempo").num();
                int runs = Commands.executed - before;
                results.add("Befehl über Chat: " + (runs == 3 && tempo == 3 ? "ok" : "FEHLER") + " (ausgeführt " + runs + "x, Fly an/aus=" + (flyOff ? "ok" : "falsch") + ", tempo=" + tempo + ")");
                TeizenClient.modules().byName("fly").setting("tempo").setNumber(2);
                try {
                    // Chatfenster mit "///" öffnen (Konstruktor per Reflection, Parameter je nach Version)
                    for (var ctor : net.minecraft.client.gui.screens.ChatScreen.class.getConstructors()) {
                        Class<?>[] ps = ctor.getParameterTypes();
                        if (ps.length == 0 || ps[0] != String.class) continue;
                        Object[] args = new Object[ps.length];
                        args[0] = "///";
                        for (int i = 1; i < ps.length; i++) args[i] = ps[i] == boolean.class ? Boolean.FALSE : null;
                        mc.gui.setScreen((net.minecraft.client.gui.screens.Screen) ctor.newInstance(args));
                        break;
                    }
                } catch (Throwable t) {
                    log("Chatfenster öffnen fehlgeschlagen: " + t);
                }
                next();
            }
            case 21 -> {
                if (!after(20)) return;
                shot("10-vorschlaege");
                mc.gui.setScreen(null);
                next();
            }
            // ------------------------------------------------ Tasten in der Minecraft-Steuerung, Übersicht, FastEat
            case 22 -> {
                if (!after(10)) return;
                int teizen = 0;
                for (net.minecraft.client.KeyMapping k : mc.options.keyMappings) if (k.getName().startsWith("key.teizenclient.")) teizen++;
                // Menü + Übersicht + Elytra-Taste + eine je Funktion
                results.add("Tasten in Minecraft-Steuerung: " + (teizen == mods.size() + 3 ? "ok" : "FEHLER") + " (" + teizen + " Stück, erwartet " + (mods.size() + 3) + ")");
                log("Tasten-Kategorie: " + net.minecraft.client.resources.language.I18n.get("key.category.teizenclient.main")
                        + " / " + net.minecraft.client.resources.language.I18n.get("key.teizenclient.overview"));
                // im Client ändern -> muss in Minecraft ankommen
                TeizenClient.modules().byName("fly").key = dev.teizen.client.compat.K.G;
                next();
            }
            case 23 -> {
                if (!after(5)) return;
                net.minecraft.client.KeyMapping fly = net.minecraft.client.KeyMapping.get("key.teizenclient.module.fly");
                int bound = net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper.getBoundKeyOf(fly).getValue();
                boolean a = bound == dev.teizen.client.compat.K.G;
                // in Minecraft ändern -> muss im Client ankommen
                fly.setKey(dev.teizen.client.compat.V.KEYBOARD.getOrCreate(dev.teizen.client.compat.K.H));
                net.minecraft.client.KeyMapping.resetMapping();
                fastEatCheck = a;
                next();
            }
            case 24 -> {
                if (!after(5)) return;
                Module fly = TeizenClient.modules().byName("fly");
                boolean b = fly.key == dev.teizen.client.compat.K.H;
                results.add("Taste Client→Minecraft: " + (fastEatCheck ? "ok" : "FEHLER") + ", Minecraft→Client: " + (b ? "ok" : "FEHLER (" + fly.key + ")"));
                fly.key = -1;
                mc.gui.setScreen(new dev.teizen.client.gui.Overview());
                next();
            }
            case 25 -> {
                if (!after(20)) return;
                shot("11-uebersicht");
                results.add("Übersicht: " + (mc.gui.screen() instanceof dev.teizen.client.gui.Overview ? "ok" : "FEHLER"));
                mc.gui.setScreen(null);
                cmd("give @s minecraft:bread 8");
                cmd("give @s minecraft:golden_apple 2");
                next();
            }
            case 26 -> {
                if (!after(20)) return;
                Commands.handle("///fasteat");
                next();
            }
            case 27 -> {
                if (!after(120)) return;
                results.add("FastEat an: Brot " + eatSeconds(net.minecraft.world.item.Items.BREAD) + "s (soll 0.2), Goldapfel "
                        + eatSeconds(net.minecraft.world.item.Items.GOLDEN_APPLE) + "s (soll unverändert 1.6)");
                Commands.handle("///fasteat");
                next();
            }
            case 28 -> {
                if (!after(60)) return;
                results.add("FastEat aus: Brot " + eatSeconds(net.minecraft.world.item.Items.BREAD) + "s (soll wieder 1.6)");
                next();
            }
            default -> NewTests.tick(step);
        }
    }

    // ================================================================== Runde 3: neue Funktionen
    static final int RECONNECT_STEP = 200;
    private static boolean sawDisconnect;
    private static int reconnectStart, backTicks;

    /** Läuft auch ohne Welt (nach dem Rauswurf). */
    private static void reconnectTick() {
        if (mc.player == null || mc.level == null || mc.gui.screen() instanceof net.minecraft.client.gui.screens.DisconnectedScreen) {
            if (!sawDisconnect) log("Verbindung getrennt (Bildschirm: " + (mc.gui.screen() == null ? "-" : mc.gui.screen().getClass().getSimpleName()) + ")");
            sawDisconnect = true;
            backTicks = 0;
        } else if (sawDisconnect) {
            if (++backTicks == 60) {
                var ar = TeizenClient.modules().get(dev.teizen.client.modules.AutoReconnect.class);
                results.add("AutoReconnect: getrennt=ja, wieder verbunden=ja (nach " + (ticks - reconnectStart) + " Ticks, " + ar.reconnects + " Versuch) OK");
                shot("20-reconnect");
            }
            if (backTicks >= 80) { step = 160; stepStart = ticks; return; } // weiter mit CombatLog
        }
        if (ticks - reconnectStart > 1200) {
            results.add("AutoReconnect: FEHLER (getrennt=" + sawDisconnect + ", wieder da=" + (mc.player != null) + ")");
            finish();
        }
    }

    // ---------------------------------------------------------------- CombatLog (ganz am Ende, trennt die Verbindung)
    static final int COMBATLOG_STEP = 201;
    private static int combatStart, disconnectedAt = -1;
    static boolean combatStillInWithTotem;

    static void startCombatLogWait(boolean stillIn) {
        combatStillInWithTotem = stillIn;
        combatStart = ticks;
        disconnectedAt = -1;
        step = COMBATLOG_STEP;
        stepStart = ticks;
    }

    private static void combatLogTick() {
        boolean gone = mc.player == null || mc.gui.screen() instanceof net.minecraft.client.gui.screens.DisconnectedScreen;
        if (gone && disconnectedAt < 0) {
            disconnectedAt = ticks;
            log("CombatLog: Verbindung getrennt nach " + (ticks - combatStart) + " Ticks");
        }
        if (disconnectedAt >= 0 && ticks - disconnectedAt == 20) shot("25-combatlog");
        if (disconnectedAt >= 0 && ticks - disconnectedAt >= 140) {
            boolean back = mc.player != null && mc.level != null;
            results.add("CombatLog: mit Totem geblieben=" + (combatStillInWithTotem ? "ja" : "nein") + ", ohne Totem rausgegangen=ja (nach "
                    + (disconnectedAt - combatStart) + " Ticks), AutoReconnect hat NICHT neu verbunden=" + (back ? "nein" : "ja") + " "
                    + (combatStillInWithTotem && !back ? "OK" : "FEHLER"));
            finish();
            return;
        }
        if (disconnectedAt < 0 && ticks - combatStart > 400) {
            results.add("CombatLog: FEHLER (nicht rausgegangen)");
            finish();
        }
    }

    static void jump(int s) { step = s; stepStart = ticks; }

    static void startReconnect() {
        reconnectStart = ticks;
        sawDisconnect = false;
        step = RECONNECT_STEP;
        stepStart = ticks;
    }

    static void log2(String s) { log(s); }
    static void cmd2(String c) { cmd(c); }
    static void shot2(String n) { shot(n); }
    static boolean after2(int t) { return after(t); }
    static void next2() { next(); }
    static int local() { return ticks - stepStart; }
    static void result(String r) { results.add(r); }
    static BlockPos origin() { return origin; }
    static void finish2() { finish(); }

    private static boolean fastEatCheck;

    /** Ess-Dauer des ersten Stapels dieses Items im Inventar (-1 = keins). */
    private static float eatSeconds(net.minecraft.world.item.Item item) {
        for (int i = 0; i < 36; i++) {
            net.minecraft.world.item.ItemStack s = mc.player.getInventory().getItem(i);
            if (!s.is(item)) continue;
            var c = s.get(net.minecraft.core.component.DataComponents.CONSUMABLE);
            return c == null ? -1 : Math.round(c.consumeSeconds() * 100) / 100f;
        }
        return -1;
    }

    private static void finish() {
        log("ERGEBNISSE:");
        for (String r : results) log("  " + r);
        log("FERTIG");
        step = 99;
        mc.execute(mc::stop);
    }
}
