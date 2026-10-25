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
                Breaker.tick(target, false, true);
                return;
            }
            BlockPos obs = obstruction(target);
            if (obs != null && safeToDig(obs)) {
                releaseKeys();
                status = "gräbt zum Erz";
                lookAt(Vec3.atCenterOf(obs));
                Breaker.tick(obs, false, true);
                return;
            }
            // Block davor wäre gefährlich (Lava/Wasser/tiefer Fall) -> Erz direkt durch die Wand abbauen (Server erlaubt das)
            releaseKeys();
            status = "baut ab (durch Wand)";
            lookAt(Vec3.atCenterOf(target));
            Breaker.tick(target, false, true);
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
                Breaker.tick(c, false, true);
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
        Rot.look(yaw, 25f);
        boolean move = horiz > 0.15;
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

    private void lookAt(Vec3 v) {
        float[] r = Rot.to(v);
        Rot.look(r[0], r[1]);
    }

    @Override
    public void onHud(GuiGraphicsExtractor g, float partial) {
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
