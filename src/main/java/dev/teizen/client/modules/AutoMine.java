package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.util.BlockScanner;
import dev.teizen.client.util.Breaker;
import dev.teizen.client.util.Chat;
import dev.teizen.client.util.Draw;
import dev.teizen.client.util.Ids;
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
    private final Setting human = add(Setting.bool("menschlich", "Weich drehen wie ein echter Spieler (nicht ruckartig)", true));
    private final Setting turnSpeed = add(Setting.number("drehtempo", "Wie schnell gedreht wird (Grad pro Sekunde)", 260, 60, 1500));

    private Set<Block> blocks = new HashSet<>();
    private String lastOres = "";
    private final BlockScanner scanner = new BlockScanner(s -> blocks.contains(s.getBlock()), 3, 32);

    private BlockPos target;
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
        failed.clear();
        if (blocks.isEmpty()) Chat.error("Keine gültigen Erze in: " + ores.str());
        else Chat.info("AutoMine sucht: §f" + ores.str());
    }

    @Override
    protected void onDisable() {
        releaseKeys();
        Breaker.stop();
        reset();
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

        // Ziel prüfen / neues Ziel wählen
        if (target != null && !blocks.contains(mc.level.getBlockState(target).getBlock())) {
            Breaker.stop();
            reset();
        }
        // erst die Erze einsammeln, die herumliegen
        if (target == null || pickup != null) {
            if (collectTick()) return;
        }
        if (target == null) {
            if (!pickTarget()) {
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

        if (path == null || index >= path.size()) {
            path = MinePath.find(mc.player.blockPosition(), target, mc.player.blockInteractionRange(), nodes.integer());
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
        list.sort(Comparator.comparingDouble(p -> p.distSqr(me) + Math.abs(p.getY() - me.getY()) * 6));
        if (list.isEmpty()) return false;
        target = list.get(0);
        path = null;
        status = "läuft zu " + Ids.path(mc.level.getBlockState(target).getBlock());
        return true;
    }

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
        // 2) Hinlaufen
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
    private final java.util.Set<Integer> ignoredItems = new java.util.HashSet<>();
    public int collected;

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

    /** Liegt ein Erz-Drop in der Nähe? Dann hinlaufen. @return true wenn gerade eingesammelt wird */
    private boolean collectTick() {
        if (!collect.on()) return false;
        if (pickup != null && (!pickup.isAlive() || ++pickupTicks > 220)) {
            if (!pickup.isAlive()) collected++;
            else ignoredItems.add(pickup.getId()); // kommt nicht ran -> weiter abbauen
            pickup = null;
            path = null;
            releaseKeys();
        }
        if (pickup == null) {
            double best = 64;
            for (var e : mc.level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, mc.player.getBoundingBox().inflate(7, 3, 7))) {
                if (!isOreDrop(e.getItem()) || ignoredItems.contains(e.getId())) continue;
                double d = e.distanceToSqr(mc.player);
                if (d < best) { best = d; pickup = e; }
            }
            if (pickup == null) return false;
            pickupTicks = 0;
            path = null;
        }
        Vec3 me = mc.player.position();
        Vec3 it = pickup.position();
        double dx = it.x - me.x, dz = it.z - me.z;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        status = "sammelt " + pickup.getItem().getHoverName().getString();
        if (horiz < 1.6 && Math.abs(it.y - me.y) < 2.2) {
            // Liegt der Drop in einem niedrigen Loch (wo das Erz war)? Dann den Block darüber abbauen, damit man hineinkommt
            BlockPos ib = pickup.blockPosition();
            BlockPos above = ib.above();
            if (it.y < me.y - 0.4 && !MinePath.passable(mc.level, above) && Breaker.canBreak(above) && safeToDig(above)) {
                releaseKeys();
                lookAt(Vec3.atCenterOf(above));
                if (aimed()) Breaker.tick(above, false, true);
                return true;
            }
            // direkt hinlaufen
            float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
            aim(yaw, 30f);
            mc.options.keyUp.setDown(horiz > 0.25);
            mc.options.keyJump.setDown(false);
            return true;
        }
        if (path == null || index >= path.size()) {
            path = MinePath.find(mc.player.blockPosition(), pickup.blockPosition().above(), 2.2, Math.min(3000, nodes.integer()));
            index = 0;
            if (path == null || path.isEmpty()) {
                // nicht erreichbar -> vergessen
                pickup = null;
                path = null;
                return false;
            }
        }
        follow();
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
