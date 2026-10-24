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
import net.minecraft.core.Direction;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Auto-Miner: sucht Erze (wie X-Ray, auch durch Wände), läuft/gräbt selbst dorthin und baut sie ab –
 * ohne dass du etwas drücken musst.
 * Befehl: ///mine diamond iron gold   oder   ///mine stop
 *
 * Ablauf pro Tick:
 *  1. Ziel-Erz wählen (nächstes, das nicht schon mehrfach gescheitert ist)
 *  2. Ist das Erz in Reichweite? -> abbauen. Steht ein Block davor, erst den wegmachen
 *     (bei Lava/Wasser dahinter: direkt durch die Wand abbauen, das erlaubt der Server).
 *  3. Sonst: Weg suchen (A*, darf durch Stein graben) und ihm folgen.
 */
public final class AutoMine extends Module {
    public final Setting ores = add(Setting.text("erze", "Welche Erze abgebaut werden", "diamond,emerald,gold,iron,redstone,lapis,ancient_debris"));
    private final Setting radius = add(Setting.number("radius", "Suchradius in Chunks", 3, 1, 8));
    private final Setting minHealth = add(Setting.number("minleben", "Stoppt bei so wenig Leben", 8, 1, 19));
    private final Setting nodes = add(Setting.number("suchtiefe", "Wie weit der Weg gesucht wird", 6000, 500, 40000));

    /** So lange darf ein einzelner Block höchstens dauern, dann wird aufgegeben (Ticks). */
    private static final int MAX_BREAK_TICKS = 20 * 20;
    /** So lange darf man ohne Fortschritt herumstehen, dann neuer Weg (Ticks). */
    private static final int MAX_STUCK_TICKS = 60;

    private Set<Block> blocks = new HashSet<>();
    private String lastOres = "";
    private final BlockScanner scanner = new BlockScanner(s -> blocks.contains(s.getBlock()), 3, 32);

    private BlockPos target;
    private List<MinePath.Step> path;
    private int index;
    private int stuck;
    private BlockPos lastFeet;
    private final Map<BlockPos, Integer> failed = new HashMap<>();

    /** Block, der gerade abgebaut wird, und wie lange schon. */
    private BlockPos breaking;
    private int breakTicks;

    private int idleMsg;
    private int mined;
    private String status = "sucht";

    public AutoMine() {
        super("AutoMine", Category.ABBAU, "Läuft selbst zu Erzen und baut sie ab.");
    }

