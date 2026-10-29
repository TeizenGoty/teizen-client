package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.util.BlockScanner;
import dev.teizen.client.util.Breaker;
import dev.teizen.client.util.Chat;
import dev.teizen.client.util.Draw;
import dev.teizen.client.util.Ids;
import dev.teizen.client.util.Inv;
import dev.teizen.client.util.MinePath;
import dev.teizen.client.util.Rot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Auto-Miner: sucht Erze, läuft/gräbt selbst dorthin und baut sie ab – ohne dass du etwas machen musst.
 * Befehl: ///mine diamond iron gold   oder   ///mine stop
 */
public final class AutoMine extends Module {
    public final Setting ores = add(Setting.multi("erze", "Welche Erze abgebaut werden: \"alle\" = alle an/aus, dann einzeln wählen",
            "diamond,emerald,gold,iron,redstone,lapis,ancient_debris", XRay.ALL_ORES));
    private final Setting radius = add(Setting.number("radius", "Suchradius in Chunks", 3, 1, 8));
    private final Setting minHealth = add(Setting.number("minleben", "Stoppt bei so wenig Leben", 8, 1, 19));
    private final Setting nodes = add(Setting.number("suchtiefe", "Wie weit der Weg gesucht wird", 6000, 500, 40000));
    private final Setting collect = add(Setting.bool("einsammeln", "Abgebaute Erze einsammeln (nur Erze, nichts anderes)", true));
    private final Setting collectAll = add(Setting.bool("alleerze", "Jedes Erz einsammeln, auch von anderen (nicht nur die ausgewählten)", true));
    private final Setting placeBlocks = add(Setting.bool("bloecke", "Blöcke setzen: Lücken überbrücken und hochbauen (nimmt Cobblestone, Deepslate, Erde, Netherrack …)", true));
    private final Setting tunnel = add(Setting.bool("tunnel", "Keine Erze in der Nähe -> auf dieser Höhe einen Suchtunnel graben statt stehen zu bleiben", true));
    private final Setting eat = add(Setting.bool("essen", "Bei Hunger automatisch essen (wie AutoEat, auch aus dem Inventar)", true));
    private final Setting human = add(Setting.bool("menschlich", "Weich drehen wie ein echter Spieler (nicht ruckartig)", true));
    private final Setting turnSpeed = add(Setting.number("drehtempo", "Wie schnell gedreht wird (Grad pro Sekunde)", 260, 60, 1500));

    private Set<Block> blocks = new HashSet<>();
    private String lastOres = "";
    private final BlockScanner scanner = new BlockScanner(s -> blocks.contains(s.getBlock()), 3, 32);

    private BlockPos target;
    private int dropWait;
    private List<MinePath.Step> path;
    private int index;
    private int stuck;
    private BlockPos lastFeet;
    private final Map<BlockPos, Integer> failed = new HashMap<>();
    private int idleMsg;
    private int lastFailMsg = -1000;
    private String status = "sucht";

    public AutoMine() {
        super("AutoMine", Category.ABBAU, "Läuft selbst zu Erzen und baut sie ab.");
    }

    private void rebuild() {
        Set<Block> set = new HashSet<>();
        for (String part : ores.str().split("[, ]+")) if (!part.isBlank()) set.addAll(Ids.resolve(part.trim()));
        blocks = set;
        lastOres = ores.str();
        scanner.clear();
    }

    @Override
    protected void onEnable() {
        rebuild();
        reset();
        hasWant = false;
        collected = 0;
        ignoredItems.clear();
        retryLater.clear();
        dropTries.clear();
        tunnelDir = null;
        tunnelling = false;
        failed.clear();
        if (blocks.isEmpty()) Chat.error("Keine gültigen Erze in: " + ores.str());
        else Chat.info("AutoMine sucht: §f" + ores.str());
    }

    @Override
    protected void onDisable() {
        releaseKeys();
        Breaker.stop();
        reset();
        dev.teizen.client.TeizenClient.modules().get(AutoEat.class).stopEating();
    }

    private void reset() {
        pickup = null;
        target = null;
        path = null;
        index = 0;
        stuck = 0;
        lastFeet = null;
    }

