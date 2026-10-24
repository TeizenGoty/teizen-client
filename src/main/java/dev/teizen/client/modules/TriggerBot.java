package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/** Schlägt automatisch zu, sobald das Fadenkreuz auf einem Gegner ist. */
public final class TriggerBot extends Module {
    private final Setting players = add(Setting.bool("spieler", "Spieler", true));
    private final Setting monsters = add(Setting.bool("monster", "Monster", true));
    private final Setting animals = add(Setting.bool("tiere", "Tiere", false));

    public TriggerBot() {
        super("TriggerBot", Category.KAMPF, "Schlägt automatisch, wenn du auf einen Gegner zielst.");
    }

    @Override
    public void onTick() {
        if (!inGame() || mc.gui.screen() != null) return;
        HitResult hr = mc.hitResult;
        if (!(hr instanceof EntityHitResult ehr) || hr.getType() != HitResult.Type.ENTITY) return;
        Entity e = ehr.getEntity();
        if (!Targets.valid(e, players.on(), monsters.on(), animals.on())) return;
        if (mc.player.getAttackStrengthScale(0.5f) < 1.0f) return;
        mc.gameMode.attack(mc.player, e);
        mc.player.swing(InteractionHand.MAIN_HAND);
    }
}
