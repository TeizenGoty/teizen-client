package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;

/**
 * NameProtect: dein Name wird überall auf deinem Bildschirm durch einen anderen ersetzt (Chat, Tab-Liste, Schilder,
 * Scoreboard ...). Gut für Videos/Screenshots. Nur bei dir – andere sehen weiter deinen echten Namen.
 */
public final class NameProtect extends Module {
    private final Setting fake = add(Setting.text("name", "Welcher Name statt deinem angezeigt wird", "Ich"));

    private static volatile String from, to;

    public NameProtect() {
        super("NameProtect", Category.SONSTIGES, "Versteckt deinen Namen auf deinem Bildschirm (für Videos).");
    }

    @Override
    protected void onEnable() { update(); }

    @Override
    protected void onDisable() { from = null; to = null; }

    private void update() {
        String real = mc.getUser() == null ? null : mc.getUser().getName();
        String f = fake.str();
        if (real == null || real.isEmpty() || f == null) { from = null; return; }
        to = f;
        from = real;
    }

    @Override
    public void onTick() { update(); }

    /** Mixin (jeder Text, der gezeichnet wird): Namen ersetzen. */
    public static String apply(String s) {
        String a = from, b = to;
        if (a == null || b == null || s == null || s.isEmpty() || s.indexOf(a) < 0) return s;
        return s.replace(a, b);
    }

    @Override
    public String info() { return fake.str(); }
}
