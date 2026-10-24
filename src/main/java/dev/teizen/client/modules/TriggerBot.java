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
    private final Setting cooldown = add(Setting.bool("cooldown", "Auf vollen Schlag warten (mehr Schaden)", true));
    private final Setting delay = add(Setting.number("verzoegerung", "Extra-Ticks Pause zwischen zwei Schlägen", 0, 0, 20));
    private int wait;

    public TriggerBot() {
        super("TriggerBot", Category.KAMPF, "Schlägt automatisch, wenn du auf einen Gegner zielst.");
    }

    @Override
    public void onTick() {
        if (!inGame() || mc.gui.screen() != null) return;
        if (wait > 0) { wait--; return; }
        HitResult hr = mc.hitResult;
        if (!(hr instanceof EntityHitResult ehr) || hr.getType() != HitResult.Type.ENTITY) return;
        Entity e = ehr.getEntity();
        if (!Targets.valid(e, players.on(), monsters.on(), animals.on())) return;
        if (cooldown.on() && mc.player.getAttackStrengthScale(0.5f) < 1.0f) return;
        mc.gameMode.attack(mc.player, e);
        wait = delay.integer();
        mc.player.swing(InteractionHand.MAIN_HAND);
    }
}
