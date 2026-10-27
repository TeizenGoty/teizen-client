package dev.teizen.client.modules;

import dev.teizen.client.TeizenClient;
import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.util.Draw;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * AirPlace: Blöcke einfach in die Luft setzen – Rechtsklick mit einem Block, wenn du keinen Block anschaust.
 * Ein Rahmen zeigt, wo der Block hinkommt.
 */
public final class AirPlace extends Module {
    private final Setting distance = add(Setting.number("abstand", "Wie weit vor dir der Block in die Luft kommt (Blöcke)", 3.5, 1.5, 4.5));

    public int placed;

    public AirPlace() {
        super("AirPlace", Category.BAUEN, "Blöcke in die Luft setzen (Rechtsklick, wenn kein Block da ist).");
    }

    /** Wohin der Block käme, oder null (kein Block in der Hand / du schaust einen Block an). */
    public BlockPos spot() {
        if (!inGame() || !(mc.player.getMainHandItem().getItem() instanceof BlockItem)) return null;
        if (mc.hitResult != null && mc.hitResult.getType() != HitResult.Type.MISS) return null;
        Vec3 p = mc.player.getEyePosition().add(mc.player.getLookAngle().scale(distance.num()));
        BlockPos b = BlockPos.containing(p);
        if (!mc.level.getBlockState(b).canBeReplaced()) return null;
        if (mc.player.getBoundingBox().intersects(new AABB(b))) return null;
        return b;
    }

    /** Mixin: Rechtsklick. @return true wenn ein Block in die Luft gesetzt wurde */
    public static boolean onUse() {
        if (TeizenClient.modules() == null) return false;
        AirPlace m = TeizenClient.modules().get(AirPlace.class);
        if (m == null || !m.isEnabled()) return false;
        return m.place();
    }

    public boolean place() {
        BlockPos b = spot();
        if (b == null) return false;
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(b), Direction.UP, b, false);
        var res = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
        if (res.consumesAction()) {
            dev.teizen.client.compat.V.swing(InteractionHand.MAIN_HAND);
            placed++;
            return true;
        }
        return false;
    }

    @Override
    public void onHud(GuiGraphicsExtractor g, float partial) {
        BlockPos b = spot();
        if (b != null) Draw.worldBox(g, new AABB(b), 0xFF60D0FF, false);
    }
}
