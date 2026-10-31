package dev.teizen.client.modules;

import dev.teizen.client.core.Friends;
import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** AutoWasp: verfolgt mit der Elytra automatisch einen Spieler (im Elytra-Flug). */
public final class AutoWasp extends Module {
    private final Setting name = add(Setting.text("ziel", "Name des Spielers (leer = nächster)", ""));
    private final Setting speed = add(Setting.number("tempo", "Flug-Tempo (Blöcke pro Tick)", 1.6, 0.5, 4));
    private final Setting above = add(Setting.number("hoehe", "So viele Blöcke über ihm bleiben", 2, 0, 10));
    private final Setting friends = add(Setting.bool("freunde", "Auch Freunde verfolgen", true));
    public Player target;
    private int hint;

    public AutoWasp() {
        super("AutoWasp", Category.BEWEGUNG, "Verfolgt mit der Elytra einen Spieler.");
    }

    @Override
    public void onTick() {
        if (!inGame()) return;
        target = null;
        double best = Double.MAX_VALUE;
        for (Player p : mc.level.players()) {
            if (p == mc.player || p.isSpectator() || !p.isAlive()) continue;
            String n = p.getGameProfile().name();
            if (!name.str().isBlank() && !n.equalsIgnoreCase(name.str().trim())) continue;
            if (name.str().isBlank() && !friends.on() && Friends.is(n)) continue;
            double d = p.distanceToSqr(mc.player);
            if (d < best) { best = d; target = p; }
        }
        if (target == null) return;
        if (!mc.player.isFallFlying()) {
            if (++hint % 100 == 1) dev.teizen.client.util.Chat.info("AutoWasp: starte den Elytra-Flug, dann folge ich §f" + target.getGameProfile().name());
            return;
        }
        Vec3 goal = target.position().add(0, above.num(), 0);
        Vec3 d = goal.subtract(mc.player.position());
        double len = d.length();
        if (len < 0.5) { mc.player.setDeltaMovement(Vec3.ZERO); return; }
        Vec3 v = d.scale(Math.min(speed.num(), len) / len);
        mc.player.setDeltaMovement(v);
        float[] r = dev.teizen.client.util.Rot.to(target.getEyePosition());
        mc.player.setYRot(r[0]);
    }

    @Override
    public String info() { return target == null ? "" : target.getGameProfile().name(); }
}
