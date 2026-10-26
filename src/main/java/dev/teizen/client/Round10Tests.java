package dev.teizen.client;

import dev.teizen.client.core.Commands;
import dev.teizen.client.core.Lock;
import dev.teizen.client.core.Module;
import dev.teizen.client.gui.LockedScreen;
import dev.teizen.client.modules.ChestStealer;
import dev.teizen.client.modules.ShulkerUnpacker;
import dev.teizen.client.util.Inv;
import dev.teizen.client.util.Loot;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Selbsttest Runde 10: ChestStealer mit Rangliste/Müll-Liste und Shulker ausräumen (aus der Kiste und mit ///shulker). */
final class Round10Tests {
    private static final Minecraft mc = Minecraft.getInstance();

    private static BlockPos area;
    private static int u0, sel0;
    private static final List<Item> order = new ArrayList<>();
    private static boolean lockA, normalOk;
    private static String lockText = "";

    private Round10Tests() {}

    private static void cmd(String c) { SelfTest.cmd2(c); }
    private static boolean after(int t) { return SelfTest.after2(t); }
    private static void next() { SelfTest.next2(); }
    private static void result(String r) { SelfTest.result(r); }
    private static void log(String s) { SelfTest.log2(s); }
    private static void shot(String n) { SelfTest.shot2(n); }
    private static void t(String c) { Commands.handle(c); }
    private static String ok(boolean b) { return b ? "OK" : "FEHLER"; }
    private static String yes(boolean b) { return b ? "ja" : "nein"; }

    private static ChestStealer cs() { return TeizenClient.modules().get(ChestStealer.class); }

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

    private static BlockPos chest() { return area.offset(2, 0, 0); }

    private static void openChest() {
        BlockPos c = chest();
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(c), Direction.WEST, c, false));
    }

    private static int count(Item item) {
        int n = 0;
        for (int i = 0; i < 36; i++) if (mc.player.getInventory().getItem(i).is(item)) n += mc.player.getInventory().getItem(i).getCount();
        return n;
    }

    private static boolean chestOpen() {
        return mc.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>;
    }

    private static int shulkerBlocksAround() {
        int n = 0;
        for (BlockPos p : BlockPos.betweenClosed(area.offset(-4, -1, -4), area.offset(4, 3, 4)))
            if (mc.level.getBlockState(p).getBlock() instanceof ShulkerBoxBlock) n++;
        return n;
    }

    private static int shulkerItemsOnGround() {
        return mc.level.getEntitiesOfClass(ItemEntity.class, new AABB(area).inflate(8), e -> Loot.isShulker(e.getItem())).size();
    }

    /** Erster Shulker im Inventar (oder leer). */
    private static ItemStack shulkerInInv() {
        for (int i = 0; i < 36; i++) if (Loot.isShulker(mc.player.getInventory().getItem(i))) return mc.player.getInventory().getItem(i);
        return ItemStack.EMPTY;
    }

    private static String contents(ItemStack shulker) {
        List<String> parts = new ArrayList<>();
        for (ItemStack s : Loot.contents(shulker)) parts.add(s.getCount() + "x " + Loot.id(s));
        return parts.isEmpty() ? "leer" : String.join(", ", parts);
    }

    private static int shulkersInInv() {
        int n = 0;
        for (int i = 0; i < 36; i++) if (Loot.isShulker(mc.player.getInventory().getItem(i))) n++;
        return n;
    }

    private static void stealerOff() {
        if (cs().isEnabled()) cs().setEnabled(false);
    }

    static void step(int step, BlockPos o) {
        switch (step) {
            // ---------------------------------------------- Rangliste: Inventar fast voll -> nur die 3 besten
            case 400 -> {
                area = o.offset(-90, 0, -90);
                tp(area, 0.5, 1, 0.5, -90, 0);
                next();
            }
            case 401 -> {
                if (!after(20)) return;
                arena(area, 6, 5);
                cmd("gamemode survival @s");
                cmd("clear @s");
                cmd("kill @e[type=minecraft:item]");
                cmd("effect give @s minecraft:instant_health 1 5");
                // 33 von 36 Plätzen voll -> nur 3 frei
                for (int i = 0; i < 9; i++) cmd("item replace entity @s hotbar." + i + " with minecraft:stone_bricks 64");
                for (int i = 0; i < 24; i++) cmd("item replace entity @s inventory." + i + " with minecraft:stone_bricks 64");
                cmd("setblock " + blk(chest()) + " minecraft:chest[facing=west]{Items:["
                        + "{Slot:0b,id:\"minecraft:dirt\",count:64},{Slot:1b,id:\"minecraft:cobblestone\",count:64},"
                        + "{Slot:2b,id:\"minecraft:oak_log\",count:16},{Slot:3b,id:\"minecraft:iron_ingot\",count:10},"
                        + "{Slot:4b,id:\"minecraft:bread\",count:8},{Slot:10b,id:\"minecraft:diamond\",count:5},"
                        + "{Slot:15b,id:\"minecraft:netherite_ingot\",count:2},{Slot:26b,id:\"minecraft:totem_of_undying\",count:1}]}");
                tp(area, 0.5, 0, 0.5, -90, 20);
                stealerOff();
                t("///set cheststealer pause 0");
                t("///set cheststealer filter an");
                t("///set cheststealer sortieren an");
                t("///cheststealer nie standard");
                t("///cheststealer zuerst leer");
                cs().setEnabled(true);
                next();
            }
            case 402 -> {
                if (!after(25)) return;
                log("frei vor dem Öffnen: " + Loot.freeSlots());
                openChest();
                next();
            }
            case 403 -> {
                if (chestOpen() && !after(100)) return;
                if (!after(10)) return;
                boolean best = count(Items.TOTEM_OF_UNDYING) == 1 && count(Items.NETHERITE_INGOT) == 2 && count(Items.DIAMOND) == 5;
                boolean rest = count(Items.OAK_LOG) == 0 && count(Items.IRON_INGOT) == 0 && count(Items.BREAD) == 0;
                boolean junk = count(Items.DIRT) == 0 && count(Items.COBBLESTONE) == 0;
                result("ChestStealer Rangliste: bei 3 freien Plätzen genommen Totem/Netherite/Diamant=" + yes(best)
                        + ", nichts Schlechteres=" + yes(rest) + ", Dirt/Cobblestone liegen gelassen=" + yes(junk)
                        + ", Kiste zu=" + yes(!chestOpen()) + " " + ok(best && rest && junk && !chestOpen()));
                shot("57-cheststealer-rangliste");
                stealerOff();
                cmd("data remove block " + blk(chest()) + " Items"); // nichts herausfallen lassen
                cmd("setblock " + blk(chest()) + " minecraft:air");
                cmd("clear @s");
                next();
            }
            // ---------------------------------------------- Reihenfolge mit Pause + Liste "zuerst" (per Befehl)
            case 404 -> {
                if (!after(5)) return;
                cmd("kill @e[type=minecraft:item]");
                cmd("setblock " + blk(chest()) + " minecraft:chest[facing=west]{Items:["
                        + "{Slot:0b,id:\"minecraft:dirt\",count:64},{Slot:3b,id:\"minecraft:gravel\",count:10},"
                        + "{Slot:5b,id:\"minecraft:oak_log\",count:32},{Slot:20b,id:\"minecraft:diamond\",count:3},"
                        + "{Slot:26b,id:\"minecraft:enchanted_golden_apple\",count:1}]}");
                t("///set cheststealer pause 2");
                t("///cheststealer zuerst add oak_log");
                cs().setEnabled(true);
                order.clear();
                next();
            }
            case 405 -> {
                if (!after(20)) return;
                log("zuerst = " + cs().first.selected());
                openChest();
                next();
            }
            case 406 -> {
                for (Item it : new Item[]{Items.OAK_LOG, Items.ENCHANTED_GOLDEN_APPLE, Items.DIAMOND, Items.DIRT, Items.GRAVEL})
                    if (count(it) > 0 && !order.contains(it)) order.add(it);
                if (chestOpen() && !after(200)) return;
                if (!after(15)) return;
                List<String> names = new ArrayList<>();
                for (Item it : order) names.add(Loot.id(new ItemStack(it)));
                boolean right = order.equals(List.of(Items.OAK_LOG, Items.ENCHANTED_GOLDEN_APPLE, Items.DIAMOND));
                boolean listOk = cs().first.selected().equals(List.of("oak_log"));
                t("///cheststealer zuerst weg oak_log");
                boolean removed = cs().first.selected().isEmpty();
                result("ChestStealer Reihenfolge (Pause 2, zuerst oak_log): " + String.join(" > ", names)
                        + " (soll oak_log > enchanted_golden_apple > diamond), Dirt/Kies liegen gelassen=" + yes(count(Items.DIRT) == 0 && count(Items.GRAVEL) == 0)
                        + ", Kiste zu=" + yes(!chestOpen()) + ", ///cheststealer zuerst add/weg=" + yes(listOk && removed) + " "
                        + ok(right && listOk && removed && !chestOpen()));
                stealerOff();
                t("///set cheststealer pause 0");
                cmd("data remove block " + blk(chest()) + " Items"); // nichts herausfallen lassen
                cmd("setblock " + blk(chest()) + " minecraft:air");
                cmd("clear @s");
                next();
            }
            // ---------------------------------------------- Shulker in der Kiste: hinstellen, gute Sachen raus, wieder einpacken
            case 407 -> {
                if (!after(5)) return;
                cmd("kill @e[type=minecraft:item]");
                cmd("item replace entity @s hotbar.0 with minecraft:diamond_pickaxe");
                cmd("item replace entity @s hotbar.1 with minecraft:stone 16");
                cmd("setblock " + blk(chest()) + " minecraft:chest[facing=west]{Items:["
                        + "{Slot:4b,id:\"minecraft:red_shulker_box\",count:1,components:{\"minecraft:container\":["
                        + "{slot:0,item:{id:\"minecraft:diamond\",count:10}},{slot:1,item:{id:\"minecraft:dirt\",count:64}},"
                        + "{slot:5,item:{id:\"minecraft:golden_apple\",count:3}}]}},"
                        + "{Slot:8b,id:\"minecraft:iron_ingot\",count:5}]}");
                tp(area, 0.5, 0, 0.5, -90, 20);
                t("///cheststealer shulker an");
                u0 = ShulkerUnpacker.unpacked;
                next();
            }
            case 408 -> {
                if (!after(20)) return;
                Inv.select(1);
                sel0 = 1;
                cs().setEnabled(true);
                next();
            }
            case 409 -> {
                if (!after(5)) return;
                openChest();
                next();
            }
            case 410 -> {
                int local = SelfTest.local();
                if (local % 20 == 0) log("Shulker aus Kiste " + local + ": busy=" + ShulkerUnpacker.busy() + ", fertig=" + (ShulkerUnpacker.unpacked - u0)
                        + ", Blöcke=" + shulkerBlocksAround() + ", Problem='" + ShulkerUnpacker.lastProblem + "'");
                if (local == 12) shot("58a-shulker-hingestellt");
                boolean done = ShulkerUnpacker.unpacked > u0 && !ShulkerUnpacker.busy();
                if (!done && !after(400)) return;
                if (!after(30)) return;
                ItemStack sh = shulkerInInv();
                List<ItemStack> inside = Loot.contents(sh);
                boolean onlyDirt = inside.size() == 1 && inside.get(0).is(Items.DIRT) && inside.get(0).getCount() == 64;
                boolean got = count(Items.DIAMOND) == 10 && count(Items.GOLDEN_APPLE) == 3 && count(Items.IRON_INGOT) == 5 && count(Items.DIRT) == 0;
                boolean clean = shulkerBlocksAround() == 0 && shulkerItemsOnGround() == 0;
                boolean hand = Inv.selected() == sel0 && mc.player.getInventory().getItem(1).is(Items.STONE);
                result("Shulker aus Kiste: hingestellt+ausgeräumt=" + yes(ShulkerUnpacker.unpacked > u0) + ", Diamanten/Goldäpfel/Eisen genommen=" + yes(got)
                        + ", im Shulker nur noch: " + contents(sh) + ", Shulker wieder im Inventar=" + yes(shulkersInInv() == 1)
                        + ", nichts mehr in der Welt=" + yes(clean) + ", Hand wie vorher=" + yes(hand)
                        + (ShulkerUnpacker.lastProblem.isEmpty() ? "" : ", Problem: " + ShulkerUnpacker.lastProblem) + " "
                        + ok(got && onlyDirt && shulkersInInv() == 1 && clean && hand && ShulkerUnpacker.lastProblem.isEmpty()));
                shot("58-shulker-aus-kiste");
                stealerOff();
                cmd("data remove block " + blk(chest()) + " Items"); // nichts herausfallen lassen
                cmd("setblock " + blk(chest()) + " minecraft:air");
                cmd("clear @s");
                next();
            }
            // ---------------------------------------------- ///shulker: Shulker im Inventar ausräumen (ChestStealer aus)
            case 411 -> {
                if (!after(5)) return;
                cmd("kill @e[type=minecraft:item]");
                cmd("item replace entity @s hotbar.0 with minecraft:diamond_pickaxe");
                cmd("item replace entity @s inventory.5 with minecraft:blue_shulker_box[minecraft:container=["
                        + "{slot:0,item:{id:\"minecraft:emerald\",count:7}},{slot:2,item:{id:\"minecraft:cobblestone\",count:64}},"
                        + "{slot:3,item:{id:\"minecraft:totem_of_undying\",count:1}}]]");
                tp(area, 0.5, 0, 0.5, 90, 20);
                stealerOff();
                u0 = ShulkerUnpacker.unpacked;
                next();
            }
            case 412 -> {
                if (!after(20)) return;
                Inv.select(0);
                t("///shulker");
                next();
            }
            case 413 -> {
                int local = SelfTest.local();
                if (local % 20 == 0) log("///shulker " + local + ": busy=" + ShulkerUnpacker.busy() + ", fertig=" + (ShulkerUnpacker.unpacked - u0)
                        + ", Problem='" + ShulkerUnpacker.lastProblem + "'");
                boolean done = ShulkerUnpacker.unpacked > u0 && !ShulkerUnpacker.busy();
                if (!done && !after(400)) return;
                if (!after(30)) return;
                ItemStack sh = shulkerInInv();
                List<ItemStack> inside = Loot.contents(sh);
                boolean onlyCobble = inside.size() == 1 && inside.get(0).is(Items.COBBLESTONE);
                boolean got = count(Items.EMERALD) == 7 && count(Items.TOTEM_OF_UNDYING) == 1 && count(Items.COBBLESTONE) == 0;
                boolean clean = shulkerBlocksAround() == 0 && shulkerItemsOnGround() == 0;
                result("///shulker (Inventar, ChestStealer aus): Smaragde+Totem genommen=" + yes(got) + ", im Shulker nur noch: " + contents(sh)
                        + ", Shulker wieder im Inventar=" + yes(shulkersInInv() == 1) + ", nichts mehr in der Welt=" + yes(clean)
                        + (ShulkerUnpacker.lastProblem.isEmpty() ? "" : ", Problem: " + ShulkerUnpacker.lastProblem) + " "
                        + ok(got && onlyCobble && shulkersInInv() == 1 && clean && ShulkerUnpacker.lastProblem.isEmpty()));
                shot("59-shulker-inventar");
                cmd("clear @s");
                next();
            }
            // ---------------------------------------------- Inventar fast voll: ein Platz bleibt frei, der Shulker kommt sicher zurück
            case 414 -> {
                if (!after(5)) return;
                cmd("kill @e[type=minecraft:item]");
                cmd("item replace entity @s hotbar.0 with minecraft:diamond_pickaxe");
                // 35 Plätze voll (Spitzhacke, Shulker, 33 Stapel Steinziegel), nur inventory.25 frei
                for (int i = 1; i < 9; i++) cmd("item replace entity @s hotbar." + i + " with minecraft:stone_bricks 64");
                for (int i = 0; i < 25; i++) {
                    if (i == 5) continue;
                    cmd("item replace entity @s inventory." + i + " with minecraft:stone_bricks 64");
                }
                cmd("item replace entity @s inventory.26 with minecraft:stone_bricks 64");
                cmd("item replace entity @s inventory.5 with minecraft:lime_shulker_box[minecraft:container=["
                        + "{slot:0,item:{id:\"minecraft:gold_ingot\",count:5}},{slot:1,item:{id:\"minecraft:emerald\",count:4}},"
                        + "{slot:2,item:{id:\"minecraft:diamond\",count:3}}]]");
                u0 = ShulkerUnpacker.unpacked;
                next();
            }
            case 415 -> {
                if (!after(20)) return;
                log("frei vor ///shulker: " + Loot.freeSlots());
                Inv.select(0);
                t("///shulker");
                next();
            }
            case 416 -> {
                boolean done = ShulkerUnpacker.unpacked > u0 && !ShulkerUnpacker.busy();
                if (!done && !after(400)) return;
                if (!after(30)) return;
                ItemStack sh = shulkerInInv();
                // 1 Platz frei + Platz vom Shulker = 2; einer bleibt frei -> genau ein Stapel (der beste: Diamant)
                boolean one = count(Items.DIAMOND) == 3 && count(Items.EMERALD) == 0 && count(Items.GOLD_INGOT) == 0;
                boolean back = shulkersInInv() == 1 && shulkerItemsOnGround() == 0 && shulkerBlocksAround() == 0;
                result("Shulker bei fast vollem Inventar: nur der beste Stapel (Diamant) genommen=" + yes(one) + ", im Shulker: " + contents(sh)
                        + ", Shulker sicher zurück im Inventar=" + yes(back) + " " + ok(one && back));
                cmd("clear @s");
                next();
            }
            case 417 -> {
                if (!after(5)) return;
                cmd("kill @e[type=minecraft:item]");
                stealerOff();
                t("///set cheststealer pause 0");
                t("///cheststealer zuerst leer");
                // ///cheststealer nie add / weg und ein unbekanntes Item
                t("///cheststealer nie add string");
                boolean added = cs().never.selected().contains("string");
                t("///cheststealer nie weg string");
                boolean gone = !cs().never.selected().contains("string");
                t("///cheststealer nie add gibtsnicht");
                boolean unknownSkipped = !cs().never.selected().contains("gibtsnicht");
                t("///cheststealer liste");
                result("///cheststealer nie add/weg=" + yes(added && gone) + ", unbekanntes Item abgelehnt=" + yes(unknownSkipped) + " "
                        + ok(added && gone && unknownSkipped));
                next();
            }
            // ---------------------------------------------- Key-Sperre: Version mit Key-Pflicht, Key-Mod fehlt -> alles gesperrt
            case 418 -> {
                if (!after(10)) return;
                Module fb = TeizenClient.modules().byName("fullbright");
                if (fb.isEnabled()) fb.setEnabled(false);
                normalOk = Lock.ok() && !Lock.REQUIRED;
                Lock.testRequired = true;
                Lock.testKeyMissing = true;
                t("///fullbright");
                boolean cmdBlocked = !fb.isEnabled();
                fb.setEnabled(true);
                fb.toggle();
                boolean directBlocked = !fb.isEnabled();
                boolean menuLocked = Lock.menu(dev.teizen.client.gui.ClickGui::new) instanceof LockedScreen
                        && Lock.menu(dev.teizen.client.gui.Overview::new) instanceof LockedScreen;
                boolean modMenuLocked = false;
                try {
                    Class<?> mm = Class.forName("com.terraformersmc.modmenu.ModMenu");
                    Object scr = mm.getMethod("getConfigScreen", String.class, net.minecraft.client.gui.screens.Screen.class).invoke(null, "teizenclient", null);
                    modMenuLocked = scr instanceof LockedScreen;
                } catch (Throwable t) {
                    log("Mod Menu Zugriff fehlgeschlagen: " + t);
                }
                int u = ShulkerUnpacker.unpacked;
                t("///shulker");
                boolean shulkerBlocked = !ShulkerUnpacker.busy() && ShulkerUnpacker.unpacked == u;
                lockA = cmdBlocked && directBlocked && menuLocked && modMenuLocked && shulkerBlocked && !Lock.ok();
                lockText = "Befehl gesperrt=" + yes(cmdBlocked) + ", direkt einschalten gesperrt=" + yes(directBlocked) + ", Menü/Übersicht zeigen Fehler="
                        + yes(menuLocked) + ", Mod-Menu zeigt Fehler=" + yes(modMenuLocked) + ", ///shulker gesperrt=" + yes(shulkerBlocked);
                t("///gui");
                next();
            }
            case 419 -> {
                if (!after(10)) return;
                boolean errorShown = mc.gui.screen() instanceof LockedScreen;
                shot("60-gesperrt-fehler");
                result("Key-Sperre ohne Key-Mod: " + lockText + ", ///gui zeigt Fehler-Bildschirm=" + yes(errorShown) + " " + ok(lockA && errorShown));
                mc.gui.setScreen(null);
                next();
            }
            // ---------------------------------------------- Key-Sperre: Version mit Key-Pflicht + echte Key-Mod im mods-Ordner -> alles geht
            case 420 -> {
                if (!after(5)) return;
                Lock.testKeyMissing = null; // echte Prüfung: liegt teizen-key.jar im mods-Ordner und passt der Schlüssel?
                Module fb = TeizenClient.modules().byName("fullbright");
                boolean keyMod = net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded(Lock.KEY_MOD);
                t("///fullbright");
                boolean on = fb.isEnabled();
                boolean menu = Lock.menu(dev.teizen.client.gui.ClickGui::new) instanceof dev.teizen.client.gui.ClickGui;
                if (on) fb.setEnabled(false);
                Lock.testRequired = null;
                Lock.testKeyMissing = null;
                result("Key-Sperre mit Key-Mod: Key-Mod geladen=" + yes(keyMod) + ", Schlüssel passt=" + yes(Lock.keyPresent()) + ", Funktion geht=" + yes(on)
                        + ", Menü geht=" + yes(menu) + "; normale Version ohne Sperre=" + yes(normalOk) + " " + ok(keyMod && on && menu && normalOk && Lock.ok()));
                SelfTest.jump(152);
            }
            default -> {}
        }
    }
}
