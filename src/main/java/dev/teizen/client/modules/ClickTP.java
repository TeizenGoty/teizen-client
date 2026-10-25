package dev.teizen.client.modules;

import dev.teizen.client.TeizenClient;
import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.util.Chat;
import dev.teizen.client.util.Op;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.Locale;

/**
 * ClickTP: Rechtsklick (mit leerer Hand) auf einen Block = du stehst sofort oben drauf. Mit OP bzw. in der eigenen Welt
 * beliebig weit (per /tp), sonst bis 8 Blöcke.
 */
public final class ClickTP extends Module {
    private final Setting range = add(Setting.number("reichweite", "Wie weit du klicken kannst (Blöcke)", 100, 5, 300));
    private final Setting emptyHand = add(Setting.bool("leerehand", "Nur mit leerer Hand (sonst bei jedem Rechtsklick)", true));

    public int teleports;
    public BlockPos lastTarget;

    public ClickTP() {
        super("ClickTP", Category.BEWEGUNG, "Rechtsklick auf einen Block = dorthin teleportieren.");
    }

    /** Mixin: Rechtsklick. @return true wenn teleportiert wurde (dann nichts anderes tun) */
    public static boolean onUse() {
        if (TeizenClient.modules() == null) return false;
        ClickTP m = TeizenClient.modules().get(ClickTP.class);
        if (m == null || !m.isEnabled() || !m.inGame()) return false;
        if (m.emptyHand.on() && !mc.player.getMainHandItem().isEmpty()) return false;
        return m.teleport();
    }

    /** Auf den angeschauten Block teleportieren. */
    public boolean teleport() {
        HitResult hit = mc.player.pick(range.num(), 0f, false);
        if (!(hit instanceof BlockHitResult bh) || hit.getType() != HitResult.Type.BLOCK) {
            Chat.error("ClickTP: kein Block in " + range.integer() + " Blöcken.");
            return true;
        }
        BlockPos t = bh.getBlockPos().relative(bh.getDirection());
        // einen Platz suchen, wo man stehen kann (2 Blöcke frei)
        for (int i = 0; i < 6 && !(free(t) && free(t.above())); i++) t = t.above();
        double x = t.getX() + 0.5, y = t.getY(), z = t.getZ() + 0.5;
        double dist = mc.player.position().distanceTo(new net.minecraft.world.phys.Vec3(x, y, z));
        if (Op.available()) {
            Op.run(String.format(Locale.ROOT, "tp @s %.3f %.3f %.3f", x, y, z));
        } else if (dist <= 8.5) {
            mc.player.setPos(x, y, z);
        } else {
            Chat.error("ClickTP: ohne OP nur bis 8 Blöcke (hier " + Math.round(dist) + ").");
            return true;
        }
        lastTarget = t;
        teleports++;
        return true;
    }

    private static boolean free(BlockPos p) {
        return mc.level.getBlockState(p).getCollisionShape(mc.level, p).isEmpty();
    }
}
