package dev.teizen.client.modules;

import dev.teizen.client.TeizenClient;
import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.util.Chat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * GodPvP: gewinnt fast jeden Kampf – ganz ohne OP. Es macht NICHT unsterblich (das bräuchte OP), sondern schaltet
 * mit einem Klick die starken Kampf-Helfer zusammen an: KillAura, Reichweite, Ziel-Hilfe, TriggerBot, Crits und Schutz
 * (AutoTotem, AutoArmor, AutoShield, AntiKnockback, Velocity). Dazu: Streitkolben (MaceSwap, klappt auch beim Fliegen),
 * Schild-Brecher (Axt gegen blockende Gegner) und immer die beste Waffe in der Hand. Beim Ausschalten gehen nur die aus,
 * die GodPvP selbst angemacht hat – was du vorher schon an hattest, bleibt an.
 */
public final class GodPvP extends Module {
    private final Setting killaura = add(Setting.bool("killaura", "Automatisch auf Gegner einschlagen (KillAura)", true));
    private final Setting reach = add(Setting.bool("reichweite", "Größere Reichweite (Reach)", true));
    private final Setting aim = add(Setting.bool("zielhilfe", "Ziel-Hilfe: zielt automatisch auf Gegner (AimAssist)", true));
    private final Setting trigger = add(Setting.bool("triggerbot", "Schlägt sofort zu, wenn du einen Gegner ansiehst (TriggerBot)", true));
    private final Setting crits = add(Setting.bool("crits", "Immer kritische Treffer (Criticals)", true));
    private final Setting mace = add(Setting.bool("mace", "Streitkolben: im Fallen und beim Fliegen kurz zum Mace wechseln (MaceSwap, Mace in der Hotbar)", true));
    private final Setting shield = add(Setting.bool("schildbrecher", "Blockt der Gegner, kurz zur Axt: Schild kaputt (ShieldBreaker, Axt in der Hotbar)", true));
    private final Setting weapon = add(Setting.bool("bestewaffe", "Beim Kampf automatisch die beste Waffe aus der Hotbar nehmen", true));
    private final Setting protect = add(Setting.bool("schutz", "Schutz: AutoTotem, AutoArmor, AutoShield, AntiKnockback, Velocity", true));

    /** Name -> Klasse, in der Reihenfolge wie im Menü. */
    private static final Map<String, Class<? extends Module>> PARTS = new LinkedHashMap<>();
    static {
        PARTS.put("killaura", KillAura.class);
        PARTS.put("reichweite", Reach.class);
        PARTS.put("zielhilfe", AimAssist.class);
        PARTS.put("triggerbot", TriggerBot.class);
        PARTS.put("crits", Criticals.class);
    }

    private static final List<Class<? extends Module>> PROTECT = List.of(
            AutoTotem.class, AutoArmor.class, AutoShield.class, AntiKnockback.class, Velocity.class);

    /** Module, die GodPvP selbst eingeschaltet hat (nur die werden wieder ausgeschaltet). */
    private final List<Module> turnedOn = new ArrayList<>();

    public GodPvP() {
        super("GodPvP", Category.KAMPF, "Alle Kampf-Helfer auf einmal: KillAura, Reach, Crits, Mace (auch beim Fliegen), Schild-Brecher, beste Waffe, Schutz.");
    }

    private void turnOn(Class<? extends Module> c) {
        Module m = TeizenClient.modules().get(c);
        if (m != null && !m.isEnabled()) { m.setEnabled(true); turnedOn.add(m); }
    }

    @Override
    protected void onEnable() {
        turnedOn.clear();
        if (killaura.on()) turnOn(KillAura.class);
        if (reach.on()) turnOn(Reach.class);
        if (aim.on()) turnOn(AimAssist.class);
        if (trigger.on()) turnOn(TriggerBot.class);
        if (crits.on()) turnOn(Criticals.class);
        if (mace.on()) turnOn(MaceSwap.class);
        if (shield.on()) turnOn(ShieldBreaker.class);
        if (protect.on()) for (Class<? extends Module> c : PROTECT) turnOn(c);
        if (turnedOn.isEmpty()) Chat.info("GodPvP: diese Helfer waren schon alle an.");
        else Chat.ok("§c§lGodPvP an§a – " + turnedOn.size() + " Kampf-Helfer dazugeschaltet (kein OP nötig).");
    }

    @Override
    protected void onDisable() {
        for (Module m : turnedOn) m.setEnabled(false);
        turnedOn.clear();
    }

    /** Für Selbsttest/Anzeige: wie oft zur besten Waffe gewechselt wurde. */
    public int weaponPicks;

    /** Wert einer Waffe (Schwert/Axt, besseres Material = mehr). Streitkolben/Speer übernehmen MaceSwap/SpearSwap. */
    static int weaponScore(net.minecraft.world.item.ItemStack st) {
        if (st.isEmpty()) return 0;
        String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem()).getPath();
        int kind = id.endsWith("_sword") ? 10 : id.endsWith("_axe") ? 9 : 0;
        if (kind == 0) return 0;
        int tier = id.startsWith("netherite") ? 6 : id.startsWith("diamond") ? 5 : id.startsWith("iron") ? 4
                : id.startsWith("copper") ? 3 : id.startsWith("stone") ? 3 : id.startsWith("golden") ? 2 : 1;
        int ench = st.isEnchanted() ? 1 : 0;
        return tier * 100 + kind * 2 + ench;
    }

    @Override
    public void onTick() {
        if (!weapon.on() || !inGame() || mc.gui.screen() != null || mc.player.isUsingItem()) return;
        KillAura ka = TeizenClient.modules().get(KillAura.class);
        if (ka == null || !ka.isEnabled() || ka.target == null) return;
        var hand = mc.player.getMainHandItem();
        if (dev.teizen.client.util.WeaponSwap.isMace(hand) || dev.teizen.client.util.WeaponSwap.isSpear(hand)) return;
        int best = -1, bestScore = weaponScore(hand);
        for (int i = 0; i < 9; i++) {
            int sc = weaponScore(mc.player.getInventory().getItem(i));
            if (sc > bestScore) { bestScore = sc; best = i; }
        }
        if (best >= 0) { dev.teizen.client.util.Inv.select(best); weaponPicks++; }
    }

    /** Wie viele Helfer GodPvP gerade zusätzlich anhat (Selbsttest / Anzeige). */
    public int activeHelpers() { return turnedOn.size(); }

    @Override
    public String info() { return isEnabled() ? "§a" + turnedOn.size() : ""; }
}