    private void releaseKeys() {
        if (mc.options == null) return;
        mc.options.keyUp.setDown(false);
        mc.options.keyJump.setDown(false);
        mc.options.keyShift.setDown(false);
    }

    @Override
    public void onTick() {
        if (!inGame()) return;
        if (!ores.str().equals(lastOres)) rebuild();
        scanner.setRadius(radius.integer());
        scanner.tick();
        if (mc.player.tickCount % 10 == 0) scanner.validate();

        if (mc.player.getHealth() <= minHealth.num()) {
            Chat.error("AutoMine gestoppt – zu wenig Leben!");
            setEnabled(false);
            return;
        }
        if (mc.gui.screen() != null) { releaseKeys(); return; }
        // Hunger? Erst essen (AutoEat eingebaut)
        if (eat.on()) {
            var ae = dev.teizen.client.TeizenClient.modules().get(AutoEat.class);
            if (ae.isEnabled() ? ae.isEating() : ae.eatIfHungry()) {
                releaseKeys();
                Breaker.stop();
                status = "isst";
                return;
            }
        }

        // Ziel prüfen / neues Ziel wählen
        if (target != null && !blocks.contains(mc.level.getBlockState(target).getBlock())) {
            Breaker.stop();
            reset();
            dropWait = 7; // kurz warten, bis der Drop da ist (sonst läuft er schon zum nächsten Erz)
        }
        // erst die Erze einsammeln, die herumliegen (auch wenn schon das nächste Erz ausgesucht ist)
        if (collectTick()) return;
        if (dropWait > 0) { dropWait--; releaseKeys(); status = "wartet auf Drop"; return; }
        if (target == null) {
            if (!pickTarget()) {
                if (tunnel.on() && scanner.finishedOnce() && tunnelTick()) return;
                releaseKeys();
                status = scanner.finishedOnce() ? "keine Erze in der Nähe" : "sucht";
                if (scanner.finishedOnce() && ++idleMsg % 200 == 1) Chat.info("AutoMine: keine passenden Erze in der Nähe – lauf ein Stück weiter.");
                return;
            }
        }

        // Erz in Reichweite? -> abbauen (oder die Blöcke davor wegmachen)
        if (Breaker.inReach(target)) {
            if (canSee(target)) {
                releaseKeys();
                status = "baut ab";
                lookAt(Vec3.atCenterOf(target));
                if (aimed()) Breaker.tick(target, false, true);
                return;
            }
            BlockPos obs = obstruction(target);
            if (obs != null && safeToDig(obs)) {
                releaseKeys();
                status = "gräbt zum Erz";
                lookAt(Vec3.atCenterOf(obs));
                if (aimed()) Breaker.tick(obs, false, true);
                return;
            }
            // Block davor wäre gefährlich (Lava/Wasser/tiefer Fall) -> Erz direkt durch die Wand abbauen (Server erlaubt das)
            releaseKeys();
            status = "baut ab (durch Wand)";
            lookAt(Vec3.atCenterOf(target));
            if (aimed()) Breaker.tick(target, false, true);
            return;
        }

        tunnelling = false;
        if (path == null || index >= path.size()) {
            path = MinePath.find(mc.player.blockPosition(), target, mc.player.blockInteractionRange(), nodes.integer(), canPlace());
            index = 0;
            if (path == null || path.isEmpty()) {
                failed.merge(target, 2, Integer::sum);
                if (mc.player.tickCount - lastFailMsg > 100) {
                    lastFailMsg = mc.player.tickCount;
                    Chat.info("AutoMine: kein sicherer Weg zu " + target.toShortString() + " – nächstes Erz.");
                }
                reset();
                return;
            }
        }
        follow();
    }

    /** Erster Block, der zwischen Augen und Erz im Weg ist (oder null). */
    private BlockPos obstruction(BlockPos p) {
        var hit = mc.level.clip(new net.minecraft.world.level.ClipContext(mc.player.getEyePosition(), Vec3.atCenterOf(p),
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, mc.player));
        if (hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) return null;
        BlockPos b = hit.getBlockPos();
        return b.equals(p) ? null : b;
    }

