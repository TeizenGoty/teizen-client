package dev.teizen.client.modules;

import dev.teizen.client.core.Friends;
import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.util.Inv;
import dev.teizen.client.util.Rot;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.HashMap;
import java.util.Map;

/**
 * AutoClicker mit Modi:
 *  - farmen: schlägt alles vor dir, sobald der Schlag voll aufgeladen ist (Mob-Farm, AFK)
 *  - links: klickt links mit eingestellter Klickrate
 *  - rechts: klickt rechts mit eingestellter Klickrate (benutzen/setzen)
 *  - zuechten: füttert Tiere in der Nähe mit dem passenden Futter (Weizen, Karotten, Samen ...), damit sie Junge bekommen
 */
public final class AutoClicker extends Module {
    private final Setting mode = add(Setting.choice("modus", "farmen = Mob-Farm (voller Schlag), links, rechts, zuechten = Tiere füttern", "farmen", "farmen", "links", "rechts", "zuechten"));
    private final Setting cps = add(Setting.number("cps", "Klicks pro Sekunde (links/rechts/zuechten)", 10, 1, 20));
    private final Setting hold = add(Setting.bool("nurhalten", "Nur solange die Maustaste gedrückt ist (links/rechts)", false));
    private final Setting players = add(Setting.bool("spieler", "farmen: auch Spieler schlagen", false));
    private final Setting range = add(Setting.number("reichweite", "zuechten: wie weit Tiere gesucht werden (Blöcke)", 4, 1, 5));

    private double acc;
    /** Gefütterte Tiere: ID -> Tick (nicht doppelt füttern). */
    private final Map<Integer, Integer> fed = new HashMap<>();
    public int clicks, feeds;

    public AutoClicker() {
        super("AutoClicker", Category.SONSTIGES, "Klickt automatisch: Mob-Farm, links/rechts oder Tiere züchten.");
    }

    @Override
    protected void onEnable() { acc = 0; fed.clear(); }

    /** Wie viele Klicks diesen Tick (gleichmäßig verteilt). */
    private int due() {
        acc += cps.num() / 20.0;
        int n = (int) acc;
        acc -= n;
        return n;
    }

    @Override
    public void onTick() {
        if (!inGame() || mc.gui.screen() != null) return;
        switch (mode.str()) {
            case "links" -> left();
            case "rechts" -> right();
            case "zuechten" -> breed();
            default -> farm();
        }
    }

    private void farm() {
        Entity e = mc.crosshairPickEntity;
        if (!(e instanceof LivingEntity le) || !le.isAlive()) return;
        if (e instanceof Player p && (!players.on() || Friends.is(p.getGameProfile().name()))) return;
        if (mc.player.getAttackStrengthScale(0.5f) < 1.0f) return;
        mc.gameMode.attack(mc.player, e);
        mc.player.swing(InteractionHand.MAIN_HAND);
        clicks++;
    }

    private void left() {
        if (hold.on() && !mc.options.keyAttack.isDown()) return;
        for (int i = due(); i > 0; i--) {
            if (mc.hitResult instanceof EntityHitResult eh) mc.gameMode.attack(mc.player, eh.getEntity());
            mc.player.swing(InteractionHand.MAIN_HAND);
            clicks++;
        }
    }

    private void right() {
        if (hold.on() && !mc.options.keyUse.isDown()) return;
        for (int i = due(); i > 0; i--) {
            HitResult h = mc.hitResult;
            if (h instanceof BlockHitResult bh && h.getType() == HitResult.Type.BLOCK) mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, bh);
            else if (h instanceof EntityHitResult eh) mc.gameMode.interact(mc.player, eh.getEntity(), eh, InteractionHand.MAIN_HAND);
            else mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            mc.player.swing(InteractionHand.MAIN_HAND);
            clicks++;
        }
    }

    private void breed() {
        int now = mc.player.tickCount;
        fed.values().removeIf(t -> now - t > 1200 || now < t);
        if (due() <= 0) return;
        double r = range.num();
        for (Entity e : mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(r), x -> x instanceof Animal)) {
            Animal a = (Animal) e;
            if (!a.isAlive() || a.isBaby() || fed.containsKey(a.getId())) continue;
            if (Targets.distanceToBox(a) > r) continue;
            int slot = Inv.findHotbar(a::isFood);
            if (slot < 0) continue;
            int prev = Inv.selected();
            Inv.select(slot);
            float[] rot = Rot.to(a.getBoundingBox().getCenter());
            Rot.sendSilent(rot[0], rot[1]);
            EntityHitResult hit = new EntityHitResult(a, a.getBoundingBox().getCenter());
            mc.gameMode.interact(mc.player, a, hit, InteractionHand.MAIN_HAND);
            mc.player.swing(InteractionHand.MAIN_HAND);
            Inv.select(prev);
            fed.put(a.getId(), now);
            feeds++;
            return; // ein Tier pro Klick
        }
    }

    @Override
    public String info() { return mode.str(); }
}
