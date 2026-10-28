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
    /** Was vor Runde 11 an war (danach wieder so herstellen, z.B. AutoTotem für den CombatLog-Test). */
    private static final java.util.List<String> before = new java.util.ArrayList<>();

    private Round11Tests() {}

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
        s.mouseClicked(new MouseButtonEvent(c[0], c[1], new MouseButtonInfo(button, 0)), false);
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
                for (String n : before) { Module m = TeizenClient.modules().byName(n); if (m != null && !m.isEnabled()) m.setEnabled(true); }
                dev.teizen.client.core.Config.save();
                SelfTest.jump(152);
            }
            default -> {}
        }
    }
}
