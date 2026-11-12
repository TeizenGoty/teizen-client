package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.util.BlockScanner;
import dev.teizen.client.util.Chat;
import dev.teizen.client.util.Draw;
import dev.teizen.client.util.Inv;
import dev.teizen.client.util.MinePath;
import dev.teizen.client.util.Rot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Auto-Brush: sucht wie der AutoMiner von selbst jeden Suspicious Sand / Gravel in der Nähe, läuft hin und
 * pinselt ihn mit einem Pinsel frei. Zerstört dabei nie einen Block – es wird nur gepinselt, nie gegraben.
 * Was herauskommt, würfelt der Server; das kann der Client nicht bestimmen.
 */
public final class AutoBrush extends Module {
    private final Setting what = add(Setting.multi("was", "Welche Blöcke freipinseln", "sand,gravel", "sand", "gravel"));
    private final Setting radius = add(Setting.number("radius", "Suchradius in Chunks", 3, 1, 8));
    private final Setting nodes = add(Setting.number("suchtiefe", "Wie weit der Weg gesucht wird", 4000, 500, 20000));
    private final Setting walk = add(Setting.bool("hinlaufen", "Von selbst zum nächsten Block laufen (aus = nur pinseln, was in Reichweite ist)", true));
    private final Setting show = add(Setting.bool("anzeigen", "Gefundene Blöcke mit Rahmen anzeigen", true));
    private final Setting human = add(Setting.bool("menschlich", "Weich drehen wie ein echter Spieler", true));
    private final Setting turnSpeed = add(Setting.number("drehtempo", "Wie schnell gedreht wird (Grad pro Sekunde)", 300, 60, 1500));

    private final BlockScanner scanner = new BlockScanner(this::wanted, 3, 48).maxResults(4000);
    private BlockPos target;
    private List<MinePath.Step> path;
    private int index, stuck;
    private BlockPos lastFeet;
    /** Blöcke, die wir nicht erreichen (ohne zu graben) – kurze Zeit überspringen. */
    private final Map<BlockPos, Integer> skip = new HashMap<>();
    private int noBrushMsg;
    private String status = "sucht";
    /** Für den Selbsttest. */
    public int brushed;

    public AutoBrush() {
        super("AutoBrush", Category.SONSTIGES, "Sucht Suspicious Sand/Gravel, läuft hin und pinselt sie von selbst frei (zerstört nichts).");
    }

    private boolean wanted(BlockState s) {
        if (s.is(Blocks.SUSPICIOUS_SAND)) return what.isSelected("sand");
        if (s.is(Blocks.SUSPICIOUS_GRAVEL)) return what.isSelected("gravel");
        return false;
    }

    private boolean stillThere(BlockPos p) {
        return p != null && wanted(mc.level.getBlockState(p));
    }

    @Override
    protected void onEnable() {
        scanner.clear();
        scanner.restart();
        target = null;
        path = null;
        skip.clear();
        if (inGame() && Inv.findHotbar(s -> s.is(Items.BRUSH)) < 0 && Inv.findAny(s -> s.is(Items.BRUSH)) < 0)
            Chat.error("AutoBrush: kein Pinsel im Inventar. Nimm einen Pinsel (Brush) mit.");
    }

    @Override
    protected void onDisable() {
        releaseKeys();
        scanner.clear();
        target = null;
        path = null;
    }

    private void releaseKeys() {
        mc.options.keyUp.setDown(false);
        mc.options.keyJump.setDown(false);
        mc.options.keyUse.setDown(false);
    }

    @Override
    public void onTick() {
        if (!inGame()) return;
        scanner.setRadius(radius.integer());
        scanner.tick();
        if (mc.player.tickCount % 10 == 0) scanner.validate();
        skip.entrySet().removeIf(e -> mc.player.tickCount - e.getValue() > 200);

        // Ziel prüfen / neu wählen
        if (!stillThere(target)) {
            if (target != null) brushed++; // war suspicious, ist jetzt frei -> fertig
            target = nearest();
            path = null;
        }
        if (target == null) {
            releaseKeys();
            status = scanner.finishedOnce() ? "nichts in der Nähe" : "sucht";
            return;
        }

        Vec3 center = Vec3.atCenterOf(target);
        double dist = mc.player.getEyePosition().distanceTo(center);
        double reach = mc.player.blockInteractionRange();
        if (dist <= reach + 0.2) {
            releaseKeys();
            brush(center);
        } else if (walk.on()) {
            mc.options.keyUse.setDown(false);
            goTo();
        } else {
            releaseKeys();
            status = "außer Reichweite";
            skip.put(target, mc.player.tickCount);
            target = null;
        }
    }

    /** Nächsten passenden Block wählen, den wir nicht gerade übersprungen haben. */
    private BlockPos nearest() {
        Vec3 eye = mc.player.getEyePosition();
        BlockPos best = null;
        double bd = Double.MAX_VALUE;
        for (BlockPos p : scanner.results()) {
            if (skip.containsKey(p) || !stillThere(p)) continue;
            double d = Vec3.atCenterOf(p).distanceToSqr(eye);
            if (d < bd) { bd = d; best = p; }
        }
        return best;
    }