    /** Sicher abbaubar? Keine Flüssigkeit daneben, und wenn es der Block unter den Füßen ist: kein tiefer Fall. */
    private boolean safeToDig(BlockPos b) {
        if (!Breaker.canBreak(b) || !MinePath.canClear(mc.level, b)) return false;
        if (b.equals(mc.player.blockPosition().below())) {
            // höchstens 3 Blöcke tief fallen, und unten keine Lava/kein Wasser
            for (int d = 1; d <= 4; d++) {
                BlockPos q = b.below(d);
                if (!mc.level.getFluidState(q).isEmpty()) return false;
                if (MinePath.solidFloor(mc.level, q)) return true;
            }
            return false;
        }
        return true;
    }

    /** Ist ein Block erreichbar ohne dass etwas dazwischen steht? */
    private boolean canSee(BlockPos p) {
        var from = mc.player.getEyePosition();
        var to = Vec3.atCenterOf(p);
        var hit = mc.level.clip(new net.minecraft.world.level.ClipContext(from, to,
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, mc.player));
        return hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS || hit.getBlockPos().equals(p);
    }

    private boolean pickTarget() {
        List<BlockPos> list = new ArrayList<>(scanner.results());
        if (list.isEmpty()) return false;
        BlockPos me = mc.player.blockPosition();
        list.removeIf(p -> failed.getOrDefault(p, 0) >= 2);
        if (list.isEmpty()) return false;
        // große Adern zuerst: Weg geteilt durch Adergröße (4er-Ader in 10 m schlägt Einzelblock in 5 m)
        Map<BlockPos, Integer> vein = veinSizes(list);
        list.sort(Comparator.comparingDouble(p -> Math.sqrt(p.distSqr(me) + Math.abs(p.getY() - me.getY()) * 6)
                / Math.min(vein.getOrDefault(p, 1), 8)));
        target = list.get(0);
        path = null;
        status = "läuft zu " + Ids.path(mc.level.getBlockState(target).getBlock());
        return true;
    }

    /** Erz-Art ohne Deepslate (deepslate_diamond_ore und diamond_ore = gleiche Ader). */
    private String family(BlockPos p) {
        return Ids.path(mc.level.getBlockState(p).getBlock()).replace("deepslate_", "");
    }

