package dev.teizen.client.modules;

import dev.teizen.client.TeizenClient;
import dev.teizen.client.mixin.MultiPlayerGameModeAccessor;
import dev.teizen.client.util.BlockSwap;
import dev.teizen.client.util.Breaker;
import dev.teizen.client.util.Chat;
import dev.teizen.client.util.Ids;
import dev.teizen.client.util.Inv;
import dev.teizen.client.util.Loot;
import dev.teizen.client.util.Placer;
import dev.teizen.client.util.Rot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;

/**
 * Räumt Shulker aus, die als Item im Inventar liegen: kurz neben dich stellen, öffnen,
 * die guten Sachen nehmen (wie ChestStealer: beste zuerst, Müll bleibt drin), wieder abbauen und aufheben.
 * Ohne Server-Mod geht es nicht anders – der Server lässt Shulker nur als Block öffnen.
 */
public final class ShulkerUnpacker {
    private enum Phase { IDLE, PLACE, OPEN, TAKE, BREAK, PICKUP }

    private static final Minecraft mc = Minecraft.getInstance();
    /** Volle Blöcke ohne Block-Entity, die beim Anklicken ein Fenster öffnen (darauf wird nichts gestellt). */
    private static final Set<String> INTERACTIVE = Set.of("crafting_table", "smithing_table", "cartography_table", "loom",
            "fletching_table", "note_block", "respawn_anchor");

    private static final Deque<Integer> queue = new ArrayDeque<>();
    private static Phase phase = Phase.IDLE;
    private static boolean running;
    private static BlockPos pos;
    private static int timer;
    /** Hand vorher, Hotbar-Platz für den Shulker, Platz mit dem weggetauschten Hand-Item (-1 = nichts getauscht). */
    private static int prevSlot = -1, handSlot = -1, swappedFrom = -1, breakSlot = -1;
    private static int shulkersBefore, doneThisRun, takenThisRun;
    /** Läuft gerade zum heruntergefallenen Shulker. */
    private static boolean walked;

    /** Für den Selbsttest: so viele Shulker ganz ausgeräumt und wieder eingepackt, so viele Stapel genommen. */
    public static int unpacked, stacksTaken;
    /** Letzte Fehlermeldung (für den Selbsttest), leer = alles gut. */
    public static String lastProblem = "";

    private ShulkerUnpacker() {}

    /** Läuft gerade etwas (oder wartet noch ein Shulker)? */
    public static boolean busy() { return running; }

    /** Gehört das offene Fenster gerade dem Shulker-Helfer (dann hält sich ChestStealer raus)? */
    public static boolean ownsScreen() { return phase == Phase.OPEN || phase == Phase.TAKE; }

    private static Loot.Prefs prefs() {
        return TeizenClient.modules().get(ChestStealer.class).prefs();
    }

    // ------------------------------------------------------------------ Start / Stopp

