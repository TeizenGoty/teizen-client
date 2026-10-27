package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.util.Rot;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;

/** Greift automatisch das nächste Ziel in Reichweite an. */
public final class KillAura extends Module {
    private final Setting range = add(Setting.number("reichweite", "Angriffs-Reichweite in Blöcken", 4.2, 1, 8));
    private final Setting players = add(Setting.bool("spieler", "Spieler angreifen", true));
    private final Setting monsters = add(Setting.bool("monster", "Monster angreifen", true));
    private final Setting animals = add(Setting.bool("tiere", "Tiere/andere Mobs angreifen", false));
    private final Setting walls = add(Setting.bool("durchwaende", "Auch durch Wände angreifen", false));
    private final Setting rotate = add(Setting.choice("drehen", "unsichtbar = nur Server sieht Drehung", "unsichtbar", "unsichtbar", "sichtbar", "aus"));
    private final Setting cooldown = add(Setting.bool("cooldown", "Auf vollen Schlag warten (mehr Schaden)", true));
    private final Setting cps = add(Setting.number("cps", "Schläge pro Sekunde wenn cooldown aus", 12, 1, 20));

    public LivingEntity target;
    private int tickCounter;

    public KillAura() {
        super("KillAura", Category.KAMPF, "Kämpft automatisch für dich gegen alles in Reichweite.");
    }

    @Override
    protected void onDisable() { target = null; }

    @Override
    public void onTick() {
        if (!inGame() || mc.gui.screen() != null) { target = null; return; }
        target = Targets.nearest(range.num(), players.on(), monsters.on(), animals.on(), walls.on());
        if (target == null) return;

        tickCounter++;
        boolean ready = cooldown.on()
                ? mc.player.getAttackStrengthScale(0.5f) >= 1.0f
                : tickCounter >= Math.max(1, Math.round(20.0 / cps.num()));
        if (!ready) return;
        tickCounter = 0;

        float[] r = Rot.to(target.getBoundingBox().getCenter());
        if (rotate.is("unsichtbar")) Rot.sendSilent(r[0], r[1]);
        else if (rotate.is("sichtbar")) Rot.look(r[0], r[1]);

        mc.gameMode.attack(mc.player, target);
        dev.teizen.client.compat.V.swing(InteractionHand.MAIN_HAND);
    }

    @Override
    public String info() { return Setting.fmt(range.num()); }
}