    /** Für jeden Erzblock: wie groß seine Ader ist (zusammenhängende gleiche Erze, auch diagonal). */
    private Map<BlockPos, Integer> veinSizes(List<BlockPos> ores) {
        Set<BlockPos> all = new HashSet<>(ores);
        Map<BlockPos, Integer> out = new HashMap<>();
        for (BlockPos start : ores) {
            if (out.containsKey(start)) continue;
            String fam = family(start);
            List<BlockPos> group = new ArrayList<>();
            java.util.ArrayDeque<BlockPos> todo = new java.util.ArrayDeque<>();
            todo.add(start);
            Set<BlockPos> seen = new HashSet<>();
            seen.add(start);
            while (!todo.isEmpty() && group.size() < 64) {
                BlockPos c = todo.poll();
                group.add(c);
                for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                    BlockPos n = c.offset(dx, dy, dz);
                    if (all.contains(n) && seen.add(n) && family(n).equals(fam)) todo.add(n);
                }
            }
            for (BlockPos g : group) out.put(g, group.size());
        }
        return out;
    }

    // ------------------------------------------------------------ Blöcke setzen
    private static final Set<String> THROWAWAY = Set.of("cobblestone", "cobbled_deepslate", "dirt", "coarse_dirt", "netherrack",
            "stone", "deepslate", "andesite", "diorite", "granite", "tuff", "blackstone", "basalt", "end_stone", "calcite");
    public int placed;

    private static boolean throwaway(net.minecraft.world.item.ItemStack st) {
        return st.getItem() instanceof net.minecraft.world.item.BlockItem
                && THROWAWAY.contains(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem()).getPath());
    }

    private boolean canPlace() { return placeBlocks.on() && Inv.findAny(AutoMine::throwaway) >= 0; }

    /** Einen Block für den Weg setzen. @return false wenn es nicht geht (dann neuen Weg suchen) */
    private MinePath.Step placeStep;
    private int placeTicks;
    private double pillarLastY = -1e9;

    private boolean placeTick(MinePath.Step step) {
        BlockPos at = step.place();
        if (step != placeStep) { placeStep = step; placeTicks = 0; }
        if (++placeTicks > 80) { placeStep = null; mc.options.keyShift.setDown(false); mc.options.keyJump.setDown(false); return false; } // hängt -> neuen Weg
        if (dev.teizen.client.SelfTest.ACTIVE && placeTicks % 10 == 1)
            dev.teizen.client.TeizenClient.LOG.info("AutoMine setzt " + step.kind() + " bei " + at.toShortString() + ": y=" + String.format(java.util.Locale.ROOT, "%.2f", mc.player.getY())
                    + " amBoden=" + mc.player.onGround() + " gezielt=" + aimed() + " Hand=" + mc.player.getMainHandItem().getItem() + " Schritt " + index + "/" + (path == null ? -1 : path.size()));
        if (!Inv.ensureInHand(AutoMine::throwaway)) return false;
        mc.options.keyUp.setDown(false);
        mc.options.keyShift.setDown(true); // schleichen: nicht von der Kante fallen, keine Kisten öffnen
        status = "setzt Block";
        if (step.kind() == MinePath.Kind.PILLAR) {
            // hochbauen: nach unten schauen, springen, oben angekommen unter sich setzen
            Rot.look(mc.player.getYRot(), 90f); // sofort nach unten (weiches Drehen kam hier nie an)
            hasWant = false;
            if (mc.player.onGround() && mc.player.getY() < at.getY() + 0.5) { mc.options.keyJump.setDown(true); return true; }
            mc.options.keyJump.setDown(false);
            // erst setzen, wenn man JETZT und schon im letzten Tick (= was der Server kennt) über dem Platz ist,
            // sonst steht man beim Server noch drin und er lehnt den Block ab
            double y = mc.player.getY(), last = pillarLastY;
            pillarLastY = y;
            if (y < at.getY() + 1.0 || last < at.getY() + 1.0) return true;
            return clickFace(at.below(), net.minecraft.core.Direction.UP, at);
        }
        // Brücke: an die Seite eines festen Nachbarn klicken (meist der Block, auf dem man steht)
        for (net.minecraft.core.Direction d : new net.minecraft.core.Direction[]{net.minecraft.core.Direction.DOWN,
                net.minecraft.core.Direction.NORTH, net.minecraft.core.Direction.SOUTH, net.minecraft.core.Direction.EAST, net.minecraft.core.Direction.WEST}) {
            BlockPos nb = at.relative(d);
            if (!MinePath.solidFloor(mc.level, nb)) continue;
            Vec3 hit = Vec3.atCenterOf(nb).add(-d.getStepX() * 0.5, -d.getStepY() * 0.5, -d.getStepZ() * 0.5);
            float[] r = Rot.to(hit);
            Rot.look(r[0], r[1]); // sofort hinschauen
            hasWant = false;
            return clickFace(nb, d.getOpposite(), at);
        }
        return false;
    }

    private boolean clickFace(BlockPos on, net.minecraft.core.Direction face, BlockPos expect) {
        Vec3 hit = Vec3.atCenterOf(on).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
        var res = mc.gameMode.useItemOn(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.phys.BlockHitResult(hit, face, on, false));
        if (res.consumesAction()) {
            dev.teizen.client.compat.V.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            placed++;
        }
        mc.options.keyShift.setDown(false);
        return true;
    }

    // ------------------------------------------------------------ Suchtunnel
    private boolean tunnelling;
    private net.minecraft.core.Direction tunnelDir;
    private int tunnelFails;
    public int tunnelSteps;

    /** Keine Erze in Reichweite: auf dieser Höhe geradeaus einen 1x2-Tunnel graben (Scanner findet unterwegs neue Erze). */
    private boolean tunnelTick() {
        if (!tunnelling || path == null || index >= path.size()) {
            if (tunnelDir == null) tunnelDir = mc.player.getDirection();
            BlockPos feet = mc.player.blockPosition();
            BlockPos goal = feet.relative(tunnelDir, 12);
            path = MinePath.find(feet, goal, 1.5, Math.min(4000, nodes.integer()), canPlace());
            index = 0;
            if (path == null || path.isEmpty()) {
                // da geht's nicht weiter (Lava, Wasser, Grundgestein) -> andere Richtung
                tunnelDir = tunnelDir.getClockWise();
                path = null;
                if (++tunnelFails > 4) { tunnelFails = 0; return false; }
                return true;
            }
            tunnelFails = 0;
            if (!tunnelling) Chat.info("AutoMine: keine Erze in der Nähe – grabe einen Suchtunnel nach §f" + tunnelDir.getName() + "§7.");
            tunnelling = true;
        }
        int before = index;
        follow();
        if (index > before) tunnelSteps++;
        if (status.equals("gräbt")) status = "gräbt Suchtunnel";
        return true;
    }

    /** Für den Selbsttest: aktuelles Ziel. */
    public BlockPos currentTarget() { return target; }

    private void follow() {
        MinePath.Step step = path.get(index);
        // 1) Blöcke im Weg abbauen
        for (BlockPos c : step.clear()) {
            if (!MinePath.passable(mc.level, c)) {
                BlockState s = mc.level.getBlockState(c);
                if (!s.getFluidState().isEmpty()) { path = null; return; }
                releaseKeys();
                status = "gräbt";
                lookAt(Vec3.atCenterOf(c));
                if (aimed()) Breaker.tick(c, false, true);
                stuck = 0;
                return;
            }
        }
        // 2) Block setzen (Brücke / beim Hochbauen unter sich)
        if (step.place() != null && MinePath.passable(mc.level, step.place())) {
            if (!placeTick(step)) { path = null; }
            return;
        }
        // 3) Hinlaufen
        BlockPos dest = step.pos();
        Vec3 center = Vec3.atBottomCenterOf(dest);
        Vec3 me = mc.player.position();
        double dx = center.x - me.x, dz = center.z - me.z;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        BlockPos feet = mc.player.blockPosition();

        if (feet.equals(dest) && horiz < 0.3) {
            index++;
            stuck = 0;
            releaseKeys();
            return;
        }
        if (step.kind() == MinePath.Kind.DIG_DOWN) {
            // Block unter den Füßen ist schon weg -> fallen lassen und zentrieren
            mc.options.keyJump.setDown(false);
        }
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        aim(yaw, 25f);
        boolean move = horiz > 0.15 && (!human.on() || Math.abs(net.minecraft.util.Mth.wrapDegrees(yaw - mc.player.getYRot())) < 50);
        mc.options.keyUp.setDown(move);
        mc.options.keyJump.setDown(step.kind() == MinePath.Kind.UP && mc.player.onGround() && horiz < 1.3);
        mc.player.setSprinting(false);

        if (lastFeet != null && lastFeet.equals(feet) && mc.player.getDeltaMovement().horizontalDistanceSqr() < 1e-4) {
            if (++stuck > 60) {
                Chat.info("AutoMine: festgesteckt – suche neuen Weg.");
                path = null;
                stuck = 0;
                failed.merge(target, 1, Integer::sum);
            }
        } else stuck = 0;
        lastFeet = feet;
    }

    // ------------------------------------------------------------ weich drehen
    private float wantYaw, wantPitch;
    private boolean hasWant;
    private long lastFrame;
    private int lastFrameTick = -100;
    private final java.util.Random rnd = new java.util.Random();
    private float jitterYaw, jitterPitch;

    private void lookAt(Vec3 v) {
        float[] r = Rot.to(v);
        aim(r[0], r[1]);
    }

    private void aim(float yaw, float pitch) {
        if (!human.on()) { Rot.look(yaw, pitch); hasWant = false; return; }
        if (!hasWant || Math.abs(net.minecraft.util.Mth.wrapDegrees(yaw - wantYaw)) > 4 || Math.abs(pitch - wantPitch) > 4) {
            // kleine Ungenauigkeit wie bei einem Menschen
            jitterYaw = (rnd.nextFloat() - 0.5f) * 1.6f;
            jitterPitch = (rnd.nextFloat() - 0.5f) * 1.2f;
        }
        wantYaw = yaw;
        wantPitch = pitch;
        hasWant = true;
        // HUD aus (F1)? Dann im Tick drehen
        if (mc.player.tickCount - lastFrameTick > 1) turnStep(0.05);
    }

    /** Wie weit (Grad) das Fadenkreuz noch vom Ziel weg ist. */
    private double aimError() {
        if (!hasWant || !human.on()) return 0;
        double dy = net.minecraft.util.Mth.wrapDegrees(wantYaw - mc.player.getYRot());
        double dp = wantPitch - mc.player.getXRot();
        return Math.sqrt(dy * dy + dp * dp);
    }

    /** Ein Stück zur Wunsch-Richtung drehen: erst schneller, kurz vor dem Ziel langsamer (wie mit der Maus). */
    private void turnStep(double dt) {
        float yaw = mc.player.getYRot(), pitch = mc.player.getXRot();
        double ty = wantYaw + jitterYaw, tp = wantPitch + jitterPitch;
        double dy = net.minecraft.util.Mth.wrapDegrees(ty - yaw), dp = tp - pitch;
        double dist = Math.sqrt(dy * dy + dp * dp);
        if (dist < 0.05) return;
        double ease = 1.0 - Math.exp(-9.0 * dt);
        double step = Math.min(dist * ease, turnSpeed.num() * dt);
        step = Math.max(step, Math.min(dist, 12 * dt));
        float ny = (float) (yaw + dy / dist * step);
        float np = (float) net.minecraft.util.Mth.clamp(pitch + dp / dist * step, -90, 90);
        mc.player.setYRot(ny);
        mc.player.setXRot(np);
        mc.player.yRotO += ny - yaw;
        mc.player.xRotO += np - pitch;
        mc.player.setYHeadRot(ny);
    }

    /** Darf schon abgebaut werden? (Menschlich: erst wenn man ungefähr hinschaut.) */
    private boolean aimed() { return !human.on() || aimError() < 14; }

    // ------------------------------------------------------------ Erze einsammeln
    private net.minecraft.world.entity.item.ItemEntity pickup;
    private int pickupTicks;
    /** Fortschritts-Wächter: wird der Drop nicht näher, nach kurzer Zeit aufgeben (sonst bleibt der Miner stehen). */
    private double lastDropDist;
    private int noProgress;
    private final java.util.Set<Integer> ignoredItems = new java.util.HashSet<>();
    public int collected;

    private static final java.util.Set<String> ANY_ORE_DROPS = java.util.Set.of(
            "diamond", "emerald", "raw_iron", "raw_gold", "raw_copper", "gold_nugget", "redstone", "lapis_lazuli",
            "coal", "quartz", "ancient_debris", "netherite_scrap", "amethyst_shard", "glowstone_dust");

    /** Jeder Erz-Drop (jedes *_ore und die typischen Erz-Beutestücke), egal welches Erz gerade gesucht wird. */
    public boolean isAnyOreDrop(net.minecraft.world.item.ItemStack st) {
        String p = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem()).getPath();
        return p.endsWith("_ore") || ANY_ORE_DROPS.contains(p);
    }

    /** Erz-Drop, das gerade eingesammelt werden soll (je nach Einstellung alle oder nur die gewählten). */
    private boolean wantDrop(net.minecraft.world.item.ItemStack st) {
        return collectAll.on() ? isAnyOreDrop(st) : isOreDrop(st);
    }

    /** Ist das ein Erz-Drop (Diamant, Rohes Eisen, Kohle, ...) von den gewählten Erzen? */
    public boolean isOreDrop(net.minecraft.world.item.ItemStack st) {
        String p = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem()).getPath();
        for (String g : ores.selected()) {
            if (p.endsWith("_ore") && p.contains(g)) return true;
            switch (g) {
                case "diamond" -> { if (p.equals("diamond")) return true; }
                case "emerald" -> { if (p.equals("emerald")) return true; }
                case "gold" -> { if (p.equals("raw_gold") || p.equals("gold_nugget")) return true; }
                case "iron" -> { if (p.equals("raw_iron")) return true; }
                case "copper" -> { if (p.equals("raw_copper")) return true; }
                case "redstone" -> { if (p.equals("redstone")) return true; }
                case "lapis" -> { if (p.equals("lapis_lazuli")) return true; }
                case "coal" -> { if (p.equals("coal")) return true; }
                case "quartz" -> { if (p.equals("quartz")) return true; }
                case "ancient_debris" -> { if (p.equals("ancient_debris")) return true; }
                default -> { }
            }
        }
        return false;
    }

    /** Drops, zu denen gerade kein Weg gefunden wurde: Entity-ID -> ab welchem Tick wieder versuchen. */
    private final java.util.Map<Integer, Integer> retryLater = new java.util.HashMap<>();
    /** Läuft gerade ohne Weg direkt auf den Drop zu (dann kürzer probieren). */
    private boolean pickupDirect;

    /** Liegt ein Erz-Drop in der Nähe? Dann hinlaufen. @return true wenn gerade eingesammelt wird */
    private boolean collectTick() {
        if (!collect.on()) return false;
        int now = mc.player.tickCount;
        if (pickup != null && (!pickup.isAlive() || ++pickupTicks > (pickupDirect ? 120 : 300) || noProgress > 60)) {
            if (!pickup.isAlive()) collected++;
            else {
                // kommt gerade nicht ran: später nochmal; erst nach 4 Versuchen liegen lassen
                int tries = dropTries.merge(pickup.getId(), 1, Integer::sum);
                if (tries >= 4) ignoredItems.add(pickup.getId());
                else retryLater.put(pickup.getId(), now + 40);
            }
            pickup = null;
            path = null;
            noProgress = 0;
            releaseKeys();
        }
        if (pickup == null) {
            retryLater.values().removeIf(t -> t <= now);
            double best = 12 * 12;
            for (var e : mc.level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, mc.player.getBoundingBox().inflate(10, 6, 10))) {
                if (!wantDrop(e.getItem()) || ignoredItems.contains(e.getId()) || retryLater.containsKey(e.getId())) continue;
                double d = e.distanceToSqr(mc.player);
                if (d < best) { best = d; pickup = e; }
            }
            if (pickup == null) return false;
            pickupTicks = 0;
            pickupDirect = false;
            path = null;
            lastDropDist = Double.MAX_VALUE;
            noProgress = 0;
            if (target != null) Breaker.stop();
        }
        Vec3 me = mc.player.position();
        Vec3 it = pickup.position();
        double dx = it.x - me.x, dz = it.z - me.z;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        double dist = horiz + Math.abs(it.y - me.y);
        if (dist < lastDropDist - 0.05) { lastDropDist = dist; noProgress = 0; } else noProgress++;
        status = "sammelt " + pickup.getItem().getHoverName().getString();
        if (horiz < 1.6 && Math.abs(it.y - me.y) < 2.2) return digIsProgress(walkToDrop(it, me, dx, dz, horiz));
        if (path == null || index >= path.size()) {
            path = MinePath.find(mc.player.blockPosition(), pickup.blockPosition().above(), 2.2, Math.min(3000, nodes.integer()), canPlace());
            index = 0;
            if (path == null || path.isEmpty()) {
                boolean alreadyThere = path != null;
                path = null;
                // schon ganz nah (Weg leer) oder kein Weg, aber nah dran -> direkt hinlaufen
                if ((alreadyThere || horiz < 3.5) && Math.abs(it.y - me.y) < 3) {
                    pickupDirect = true;
                    return digIsProgress(walkToDrop(it, me, dx, dz, horiz));
                }
                // weiter weg und kein Weg -> erst weiter abbauen, später nochmal versuchen
                retryLater.put(pickup.getId(), now + 100);
                pickup = null;
                return false;
            }
        }
        follow();
        digIsProgress(true);
        return true;
    }

    /** Graben zum Drop ist Fortschritt (vorher: nach 2 s "kommt nicht näher" -> Diamant liegen gelassen). */
    private boolean digIsProgress(boolean r) {
        if (status.startsWith("gräbt")) { noProgress = 0; pickupTicks = Math.max(0, pickupTicks - 1); }
        return r;
    }

    /** Wie oft ein Drop schon nicht erreicht wurde. */
    private final java.util.Map<Integer, Integer> dropTries = new java.util.HashMap<>();

    /** Block direkt vor dem Spieler (in Blickrichtung zum Drop) auf Kopf- bzw. Fußhöhe. */
    private BlockPos frontBlock(double dx, double dz) {
        net.minecraft.core.Direction d = net.minecraft.core.Direction.getApproximateNearest(dx, 0, dz);
        BlockPos feet = mc.player.blockPosition();
        BlockPos f1 = feet.relative(d), f2 = feet.above().relative(d);
        if (!MinePath.passable(mc.level, f2)) return f2;
        if (!MinePath.passable(mc.level, f1)) return f1;
        return null;
    }

    /** Ohne Weg direkt zum Drop laufen; liegt er in einem Loch, den Block darüber wegmachen. */
    private boolean walkToDrop(Vec3 it, Vec3 me, double dx, double dz, double horiz) {
        BlockPos ib = pickup.blockPosition();
        BlockPos above = ib.above();
        if (it.y < me.y - 0.4 && !MinePath.passable(mc.level, above) && Breaker.canBreak(above) && safeToDig(above)) {
            releaseKeys();
            status = "gräbt zum Erz";
            lookAt(Vec3.atCenterOf(above));
            if (aimed()) Breaker.tick(above, false, true);
            return true;
        }
        // auf ~gleicher Höhe, aber eine Wand dazwischen -> die Wand wegbauen statt stehen zu bleiben
        if (horiz > 0.8 && Math.abs(it.y - me.y) < 1.2) {
            BlockPos wall = frontBlock(dx, dz);
            if (wall != null && !MinePath.passable(mc.level, wall) && Breaker.canBreak(wall) && safeToDig(wall)) {
                releaseKeys();
                status = "gräbt zum Erz";
                lookAt(Vec3.atCenterOf(wall));
                if (aimed()) Breaker.tick(wall, false, true);
                return true;
            }
        }
        // Drop höher als man selbst (auf einer Stufe) -> hochspringen
        boolean jump = it.y > me.y + 0.6 && mc.player.onGround();
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        aim(yaw, 30f);
        mc.options.keyUp.setDown(horiz > 0.25);
        mc.options.keyJump.setDown(jump);
        return true;
    }

    @Override
    public void onHud(GuiGraphicsExtractor g, float partial) {
        long now = System.nanoTime();
        double dt = lastFrame == 0 ? 0.016 : Math.min(0.1, (now - lastFrame) / 1e9);
        lastFrame = now;
        lastFrameTick = mc.player.tickCount;
        if (hasWant && human.on() && mc.gui.screen() == null) turnStep(dt);
        if (target != null) Draw.worldBox(g, new AABB(target), 0xFF33EBFF, true);
        if (path != null) {
            for (int i = index; i < path.size() && i < index + 40; i++) {
                BlockPos p = path.get(i).pos();
                Draw.worldBox(g, new AABB(p).deflate(0.35), 0xFFC08CFF, false);
            }
        }
    }

    @Override
    public String info() { return status; }

    /** Zustand für Fehlersuche (Selbsttest-Log). */
    public String debugState() {
        if (!inGame()) return "nicht im Spiel";
        StringBuilder sb = new StringBuilder();
        sb.append("status=").append(status).append(" pos=").append(mc.player.blockPosition().toShortString())
          .append(" ground=").append(mc.player.onGround());
        sb.append(" ziel=").append(target == null ? "-" : target.toShortString() + "/" + Ids.path(mc.level.getBlockState(target).getBlock()));
        sb.append(" scan=").append(scanner.results().size());
        if (path != null) {
            sb.append(" weg=").append(index).append('/').append(path.size());
            if (index < path.size()) {
                MinePath.Step st = path.get(index);
                sb.append(" schritt=").append(st.kind()).append('@').append(st.pos().toShortString()).append(" frei=[");
                for (BlockPos c : st.clear()) sb.append(Ids.path(mc.level.getBlockState(c).getBlock())).append(MinePath.passable(mc.level, c) ? "+" : "!").append(' ');
                sb.append(']');
            }
        }
        sb.append(" abbau=").append(mc.gameMode.isDestroying());
        try { sb.append(" fortschritt=").append(((dev.teizen.client.mixin.MultiPlayerGameModeAccessor) mc.gameMode).teizen$getDestroyProgress()); } catch (Throwable ignored) {}
        sb.append(" stuck=").append(stuck);
        return sb.toString();
    }
}