    /** ///shulker: alle Shulker im Inventar ausräumen. */
    public static void startInventory() {
        if (mc.player == null) return;
        if (running) { Chat.info("Shulker werden schon ausgeräumt …"); return; }
        Loot.Prefs p = prefs();
        List<Integer> slots = new ArrayList<>();
        int shulkers = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getItem(i);
            if (!Loot.isShulker(s)) continue;
            shulkers++;
            if (Loot.hasWanted(s, p)) slots.add(i);
        }
        if (slots.isEmpty()) {
            Chat.info(shulkers == 0 ? "Du hast keine Shulker im Inventar." : "In deinen Shulkern ist nichts, was genommen werden soll.");
            return;
        }
        queue(slots);
    }

    /** Diese Inventar-Plätze (mit Shulkern) nacheinander ausräumen. */
    public static void queue(List<Integer> slots) {
        if (slots.isEmpty()) return;
        if (!running) {
            running = true;
            doneThisRun = 0;
            takenThisRun = 0;
            lastProblem = "";
        }
        for (int s : slots) if (!queue.contains(s)) queue.add(s);
        Chat.info("Räume " + slots.size() + " Shulker aus …");
    }

    /** Abbrechen (z.B. ///panic): offene Fenster zu, Hand zurück. */
    public static void stop() {
        if (!running) return;
        if (phase == Phase.BREAK) Breaker.stop();
        if (ownsScreen() && mc.player != null) mc.player.closeContainer();
        if (mc.player != null) restoreHand();
        if (pos != null && mc.level != null && mc.level.getBlockState(pos).getBlock() instanceof ShulkerBoxBlock)
            Chat.error("Shulker steht noch bei " + pos.toShortString());
        reset();
    }

    /** Alles vergessen (z.B. Verbindung weg). */
    public static void reset() {
        queue.clear();
        phase = Phase.IDLE;
        running = false;
        pos = null;
        prevSlot = handSlot = swappedFrom = breakSlot = -1;
        walked = false;
    }

    // ------------------------------------------------------------------ Ablauf

    public static void tick() {
        if (!running) return;
        if (mc.player == null || mc.level == null || mc.gameMode == null) { reset(); return; }
        if (mc.player.isDeadOrDying()) { stop(); return; }
        timer++;
        switch (phase) {
            case IDLE -> startNext();
            case PLACE -> tickPlace();
            case OPEN -> tickOpen();
            case TAKE -> tickTake();
            case BREAK -> tickBreak();
            case PICKUP -> tickPickup();
        }
    }

    private static void problem(String msg) {
        lastProblem = msg;
        Chat.error(msg);
    }

    private static void startNext() {
        // warten, bis kein Fenster mehr offen ist (z.B. die Kiste, aus der der Shulker kam)
        if (mc.gui.screen() != null) return;
        Loot.Prefs p = prefs();
        while (!queue.isEmpty()) {
            int slot = queue.poll();
            ItemStack s = mc.player.getInventory().getItem(slot);
            if (!Loot.isShulker(s) || !Loot.hasWanted(s, p)) continue;
            if (!Loot.roomFor(s, p)) {
                problem("Inventar voll – Shulker nicht ausgeräumt.");
                queue.clear();
                break;
            }
            BlockPos spot = findSpot();
            if (spot == null) {
                problem("Kein Platz für den Shulker: stell dich auf freien, ebenen Boden (neben dir 2 Blöcke Luft).");
                queue.clear();
                break;
            }
            shulkersBefore = countShulkers();
            if (!takeInHand(slot)) {
                restoreHand();
                problem("Shulker konnte nicht in die Hand genommen werden.");
                continue;
            }
            pos = spot;
            Placer.placeAgainst(pos, Direction.DOWN, 0.5, true);
            phase = Phase.PLACE;
            timer = 0;
            return;
        }
        // nichts mehr zu tun
        if (doneThisRun > 0) Chat.ok("Shulker ausgeräumt: " + doneThisRun + " Shulker, " + takenThisRun + " Stapel genommen.");
        reset();
    }

    private static void tickPlace() {
        if (mc.level.getBlockState(pos).getBlock() instanceof ShulkerBoxBlock) {
            restoreHand();
            phase = Phase.OPEN;
            timer = 0;
            openClick();
            return;
        }
        if (timer > 10) {
            restoreHand();
            problem("Shulker ließ sich nicht hinstellen.");
            phase = Phase.IDLE;
        }
    }

    private static void tickOpen() {
        if (mc.player.containerMenu instanceof ShulkerBoxMenu && mc.gui.screen() instanceof AbstractContainerScreen<?>) {
            phase = Phase.TAKE;
            timer = 0;
            return;
        }
        if (!(mc.level.getBlockState(pos).getBlock() instanceof ShulkerBoxBlock) && countShulkers() >= shulkersBefore) {
            // der Server hat das Hinstellen abgelehnt – der Shulker ist noch im Inventar
            problem("Shulker ließ sich nicht hinstellen.");
            phase = Phase.IDLE;
            return;
        }
        if (timer % 10 == 0) openClick();
        if (timer > 40) {
            problem("Shulker ging nicht auf (ist darüber frei? nicht schleichen).");
            startBreak();
        }
    }

    private static void tickTake() {
        // kurz warten, bis der Inhalt vom Server da ist
        if (timer < 2) return;
        if (!(mc.player.containerMenu instanceof ShulkerBoxMenu menu)) {
            startBreak();
            return;
        }
        // einen Platz frei lassen, damit der Shulker wieder ins Inventar passt
        int n = Loot.steal(menu, prefs(), 0, 1);
        stacksTaken += n;
        takenThisRun += n;
        mc.player.closeContainer();
        startBreak();
    }

    private static void startBreak() {
        if (ownsScreen() && mc.gui.screen() != null) mc.player.closeContainer();
        phase = Phase.BREAK;
        timer = 0;
        breakSlot = Inv.selected();
    }

    private static void tickBreak() {
        if (!(mc.level.getBlockState(pos).getBlock() instanceof ShulkerBoxBlock)) {
            Breaker.stop();
            if (breakSlot >= 0) Inv.select(breakSlot);
            breakSlot = -1;
            phase = Phase.PICKUP;
            timer = 0;
            return;
        }
        if (!Breaker.inReach(pos)) {
            Breaker.stop();
            problem("Shulker steht noch bei " + pos.toShortString() + " (zu weit weg zum Abbauen).");
            phase = Phase.IDLE;
            return;
        }
        Breaker.tick(pos, true, true);
        if (timer > 200) {
            Breaker.stop();
            if (breakSlot >= 0) Inv.select(breakSlot);
            breakSlot = -1;
            problem("Shulker ließ sich nicht abbauen – er steht bei " + pos.toShortString());
            phase = Phase.IDLE;
        }
    }

    private static void tickPickup() {
        if (countShulkers() >= shulkersBefore) {
            if (walked) stopWalking();
            unpacked++;
            doneThisRun++;
            pos = null;
            phase = Phase.IDLE;
            return;
        }
        // nach der kurzen Aufhebe-Pause noch nicht im Inventar (weggerollt)? -> die paar Schritte hingehen
        ItemEntity drop = timer > 12 ? findDrop() : null;
        if (drop != null) walkTo(drop.position());
        if (timer > 100) {
            if (walked) stopWalking();
            problem("Shulker liegt noch auf dem Boden bei " + (drop != null ? drop.blockPosition() : pos).toShortString() + " – bitte aufheben.");
            pos = null;
            phase = Phase.IDLE;
        }
    }

    /** Der abgebaute Shulker als Item am Boden (nächster an der Stelle, wo er stand). */
    private static ItemEntity findDrop() {
        ItemEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (ItemEntity e : mc.level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(4), it -> it.isAlive() && Loot.isShulker(it.getItem()))) {
            double d = e.distanceToSqr(Vec3.atCenterOf(pos));
            if (d < bestD) { bestD = d; best = e; }
        }
        return best;
    }

    /** Ein Stück direkt auf das Ziel zu gehen (nur kurze Wege auf gleicher Höhe). */
    private static void walkTo(Vec3 target) {
        Vec3 me = mc.player.position();
        double dx = target.x - me.x, dz = target.z - me.z, h = Math.sqrt(dx * dx + dz * dz);
        if (h < 0.15 || h > 5 || Math.abs(target.y - me.y) > 1.5) return;
        double speed = Math.min(0.2, h);
        Vec3 v = mc.player.getDeltaMovement();
        mc.player.setDeltaMovement(dx / h * speed, v.y, dz / h * speed);
        walked = true;
    }

    private static void stopWalking() {
        walked = false;
        if (mc.player == null) return;
        Vec3 v = mc.player.getDeltaMovement();
        mc.player.setDeltaMovement(0, v.y, 0);
    }

    // ------------------------------------------------------------------ Helfer

    private static int countShulkers() {
        int n = 0;
        for (int i = 0; i < 36; i++) if (Loot.isShulker(mc.player.getInventory().getItem(i))) n++;
        return n;
    }

    /** Shulker aus diesem Inventar-Platz in die Hand nehmen (möglichst auf einen leeren Hotbar-Platz). */
    private static boolean takeInHand(int slot) {
        prevSlot = Inv.selected();
        swappedFrom = -1;
        if (slot < 9) {
            handSlot = slot;
        } else {
            int empty = -1;
            for (int h = 0; h < 9; h++) if (mc.player.getInventory().getItem(h).isEmpty()) { empty = h; break; }
            handSlot = empty >= 0 ? empty : prevSlot;
            Inv.swapToHotbar(slot, handSlot);
            // kein leerer Platz: das Item aus der Hand liegt jetzt dort, wo der Shulker war
            if (empty < 0) swappedFrom = slot;
        }
        Inv.select(handSlot);
        ((MultiPlayerGameModeAccessor) mc.gameMode).teizen$sendCarriedItem();
        return Loot.isShulker(mc.player.getInventory().getItem(handSlot));
    }

    /** Altes Item zurück in die Hand und den alten Hotbar-Platz wählen. */
    private static void restoreHand() {
        if (swappedFrom >= 0 && handSlot >= 0) Inv.swapToHotbar(swappedFrom, handSlot);
        swappedFrom = -1;
        if (prevSlot >= 0) Inv.select(prevSlot);
        prevSlot = -1;
        handSlot = -1;
    }

    /** Rechtsklick oben auf den Shulker (öffnet ihn). */
    private static void openClick() {
        if (mc.player.isShiftKeyDown()) return; // schleichend würde er nicht aufgehen
        Vec3 hit = new Vec3(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
        float[] r = Rot.to(hit);
        Rot.sendSilent(r[0], r[1]);
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(hit, Direction.UP, pos, false));
        mc.player.swing(InteractionHand.MAIN_HAND);
    }

    /** Freier Platz direkt neben dir (dort fällt der Shulker beim Abbauen wieder zu dir). */
    static BlockPos findSpot() {
        BlockPos feet = mc.player.blockPosition();
        int[][] around = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
        for (int dy : new int[]{0, 1}) {
            for (int[] o : around) {
                BlockPos p = feet.offset(o[0], dy, o[1]);
                if (goodSpot(p)) return p;
            }
        }
        return null;
    }

    private static boolean goodSpot(BlockPos p) {
        BlockState here = mc.level.getBlockState(p), above = mc.level.getBlockState(p.above()), below = mc.level.getBlockState(p.below());
        if (!here.isAir() || !above.isAir()) return false;            // darüber muss frei sein, sonst geht der Deckel nicht auf
        if (below.isAir() || below.canBeReplaced()) return false;
        if (!below.isCollisionShapeFullBlock(mc.level, p.below())) return false;
        if (mc.level.getBlockEntity(p.below()) != null) return false;  // Kiste, Ofen … würden aufgehen statt den Shulker hinzustellen
        if (INTERACTIVE.contains(Ids.path(below.getBlock()))) return false;
        if (BlockSwap.blockedByEntity(p) || BlockSwap.blockedByEntity(p.above())) return false;
        return Breaker.inReach(p);
    }
}
