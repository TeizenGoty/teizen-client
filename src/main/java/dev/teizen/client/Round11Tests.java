package dev.teizen.client;

import dev.teizen.client.core.Commands;
import dev.teizen.client.core.Module;
import dev.teizen.client.core.Profiles;
import dev.teizen.client.gui.HudEditorScreen;
import dev.teizen.client.gui.HudLayout;
import dev.teizen.client.gui.Overview;
import dev.teizen.client.modules.Criticals;
import dev.teizen.client.util.Inv;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Locale;

/** Selbsttest Runde 11: Criticals (26.3 ohne Kick), Profile, HUD-Editor. */
final class Round11Tests {
    private static final Minecraft mc = Minecraft.getInstance();

    private static BlockPos area;
    private static float h0;
    private static int d0;
    private static HudLayout.Box box0;
    private static boolean flag1, flag2;
    private static String text = "";
    private static int n0;
    private static float maxY;
    private static BlockPos wrongPos;
    private static Long seed0;
    private static java.util.List<int[]> pasteRegion = java.util.List.of();
    /** Was vor Runde 11 an war (danach wieder so herstellen, z.B. AutoTotem für den CombatLog-Test). */
    private static final java.util.List<String> before = new java.util.ArrayList<>();

    private Round11Tests() {}

    /** Fly-AntiKick-Test: Server hat uns rausgeworfen ("schwebt zu lange") -> Ergebnis melden, Test beenden. */
    /** Chatfenster öffnen (Konstruktor per Reflection, Parameter je nach Version). */
    private static void openChat() {
        try {
            for (var ctor : net.minecraft.client.gui.screens.ChatScreen.class.getConstructors()) {
                Class<?>[] ps = ctor.getParameterTypes();
                if (ps.length == 0 || ps[0] != String.class) continue;
                Object[] args = new Object[ps.length];
                args[0] = "";
                for (int i = 1; i < ps.length; i++) args[i] = ps[i] == boolean.class ? Boolean.FALSE : null;
                mc.gui.setScreen((net.minecraft.client.gui.screens.Screen) ctor.newInstance(args));
                return;
            }
        } catch (Throwable t) {
            log("Chatfenster öffnen fehlgeschlagen: " + t);
        }
    }

    /** Text im Eingabefeld des offenen Chatfensters. */
    private static String chatInput() {
        if (!(mc.gui.screen() instanceof net.minecraft.client.gui.screens.ChatScreen cs)) return "(kein Chat offen)";
        for (Class<?> c = cs.getClass(); c != null; c = c.getSuperclass()) {
            for (var f : c.getDeclaredFields()) {
                if (f.getType() != net.minecraft.client.gui.components.EditBox.class) continue;
                try {
                    f.setAccessible(true);
                    return ((net.minecraft.client.gui.components.EditBox) f.get(cs)).getValue();
                } catch (Throwable ignored) {}
            }
        }
        return "(Feld nicht gefunden)";
    }

    private static java.nio.file.Path typeFile(String n) { return mc.gameDirectory.toPath().resolve(n); }

    /** Bittet ci/typer.sh, diesen Text wie eine echte Tastatur zu tippen. @return false wenn es kein CI-Tipper gibt */
    private static boolean requestTyping(String s) {
        if (System.getenv("CI") == null) return false;
        try {
            java.nio.file.Files.deleteIfExists(typeFile("teizen-typed.txt"));
            java.nio.file.Files.writeString(typeFile("teizen-type.txt"), s);
            log("Echt-Tippen angefordert: '" + s + "'");
            return true;
        } catch (Throwable t) {
            log("Echt-Tippen anfordern ging nicht: " + t);
            return false;
        }
    }

    private static boolean typingDone() { return java.nio.file.Files.exists(typeFile("teizen-typed.txt")); }

    private static void flyLog() {
        Module fl = TeizenClient.modules().byName("fly");
        log(String.format(Locale.ROOT, "Fly-AntiKick %d: y=%.2f fliegt=%s an=%s Durchgänge=%d", SelfTest.local(), mc.player.getY(),
                mc.player.getAbilities().flying, fl.isEnabled(), ((dev.teizen.client.modules.Fly) fl).protectedPackets - n0));
    }

    static void flyKicked() {
        if (mc.options != null) mc.options.keyJump.setDown(false);
        result("Fly-AntiKick: rausgeworfen=ja (Server: schwebt zu lange) nach " + SelfTest.local() + " Ticks in Schritt " + SelfTest.currentStep() + " FEHLER");
        SelfTest.finish2();
    }

    private static void cmd(String c) { SelfTest.cmd2(c); }
    private static boolean after(int t) { return SelfTest.after2(t); }
    private static void next() { SelfTest.next2(); }
    private static void result(String r) { SelfTest.result(r); }
    private static void log(String s) { SelfTest.log2(s); }
    private static void shot(String n) { SelfTest.shot2(n); }
    private static void t(String c) { Commands.handle(c); }
    private static String ok(boolean b) { return b ? "OK" : "FEHLER"; }
    private static String yes(boolean b) { return b ? "ja" : "nein"; }

    private static String xyz(BlockPos p, double dx, double dy, double dz) {
        return String.format(Locale.ROOT, "%.2f %.2f %.2f", p.getX() + dx, p.getY() + dy, p.getZ() + dz);
    }

    private static String blk(BlockPos p) { return p.getX() + " " + p.getY() + " " + p.getZ(); }

    private static Module mod(String n) { return TeizenClient.modules().byName(n); }

    private static LivingEntity husk() {
        List<LivingEntity> l = mc.level.getEntitiesOfClass(LivingEntity.class, new AABB(area).inflate(6),
                e -> e instanceof net.minecraft.world.entity.monster.zombie.Husk && e.isAlive());
        return l.isEmpty() ? null : l.get(0);
    }

    private static void click(net.minecraft.client.gui.screens.Screen s, double[] c, int button) {
        s.mouseClicked(new MouseButtonEvent(c[0], c[1], new MouseButtonInfo(dev.teizen.client.compat.V.rawMouse(button), 0)), false);
    }

