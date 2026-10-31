package dev.teizen.client;

import dev.teizen.client.core.Commands;
import dev.teizen.client.core.Module;
import dev.teizen.client.modules.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Locale;

/** Selbsttest Runde 12: Meteor-Funktionen (Paket 1), Mace beim Fliegen, NoClip ohne OP, Merken. */
final class Round12Tests {
    private static final Minecraft mc = Minecraft.getInstance();
    private static BlockPos a;
    private static double y0, v0;
    private static long ms0;
    private static int n0, n1;
    private static float hp0, hpMe;
    private static boolean f1;
    private static volatile boolean swarmOk;
    private static volatile String swarmLine;

    private Round12Tests() {}

    private static void cmd(String c) { SelfTest.cmd2(c); }
    private static boolean after(int t) { return SelfTest.after2(t); }
    private static int local() { return SelfTest.local(); }
    private static void next() { SelfTest.next2(); }
    private static void result(String r) { SelfTest.result(r); }
    private static void log(String s) { SelfTest.log2(s); }
    private static void t(String c) { Commands.handle(c); }
    private static String ok(boolean b) { return b ? "OK" : "FEHLER"; }
    private static String yes(boolean b) { return b ? "ja" : "nein"; }
    private static String blk(BlockPos p) { return p.getX() + " " + p.getY() + " " + p.getZ(); }
    private static Module mod(String n) { return TeizenClient.modules().byName(n); }
    private static <T extends Module> T get(Class<T> c) { return TeizenClient.modules().get(c); }
    private static void on(Module m) { if (!m.isEnabled()) m.setEnabled(true); }
    private static void off(Module m) { if (m.isEnabled()) m.setEnabled(false); }
    private static void tp(double x, double y, double z) { cmd(String.format(Locale.ROOT, "tp @s %.2f %.2f %.2f 0 0", x, y, z)); }
    private static void releaseKeys() {
        var o = mc.options;
        o.keyUp.setDown(false); o.keyJump.setDown(false); o.keyShift.setDown(false); o.keySprint.setDown(false);
        o.keyAttack.setDown(false); o.keyUse.setDown(false);
    }

    /** Freie Testfläche (Steinboden, Luft drüber). */
    private static void platform(BlockPos c) {
        cmd("forceload add " + (c.getX() - 16) + " " + (c.getZ() - 16) + " " + (c.getX() + 16) + " " + (c.getZ() + 16));
        cmd("fill " + blk(c.offset(-10, -1, -10)) + " " + blk(c.offset(10, -1, 10)) + " minecraft:stone");
        cmd("fill " + blk(c.offset(-10, 0, -10)) + " " + blk(c.offset(10, 8, 10)) + " minecraft:air");
    }

