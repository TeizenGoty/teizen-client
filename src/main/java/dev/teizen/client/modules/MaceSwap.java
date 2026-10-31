package dev.teizen.client.modules;

import dev.teizen.client.TeizenClient;
import dev.teizen.client.compat.V;
import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.util.Inv;
import dev.teizen.client.util.WeaponSwap;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * MaceSwap: schlägst du im Fallen zu, wird für den Schlag kurz zum Streitkolben (Mace) gewechselt – der macht beim Fallen
 * riesigen Schaden. Danach hast du sofort wieder dein Schwert in der Hand. Du brauchst einen Streitkolben in der Hotbar.
 *
 * Beim Fliegen (Fly) fällt man nicht – darum zeigt MaceSwap dem Server vor dem Schlag einen kurzen Sturz
 * (hoch, wieder runter = Fallhöhe), damit der Streitkolben trotzdem vollen Fall-Schaden macht.
 * 26.2: Pakete direkt vor dem Schlag. 26.3: nur 1 Positions-Paket pro Tick -> Schlag 2 Ticks später.
 */
public final class MaceSwap extends Module {
    private final Setting minFall = add(Setting.number("fallhoehe", "Ab wie vielen Blöcken Fallhöhe gewechselt wird", 1.5, 0.5, 20));
    private final Setting fly = add(Setting.bool("fliegen", "Auch beim Fliegen/Schweben: dem Server einen Sturz zeigen, damit der Mace vollen Schaden macht", true));
    private final Setting height = add(Setting.number("sturz", "So viele Blöcke Sturz zeigen (mehr = mehr Schaden, max 9.9 pro Paket)", 9.9, 2, 9.9));

    public int swaps;
    /** Für Selbsttest/Anzeige: wie oft ein Sturz für den Mace gezeigt wurde. */
    public int fakeFalls;
    /** Zuletzt gezeigte Sturzhöhe. */
    public double lastFall;

    public MaceSwap() {
        super("MaceSwap", Category.KAMPF, "Schlag im Fallen (oder Fliegen): kurz zum Streitkolben wechseln für riesigen Schaden.");
    }

    private static boolean forced;

    /** Lohnt sich der Streitkolben gerade (genug Fallhöhe – oder gleich gezeigter Sturz)? */
    public boolean wants() {
        return mc.player != null && (forced || mc.player.fallDistance >= minFall.num());
    }

    private static MaceSwap active() {
        if (TeizenClient.modules() == null) return null;
        MaceSwap m = TeizenClient.modules().get(MaceSwap.class);
        return m != null && m.isEnabled() && m.inGame() ? m : null;
    }

    /** Freie Höhe über einem (für den gezeigten Sturz), 0 = zu wenig Platz. */
    private double freeHeight(LocalPlayer p) {
        double h = height.num();
        while (h >= 2) {
            if (mc.level.noCollision(p, p.getBoundingBox().expandTowards(0, h, 0))) return h;
            h -= 1;
        }
        return 0;
    }

    // ---- 26.3: verzögerter Schlag (wie Criticals)
    private static Entity pending;
    private static int stage, waited;
    private static double fakeY, baseY;
    private static boolean bypass;

    private boolean flyingNow(LocalPlayer p) {
        Fly f = TeizenClient.modules().get(Fly.class);
        return p.getAbilities().flying || (f != null && f.isEnabled() && !p.onGround()) || (!p.onGround() && p.fallDistance < minFall.num());
    }

    /** Direkt vor jedem Schlag (Mixin). @return true = Schlag jetzt abbrechen (kommt gleich mit Sturz nach) */
    public static boolean beforeAttack(Entity target) {
        MaceSwap m = active();
        if (m == null || bypass || !m.fly.on()) return false;
        LocalPlayer p = mc.player;
        if (!(target instanceof LivingEntity) || p.isFallFlying() || p.isPassenger() || p.isInWater() || p.isInLava()) return false;
        if (!m.flyingNow(p) || p.fallDistance >= m.minFall.num()) return false;
        if (!WeaponSwap.isMace(p.getMainHandItem()) && Inv.findHotbar(WeaponSwap::isMace) < 0) return false;
        if (stage != 0) return true;
        double h = m.freeHeight(p);
        if (h < 2) return false;
        double x = p.getX(), y = p.getY(), z = p.getZ();
        if (V.MULTI_MOVE_PACKETS) {
            // 26.2: hoch und wieder runter, sofort
            p.connection.send(new ServerboundMovePlayerPacket.Pos(x, y + h, z, false, false));
            p.connection.send(new ServerboundMovePlayerPacket.Pos(x, y, z, false, false));
            m.fakeFalls++;
            m.lastFall = h;
            forced = true; // WeaponSwap nimmt jetzt den Mace
            return false;
        }
        pending = target;
        stage = 1;
        waited = 0;
        baseY = y;
        fakeY = y + h;
        return true;
    }

    /** Nach dem Schlag (Mixin, RETURN). */
    public static void afterAttack() { forced = false; }

    /** Aus LocalPlayer.sendPosition (Mixin). @return true = normales Paket in diesem Tick nicht schicken */
    public static boolean onSendPosition() {
        if (stage == 0 || mc.player == null) return false;
        LocalPlayer p = mc.player;
        double x = p.getX(), z = p.getZ();
        switch (stage) {
            case 1 -> { p.connection.send(new ServerboundMovePlayerPacket.Pos(x, fakeY, z, false, false)); stage = 2; }
            case 2 -> { p.connection.send(new ServerboundMovePlayerPacket.Pos(x, baseY, z, false, false)); stage = 3; }
            case 4 -> { p.connection.send(new ServerboundMovePlayerPacket.Pos(x, p.getY(), z, false, p.horizontalCollision)); stage = 0; pending = null; }
            default -> { return false; }
        }
        return true;
    }

    @Override
    public void onTick() {
        if (stage == 0) return;
        if (++waited > 10) { stage = 0; pending = null; forced = false; return; }
        if (stage != 3) return;
        LocalPlayer p = mc.player;
        if (pending != null && pending.isAlive() && p.distanceTo(pending) < 7) {
            bypass = true;
            forced = true;
            try {
                mc.gameMode.attack(p, pending);
                V.swing(InteractionHand.MAIN_HAND);
                fakeFalls++;
                lastFall = fakeY - baseY;
            } finally {
                bypass = false;
                forced = false;
            }
        }
        stage = 4;
    }

    @Override
    protected void onDisable() { stage = 0; pending = null; forced = false; }

    @Override
    public String info() { return mc.player != null && Inv.findHotbar(WeaponSwap::isMace) < 0 ? "§ckein Streitkolben" : ""; }
}
