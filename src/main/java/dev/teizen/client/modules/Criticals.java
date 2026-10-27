package dev.teizen.client.modules;

import dev.teizen.client.TeizenClient;
import dev.teizen.client.compat.V;
import dev.teizen.client.core.Module;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;

/**
 * Jeder Schlag wird ein kritischer Treffer.
 * 26.2: mini-Sprung, den nur der Server sieht (2 Pakete).
 * 26.3: der Server erlaubt nur noch 1 Positions-Paket pro Tick -> echter kleiner Sprung, wenn ein Gegner in Reichweite ist,
 * und der Schlag kommt im Fallen (dann ist er auch für Minecraft ein Krit).
 */
public final class Criticals extends Module {
    public Criticals() {
        super("Criticals", Category.KAMPF, "Jeder Schlag ist ein Krit-Treffer (mehr Schaden).");
    }

    private static boolean blocked(LocalPlayer p) {
        return p.isInWater() || p.isInLava() || p.onClimbable() || p.isPassenger() || p.getAbilities().flying;
    }

    /** Wird direkt vor jedem Angriff aufgerufen (auch bei normalen Klicks über ein Mixin). */
    public static void beforeAttack() {
        Criticals c = TeizenClient.modules().get(Criticals.class);
        if (c == null || !c.isEnabled() || !c.inGame()) return;
        if (!V.MULTI_MOVE_PACKETS) return; // 26.3: Sprung in onTick
        LocalPlayer p = mc.player;
        if (!p.onGround() || blocked(p)) return;
        double x = p.getX(), y = p.getY(), z = p.getZ();
        p.connection.send(new ServerboundMovePlayerPacket.Pos(x, y + 0.0625, z, false, p.horizontalCollision));
        p.connection.send(new ServerboundMovePlayerPacket.Pos(x, y, z, false, p.horizontalCollision));
    }

    /** 26.3: hüpfen, wenn ein Gegner in Reichweite ist (KillAura/TriggerBot schlagen dann im Fallen). */
    @Override
    public void onTick() {
        if (V.MULTI_MOVE_PACKETS || !inGame()) return;
        LocalPlayer p = mc.player;
        if (!p.onGround() || blocked(p) || mc.gui.screen() != null) return;
        boolean target = mc.hitResult instanceof EntityHitResult eh && eh.getEntity() instanceof LivingEntity;
        KillAura ka = TeizenClient.modules().get(KillAura.class);
        if (!target && ka != null && ka.isEnabled() && ka.target != null && ka.target.isAlive() && p.distanceTo(ka.target) < 4.5) target = true;
        if (target && p.getAttackStrengthScale(0f) > 0.7f) p.jumpFromGround();
    }
}