    static void step(int step, BlockPos o) {
        switch (step) {
            // ---------------------------------------------- Criticals: Krit-Schaden, ohne Rauswurf (26.3: 1 Positions-Paket pro Tick)
            case 500 -> {
                // erst hinteleportieren (Chunks laden), sonst geht /fill nicht ("That position is not loaded")
                if (SelfTest.local() <= 1) cmd(String.format(Locale.ROOT, "tp @s %s -90 0", xyz(o.offset(-60, 0, 90), 0.5, 0, 0.5)));
                if (!after(40)) return;
                area = o.offset(-60, 0, 90);
                before.clear();
                for (Module m : TeizenClient.modules().all()) if (m.isEnabled()) before.add(m.name);
                cmd("fill " + blk(area.offset(-6, -1, -6)) + " " + blk(area.offset(6, -1, 6)) + " minecraft:stone");
                cmd("fill " + blk(area.offset(-6, 0, -6)) + " " + blk(area.offset(6, 4, 6)) + " minecraft:air");
                cmd("kill @e[type=minecraft:husk]");
                cmd("clear @s");
                cmd("item replace entity @s hotbar.0 with minecraft:iron_sword");
                cmd("effect give @s minecraft:instant_health 1 5");
                cmd("summon minecraft:husk " + xyz(area, 2.5, 0, 0.5) + " {NoAI:1b,PersistenceRequired:1b,Silent:1b}");
                cmd(String.format(Locale.ROOT, "tp @s %s -90 0", xyz(area, 0.5, 0, 0.5)));
                for (String m : new String[]{"killaura", "triggerbot", "aimassist", "reach", "shieldbreaker", "maceswap", "spearswap", "autosprint"}) {
                    Module x = mod(m);
                    if (x != null && x.isEnabled()) x.setEnabled(false);
                }
                if (!mod("criticals").isEnabled()) t("///criticals");
                next();
            }
            case 501 -> {
                if (!after(40)) return;
                Inv.select(0);
                if (mc.player.isSprinting()) mc.player.setSprinting(false);
                LivingEntity h = husk();
                h0 = h == null ? -1 : h.getHealth();
                d0 = Criticals.delayedHits;
                flag1 = mc.player.onGround();
                if (h != null) mc.gameMode.attack(mc.player, h);
                next();
            }
            case 502 -> {
                if (!after(20)) return;
                LivingEntity h = husk();
                float h1 = h == null ? 0 : h.getHealth();
                float dmg = h0 - h1;
                boolean connected = mc.getConnection() != null && mc.player != null && mc.level != null;
                boolean delayed = Criticals.delayedHits > d0;
                boolean way = dev.teizen.client.compat.V.MULTI_MOVE_PACKETS ? !delayed : delayed;
                // Eisenschwert: normal 6, Krit 9 Schaden
                result("Criticals (" + dev.teizen.client.compat.V.MC + "): am Boden=" + yes(flag1) + ", Schaden=" + String.format(Locale.ROOT, "%.1f", dmg)
                        + " (Krit = 9, normal = 6), " + (dev.teizen.client.compat.V.MULTI_MOVE_PACKETS ? "2 Pakete sofort" : "über 2 Ticks verteilt") + "=" + yes(way)
                        + ", nicht rausgeworfen=" + yes(connected) + " " + ok(h0 > 0 && dmg >= 8.5f && connected && way));
                t("///criticals");
                cmd("kill @e[type=minecraft:husk]");
                next();
            }
            // ---------------------------------------------- Profile
            case 503 -> {
                if (!after(10)) return;
                Profiles.off(false);
                dev.teizen.client.modules.PanicButton.panic();
                Profiles.toggle("pvp");
                flag1 = "PvP".equals(Profiles.active) && mod("killaura").isEnabled() && mod("criticals").isEnabled() && mod("autototem").isEnabled()
                        && mod("antiknockback").isEnabled();
                Profiles.toggle("pvp");
                flag2 = Profiles.active == null && !mod("killaura").isEnabled() && !mod("criticals").isEnabled() && !mod("autototem").isEnabled();
                text = "PvP an: KillAura/Criticals/AutoTotem/AntiKnockback=" + yes(flag1) + ", PvP aus: alles wieder aus=" + yes(flag2);
                // eigenes Profil mit Einstellung
                t("///fullbright");
                t("///tracers");
                t("///set tracers distanz 77");
                boolean saved = Profiles.save("testprofil");
                t("///set tracers distanz 20");
                dev.teizen.client.modules.PanicButton.panic();
                Profiles.toggle("testprofil");
                boolean ownOn = mod("fullbright").isEnabled() && mod("tracers").isEnabled() && Math.round(mod("tracers").setting("distanz").num()) == 77;
                Profiles.toggle("testprofil");
                boolean ownOff = !mod("fullbright").isEnabled() && !mod("tracers").isEnabled() && Math.round(mod("tracers").setting("distanz").num()) == 20;
                boolean inConfig = false;
                try {
                    String cfg = java.nio.file.Files.readString(net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("teizenclient.json"));
                    inConfig = cfg.contains("testprofil");
                } catch (Exception e) {
                    log("Config lesen: " + e);
                }
                boolean deleted = Profiles.delete("testprofil") && Profiles.get("testprofil") == null;
                result("Profile: " + text + "; eigenes Profil gespeichert=" + yes(saved) + ", an (mit Einstellung 77)=" + yes(ownOn) + ", aus (Einstellung wieder 20)=" + yes(ownOff)
                        + ", in der Config=" + yes(inConfig) + ", gelöscht=" + yes(deleted) + " " + ok(flag1 && flag2 && saved && ownOn && ownOff && inConfig && deleted));
                mc.gui.setScreen(new Overview());
                next();
            }
            case 504 -> {
                if (!after(10)) return;
                if (!(mc.gui.screen() instanceof Overview ov)) { result("Profil-Knöpfe: Übersicht nicht offen FEHLER"); next(); return; }
                double[] c = ov.profileButtonCenter("Erkunden");
                if (c != null) click(ov, c, 0);
                flag1 = c != null && "Erkunden".equals(Profiles.active) && mod("playeresp").isEnabled() && mod("fullbright").isEnabled();
                next();
            }
            case 505 -> {
                if (!after(10)) return;
                shot("61-profile");
                var ov = (Overview) mc.gui.screen();
                double[] c = ov.profileButtonCenter("Erkunden");
                if (c != null) click(ov, c, 0);
                flag2 = Profiles.active == null && !mod("playeresp").isEnabled();
                result("Profil-Knöpfe in der Übersicht: Erkunden an per Klick=" + yes(flag1) + ", nochmal Klick = aus=" + yes(flag2) + " " + ok(flag1 && flag2));
                // HUD-Knopf in der Übersicht
                click(ov, ov.hudButtonCenter(), 0);
                next();
            }
            // ---------------------------------------------- HUD-Editor
            case 506 -> {
                if (!after(10)) return;
                boolean opened = mc.gui.screen() instanceof HudEditorScreen;
                mc.gui.setScreen(null);
                HudLayout.resetAll();
                t("///fullbright");
                t("///playerfinder");
                result("HUD-Knopf in der Übersicht öffnet den HUD-Editor=" + yes(opened) + " " + ok(opened));
                next();
            }
            case 507 -> {
                if (!after(10)) return;
                t("///hud verschieben");
                next();
            }
            case 508 -> {
                if (!after(10)) return;
                if (!(mc.gui.screen() instanceof HudEditorScreen ed)) { result("HUD-Editor: ///hud verschieben öffnet nichts FEHLER"); next(); return; }
                flag1 = HudLayout.visible("liste");
                box0 = HudLayout.screenBox("liste");
                double cx = (box0.x1() + box0.x2()) / 2.0, cy = (box0.y1() + box0.y2()) / 2.0;
                boolean found = "liste".equals(ed.at(cx, cy));
                ed.startDrag("liste", cx, cy);
                ed.dragTo(cx - 120, cy + 60);
                ed.stopDrag();
                HudLayout.Box b1 = HudLayout.screenBox("liste");
                flag2 = found && b1.x1() == box0.x1() - 120 && b1.y1() == box0.y1() + 60;
                text = "Liste sichtbar=" + yes(flag1) + ", mit der Maus gefunden=" + yes(found) + ", verschoben um (-120, +60)=" + yes(flag2);
                shot("62-hud-editor");
                next();
            }
            case 509 -> {
                if (!after(5)) return;
                mc.gui.setScreen(null);
                next();
            }
            case 510 -> {
                if (!after(15)) return;
                shot("63-hud-verschoben");
                HudLayout.Box b = HudLayout.screenBox("liste");
                boolean stays = b.x1() == box0.x1() - 120 && b.y1() == box0.y1() + 60;
                boolean inConfig = false;
                try {
                    String cfg = java.nio.file.Files.readString(net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("teizenclient.json"));
                    inConfig = cfg.contains("hudPositionen") && cfg.contains("\"liste\"");
                } catch (Exception e) {
                    log("Config lesen: " + e);
                }
                // Rechtsklick im Editor = zurück an den Standard-Platz
                HudEditorScreen ed = new HudEditorScreen(null);
                mc.gui.setScreen(ed);
                double[] c = {(b.x1() + b.x2()) / 2.0, (b.y1() + b.y2()) / 2.0};
                click(ed, c, 1);
                boolean back = !HudLayout.moved("liste");
                mc.gui.setScreen(null);
                result("HUD-Editor: " + text + ", bleibt nach dem Schließen dort=" + yes(stays) + ", gespeichert=" + yes(inConfig) + ", Rechtsklick = zurück=" + yes(back) + " "
                        + ok(flag1 && flag2 && stays && inConfig && back));
                HudLayout.resetAll();
                dev.teizen.client.core.Config.save();
                next();
            }
            // ---------------------------------------------- ESP-Leistung: viele Mobs, Items, Kisten + Linien – alt (Pixel-Linien) gegen neu
            case 511 -> {
                if (!after(10)) return;
                dev.teizen.client.modules.PanicButton.panic();
                cmd(String.format(Locale.ROOT, "tp @s %s -90 0", xyz(area, 0.5, 0, 0.5)));
                cmd("kill @e[type=minecraft:husk]");
                for (int i = 0; i < 30; i++) {
                    double ang = i * Math.PI * 2 / 30;
                    cmd("summon minecraft:husk " + xyz(area, 0.5 + Math.cos(ang) * 5, 0, 0.5 + Math.sin(ang) * 5) + " {NoAI:1b,PersistenceRequired:1b,Silent:1b,Invulnerable:1b}");
                }
                for (int i = 0; i < 40; i++)
                    cmd("summon minecraft:item " + xyz(area, -4 + (i % 8), 0.2, -4 + (i / 8)) + " {Item:{id:\"minecraft:diamond\",count:1},PickupDelay:32767,Age:-32768}");
                cmd("fill " + blk(area.offset(-6, 0, 5)) + " " + blk(area.offset(5, 0, 6)) + " minecraft:barrel");
                t("///set itemesp filter diamond");
                t("///set playeresp mobs an");
                for (String m : new String[]{"playeresp", "itemesp", "storageesp", "nametags", "armoresp", "tracers"}) t("///" + m);
                next();
            }
            case 512 -> {
                if (!after(60)) return;
                dev.teizen.client.util.Draw.legacyLines = true;
                dev.teizen.client.gui.Hud.renderNanos = 0;
                dev.teizen.client.gui.Hud.renderFrames = 0;
                next();
            }
            case 513 -> {
                if (!after(100)) return;
                long f = Math.max(1, dev.teizen.client.gui.Hud.renderFrames);
                h0 = dev.teizen.client.gui.Hud.renderNanos / (float) f / 1_000_000f;
                d0 = mc.getFps();
                dev.teizen.client.util.Draw.legacyLines = false;
                dev.teizen.client.gui.Hud.renderNanos = 0;
                dev.teizen.client.gui.Hud.renderFrames = 0;
                next();
            }
            case 514 -> {
                if (!after(100)) return;
                long f = Math.max(1, dev.teizen.client.gui.Hud.renderFrames);
                float now = dev.teizen.client.gui.Hud.renderNanos / (float) f / 1_000_000f;
                int fps = mc.getFps();
                shot("64-esp-viele");
                var ie = TeizenClient.modules().get(dev.teizen.client.modules.ItemESP.class);
                boolean faster = now < h0 * 0.8f;
                result(String.format(Locale.ROOT, "ESP-Leistung (30 Mobs leuchten, %d Items, Fässer, Linien): HUD vorher %.2f ms/Bild (%d FPS), jetzt %.2f ms/Bild (%d FPS), schneller=%s %s",
                        ie.total, h0, d0, now, fps, yes(faster), ok(faster && now < 6f)));
                cmd("kill @e[type=minecraft:husk]");
                cmd("kill @e[type=minecraft:item]");
                cmd("fill " + blk(area.offset(-6, 0, 5)) + " " + blk(area.offset(5, 0, 6)) + " minecraft:air");
                t("///set playeresp mobs aus");
                dev.teizen.client.modules.PanicButton.panic();
                next();
            }
            // ---------------------------------------------- AutoBuild baut auch ab: Erde wo die Schematic Luft hat (Loch), falsche Blöcke weg
            case 515 -> {
                if (!after(10)) return;
                BlockPos b = SelfTest.buildOrigin();
                if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("litematica") || dev.teizen.client.compat.LitematicaCompat.placementCount() == 0) {
                    result("AutoBuild abbauen: Litematica/Schematic nicht da – übersprungen");
                    SelfTest.jump(519);
                    return;
                }
                cmd(String.format(Locale.ROOT, "tp @s %.1f %d %.1f 0 60", b.getX() + 2.5, b.getY() + 3, b.getZ() + 2.5));
                next();
            }
            case 516 -> {
                if (!after(30)) return;
                BlockPos b = SelfTest.buildOrigin();
                cmd("setblock " + (b.getX() + 2) + " " + (b.getY() + 2) + " " + (b.getZ() + 2) + " minecraft:barrier");
                cmd("clear @s");
                cmd("item replace entity @s hotbar.0 with minecraft:diamond_pickaxe");
                cmd("item replace entity @s hotbar.1 with minecraft:diamond_shovel");
                for (String it : new String[]{"stone_bricks 32", "oak_stairs 8", "oak_log 8", "oak_slab 8", "glass 8", "chest 2", "furnace 2", "torch 8"}) cmd("give @s minecraft:" + it);
                t("///set autobuild laufen aus");
                cmd("attribute @s minecraft:block_interaction_range base set 6");
                // überall, wo die Schematic Luft will: Erde hin (= Loch muss gegraben werden); ein richtiger Block -> Goldblock (falsch)
                n0 = 0;
                wrongPos = null;
                for (BlockPos p : BlockPos.betweenClosed(b.getX(), b.getY(), b.getZ(), b.getX() + 4, b.getY() + 1, b.getZ() + 4)) {
                    var want = dev.teizen.client.compat.LitematicaCompat.schematicState(p);
                    if (want == null) continue;
                    if (want.isAir()) { cmd("setblock " + blk(p) + " minecraft:dirt"); n0++; }
                    else if (wrongPos == null && want.isCollisionShapeFullBlock(mc.level, p)) { wrongPos = p.immutable(); cmd("setblock " + blk(p) + " minecraft:gold_block"); }
                }
                next();
            }
            case 517 -> {
                if (!after(20)) return;
                d0 = TeizenClient.modules().get(dev.teizen.client.modules.AutoBuild.class).dugTotal;
                t("///autobuild");
                shot("65-autobuild-vorher");
                next();
            }
            case 518 -> {
                BlockPos b = SelfTest.buildOrigin();
                int air = 0, airOk = 0, blocks = 0, blocksOk = 0;
                for (BlockPos p : BlockPos.betweenClosed(b.getX(), b.getY(), b.getZ(), b.getX() + 4, b.getY() + 1, b.getZ() + 4)) {
                    var want = dev.teizen.client.compat.LitematicaCompat.schematicState(p);
                    if (want == null) continue;
                    var have = mc.level.getBlockState(p);
                    if (want.isAir()) { air++; if (have.isAir()) airOk++; }
                    else { blocks++; if (have == want) blocksOk++; }
                }
                boolean done = airOk == air && blocksOk == blocks;
                if (!done && !after(900)) return;
                var ab = TeizenClient.modules().get(dev.teizen.client.modules.AutoBuild.class);
                boolean wrongFixed = wrongPos != null && mc.level.getBlockState(wrongPos) == dev.teizen.client.compat.LitematicaCompat.schematicState(wrongPos);
                boolean underFeet = !mc.level.getBlockState(mc.player.blockPosition().below()).isAir();
                shot("66-autobuild-abgebaut");
                result("AutoBuild abbauen: Erde wo Luft sein soll " + airOk + "/" + air + " weggegraben (" + n0 + " hingesetzt), falscher Block (Gold) ersetzt=" + yes(wrongFixed)
                        + ", alle Blöcke richtig " + blocksOk + "/" + blocks + ", Boden unter mir noch da=" + yes(underFeet) + " (nach " + SelfTest.local() + " Ticks) "
                        + ok(n0 > 0 && done && wrongFixed && underFeet));
                if (ab.isEnabled()) t("///autobuild");
                cmd("attribute @s minecraft:block_interaction_range base reset");
                SelfTest.jump(527);
            }
            // ---------------------------------------------- Schematic-Fenster: listen/suchen + sofort einfügen (mit OP)
            case 527 -> {
                if (!after(10)) return;
                var scr = new dev.teizen.client.gui.SchematicsScreen(null);
                boolean lists = scr.testListContains("teizentest");
                int all = scr.testSearch("");
                int none = scr.testSearch("gibtsnicht_xyz");
                int hit = scr.testSearch("teizentest");
                boolean filters = all >= 1 && none == 0 && hit >= 1;
                result("Schematic-Fenster: Test-Schematic gelistet=" + yes(lists) + ", Suche filtert (" + all + "→" + hit + ", nichts=" + none + ") " + ok(lists && filters));
                shot("66b-schematic-fenster");
                // Bau-Bereich leeren, damit das Einfügen wirklich Blöcke setzen muss
                pasteRegion = dev.teizen.client.compat.LitematicaCompat.regionBoxes();
                for (int[] r : pasteRegion) cmd("fill " + r[0] + " " + r[1] + " " + r[2] + " " + r[3] + " " + r[4] + " " + r[5] + " minecraft:air");
                next();
            }
            case 528 -> {
                if (!after(10)) return;
                n0 = dev.teizen.client.util.SchematicPaste.start();
                log("Einfügen gestartet: " + n0 + " Blöcke");
                next();
            }
            case 529 -> {
                if (dev.teizen.client.util.SchematicPaste.running()) { if (!after(600)) return; }
                else if (!after(40)) return; // kurz warten, bis die gesetzten Blöcke beim Client ankommen
                int blocks = 0, okc = 0;
                for (int[] r : pasteRegion) {
                    for (BlockPos p : BlockPos.betweenClosed(r[0], r[1], r[2], r[3], r[4], r[5])) {
                        var want = dev.teizen.client.compat.LitematicaCompat.schematicState(p);
                        if (want == null || want.isAir()) continue;
                        blocks++;
                        if (mc.level.getBlockState(p) == want) okc++;
                    }
                }
                boolean good = n0 > 0 && blocks > 0 && okc == blocks;
                shot("66c-schematic-eingefuegt");
                result("Schematic einfügen (sofort, mit OP): " + okc + "/" + blocks + " Blöcke gesetzt (Start " + n0 + ") " + ok(good));
                dev.teizen.client.util.SchematicPaste.cancel();
                SelfTest.jump(519);
            }
            // ---------------------------------------------- Favorit per Mittelklick (prüft die Maustasten-Zuordnung für 26.3)
            case 519 -> {
                if (!after(5)) return;
                dev.teizen.client.modules.PanicButton.panic();
                mc.gui.setScreen(new Overview());
                next();
            }
            case 520 -> {
                if (!after(10)) return;
                if (!(mc.gui.screen() instanceof Overview ov)) { result("Favorit-Test: Übersicht nicht offen FEHLER"); SelfTest.jump(521); return; }
                double[] c = ov.firstCellCenter(Module.Category.KAMPF);
                boolean faved = false;
                if (c != null) {
                    Module first = ov.shownModules().stream().filter(m -> m.category == Module.Category.KAMPF).findFirst().orElse(null);
                    boolean was = first != null && first.favorite;
                    ov.mouseClicked(new MouseButtonEvent(c[0], c[1], new MouseButtonInfo(dev.teizen.client.compat.V.rawMouse(2), 0)), false);
                    faved = first != null && first.favorite != was;
                    if (first != null) first.favorite = was;
                }
                result("Favorit per Mittelklick in der Übersicht (Maustaste 26.2/26.3): " + yes(faved) + " " + ok(faved));
                mc.gui.setScreen(null);
                next();
            }
            case 521 -> {
                if (!after(5)) return;
                dev.teizen.client.modules.PanicButton.panic();
                // ---- HighJump (mit OP): springt wirklich höher, kein Zurückziehen
                area = o.offset(40, 0, 40);
                cmd("fill " + blk(area.offset(-2, -1, -2)) + " " + blk(area.offset(2, -1, 2)) + " minecraft:stone");
                cmd("fill " + blk(area.offset(-2, 0, -2)) + " " + blk(area.offset(2, 20, 2)) + " minecraft:air");
                cmd(String.format(Locale.ROOT, "tp @s %.1f %d %.1f 0 0", area.getX() + 0.5, area.getY(), area.getZ() + 0.5));
                t("///set highjump hoehe 5");
                next();
            }
            case 522 -> {
                if (!after(20)) return;
                h0 = (float) mc.player.getY();
                maxY = h0;
                t("///highjump");
                mc.options.keyJump.setDown(true);
                n0 = 0;
                next();
            }
            case 523 -> {
                maxY = Math.max(maxY, (float) mc.player.getY());
                if (!after(40)) return;
                mc.options.keyJump.setDown(false);
                float rise = maxY - h0;
                t("///highjump");
                result("HighJump (hoehe 5, mit OP): hochgekommen " + String.format(Locale.ROOT, "%.1f", rise) + " Blöcke (normal ~1.25) " + ok(rise > 3));
                next();
            }
            case 524 -> {
                if (!after(10)) return;
                // ---- AutoClutch: Sturz aus großer Höhe, Wassereimer rettet
                area = o.offset(40, 0, 60);
                cmd("fill " + blk(area.offset(-3, -1, -3)) + " " + blk(area.offset(3, -1, 3)) + " minecraft:stone");
                cmd("fill " + blk(area.offset(-3, 0, -3)) + " " + blk(area.offset(3, 40, 3)) + " minecraft:air");
                cmd("clear @s");
                cmd("item replace entity @s hotbar.0 with minecraft:water_bucket");
                cmd("effect give @s minecraft:instant_health 1 10");
                cmd(String.format(Locale.ROOT, "tp @s %.1f %d %.1f 0 90", area.getX() + 0.5, area.getY() + 14, area.getZ() + 0.5));
                var ac = TeizenClient.modules().get(dev.teizen.client.modules.AutoClutch.class);
                ac.setEnabled(true);
                n0 = ac.clutches;
                flag1 = false;
                flag2 = false;
                next();
            }
            case 525 -> {
                // erst wirklich in der Luft gewesen sein (direkt nach dem Teleport meldet der Client oft noch "am Boden")
                if (!mc.player.onGround()) flag2 = true;
                if (flag2 && mc.player.onGround()) flag1 = true;
                if (!flag1 && !after(120)) return;
                var ac = TeizenClient.modules().get(dev.teizen.client.modules.AutoClutch.class);
                float hp = mc.player.getHealth();
                boolean survived = mc.player.isAlive() && hp > 6;
                shot("68-autoclutch");
                result("AutoClutch: Sturz aus 14 Blöcken, Rettung gesetzt=" + yes(ac.clutches > n0) + ", gelandet=" + yes(flag1)
                        + ", Leben übrig " + String.format(Locale.ROOT, "%.0f", hp) + " " + ok(ac.clutches > n0 && survived));
                ac.setEnabled(false);
                SelfTest.jump(530);
            }
            // ---------------------------------------------- Fly-AntiKick: lange fliegen ohne Server-Rauswurf ("Flying is not enabled")
            case 530 -> {
                if (!after(10)) return;
                // hoch in den freien Himmel, da fliegt man ungestört (kein Boden -> Server zählt "schwebt zu lange")
                cmd(String.format(Locale.ROOT, "tp @s %.1f %d %.1f 0 0", o.getX() + 0.5, o.getY() + 140, o.getZ() + 0.5));
                t("///set fly doppelsprung aus");
                t("///set fly antikick an");
                next();
            }
            case 531 -> {
                if (!after(10)) return;
                Module fly = TeizenClient.modules().byName("fly");
                if (!fly.isEnabled()) fly.setEnabled(true);
                mc.options.keyJump.setDown(true); // steigen, nicht fallen
                flag1 = false;
                n0 = ((dev.teizen.client.modules.Fly) fly).protectedPackets;
                SelfTest.jump(532);
            }
            case 532 -> {
                if (SelfTest.local() % 20 == 0) flyLog();
                if (!after(140)) return; // Vanilla wirft normal nach ~80 Ticks raus
                // jetzt still in der Luft stehen: die Höhe soll gleich bleiben (Absenken wird wieder ausgeglichen)
                mc.options.keyJump.setDown(false);
                SelfTest.jump(533);
            }
            case 533 -> {
                if (SelfTest.local() % 20 == 0) flyLog();
                if (SelfTest.local() == 20) h0 = (float) mc.player.getY(); // erst nach dem Ausgleiten messen
                if (!after(120)) return;
                float drift = (float) mc.player.getY() - h0;
                Module fly = TeizenClient.modules().byName("fly");
                int sent = ((dev.teizen.client.modules.Fly) fly).protectedPackets - n0;
                shot("69-fly-antikick");
                cmd(String.format(Locale.ROOT, "tp @s %.1f %d %.1f 0 0", o.getX() + 0.5, o.getY() + 1, o.getZ() + 0.5));
                if (fly.isEnabled()) fly.setEnabled(false);
                t("///set fly doppelsprung an");
                result(String.format(Locale.ROOT, "Fly-AntiKick: 140 Ticks hoch + 100 Ticks schweben, rausgeworfen=nein, AntiKick-Durchgänge=%d, Höhe beim Schweben verändert um %.2f %s",
                        sent, drift, ok(sent > 0 && Math.abs(drift) < 0.3f)));
                SelfTest.jump(534);
            }
            // ---------------------------------------------- Echtes Tippen (xdotool = echte Tastatur): kommen Buchstaben im Menü an? (26.3/SDL)
            case 534 -> {
                if (!after(20)) return;
                openChat();
                next();
            }
            case 535 -> {
                if (!after(20)) return;
                text = "";
                if (!requestTyping("abc")) { result("Echt-Tippen: Testtastatur nicht da – übersprungen"); mc.gui.setScreen(null); SelfTest.jump(526); return; }
                next();
            }
            case 536 -> {
                if (!typingDone() && !after(200)) return;
                text = chatInput();
                log("Echt-Tippen in den Vanilla-Chat: '" + text + "'");
                mc.gui.setScreen(new Overview());
                next();
            }
            case 537 -> {
                if (!after(20)) return;
                requestTyping("velo");
                next();
            }
            case 538 -> {
                if (!typingDone() && !after(200)) return;
                if (!after(10) && !typingDone()) return;
                String q = mc.gui.screen() instanceof Overview ov ? ov.search().query : "(Übersicht nicht offen)";
                shot("70-echt-tippen");
                boolean control = text.contains("abc");
                if (!control) result("Echt-Tippen: Testtastatur kam nicht mal im Vanilla-Chat an (Chat='" + text + "', Suche='" + q + "') – übersprungen");
                else result("Echt-Tippen (echte Tastatur): Vanilla-Chat='" + text + "', Teizen-Suche='" + q + "' " + ok(q.contains("velo")));
                mc.gui.setScreen(null);
                SelfTest.jump(539);
            }
            // ---------------------------------------------- Seed: Anzeige zeigt immer etwas + eigener Seed im StructureFinder-Menü
            case 539 -> {
                if (!after(10)) return;
                seed0 = dev.teizen.client.core.Seed.known();
                String p0 = dev.teizen.client.core.Seed.progressNow();
                log("Seed-Anzeige vorher: " + p0);
                flag1 = p0 != null && !p0.isBlank();
                var sf = TeizenClient.modules().get(dev.teizen.client.modules.StructureFinder.class);
                sf.ownSeed.setText("teizen"); // wie im Menü eingetippt (Text-Seed wie bei Minecraft)
                next();
            }
            case 540 -> {
                if (!after(45)) return;
                Long k = dev.teizen.client.core.Seed.known();
                flag2 = k != null && k == (long) "teizen".hashCode();
                text = String.valueOf(dev.teizen.client.core.Seed.progressNow());
                TeizenClient.modules().get(dev.teizen.client.modules.StructureFinder.class).ownSeed.setText("-4172144997902289642");
                next();
            }
            case 541 -> {
                if (!after(45)) return;
                Long k = dev.teizen.client.core.Seed.known();
                boolean num = k != null && k == -4172144997902289642L;
                boolean stored = dev.teizen.client.core.Seed.MANUAL.containsValue(-4172144997902289642L);
                String hud = String.valueOf(dev.teizen.client.core.Seed.progressNow());
                TeizenClient.modules().get(dev.teizen.client.modules.StructureFinder.class).ownSeed.setText("");
                result("Seed: Anzeige im Bild=" + yes(flag1) + ", eigener Seed per Menü-Feld: Text 'teizen'=" + yes(flag2) + ", Zahl=" + yes(num)
                        + ", gespeichert=" + yes(stored) + ", Anzeige jetzt '" + hud.replaceAll("§.", "") + "' " + ok(flag1 && flag2 && num && stored && hud.contains("bekannt")));
                next();
            }
            case 542 -> {
                if (!after(45)) return;
                boolean removed = dev.teizen.client.core.Seed.manual() == null;
                String p1 = String.valueOf(dev.teizen.client.core.Seed.progressNow()).replaceAll("§.", "");
                result("Seed: Menü-Feld leeren entfernt den eigenen Seed=" + yes(removed) + ", Anzeige danach '" + p1 + "', Restzeit-Format: "
                        + dev.teizen.client.core.Seed.durationText(45_000) + " / " + dev.teizen.client.core.Seed.durationText(750_000) + " / " + dev.teizen.client.core.Seed.durationText(4_000_000) + " " + ok(removed));
                if (seed0 != null) dev.teizen.client.core.Seed.set(seed0);
                SelfTest.jump(543);
            }
            // ---------------------------------------------- Fly + Sprint schnell runter: kein Fallschaden (vorher zufälliger Schaden)
            case 543 -> {
                if (!after(5)) return;
                area = o.offset(60, 0, 60);
                cmd("fill " + blk(area.offset(-2, -1, -2)) + " " + blk(area.offset(2, -1, 2)) + " minecraft:stone");
                cmd("fill " + blk(area.offset(-2, 0, -2)) + " " + blk(area.offset(2, 90, 2)) + " minecraft:air");
                cmd(String.format(Locale.ROOT, "tp @s %.1f %d %.1f 0 0", area.getX() + 0.5, area.getY() + 80, area.getZ() + 0.5));
                cmd("effect give @s minecraft:instant_health 1 10");
                t("///set fly doppelsprung aus");
                t("///set fly tempo 3");
                Module fly = TeizenClient.modules().byName("fly");
                if (!fly.isEnabled()) fly.setEnabled(true);
                next();
            }
            case 544 -> {
                // erst lange schweben (vorher sammelte der Server dabei Fallhöhe an -> Tod beim Runterfliegen)
                if (!after(220)) return;
                h0 = mc.player.getHealth();
                maxY = (float) mc.player.getY();
                d0 = ((dev.teizen.client.modules.Fly) TeizenClient.modules().byName("fly")).groundPackets;
                mc.options.keySprint.setDown(true);
                mc.options.keyShift.setDown(true);
                next();
            }
            case 545 -> {
                if (!after(80)) return;
                mc.options.keySprint.setDown(false);
                mc.options.keyShift.setDown(false);
                var fly = (dev.teizen.client.modules.Fly) TeizenClient.modules().byName("fly");
                float hp = mc.player.getHealth();
                float fell = maxY - (float) mc.player.getY();
                int spoof = fly.groundPackets - d0;
                shot("71-fly-sprint-runter");
                fly.setEnabled(false);
                t("///set fly tempo 2");
                t("///set fly doppelsprung an");
                result(String.format(Locale.ROOT, "Fly: 200 Ticks schweben, dann mit Sprint schnell runter: %.0f Blöcke gefallen, Leben %.1f -> %.1f, 'am Boden'-Pakete=%d %s",
                        fell, h0, hp, spoof, ok(fell > 40 && hp >= h0 - 0.01f && spoof > 0)));
                SelfTest.jump(546);
            }
            // ---------------------------------------------- StructureFinder: sucht nach weitem Laufen neu (statt ewig auf die alte zu zeigen)
            case 546 -> {
                if (!after(10)) return;
                var sf = TeizenClient.modules().get(dev.teizen.client.modules.StructureFinder.class);
                sf.structures.setText("village");
                if (sf.isEnabled()) sf.setEnabled(false);
                sf.setEnabled(true);
                next();
            }
            case 547 -> {
                var sf = TeizenClient.modules().get(dev.teizen.client.modules.StructureFinder.class);
                if (sf.found.isEmpty() && !after(400)) return;
                n0 = sf.searches;
                wrongPos = sf.found.isEmpty() ? null : sf.found.get(0).pos();
                cmd(String.format(Locale.ROOT, "tp @s %d 200 %d", o.getX() + 900, o.getZ() + 900));
                cmd("effect give @s minecraft:slow_falling 60 0 true");
                next();
            }
            case 548 -> {
                var sf = TeizenClient.modules().get(dev.teizen.client.modules.StructureFinder.class);
                boolean again = sf.searches > n0;
                boolean answered = again && !sf.found.isEmpty() && !dev.teizen.client.core.Locator.busy();
                if (!answered && !after(500)) return;
                BlockPos now = sf.found.isEmpty() ? null : sf.found.get(0).pos();
                result("StructureFinder neu suchen: Dorf vorher=" + (wrongPos == null ? "-" : wrongPos.toShortString()) + ", nach 900 Blöcken Laufen neu gesucht=" + yes(again)
                        + ", jetzt=" + (now == null ? "-" : now.toShortString()) + " " + ok(wrongPos != null && again && now != null));
                sf.setEnabled(false);
                cmd(String.format(Locale.ROOT, "tp @s %.1f %d %.1f 0 0", o.getX() + 0.5, o.getY() + 1, o.getZ() + 0.5));
                cmd("effect clear @s minecraft:slow_falling");
                SelfTest.jump(549);
            }
            // ---------------------------------------------- Fly über Lava mit Shift runter: Lavaschutz, nie in Lava
            case 549 -> {
                if (!after(20)) return;
                area = o.offset(-60, 0, 60);
                cmd("fill " + blk(area.offset(-4, -2, -4)) + " " + blk(area.offset(4, -2, 4)) + " minecraft:stone");
                cmd("fill " + blk(area.offset(-4, -1, -4)) + " " + blk(area.offset(4, -1, 4)) + " minecraft:lava");
                cmd("fill " + blk(area.offset(-4, 0, -4)) + " " + blk(area.offset(4, 30, 4)) + " minecraft:air");
                cmd(String.format(Locale.ROOT, "tp @s %.1f %d %.1f 0 0", area.getX() + 0.5, area.getY() + 20, area.getZ() + 0.5));
                cmd("effect give @s minecraft:instant_health 1 10");
                t("///set fly doppelsprung aus");
                Module fly = TeizenClient.modules().byName("fly");
                if (!fly.isEnabled()) fly.setEnabled(true);
                flag1 = false;
                next();
            }
            case 550 -> {
                if (!after(30)) return;
                h0 = mc.player.getHealth();
                d0 = ((dev.teizen.client.modules.Fly) TeizenClient.modules().byName("fly")).lavaSaves;
                mc.options.keyShift.setDown(true);
                mc.options.keySprint.setDown(true);
                next();
            }
            case 551 -> {
                // war man wirklich in Lava? Dann brennt man (vom Server) oder verliert Leben
                if (mc.player.isOnFire() || mc.player.getHealth() < h0 - 0.01f || mc.player.isDeadOrDying()) flag1 = true;
                if (!after(120)) return;
                mc.options.keyShift.setDown(false);
                mc.options.keySprint.setDown(false);
                var fly = (dev.teizen.client.modules.Fly) TeizenClient.modules().byName("fly");
                float hp = mc.player.getHealth();
                double above = mc.player.getY() - (area.getY() - 1);
                shot("72-fly-lava");
                int saves = fly.lavaSaves - d0;
                cmd(String.format(Locale.ROOT, "tp @s %.1f %d %.1f 0 0", o.getX() + 0.5, o.getY() + 1, o.getZ() + 0.5));
                fly.setEnabled(false);
                t("///set fly doppelsprung an");
                cmd("fill " + blk(area.offset(-4, -1, -4)) + " " + blk(area.offset(4, -1, 4)) + " minecraft:stone");
                cmd("effect clear @s minecraft:fire_resistance");
                result(String.format(Locale.ROOT, "Fly über Lava mit Shift runter: gebrannt/Schaden=%s, Höhe über Lava %.2f, Leben %.1f -> %.1f, Lavaschutz griff %dx %s",
                        yes(flag1), above, h0, hp, saves, ok(!flag1 && hp >= h0 - 0.01f && above > 0)));
                SelfTest.jump(552);
            }
            // ---------------------------------------------- AutoMine: große Ader (4 Diamanten, 10 m) vor Einzelblock (5 m)
            case 552 -> {
                if (!after(20)) return;
                area = o.offset(-60, 90, -60);
                cmd("fill " + blk(area.offset(-14, -2, -4)) + " " + blk(area.offset(8, 4, 4)) + " minecraft:stone");
                cmd("fill " + blk(area.offset(-1, 0, -1)) + " " + blk(area.offset(1, 1, 1)) + " minecraft:air");
                cmd("setblock " + blk(area.offset(5, 0, 0)) + " minecraft:diamond_ore");
                cmd("fill " + blk(area.offset(-11, 0, 0)) + " " + blk(area.offset(-10, 1, 0)) + " minecraft:diamond_ore");
                cmd(String.format(Locale.ROOT, "tp @s %.1f %d %.1f 0 0", area.getX() + 0.5, area.getY(), area.getZ() + 0.5));
                text = TeizenClient.modules().get(dev.teizen.client.modules.AutoMine.class).ores.str();
                next();
            }
            case 553 -> {
                if (!after(30)) return;
                t("///mine diamond");
                next();
            }
            case 554 -> {
                var am = TeizenClient.modules().get(dev.teizen.client.modules.AutoMine.class);
                BlockPos tg = am.currentTarget();
                if (tg == null && !after(300)) return;
                boolean vein = tg != null && tg.getX() <= area.getX() - 9;
                result("AutoMine Ader-Wahl: Einzel-Diamant 5 m, 4er-Ader 10 m -> Ziel " + (tg == null ? "-" : tg.toShortString())
                        + " = " + (tg == null ? "keins" : vein ? "Ader" : "Einzelblock") + " " + ok(vein));
                t("///mine stop");
                am.ores.setText(text);
                cmd("fill " + blk(area.offset(-14, -2, -4)) + " " + blk(area.offset(8, 4, 4)) + " minecraft:air");
                cmd(String.format(Locale.ROOT, "tp @s %.1f %d %.1f 0 0", o.getX() + 0.5, o.getY() + 1, o.getZ() + 0.5));
                SelfTest.jump(555);
            }
            // ---------------------------------------------- AutoMine setzt Blöcke: Brücke über eine Lücke
            case 555 -> {
                if (!after(20)) return;
                TeizenClient.modules().get(dev.teizen.client.modules.AutoEat.class).setEnabled(false);
                area = o.offset(80, 90, -60);
                cmd("fill " + blk(area.offset(-4, -7, -4)) + " " + blk(area.offset(11, -7, 4)) + " minecraft:stone"); // Sicherheitsboden
                cmd("fill " + blk(area.offset(-4, -6, -4)) + " " + blk(area.offset(11, 6, 4)) + " minecraft:air");
                cmd("setblock " + blk(area.below()) + " minecraft:stone");
                cmd("setblock " + blk(area.offset(7, -1, 0)) + " minecraft:stone");
                cmd("setblock " + blk(area.offset(7, 0, 0)) + " minecraft:diamond_ore");
                cmd(String.format(Locale.ROOT, "tp @s %.1f %d %.1f -90 0", area.getX() + 0.5, area.getY(), area.getZ() + 0.5));
                cmd("clear @s");
                cmd("item replace entity @s hotbar.0 with minecraft:diamond_pickaxe");
                cmd("give @s minecraft:cobblestone 32");
                text = TeizenClient.modules().get(dev.teizen.client.modules.AutoMine.class).ores.str();
                next();
            }
            case 556 -> {
                if (!after(30)) return;
                n0 = TeizenClient.modules().get(dev.teizen.client.modules.AutoMine.class).placed;
                t("///mine diamond");
                next();
            }
            case 557 -> {
                boolean gone = !mc.level.getBlockState(area.offset(7, 0, 0)).is(net.minecraft.world.level.block.Blocks.DIAMOND_ORE);
                if (!gone && !after(700)) return;
                var am = TeizenClient.modules().get(dev.teizen.client.modules.AutoMine.class);
                int pl = am.placed - n0;
                shot("73-automine-bruecke");
                result("AutoMine Brücke: Diamant hinter 6 Blöcken Lücke abgebaut=" + yes(gone) + ", Blöcke gesetzt=" + pl + " " + ok(gone && pl > 0));
                t("///mine stop");
                next();
            }
            // ---------------------------------------------- AutoMine setzt Blöcke: hochbauen
            case 558 -> {
                if (!after(20)) return;
                area = o.offset(80, 90, -80);
                cmd("fill " + blk(area.offset(-4, -7, -4)) + " " + blk(area.offset(4, -7, 4)) + " minecraft:stone");
                cmd("fill " + blk(area.offset(-4, -6, -4)) + " " + blk(area.offset(4, 9, 4)) + " minecraft:air");
                cmd("setblock " + blk(area.below()) + " minecraft:stone");
                cmd("setblock " + blk(area.offset(0, 7, 1)) + " minecraft:diamond_ore");
                cmd(String.format(Locale.ROOT, "tp @s %.1f %d %.1f 0 0", area.getX() + 0.5, area.getY(), area.getZ() + 0.5));
                cmd("give @s minecraft:cobblestone 32");
                next();
            }
            case 559 -> {
                if (!after(30)) return;
                n0 = TeizenClient.modules().get(dev.teizen.client.modules.AutoMine.class).placed;
                t("///mine diamond");
                next();
            }
            case 560 -> {
                boolean gone = !mc.level.getBlockState(area.offset(0, 7, 1)).is(net.minecraft.world.level.block.Blocks.DIAMOND_ORE);
                if (!gone && !after(700)) return;
                var am = TeizenClient.modules().get(dev.teizen.client.modules.AutoMine.class);
                int pl = am.placed - n0;
                shot("74-automine-hochbauen");
                result("AutoMine hochbauen: Diamant 7 Blöcke über mir abgebaut=" + yes(gone) + ", Blöcke gesetzt=" + pl + " " + ok(gone && pl > 0));
                t("///mine stop");
                next();
            }
            // ---------------------------------------------- AutoMine: keine Erze -> Suchtunnel; Hunger -> isst selbst
            case 561 -> {
                if (!after(20)) return;
                area = o.offset(-40, 90, 30); // nah genug, damit die Chunks geladen sind
                cmd("fill " + blk(area.offset(-15, -1, -15)) + " " + blk(area.offset(15, 2, 15)) + " minecraft:stone");
                cmd("fill " + blk(area) + " " + blk(area.above()) + " minecraft:air");
                cmd(String.format(Locale.ROOT, "tp @s %.1f %d %.1f 0 0", area.getX() + 0.5, area.getY(), area.getZ() + 0.5));
                cmd("clear @s");
                cmd("item replace entity @s hotbar.0 with minecraft:diamond_pickaxe");
                cmd("item replace entity @s inventory.5 with minecraft:bread 8"); // Essen NICHT in der Hotbar
                cmd("effect give @s minecraft:hunger 3 255 true");
                next();
            }
            case 562 -> {
                if (!after(80)) return;
                cmd("effect clear @s minecraft:hunger");
                d0 = mc.player.getFoodData().getFoodLevel();
                var am = TeizenClient.modules().get(dev.teizen.client.modules.AutoMine.class);
                n0 = am.tunnelSteps;
                maxY = (float) mc.player.getX();
                h0 = (float) mc.player.getZ();
                t("///mine ancient_debris"); // gibt es hier nicht -> Suchtunnel
                next();
            }
            case 563 -> {
                if (!after(500)) return;
                var am = TeizenClient.modules().get(dev.teizen.client.modules.AutoMine.class);
                double moved = Math.hypot(mc.player.getX() - maxY, mc.player.getZ() - h0);
                int bread = 0;
                for (int i = 0; i < 36; i++) if (mc.player.getInventory().getItem(i).is(net.minecraft.world.item.Items.BREAD)) bread += mc.player.getInventory().getItem(i).getCount();
                int food = mc.player.getFoodData().getFoodLevel();
                shot("75-automine-tunnel-essen");
                result(String.format(Locale.ROOT, "AutoMine ohne Erze: Suchtunnel %.1f Blöcke gegraben (Schritte %d); Hunger nach dem Hunger-Effekt %d -> jetzt %d, Brot 8 -> %d %s",
                        moved, am.tunnelSteps - n0, d0, food, bread, ok(moved >= 4 && bread < 8 && food >= 15)));
                t("///mine stop");
                am.ores.setText(text);
                cmd(String.format(Locale.ROOT, "tp @s %.1f %d %.1f 0 0", o.getX() + 0.5, o.getY() + 1, o.getZ() + 0.5));
                SelfTest.jump(564);
            }
            // ---------------------------------------------- Taste auf "Vorwärts" -> wird entfernt; Seed bekannt -> SeedCrackerX pausiert
            case 564 -> {
                if (!after(10)) return;
                Module fly = TeizenClient.modules().byName("fly");
                fly.key = net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper.getBoundKeyOf(mc.options.keyUp).getValue();
                next();
            }
            case 565 -> {
                if (!after(10)) return; // erst in die Minecraft-Steuerung übernehmen lassen
                dev.teizen.client.core.KeyBinds.recheck();
                next();
            }
            case 566 -> {
                if (!after(10)) return;
                Module fly = TeizenClient.modules().byName("fly");
                boolean removed = fly.key == -1 && dev.teizen.client.core.KeyBinds.removedConflicts.contains("fly");
                Long k = dev.teizen.client.core.Seed.known();
                Boolean run = dev.teizen.client.core.Seed.crackerRunning();
                result("Doppelte Taste: Fly lag auf Vorwärts -> Taste entfernt=" + yes(removed) + "; Seed bekannt (" + k + ") -> SeedCrackerX läuft="
                        + run + " " + ok(removed && k != null && Boolean.FALSE.equals(run)));
                SelfTest.jump(567);
            }
            // ---------------------------------------------- NoFall: 40 Blöcke freier Fall auf Stein -> kein Schaden
            case 567 -> {
                if (!after(10)) return;
                dev.teizen.client.modules.PanicButton.panic();
                area = o.offset(30, 0, -40); // nah genug, damit die Chunks geladen sind
                cmd("fill " + blk(area.offset(-2, -1, -2)) + " " + blk(area.offset(2, -1, 2)) + " minecraft:stone");
                cmd("fill " + blk(area.offset(-2, 0, -2)) + " " + blk(area.offset(2, 45, 2)) + " minecraft:air");
                cmd("effect give @s minecraft:instant_health 1 10");
                cmd(String.format(Locale.ROOT, "tp @s %.1f %d %.1f 0 0", area.getX() + 0.5, area.getY() + 40, area.getZ() + 0.5));
                var nf = TeizenClient.modules().get(dev.teizen.client.modules.NoFall.class);
                nf.setEnabled(true);
                n0 = nf.saved;
                flag1 = false;
                flag2 = false;
                next();
            }
            case 568 -> {
                if (SelfTest.local() == 15) h0 = mc.player.getHealth();
                if (!mc.player.onGround()) flag2 = true;
                if (flag2 && mc.player.onGround()) flag1 = true;
                if (!(flag1 && SelfTest.local() > 40) && !after(160)) return;
                var nf = TeizenClient.modules().get(dev.teizen.client.modules.NoFall.class);
                float hp = mc.player.getHealth();
                shot("76-nofall");
                result(String.format(Locale.ROOT, "NoFall: 40 Blöcke Fall, gelandet=%s, Leben %.1f -> %.1f, 'am Boden'-Pakete=%d %s",
                        yes(flag1), h0, hp, nf.saved - n0, ok(flag1 && hp >= h0 - 0.01f && nf.saved > n0)));
                nf.setEnabled(false);
                cmd(String.format(Locale.ROOT, "tp @s %.1f %d %.1f 0 0", o.getX() + 0.5, o.getY() + 1, o.getZ() + 0.5));
                SelfTest.jump(569);
            }
            // ---------------------------------------------- NoFall nach langem Sturz (wie nach Elytra/Mace): kein Schaden mitten in der Luft
            case 569 -> {
                if (!after(10)) return;
                area = o.offset(-30, 0, -50); // nah genug, damit die Chunks geladen sind
                cmd("fill " + blk(area.offset(-3, -4, -3)) + " " + blk(area.offset(3, -4, 3)) + " minecraft:stone");
                cmd("fill " + blk(area.offset(-3, -3, -3)) + " " + blk(area.offset(3, -1, 3)) + " minecraft:water");
                cmd("fill " + blk(area.offset(-3, 0, -3)) + " " + blk(area.offset(3, 75, 3)) + " minecraft:air");
                cmd("effect give @s minecraft:instant_health 1 10");
                cmd(String.format(Locale.ROOT, "tp @s %.1f %d %.1f 0 0", area.getX() + 0.5, area.getY() + 70, area.getZ() + 0.5));
                TeizenClient.modules().get(dev.teizen.client.modules.NoFall.class).setEnabled(false);
                flag1 = false;
                next();
            }
            case 570 -> {
                if (SelfTest.local() == 10) h0 = mc.player.getHealth();
                if (SelfTest.local() == 25) TeizenClient.modules().get(dev.teizen.client.modules.NoFall.class).setEnabled(true); // erst jetzt an: schon >3 Blöcke gespeichert
                if (SelfTest.local() > 25 && !mc.player.isInWater() && mc.player.getHealth() < h0 - 0.01f) flag1 = true; // Schaden in der Luft
                if (!mc.player.isInWater() && !after(200)) return;
                float hp = mc.player.getHealth();
                var nf = TeizenClient.modules().get(dev.teizen.client.modules.NoFall.class);
                shot("77-nofall-langer-sturz");
                result(String.format(Locale.ROOT, "NoFall nach langem Sturz (wie Elytra/Mace): Schaden in der Luft=%s, im Wasser gelandet=%s, Leben %.1f -> %.1f %s",
                        yes(flag1), yes(mc.player.isInWater()), h0, hp, ok(!flag1 && mc.player.isInWater() && hp >= h0 - 0.01f)));
                nf.setEnabled(false);
                cmd(String.format(Locale.ROOT, "tp @s %.1f %d %.1f 0 0", o.getX() + 0.5, o.getY() + 1, o.getZ() + 0.5));
                SelfTest.jump(571);
            }
            // ---------------------------------------------- StructureFinder ohne OP: über den Seed (SeedMapper) – gleiches Dorf wie /locate?
            case 571 -> {
                if (!after(20)) return;
                dev.teizen.client.core.Locator.forceSeedMode = false;
                var sf = TeizenClient.modules().get(dev.teizen.client.modules.StructureFinder.class);
                sf.structures.setText("village");
                if (sf.isEnabled()) sf.setEnabled(false);
                sf.setEnabled(true);
                next();
            }
            case 572 -> {
                var sf = TeizenClient.modules().get(dev.teizen.client.modules.StructureFinder.class);
                if (sf.found.isEmpty() && !after(400)) return;
                wrongPos = sf.found.isEmpty() ? null : sf.found.get(0).pos(); // per /locate
                sf.setEnabled(false);
                dev.teizen.client.core.Locator.forceSeedMode = true; // so tun, als hätte man kein OP
                n0 = dev.teizen.client.core.Locator.seedSearches;
                sf.setEnabled(true);
                next();
            }
            case 573 -> {
                var sf = TeizenClient.modules().get(dev.teizen.client.modules.StructureFinder.class);
                boolean viaSeed = dev.teizen.client.core.Locator.seedSearches > n0;
                if ((sf.found.isEmpty() || !viaSeed) && !after(500)) return;
                BlockPos bySeed = sf.found.isEmpty() ? null : sf.found.get(0).pos();
                double d = wrongPos == null || bySeed == null ? -1 : Math.hypot(wrongPos.getX() - bySeed.getX(), wrongPos.getZ() - bySeed.getZ());
                shot("78-structurefinder-ohne-op");
                result("StructureFinder ohne OP (über Seed + SeedMapper): gefunden=" + (bySeed == null ? "nein" : bySeed.toShortString()) + ", per /locate="
                        + (wrongPos == null ? "-" : wrongPos.toShortString()) + ", Abstand " + Math.round(d) + " Blöcke, Seed-Suche benutzt=" + yes(viaSeed)
                        + " " + ok(viaSeed && bySeed != null && wrongPos != null && d >= 0 && d < 48));
                dev.teizen.client.core.Locator.forceSeedMode = false;
                sf.setEnabled(false);
                SelfTest.jump(526);
            }
            case 526 -> {
                if (!after(5)) return;
                dev.teizen.client.modules.PanicButton.panic();
                for (String n : before) { Module m = TeizenClient.modules().byName(n); if (m != null && !m.isEnabled()) m.setEnabled(true); }
                dev.teizen.client.core.Config.save();
                SelfTest.jump(152);
            }
            default -> {}
        }
    }
}