    // ------------------------------------------------------------ Pinseln

    private void brush(Vec3 center) {
        // Pinsel in die Hand
        if (!Inv.ensureInHand(s -> s.is(Items.BRUSH))) {
            if (mc.player.tickCount - noBrushMsg > 100) { noBrushMsg = mc.player.tickCount; Chat.error("AutoBrush: kein Pinsel im Inventar."); }
            status = "kein Pinsel";
            return;
        }
        status = "pinselt";
        float[] r = Rot.to(center);
        aim(r[0], r[1]);
        // erst pinseln, wenn das Fadenkreuz wirklich den Ziel-Block trifft (sonst nie einen anderen Block anpinseln)
        boolean onTarget = mc.hitResult instanceof BlockHitResult bhr && bhr.getBlockPos().equals(target);
        mc.options.keyUse.setDown(onTarget);
    }

    // ------------------------------------------------------------ Hinlaufen (ohne zu graben)

    private void goTo() {
        status = "läuft hin";
        BlockPos feet = mc.player.blockPosition();
        if (path == null || index >= path.size()) {
            path = MinePath.find(feet, target.above(), mc.player.blockInteractionRange(), nodes.integer(), false);
            index = 0;
            if (path == null || path.isEmpty()) {
                // nicht ohne Graben erreichbar -> überspringen, nichts zerstören
                skip.put(target, mc.player.tickCount);
                target = null;
                releaseKeys();
                return;
            }
        }
        follow();
    }

    private void follow() {
        MinePath.Step step = path.get(index);
        // Schritt bräuchte Graben oder Setzen -> nicht machen (nichts zerstören), Ziel überspringen
        boolean needsDig = false;
        for (BlockPos c : step.clear()) if (!MinePath.passable(mc.level, c)) { needsDig = true; break; }
        if (needsDig || (step.place() != null && MinePath.passable(mc.level, step.place()))) {
            skip.put(target, mc.player.tickCount);
            target = null;
            path = null;
            releaseKeys();
            return;
        }
        BlockPos dest = step.pos();
        Vec3 c = Vec3.atBottomCenterOf(dest);
        Vec3 me = mc.player.position();
        double dx = c.x - me.x, dz = c.z - me.z;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        BlockPos feet = mc.player.blockPosition();
        if (feet.equals(dest) && horiz < 0.45) {
            index++;
            stuck = 0;
            mc.options.keyUp.setDown(false);
            return;
        }
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        aim(yaw, 0f);
        boolean move = horiz > 0.15 && (!human.on() || Math.abs(Mth.wrapDegrees(yaw - mc.player.getYRot())) < 55);
        mc.options.keyUp.setDown(move);
        mc.options.keyJump.setDown(step.kind() == MinePath.Kind.UP && mc.player.onGround() && horiz < 1.3);
        mc.player.setSprinting(false);

        if (lastFeet != null && lastFeet.equals(feet) && mc.player.getDeltaMovement().horizontalDistanceSqr() < 1e-4) {
            if (++stuck > 50) { path = null; stuck = 0; skip.put(target, mc.player.tickCount); target = null; }
        } else stuck = 0;
        lastFeet = feet;
    }

    // ------------------------------------------------------------ weich drehen

    private float wantYaw, wantPitch;
    private boolean hasWant;

    private void aim(float yaw, float pitch) {
        if (!human.on()) { Rot.look(yaw, pitch); return; }
        wantYaw = yaw;
        wantPitch = pitch;
        hasWant = true;
        turnStep();
    }

    private void turnStep() {
        if (!hasWant) return;
        float max = (float) (turnSpeed.num() / 20.0);
        float dy = Mth.wrapDegrees(wantYaw - mc.player.getYRot());
        float dp = wantPitch - mc.player.getXRot();
        mc.player.setYRot(mc.player.getYRot() + Mth.clamp(dy, -max, max));
        mc.player.setXRot(Mth.clamp(mc.player.getXRot() + Mth.clamp(dp, -max, max), -90, 90));
    }

    // ------------------------------------------------------------ Anzeige / Info

    public List<BlockPos> results() { return scanner.results(); }

    @Override
    public void onHud(GuiGraphicsExtractor g, float partial) {
        if (!inGame() || !show.on()) return;
        List<BlockPos> list = new ArrayList<>(scanner.results());
        BlockPos me = mc.player.blockPosition();
        list.sort(Comparator.comparingDouble(p -> p.distSqr(me)));
        if (list.size() > 400) list = list.subList(0, 400);
        for (int i = list.size() - 1; i >= 0; i--) {
            BlockPos p = list.get(i);
            int col = p.equals(target) ? 0xFF55FF55 : 0xFFE0B050;
            Draw.worldBox(g, new AABB(p), col, p.equals(target));
        }
    }

    @Override
    public String info() {
        int n = scanner.results().size();
        return n == 0 ? status : status + " (" + n + ")";
    }
}
