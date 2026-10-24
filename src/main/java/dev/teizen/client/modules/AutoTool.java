package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.util.Inv;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** Nimmt beim Abbauen automatisch das beste Werkzeug aus der Hotbar. */
public final class AutoTool extends Module {
    public AutoTool() {
        super("AutoTool", Category.ABBAU, "Wechselt beim Abbauen automatisch zum besten Werkzeug.");
    }

    @Override
    public void onTick() {
        if (!inGame() || !mc.options.keyAttack.isDown()) return;
        if (!(mc.hitResult instanceof BlockHitResult bhr) || bhr.getType() != HitResult.Type.BLOCK) return;
        int t = Inv.bestTool(mc.level.getBlockState(bhr.getBlockPos()));
        if (t >= 0) Inv.select(t);
    }
}
