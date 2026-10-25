package dev.teizen.client.modules;

import dev.teizen.client.TeizenClient;
import dev.teizen.client.core.Friends;
import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.util.Chat;
import dev.teizen.client.util.Op;
import dev.teizen.client.util.Rot;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * GodPvP: von allem das Stärkste. Du nimmst keinen Schaden mehr (auch nicht von Kristallen, Ankern, Feuer, Fallen),
 * kein Rückstoß, jeder Schlag tötet sofort, und du kämpfst automatisch gegen alles in Reichweite.
 * Braucht OP (bzw. geht in der eigenen Welt immer). Man sieht dann natürlich, dass du cheatest.
 */
public final class GodPvP extends Module {
    private final Setting range = add(Setting.number("reichweite", "Wie weit automatisch geschlagen wird (Blöcke)", 6, 1, 6));
    private final Setting players = add(Setting.bool("spieler", "Spieler angreifen", true));
    private final Setting monsters = add(Setting.bool("monster", "Monster angreifen", true));
    private final Setting walls = add(Setting.bool("durchwaende", "Auch durch Wände schlagen", false));
    private final Setting kill = add(Setting.bool("killbefehl", "Gegner-Spieler in Reichweite zusätzlich per /kill töten (auch mit Totem/God)", true));
    private final Setting hearts = add(Setting.number("herzen", "Wie viele Herzen du hast", 50, 10, 512));
    private final Setting helpers = add(Setting.bool("helfer", "Auch AutoTotem, AutoArmor und AntiKnockback einschalten", true));

    private static final String[] EFFECTS = {"resistance", "fire_resistance", "regeneration", "water_breathing", "saturation"};
    private static final String[] ATTRS = {"attack_damage", "attack_speed", "max_health", "armor", "armor_toughness", "knockback_resistance",
            "explosion_knockback_resistance", "entity_interaction_range", "safe_fall_distance"};

    private int lastApply = -1000;
    private boolean warned;
    private final List<Module> turnedOn = new ArrayList<>();
    private final Map<String, Integer> lastKill = new HashMap<>();
    public LivingEntity target;
    /** Zähler für Anzeige und Selbsttest. */
    public int hits, kills;

    public GodPvP() {
        super("GodPvP", Category.KAMPF, "Unbesiegbar + jeder Schlag tötet + kämpft von selbst (braucht OP).");
    }

    @Override
    protected void onEnable() {
        lastApply = -1000;
        warned = false;
        turnedOn.clear();
        if (!inGame()) return;
        if (!Op.available()) {
            Chat.error("GodPvP braucht OP-Rechte (in der eigenen Welt geht es immer).");
        }
        apply(true);
        if (helpers.on()) {
            for (Class<? extends Module> c : List.of(AutoTotem.class, AutoArmor.class, AntiKnockback.class)) {
                Module m = TeizenClient.modules().get(c);
                if (m != null && !m.isEnabled()) { m.setEnabled(true); turnedOn.add(m); }
            }
        }
        Chat.ok("§c§lGodPvP an§a – du bist jetzt unbesiegbar.");
    }

    @Override
    protected void onDisable() {
        target = null;
        for (Module m : turnedOn) m.setEnabled(false);
        turnedOn.clear();
        if (!inGame()) return;
        for (String e : EFFECTS) Op.run("effect clear @s minecraft:" + e);
        for (String a : ATTRS) Op.run("attribute @s minecraft:" + a + " base reset");
    }

    private void apply(boolean first) {
        lastApply = mc.player.tickCount;
        // Resistenz V = 100 % weniger Schaden (Schwerter, Pfeile, Kristalle, Anker, TNT, Feuer, Fallen)
        Op.run("effect give @s minecraft:resistance infinite 4 true");
        Op.run("effect give @s minecraft:fire_resistance infinite 0 true");
        Op.run("effect give @s minecraft:regeneration infinite 4 true");
        Op.run("effect give @s minecraft:water_breathing infinite 0 true");
        Op.run("effect give @s minecraft:saturation infinite 0 true");
        Op.run("attribute @s minecraft:attack_damage base set 2048");
        Op.run("attribute @s minecraft:attack_speed base set 1024");
        Op.run("attribute @s minecraft:max_health base set " + hearts.integer() * 2);
        Op.run("attribute @s minecraft:armor base set 30");
        Op.run("attribute @s minecraft:armor_toughness base set 20");
        Op.run("attribute @s minecraft:knockback_resistance base set 1");
        Op.run("attribute @s minecraft:explosion_knockback_resistance base set 1");
        Op.run("attribute @s minecraft:entity_interaction_range base set " + Math.max(3, range.num()));
        Op.run("attribute @s minecraft:safe_fall_distance base set 1024");
        if (first) Op.run("effect give @s minecraft:instant_health 1 29 true");
    }

    /** Ist der Schutz schon aktiv (Resistenz V auf dem Spieler)? */
    public boolean protectedNow() {
        var r = mc.player.getEffect(MobEffects.RESISTANCE);
        return r != null && r.getAmplifier() >= 4;
    }

    @Override
    public void onTick() {
        if (!inGame()) return;
        int now = mc.player.tickCount;
        if (now - lastApply > 100 || (!protectedNow() && now - lastApply > 30)) {
            if (!Op.available()) {
                if (!warned) { warned = true; Chat.error("GodPvP: keine OP-Rechte – nur das automatische Kämpfen geht."); }
            } else apply(false);
        }
        if (mc.gui.screen() != null) { target = null; return; }
        target = Targets.nearest(range.num(), players.on(), monsters.on(), false, walls.on());
        if (target == null) return;
        // mit Angriffsgeschwindigkeit 1024 ist jeder Schlag voll
        if (mc.player.getAttackStrengthScale(0.5f) >= 0.9f || now % 2 == 0) {
            float[] r = Rot.to(target.getBoundingBox().getCenter());
            Rot.sendSilent(r[0], r[1]);
            mc.gameMode.attack(mc.player, target);
            mc.player.swing(InteractionHand.MAIN_HAND);
            hits++;
        }
        if (kill.on() && target instanceof Player p && Op.available()) {
            String name = p.getGameProfile().name();
            if (!Friends.is(name) && now - lastKill.getOrDefault(name, -1000) > 20) {
                lastKill.put(name, now);
                Op.run("kill " + name);
                kills++;
            }
        }
    }

    @Override
    public String info() { return protectedNow() ? "§a✔" : "§c…"; }
}
