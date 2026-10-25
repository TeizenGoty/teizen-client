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
 * (AutoTotem, AutoArmor, AutoShield, AntiKnockback, Velocity). Beim Ausschalten gehen nur die aus, die GodPvP selbst
 * angemacht hat – was du vorher schon an hattest, bleibt an.
 */
public final class GodPvP extends Module {
    private final Setting killaura = add(Setting.bool("killaura", "Automatisch auf Gegner einschlagen (KillAura)", true));
    private final Setting reach = add(Setting.bool("reichweite", "Größere Reichweite (Reach)", true));
    private final Setting aim = add(Setting.bool("zielhilfe", "Ziel-Hilfe: zielt automatisch auf Gegner (AimAssist)", true));
    private final Setting trigger = add(Setting.bool("triggerbot", "Schlägt sofort zu, wenn du einen Gegner ansiehst (TriggerBot)", true));
    private final Setting crits = add(Setting.bool("crits", "Immer kritische Treffer (Criticals)", true));
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
        super("GodPvP", Category.KAMPF, "Gewinnt fast jeden Kampf, ganz ohne OP (schaltet die Kampf-Helfer zusammen an).");
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
        if (protect.on()) for (Class<? extends Module> c : PROTECT) turnOn(c);
        if (turnedOn.isEmpty()) Chat.info("GodPvP: diese Helfer waren schon alle an.");
        else Chat.ok("§c§lGodPvP an§a – " + turnedOn.size() + " Kampf-Helfer dazugeschaltet (kein OP nötig).");
    }

    @Override
    protected void onDisable() {
        for (Module m : turnedOn) m.setEnabled(false);
        turnedOn.clear();
    }

    /** Wie viele Helfer GodPvP gerade zusätzlich anhat (Selbsttest / Anzeige). */
    public int activeHelpers() { return turnedOn.size(); }

    @Override
    public String info() { return isEnabled() ? "§a" + turnedOn.size() : ""; }
}