    // ------------------------------------------------------------------ an / aus

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
        mined = 0;
        idleMsg = 0;
        status = "sucht";
        if (blocks.isEmpty()) Chat.error("Keine gültigen Erze in: " + ores.str());
        else Chat.info("AutoMine sucht: §f" + ores.str());
    }

    @Override
    protected void onDisable() {
        releaseKeys();
        stopBreaking();
        reset();
    }

    private void reset() {
        target = null;
        path = null;
        index = 0;
        stuck = 0;
        lastFeet = null;
        breaking = null;
        breakTicks = 0;
    }

    private void releaseKeys() {
        if (mc.options == null) return;
        mc.options.keyUp.setDown(false);
        mc.options.keyJump.setDown(false);
        mc.options.keyShift.setDown(false);
    }

    private void stopBreaking() {
        if (breaking != null) Breaker.stop();
        breaking = null;
        breakTicks = 0;
    }

    /** Ziel hat nicht geklappt -> merken und neues suchen. */
    private void giveUp(String why) {
        if (target != null) {
            int n = failed.merge(target, 1, Integer::sum);
            if (n >= 2) Chat.info("AutoMine: " + why + " – überspringe " + target.toShortString());
        }
        releaseKeys();
        stopBreaking();
        reset();
    }

    // ------------------------------------------------------------------ Haupt-Tick

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

        // Ziel noch da?
        if (target != null && !blocks.contains(mc.level.getBlockState(target).getBlock())) {
            mined++;
            releaseKeys();
            stopBreaking();
            reset();
        }
        if (target == null && !pickTarget()) {
            releaseKeys();
            status = scanner.finishedOnce() ? "keine Erze in der Nähe" : "sucht";
            if (scanner.finishedOnce() && ++idleMsg % 200 == 1) Chat.info("AutoMine: keine passenden Erze in der Nähe – lauf ein Stück weiter.");
            return;
        }

        // Erz in Reichweite? -> abbauen (oder erst den Block davor)
        if (Breaker.inReach(target)) {
            releaseKeys();
            path = null;
            BlockPos next = blockInTheWay(target);
            status = next.equals(target) ? "baut ab" : "gräbt zum Erz";
            mine(next);
            return;
        }

        // Sonst hinlaufen / hingraben
        if (path == null || index >= path.size()) {
            path = MinePath.find(mc.player.blockPosition(), target, mc.player.blockInteractionRange(), nodes.integer());
            index = 0;
            if (path == null) { giveUp("kein sicherer Weg"); return; }
        }
        if (path.isEmpty()) {
            // Der Weg-Finder meint, wir stehen schon richtig – nur nicht genau genug. In die Blockmitte gehen.
            if (!walkTo(mc.player.blockPosition(), MinePath.Kind.WALK)) giveUp("komme nicht nah genug ran");
            return;
        }
        follow();
    }

    // ------------------------------------------------------------------ Abbauen

    /**
     * Welcher Block muss als nächstes weg, um an das Erz zu kommen?
     * Gibt das Erz selbst zurück, wenn freie Sicht ist (oder der Block davor gefährlich wäre).
     */
    private BlockPos blockInTheWay(BlockPos ore) {
        Vec3 from = mc.player.getEyePosition();
        Vec3 to = Vec3.atCenterOf(ore);
        BlockHitResult hit = mc.level.clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
        if (hit.getType() == HitResult.Type.MISS) return ore;
        BlockPos p = hit.getBlockPos();
        if (p.equals(ore)) return ore;
        if (blocks.contains(mc.level.getBlockState(p).getBlock())) return p; // anderes passendes Erz davor -> gleich mitnehmen
        if (!safeToBreak(p)) return ore; // Server erlaubt Abbau ohne Sicht, also direkt das Erz
        return p;
    }

    /** Darf dieser Block weg, ohne dass Lava/Wasser reinläuft oder es Grundgestein ist? */
    private boolean safeToBreak(BlockPos p) {
        if (!Breaker.canBreak(p)) return false;
        BlockState s = mc.level.getBlockState(p);
        if (s.getDestroySpeed(mc.level, p) > 30) return false;
        for (Direction d : Direction.values()) {
            if (d == Direction.DOWN) continue;
            if (!mc.level.getFluidState(p.relative(d)).isEmpty()) return false;
        }
        return true;
    }

    /** Einen Tick an einem Block abbauen; bricht ab, wenn es viel zu lange dauert. */
    private void mine(BlockPos p) {
        if (!p.equals(breaking)) {
            stopBreaking();
            breaking = p.immutable();
            breakTicks = 0;
        }
        if (++breakTicks > MAX_BREAK_TICKS) {
            giveUp("Block lässt sich nicht abbauen");
            return;
        }
        lookAt(Vec3.atCenterOf(p));
        if (Breaker.tick(p, true, true)) {
            breaking = null;
            breakTicks = 0;
        }
        stuck = 0;
    }

    // ------------------------------------------------------------------ Laufen

    private boolean pickTarget() {
        List<BlockPos> list = new ArrayList<>(scanner.results());
        if (list.isEmpty()) return false;
        BlockPos me = mc.player.blockPosition();
        list.removeIf(p -> failed.getOrDefault(p, 0) >= 2 || !blocks.contains(mc.level.getBlockState(p).getBlock()));
        if (list.isEmpty()) return false;
        list.sort(Comparator.comparingDouble(p -> p.distSqr(me) + Math.abs(p.getY() - me.getY()) * 6));
        target = list.get(0).immutable();
        path = null;
        index = 0;
        idleMsg = 0;
        status = "läuft zu " + Ids.path(mc.level.getBlockState(target).getBlock());
        return true;
    }

    private void follow() {
        MinePath.Step step = path.get(index);
        // 1) Blöcke im Weg abbauen
        for (BlockPos c : step.clear()) {
            if (!MinePath.passable(mc.level, c)) {
                if (!mc.level.getBlockState(c).getFluidState().isEmpty()) { path = null; return; }
                releaseKeys();
                status = "gräbt";
                mine(c);
                return;
            }
        }
        stopBreaking();
        // 2) Hinlaufen
        status = "läuft zu " + Ids.path(mc.level.getBlockState(target).getBlock());
        if (walkTo(step.pos(), step.kind())) index++;
    }

    /**
     * Läuft einen Tick Richtung Blockmitte.
     * @return true wenn angekommen; false solange unterwegs (oder festgesteckt -> Weg wird neu gesucht)
     */
    private boolean walkTo(BlockPos dest, MinePath.Kind kind) {
        Vec3 center = Vec3.atBottomCenterOf(dest);
        Vec3 me = mc.player.position();
        double dx = center.x - me.x, dz = center.z - me.z;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        BlockPos feet = mc.player.blockPosition();

        if (feet.equals(dest) && horiz < 0.3) {
            stuck = 0;
            releaseKeys();
            return true;
        }
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        Rot.look(yaw, 25f);
        mc.options.keyUp.setDown(horiz > 0.15);
        mc.options.keyJump.setDown(kind == MinePath.Kind.UP && mc.player.onGround() && horiz < 1.3);
        mc.player.setSprinting(false);

        boolean notMoving = lastFeet != null && lastFeet.equals(feet) && mc.player.getDeltaMovement().horizontalDistanceSqr() < 1e-4;
        if (notMoving) {
            if (++stuck > MAX_STUCK_TICKS) {
                stuck = 0;
                path = null;
                lastFeet = null;
                if (target != null && failed.merge(target, 1, Integer::sum) >= 2) giveUp("festgesteckt");
                return false;
            }
        } else stuck = 0;
        lastFeet = feet;
        return false;
    }

    private void lookAt(Vec3 v) {
        float[] r = Rot.to(v);
        Rot.look(r[0], r[1]);
    }

    // ------------------------------------------------------------------ Anzeige

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
    public String info() { return mined > 0 ? status + " (" + mined + ")" : status; }

    /** Zustand für Fehlersuche (Selbsttest-Log). */
    public String debugState() {
        if (!inGame()) return "nicht im Spiel";
        StringBuilder sb = new StringBuilder();
        sb.append("an=").append(isEnabled()).append(" status=").append(status).append(" abgebaut=").append(mined)
          .append(" pos=").append(mc.player.blockPosition().toShortString())
          .append(" ground=").append(mc.player.onGround());
        sb.append(" ziel=").append(target == null ? "-" : target.toShortString() + "/" + Ids.path(mc.level.getBlockState(target).getBlock()));
        if (target != null) sb.append(" inReach=").append(Breaker.inReach(target));
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
        sb.append(" block=").append(breaking == null ? "-" : breaking.toShortString() + "/" + Ids.path(mc.level.getBlockState(breaking).getBlock()) + " t=" + breakTicks);
        sb.append(" abbau=").append(mc.gameMode.isDestroying());
        try { sb.append(" fortschritt=").append(((dev.teizen.client.mixin.MultiPlayerGameModeAccessor) mc.gameMode).teizen$getDestroyProgress()); } catch (Throwable ignored) {}
        sb.append(" stuck=").append(stuck);
        return sb.toString();
    }
}