    /** @return true wenn der Schritt hier behandelt wurde */
    static boolean step(int step, BlockPos o) {
        switch (step) {
            // ------------------------------------------------ Merken: eingeschaltete Funktionen landen in der Config
            case 600 -> {
                if (!after(5)) return true;
                dev.teizen.client.modules.PanicButton.panic();
                a = o.offset(18, 45, -18);
                platform(a);
                tp(a.getX() + 0.5, a.getY(), a.getZ() + 0.5);
                cmd("clear @s");
                cmd("effect give @s minecraft:resistance 600 4 true");
                cmd("effect give @s minecraft:saturation 600 1 true");
                on(get(Trail.class));
                dev.teizen.client.core.Config.save();
                String js = "";
                try { js = java.nio.file.Files.readString(net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("teizenclient.json")); } catch (Throwable ignored) {}
                boolean saved = js.replaceAll("\\s", "").matches("(?s).*\"Trail\":\\{[^}]*\"an\":true.*");
                result("Merken (Funktionen nach Neustart wieder an): Trail als 'an' gespeichert=" + yes(saved) + ", merken=" + yes(dev.teizen.client.core.Restore.enabled) + " " + ok(saved && dev.teizen.client.core.Restore.enabled));
                next();
            }
            // ------------------------------------------------ Timer: Spiel doppelt so schnell
            case 601 -> {
                if (!after(40)) return true;
                t("///set timer tempo 2");
                on(get(Timer.class));
                ms0 = System.currentTimeMillis();
                next();
            }
            case 602 -> {
                if (!after(40)) return true;
                long ms = System.currentTimeMillis() - ms0;
                off(get(Timer.class));
                result("Timer x2: 40 Ticks in " + ms + " ms (normal ~2000) " + ok(ms < 1500));
                next();
            }
            // ------------------------------------------------ AirJump: in der Luft nochmal springen
            case 603 -> {
                on(get(AirJump.class));
                tp(a.getX() + 0.5, a.getY() + 4, a.getZ() + 0.5);
                n0 = get(AirJump.class).jumps;
                next();
            }
            case 604 -> {
                if (!after(4)) return true;
                if (local() == 4) { mc.options.keyJump.setDown(true); return true; }
                if (local() == 5) { v0 = mc.player.getDeltaMovement().y; mc.options.keyJump.setDown(false); return true; }
                if (!after(8)) return true;
                int j = get(AirJump.class).jumps - n0;
                off(get(AirJump.class));
                result(String.format(Locale.ROOT, "AirJump: in der Luft gesprungen=%s (Tempo nach oben %.2f) %s", yes(j > 0), v0, ok(j > 0 && v0 > 0.2)));
                next();
            }
            // ------------------------------------------------ FastClimb: Leiter schneller hoch
            case 605 -> {
                if (!after(30)) return true;
                BlockPos w = a.offset(5, 0, 5);
                cmd("fill " + blk(w.offset(0, 0, 1)) + " " + blk(w.offset(0, 12, 1)) + " minecraft:stone");
                cmd("fill " + blk(w) + " " + blk(w.above(12)) + " minecraft:ladder[facing=north]");
                cmd(String.format(Locale.ROOT, "tp @s %.2f %d %.2f 0 0", w.getX() + 0.5, w.getY(), w.getZ() + 0.5));
                on(get(FastClimb.class));
                next();
            }
            case 606 -> {
                if (!after(20)) return true;
                if (local() == 20) { y0 = mc.player.getY(); mc.options.keyUp.setDown(true); return true; }
                if (!after(40)) return true;
                double up = mc.player.getY() - y0;
                mc.options.keyUp.setDown(false);
                off(get(FastClimb.class));
                result(String.format(Locale.ROOT, "FastClimb: 20 Ticks Leiter %.1f Blöcke hoch (normal ~4) %s", up, ok(up > 5.5)));
                next();
            }
            // ------------------------------------------------ ReverseStep: Kante runter sofort unten
            case 607 -> {
                if (!after(10)) return true;
                cmd("fill " + blk(a.offset(-3, 0, -3)) + " " + blk(a.offset(3, 2, 3)) + " minecraft:stone");
                tp(a.getX() + 0.5, a.getY() + 3, a.getZ() + 0.5);
                on(get(ReverseStep.class));
                n0 = get(ReverseStep.class).steps;
                next();
            }
            case 608 -> {
                if (!after(15)) return true;
                if (local() == 15) { mc.player.setYRot(0); mc.options.keyUp.setDown(true); y0 = -1; return true; }
                // erste Tick, an dem man unten steht
                if (y0 < 0 && mc.player.onGround() && mc.player.getY() < a.getY() + 0.5) y0 = local();
                if (y0 < 0 && !after(80)) return true;
                mc.options.keyUp.setDown(false);
                int s = get(ReverseStep.class).steps - n0;
                off(get(ReverseStep.class));
                cmd("fill " + blk(a.offset(-3, 0, -3)) + " " + blk(a.offset(3, 2, 3)) + " minecraft:air");
                result("ReverseStep: 3 Blöcke Kante runter, schnell runter gezogen=" + yes(s > 0) + " " + ok(s > 0));
                next();
            }
            // ------------------------------------------------ Parkour: springt an der Kante
            case 609 -> {
                if (!after(10)) return true;
                cmd("fill " + blk(a.offset(-1, 0, -6)) + " " + blk(a.offset(1, 0, 0)) + " minecraft:stone");
                tp(a.getX() + 0.5, a.getY() + 1, a.getZ() - 5.5);
                on(get(Parkour.class));
                n0 = get(Parkour.class).jumps;
                next();
            }
            case 610 -> {
                if (!after(10)) return true;
                if (local() == 10) { mc.player.setYRot(0); mc.options.keyUp.setDown(true); return true; }
                if (get(Parkour.class).jumps == n0 && !after(60)) return true;
                mc.options.keyUp.setDown(false);
                int j = get(Parkour.class).jumps - n0;
                off(get(Parkour.class));
                cmd("fill " + blk(a.offset(-1, 0, -6)) + " " + blk(a.offset(1, 0, 0)) + " minecraft:air");
                result("Parkour: an der Kante von selbst gesprungen=" + yes(j > 0) + " " + ok(j > 0));
                next();
            }
            // ------------------------------------------------ FakePlayer + Hitboxes + Notifier/PopChams
            case 611 -> {
                if (!after(10)) return true;
                tp(a.getX() + 0.5, a.getY(), a.getZ() + 0.5);
                on(get(FakePlayer.class));
                on(get(Hitboxes.class));
                on(get(Notifier.class));
                on(get(PopChams.class));
                next();
            }
            case 612 -> {
                if (!after(10)) return true;
                var fp = get(FakePlayer.class).dummy;
                boolean there = fp != null && mc.level.getEntity(fp.getId()) == fp;
                float pick = fp == null ? 0 : fp.getPickRadius();
                int msg0 = get(Notifier.class).messages, pop0 = get(PopChams.class).seen;
                if (fp != null) Notifier.onEntityEvent(fp, (byte) 35);
                boolean noti = get(Notifier.class).messages > msg0, pop = get(PopChams.class).seen > pop0;
                result(String.format(Locale.ROOT, "FakePlayer da=%s, Hitboxes größer (%.2f statt 0), Totem-Meldung=%s, PopChams=%s %s",
                        yes(there), pick, yes(noti), yes(pop), ok(there && pick > 0.2 && noti && pop)));
                off(get(Hitboxes.class)); off(get(PopChams.class)); off(get(Notifier.class));
                off(get(FakePlayer.class));
                next();
            }
            // ------------------------------------------------ BreakIndicators (simuliert) + Portals + NoInteract (Logik)
            case 613 -> {
                on(get(BreakIndicators.class));
                BreakIndicators.onDestruction(123456, a.offset(2, 0, 0), 5);
                boolean bi = get(BreakIndicators.class).breaking.containsKey(123456);
                off(get(BreakIndicators.class));
                on(get(NoInteract.class));
                cmd("setblock " + blk(a.offset(2, 0, 2)) + " minecraft:chest");
                next();
                f1 = bi;
            }
            case 614 -> {
                if (!after(10)) return true;
                mc.options.keyUse.setDown(true);
                boolean blocked = NoInteract.skip(a.offset(2, 0, 2));
                mc.options.keyUse.setDown(false);
                off(get(NoInteract.class));
                on(get(Portals.class));
                mc.gui.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));
                boolean kept = Portals.keep();
                mc.gui.setScreen(null);
                off(get(Portals.class));
                result("BreakIndicators (fremder Abbau gemerkt)=" + yes(f1) + ", NoInteract (Kiste nicht öffnen)=" + yes(blocked) + ", Portals (Menü bleibt offen)=" + yes(kept)
                        + " " + ok(f1 && blocked && kept));
                next();
            }
            // ------------------------------------------------ GhostHand: Kiste hinter einer Wand öffnen
            case 615 -> {
                cmd("setblock " + blk(a.offset(0, 1, 3)) + " minecraft:chest");
                cmd("fill " + blk(a.offset(-1, 0, 2)) + " " + blk(a.offset(1, 2, 2)) + " minecraft:stone");
                tp(a.getX() + 0.5, a.getY(), a.getZ() + 0.5);
                on(get(GhostHand.class));
                next();
            }
            case 616 -> {
                if (!after(15)) return true;
                if (local() == 15) {
                    float[] r = dev.teizen.client.util.Rot.to(net.minecraft.world.phys.Vec3.atCenterOf(a.offset(0, 1, 3)));
                    mc.player.setYRot(r[0]); mc.player.setXRot(r[1]);
                    return true;
                }
                if (local() == 17) { GhostHand.onUse(); return true; }
                boolean open = mc.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>;
                if (!open && !after(40)) return true;
                if (open) mc.player.closeContainer();
                off(get(GhostHand.class));
                cmd("fill " + blk(a.offset(-1, 0, 2)) + " " + blk(a.offset(1, 2, 3)) + " minecraft:air");
                result("GhostHand: Kiste durch die Wand geöffnet=" + yes(open) + " " + ok(open));
                next();
            }
            // ------------------------------------------------ VeinMiner: ganze Ader
            case 617 -> {
                if (!after(10)) return true;
                cmd("clear @s");
                cmd("item replace entity @s hotbar.0 with minecraft:diamond_pickaxe");
                cmd("fill " + blk(a.offset(2, 0, 0)) + " " + blk(a.offset(3, 1, 1)) + " minecraft:diamond_ore");
                tp(a.getX() + 0.5, a.getY(), a.getZ() + 0.5);
                on(get(VeinMiner.class));
                n0 = get(VeinMiner.class).mined;
                next();
            }
            case 618 -> {
                if (!after(15)) return true;
                if (local() == 15) { VeinMiner.onBreak(a.offset(2, 0, 0)); cmd("setblock " + blk(a.offset(2, 0, 0)) + " minecraft:air"); return true; }
                int left = 0;
                for (BlockPos p : BlockPos.betweenClosed(a.offset(2, 0, 0), a.offset(3, 1, 1))) if (!mc.level.getBlockState(p).isAir()) left++;
                if (left > 0 && !after(400)) return true;
                off(get(VeinMiner.class));
                result("VeinMiner: 1 Erz abgebaut -> Rest der Ader (7) mit abgebaut, übrig " + left + " " + ok(left == 0));
                next();
            }
            // ------------------------------------------------ LiquidFiller: Lava zuschütten
            case 619 -> {
                cmd("item replace entity @s hotbar.1 with minecraft:cobblestone 16");
                cmd("setblock " + blk(a.offset(2, -1, 0)) + " minecraft:lava");
                on(get(LiquidFiller.class));
                n0 = get(LiquidFiller.class).filled;
                next();
            }
            case 620 -> {
                boolean lava = mc.level.getFluidState(a.offset(2, -1, 0)).is(net.minecraft.tags.FluidTags.LAVA);
                if (lava && !after(60)) return true;
                off(get(LiquidFiller.class));
                result("LiquidFiller: Lava neben mir zugeschüttet=" + yes(!lava) + " " + ok(!lava && get(LiquidFiller.class).filled > n0));
                cmd("setblock " + blk(a.offset(2, -1, 0)) + " minecraft:stone");
                next();
            }
            // ------------------------------------------------ AutoBreed: Kühe füttern
            case 621 -> {
                cmd("item replace entity @s hotbar.2 with minecraft:wheat 8");
                cmd("summon minecraft:cow " + (a.getX() + 2) + " " + a.getY() + " " + (a.getZ() - 2));
                cmd("summon minecraft:cow " + (a.getX() - 2) + " " + a.getY() + " " + (a.getZ() - 2));
                on(get(AutoBreed.class));
                n0 = get(AutoBreed.class).feeds;
                next();
            }
            case 622 -> {
                int f = get(AutoBreed.class).feeds - n0;
                if (f < 2 && !after(100)) return true;
                off(get(AutoBreed.class));
                cmd("kill @e[type=minecraft:cow,distance=..10]");
                result("AutoBreed: Kühe gefüttert " + f + "/2 " + ok(f >= 2));
                next();
            }
            // ------------------------------------------------ AutoSign: Schild beschreiben
            case 623 -> {
                cmd("item replace entity @s hotbar.3 with minecraft:oak_sign 4");
                on(get(AutoSign.class));
                n0 = get(AutoSign.class).signs;
                next();
            }
            case 624 -> {
                if (!after(10)) return true;
                if (local() == 10) {
                    dev.teizen.client.util.Inv.select(3);
                    BlockPos s = a.offset(0, 0, 2);
                    var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(s.below()).add(0, 0.5, 0), net.minecraft.core.Direction.UP, s.below(), false);
                    mc.gameMode.useItemOn(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
                    return true;
                }
                if (!after(40)) return true;
                String line = "";
                if (mc.level.getBlockEntity(a.offset(0, 0, 2)) instanceof net.minecraft.world.level.block.entity.SignBlockEntity sb) line = signLine(sb);
                off(get(AutoSign.class));
                cmd("setblock " + blk(a.offset(0, 0, 2)) + " minecraft:air");
                result("AutoSign: Schild beschrieben ('" + line + "') " + ok(line.equals("Teizen")));
                next();
            }
            // ------------------------------------------------ AntiAFK + AntiHunger + LightOverlay + Trail + EntityOwner
            case 625 -> {
                t("///set antiafk intervall 3");
                on(get(AntiAFK.class)); on(get(AntiHunger.class)); on(get(LightOverlay.class)); on(get(EntityOwner.class));
                n0 = get(AntiAFK.class).actions;
                n1 = get(AntiHunger.class).hidden;
                java.util.UUID u = mc.player.getUUID();
                long hi = u.getMostSignificantBits(), lo = u.getLeastSignificantBits();
                cmd(String.format(Locale.ROOT, "summon minecraft:wolf %d %d %d {Owner:[I;%d,%d,%d,%d],owner:[I;%d,%d,%d,%d],Tame:1b,NoAI:1b}", a.getX() + 3, a.getY(), a.getZ() + 3,
                        (int) (hi >> 32), (int) hi, (int) (lo >> 32), (int) lo, (int) (hi >> 32), (int) hi, (int) (lo >> 32), (int) lo));
                mc.player.setYRot(0);
                mc.options.keyUp.setDown(true);
                mc.options.keySprint.setDown(true);
                next();
            }
            case 626 -> {
                if (local() == 15) { mc.options.keyUp.setDown(false); mc.options.keySprint.setDown(false); }
                if (local() == 20) {
                    // zum Wolf schauen (EntityOwner zeichnet nur, was im Bild ist)
                    var wolves = mc.level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, mc.player.getBoundingBox().inflate(30),
                            e -> e instanceof net.minecraft.world.entity.OwnableEntity);
                    if (!wolves.isEmpty()) {
                        var w = wolves.get(0);
                        float[] r = dev.teizen.client.util.Rot.to(w.getBoundingBox().getCenter());
                        mc.player.setYRot(r[0]); mc.player.setXRot(r[1]);
                        var ref = ((net.minecraft.world.entity.OwnableEntity) w).getOwnerReference();
                        log("Wolf Besitzer beim Client: " + (ref == null ? "keiner" : ref.getUUID()));
                    } else log("kein Wolf gefunden");
                }
                if (!after(80)) return true;
                int afk = get(AntiAFK.class).actions - n0, hid = get(AntiHunger.class).hidden - n1;
                int light = get(LightOverlay.class).spots.size(), trail = get(Trail.class).points.size(), owner = get(EntityOwner.class).shown;
                off(get(AntiAFK.class)); off(get(AntiHunger.class)); off(get(LightOverlay.class)); off(get(EntityOwner.class)); off(get(Trail.class));
                cmd("kill @e[type=minecraft:wolf,distance=..20]");
                result("AntiAFK bewegt=" + afk + "x, AntiHunger Sprint versteckt=" + hid + "x, LightOverlay Stellen=" + light + ", Trail Punkte=" + trail + ", EntityOwner Wolf-Besitzer gezeigt=" + owner
                        + " " + ok(afk > 0 && hid > 0 && light > 0 && trail > 3 && owner > 0));
                next();
            }
            // ------------------------------------------------ Mace beim Fliegen: voller Fall-Schaden ohne zu fallen
            case 627 -> {
                cmd("clear @s");
                cmd("item replace entity @s hotbar.0 with minecraft:mace");
                tp(a.getX() + 0.5, a.getY() + 2, a.getZ() + 0.5);
                cmd(String.format(Locale.ROOT, "summon minecraft:zombie %d %d %d {NoAI:1b,PersistenceRequired:1b,attributes:[{id:\"minecraft:max_health\",base:200}],Health:200f}",
                        a.getX() + 2, a.getY(), a.getZ()));
                t("///set fly doppelsprung aus");
                on(get(Fly.class));
                on(get(MaceSwap.class));
                n0 = get(MaceSwap.class).fakeFalls;
                next();
            }
            case 628 -> {
                if (!after(40)) return true;
                Zombie z = zombie();
                if (local() == 40) {
                    if (z == null) { result("Mace beim Fliegen: kein Zombie FEHLER"); SelfTest.jump(631); return true; }
                    dev.teizen.client.util.Inv.select(0);
                    float[] r = dev.teizen.client.util.Rot.to(z.getBoundingBox().getCenter());
                    mc.player.setYRot(r[0]); mc.player.setXRot(r[1]);
                    hp0 = z.getHealth();
                    hpMe = mc.player.getHealth();
                    mc.gameMode.attack(mc.player, z);
                    dev.teizen.client.compat.V.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                    return true;
                }
                if (!after(70)) return true;
                float dmg = z == null ? 0 : hp0 - z.getHealth();
                int ff = get(MaceSwap.class).fakeFalls - n0;
                boolean hurtMe = mc.player.getHealth() < hpMe - 0.01f;
                result(String.format(Locale.ROOT, "Mace beim Fliegen: Sturz gezeigt=%s (%.1f Blöcke), Schaden am Zombie %.1f (ohne Sturz ~6), selbst Schaden=%s %s",
                        yes(ff > 0), get(MaceSwap.class).lastFall, dmg, yes(hurtMe), ok(ff > 0 && dmg > 14 && !hurtMe)));
                cmd("kill @e[type=minecraft:zombie,distance=..20]");
                off(get(MaceSwap.class));
                off(get(Fly.class));
                t("///set fly doppelsprung an");
                next();
            }
            // ------------------------------------------------ NoClip ohne OP: gräbt sich durch eine Wand
            case 629 -> {
                if (!after(20)) return true;
                cmd("clear @s");
                cmd("item replace entity @s hotbar.0 with minecraft:diamond_pickaxe");
                cmd("fill " + blk(a.offset(-1, 0, 2)) + " " + blk(a.offset(1, 2, 4)) + " minecraft:stone");
                tp(a.getX() + 0.5, a.getY(), a.getZ() + 0.5);
                t("///set noclip modus abbauen");
                next();
            }
            case 630 -> {
                if (!after(10)) return true;
                if (local() == 10) {
                    n0 = get(NoClip.class).dug;
                    on(get(NoClip.class));
                    mc.player.setYRot(0); mc.player.setXRot(0);
                    dev.teizen.client.util.Inv.select(0);
                    mc.options.keyUp.setDown(true);
                    return true;
                }
                boolean through = mc.player.getZ() > a.getZ() + 5;
                if (!through && !after(500)) return true;
                mc.options.keyUp.setDown(false);
                int d = get(NoClip.class).dug - n0;
                off(get(NoClip.class));
                cmd("fill " + blk(a.offset(-1, 0, 2)) + " " + blk(a.offset(1, 2, 4)) + " minecraft:air");
                result("NoClip ohne OP: durch 3 Blöcke Stein gegraben=" + yes(through) + " (" + d + " Blöcke abgebaut) " + ok(through && d >= 3));
                SelfTest.jump(640);
            }
            // ================================================ Paket 2
            // ------------------------------------------------ BetterChat: Uhrzeit + gleiche Nachrichten zusammen
            case 640 -> {
                if (!after(10)) return true;
                tp(a.getX() + 0.5, a.getY(), a.getZ() + 0.5);
                on(get(BetterChat.class));
                n0 = get(BetterChat.class).merged; n1 = get(BetterChat.class).stamped;
                for (int i = 0; i < 3; i++) mc.gui.hud.getChat().addClientSystemMessage(net.minecraft.network.chat.Component.literal("Teizen Spam-Test"));
                int m = get(BetterChat.class).merged - n0, st = get(BetterChat.class).stamped - n1;
                off(get(BetterChat.class));
                result("BetterChat: Uhrzeit dran=" + yes(st >= 3) + ", 3x gleiche Nachricht zusammengefasst (" + m + "x) " + ok(st >= 3 && m >= 2));
                next();
            }
            // ------------------------------------------------ WallHack + NoRender
            case 641 -> {
                on(get(FakePlayer.class));
                on(get(WallHack.class));
                on(get(NoRender.class));
                cmd("weather rain");
                next();
            }
            case 642 -> {
                if (!after(40)) return true;
                var fp = get(FakePlayer.class).dummy;
                boolean glow = fp != null && mc.shouldEntityAppearGlowing(fp);
                int hid = get(NoRender.class).hidden;
                boolean fire = NoRender.fire();
                cmd("weather clear");
                off(get(WallHack.class)); off(get(FakePlayer.class)); off(get(NoRender.class));
                result("WallHack: Spieler leuchtet durch Wände=" + yes(glow) + ", NoRender: Regen/Feuer ausgeblendet (" + hid + "x)=" + yes(hid > 0 && fire) + " " + ok(glow && hid > 0 && fire));
                next();
            }
            // ------------------------------------------------ BookBot
            case 643 -> {
                cmd("clear @s");
                cmd("item replace entity @s hotbar.4 with minecraft:writable_book");
                next();
            }
            case 644 -> {
                if (!after(10)) return true;
                if (local() == 10) { n0 = get(BookBot.class).written; on(get(BookBot.class)); return true; }
                if (!after(60)) return true;
                var st = mc.player.getInventory().getItem(4);
                var wb = st.get(net.minecraft.core.component.DataComponents.WRITABLE_BOOK_CONTENT);
                int pages = wb == null ? 0 : wb.pages().size();
                off(get(BookBot.class));
                result("BookBot: Buch geschrieben=" + yes(get(BookBot.class).written > n0) + ", Seiten im Buch " + pages + " " + ok(pages >= 10));
                next();
            }
            // ------------------------------------------------ BetterBeacons: Stärke schon bei kleiner Pyramide
            case 645 -> {
                BlockPos b = a.offset(6, 0, -6);
                cmd("fill " + blk(b.offset(-1, 0, -1)) + " " + blk(b.offset(1, 0, 1)) + " minecraft:iron_block");
                cmd("setblock " + blk(b.above()) + " minecraft:beacon");
                cmd("item replace entity @s hotbar.5 with minecraft:iron_ingot 4");
                tp(b.getX() + 2.5, b.getY() + 1, b.getZ() + 0.5);
                on(get(BetterBeacons.class));
                n0 = get(BetterBeacons.class).set;
                next();
            }
            case 646 -> {
                if (!after(30)) return true;
                BlockPos bb = a.offset(6, 1, -6);
                if (local() == 30) {
                    var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(bb), net.minecraft.core.Direction.WEST, bb, false);
                    mc.gameMode.useItemOn(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
                    return true;
                }
                boolean strength = mc.player.hasEffect(net.minecraft.world.effect.MobEffects.STRENGTH);
                if (!strength && !after(330)) return true;
                if (mc.player.containerMenu != mc.player.inventoryMenu) mc.player.closeContainer();
                off(get(BetterBeacons.class));
                result("BetterBeacons: Stärke bei Pyramide Stufe 1 gesetzt=" + yes(get(BetterBeacons.class).set > n0) + ", Effekt bekommen=" + yes(strength) + " " + ok(strength));
                next();
            }
            // ------------------------------------------------ EntityControl: Pferd schneller
            case 647 -> {
                tp(a.getX() + 0.5, a.getY(), a.getZ() + 0.5);
                cmd("summon minecraft:horse " + a.getX() + " " + a.getY() + " " + (a.getZ() - 8)
                        + " {Tame:1b,SaddleItem:{id:\"minecraft:saddle\",count:1},equipment:{saddle:{id:\"minecraft:saddle\",count:1}}}");
                next();
            }
            case 648 -> {
                if (!after(10)) return true;
                if (local() == 10) { cmd("ride @s mount @e[type=minecraft:horse,limit=1,sort=nearest]"); return true; }
                if (local() == 30) {
                    on(get(EntityControl.class));
                    mc.player.setYRot(0); mc.player.setXRot(0);
                    y0 = mc.player.getZ();
                    mc.options.keyUp.setDown(true);
                    return true;
                }
                if (!after(70)) return true;
                mc.options.keyUp.setDown(false);
                double moved = Math.abs(mc.player.getZ() - y0);
                boolean riding = mc.player.getVehicle() != null;
                off(get(EntityControl.class));
                mc.options.keyShift.setDown(true);
                cmd("ride @s dismount");
                result(String.format(Locale.ROOT, "EntityControl: auf dem Pferd=%s, in 2 s %.1f Blöcke (normal ~9) %s", yes(riding), moved, ok(riding && moved > 14)));
                next();
            }
            // ------------------------------------------------ HighwayBuilder: 5 Blöcke Tunnel + Boden
            case 649 -> {
                if (!after(10)) return true;
                mc.options.keyShift.setDown(false);
                cmd("kill @e[type=minecraft:horse,distance=..40]");
                cmd("clear @s");
                cmd("item replace entity @s hotbar.0 with minecraft:diamond_pickaxe");
                cmd("item replace entity @s hotbar.1 with minecraft:netherrack 64");
                BlockPos h = a.offset(-6, 0, 0);
                cmd("fill " + blk(h.offset(-2, -1, 1)) + " " + blk(h.offset(2, 3, 7)) + " minecraft:stone");
                cmd("fill " + blk(h.offset(-1, -1, 3)) + " " + blk(h.offset(1, -1, 4)) + " minecraft:air"); // Loch im Boden
                cmd("fill " + blk(h.offset(-1, 0, 0)) + " " + blk(h.offset(1, 2, 0)) + " minecraft:air");
                cmd(String.format(Locale.ROOT, "tp @s %.1f %d %.1f 0 0", h.getX() + 0.5, h.getY(), h.getZ() + 0.5));
                t("///set highwaybuilder laenge 5");
                t("///set highwaybuilder boden netherrack");
                next();
            }
            case 650 -> {
                if (!after(20)) return true;
                if (local() == 20) { mc.player.setYRot(0); on(get(HighwayBuilder.class)); return true; }
                if (get(HighwayBuilder.class).isEnabled() && !after(1500)) return true;
                BlockPos h = a.offset(-6, 0, 0);
                int clear = 0, floor = 0;
                for (int z = 1; z <= 5; z++) for (int x = -1; x <= 1; x++) {
                    boolean air = true;
                    for (int y = 0; y < 3; y++) if (!mc.level.getBlockState(h.offset(x, y, z)).isAir()) air = false;
                    if (air) clear++;
                    if (!mc.level.getBlockState(h.offset(x, -1, z)).isAir()) floor++;
                }
                off(get(HighwayBuilder.class));
                result("HighwayBuilder: 5 Blöcke weit gebaut, Tunnel frei " + clear + "/15, Boden da " + floor + "/15 (Loch zugemacht) " + ok(clear == 15 && floor == 15));
                cmd("fill " + blk(h.offset(-2, -1, 0)) + " " + blk(h.offset(2, 3, 7)) + " minecraft:air");
                next();
            }
            // ------------------------------------------------ Swarm: Chef schickt, Helfer führt aus
            case 651 -> {
                swarmOk = false; swarmLine = null;
                Thread th = new Thread(() -> {
                    try (java.net.ServerSocket ss = new java.net.ServerSocket(6971)) {
                        ss.setSoTimeout(20000);
                        try (java.net.Socket s = ss.accept()) {
                            var w = new java.io.PrintWriter(new java.io.OutputStreamWriter(s.getOutputStream(), java.nio.charset.StandardCharsets.UTF_8), true);
                            w.println("///set trail laenge 777");
                            Thread.sleep(3000);
                        }
                    } catch (Exception e) { swarmLine = "Fehler: " + e; }
                }, "swarm-test-chef");
                th.setDaemon(true);
                th.start();
                t("///set swarm modus helfer");
                t("///set swarm port 6971");
                next();
            }
            case 652 -> {
                if (!after(10)) return true;
                if (local() == 10) { on(get(Swarm.class)); return true; }
                var tl = get(Trail.class).settings.get("laenge"); boolean got = tl != null && tl.num() == 777;
                if (!got && !after(200)) return true;
                off(get(Swarm.class));
                swarmOk = got;
                // jetzt umgekehrt: Teizen ist Chef, der Test ist Helfer
                t("///set swarm modus chef");
                t("///set swarm port 6972");
                on(get(Swarm.class));
                next();
            }
            case 653 -> {
                if (!after(20)) return true;
                if (local() == 20) {
                    Thread th = new Thread(() -> {
                        try (java.net.Socket s = new java.net.Socket("127.0.0.1", 6972)) {
                            s.setSoTimeout(15000);
                            var r = new java.io.BufferedReader(new java.io.InputStreamReader(s.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
                            swarmLine = r.readLine();
                        } catch (Exception e) { swarmLine = "Fehler: " + e; }
                    }, "swarm-test-helfer");
                    th.setDaemon(true);
                    th.start();
                    return true;
                }
                if (local() == 60) { t("///swarm ///fly"); return true; }
                if (swarmLine == null && !after(300)) return true;
                off(get(Swarm.class));
                t("///set swarm port 6969");
                result("Swarm: Helfer führt Befehl vom Chef aus=" + yes(swarmOk) + ", Chef schickt an Helfer ('" + swarmLine + "') " + ok(swarmOk && "///fly".equals(swarmLine)));
                t("///set trail laenge 600");
                SelfTest.jump(631);
            }
            case 631 -> {
                releaseKeys();
                cmd("forceload remove " + (a.getX() - 16) + " " + (a.getZ() - 16) + " " + (a.getX() + 16) + " " + (a.getZ() + 16));
                cmd("effect clear @s");
                tp(o.getX() + 0.5, o.getY() + 1, o.getZ() + 0.5);
                SelfTest.jump(526);
            }
            default -> { return false; }
        }
        return true;
    }

    /** Erste Zeile vorne auf dem Schild (per Reflection: Namen unterscheiden sich zwischen 26.2 und 26.3). */
    private static String signLine(Object sb) {
        try {
            Object text = null;
            for (var m : sb.getClass().getMethods()) {
                if (m.getReturnType().getSimpleName().equals("SignText")) {
                    if (m.getParameterCount() == 0 && m.getName().toLowerCase(Locale.ROOT).contains("front")) { text = m.invoke(sb); break; }
                    if (m.getParameterCount() == 1 && m.getParameterTypes()[0].isEnum()) {
                        log("Schild lesen über " + m.getName() + "(" + m.getParameterTypes()[0].getSimpleName() + ")");
                        Object front = m.getParameterTypes()[0].getEnumConstants()[0];
                        for (Object k : m.getParameterTypes()[0].getEnumConstants()) if (k.toString().toUpperCase(Locale.ROOT).contains("FRONT")) front = k;
                        text = m.invoke(sb, front); break;
                    }
                    if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == boolean.class) { text = m.invoke(sb, true); break; }
                }
            }
            if (text == null) return "(SignText?)";
            StringBuilder names = new StringBuilder();
            for (var m : text.getClass().getMethods()) {
                if (!net.minecraft.network.chat.Component.class.isAssignableFrom(m.getReturnType())) continue;
                Class<?>[] ps = m.getParameterTypes();
                names.append(m.getName()).append(java.util.Arrays.toString(ps)).append(' ');
                if (ps.length == 2 && ps[0] == int.class && ps[1] == boolean.class) return ((net.minecraft.network.chat.Component) m.invoke(text, 0, false)).getString();
                if (ps.length == 1 && ps[0] == int.class) return ((net.minecraft.network.chat.Component) m.invoke(text, 0)).getString();
            }
            // 26.3: Zeilen evtl. als Liste/Array
            for (var m : text.getClass().getMethods()) {
                if (m.getParameterCount() != 0 || m.getDeclaringClass() == Object.class) continue;
                Object v = m.invoke(text);
                if (v instanceof net.minecraft.network.chat.Component[] arr && arr.length > 0) return arr[0].getString();
                if (v instanceof java.util.List<?> li && !li.isEmpty() && li.get(0) instanceof net.minecraft.network.chat.Component c) return c.getString();
            }
            return "(" + names + ")";
        } catch (Throwable t) {
            Throwable c = t instanceof java.lang.reflect.InvocationTargetException ite && ite.getCause() != null ? ite.getCause() : t;
            return "(" + c + ")";
        }
    }

    private static Zombie zombie() {
        List<Zombie> l = mc.level.getEntitiesOfClass(Zombie.class, new AABB(a).inflate(8), LivingEntity::isAlive);
        return l.isEmpty() ? null : l.get(0);
    }
}
