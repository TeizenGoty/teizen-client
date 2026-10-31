package dev.teizen.client.modules;

import dev.teizen.client.TeizenClient;
import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;

/** NoRender: blendet störende Sachen aus – Feuer vor den Augen, Wasser-Schleier, Block-im-Gesicht, Totem-Animation, Regen, Partikel, Nebel. */
public final class NoRender extends Module {
    private final Setting fire = add(Setting.bool("feuer", "Feuer vor den Augen", true));
    private final Setting water = add(Setting.bool("wasser", "Wasser-Schleier unter Wasser", true));
    private final Setting inWall = add(Setting.bool("imblock", "Block-Bild, wenn der Kopf in einem Block steckt", true));
    private final Setting totem = add(Setting.bool("totem", "Große Totem-Animation", true));
    private final Setting weather = add(Setting.bool("wetter", "Regen und Schnee", true));
    private final Setting particles = add(Setting.bool("partikel", "Alle Partikel (Explosionen, Rauch …)", false));
    private final Setting fog = add(Setting.bool("nebel", "Nebel (Weitsicht)", true));
    public int hidden;
    private boolean fogOff;

    public NoRender() {
        super("NoRender", Category.SICHT, "Blendet Feuer, Wasser-Schleier, Totem-Animation, Regen, Nebel usw. aus.");
    }

    private static NoRender on() {
        if (TeizenClient.modules() == null) return null;
        NoRender n = TeizenClient.modules().get(NoRender.class);
        return n != null && n.isEnabled() ? n : null;
    }

    private static boolean hide(java.util.function.Function<NoRender, Setting> s) {
        NoRender n = on();
        if (n == null || !s.apply(n).on()) return false;
        n.hidden++;
        return true;
    }

    public static boolean fire() { return hide(n -> n.fire); }
    public static boolean water() { return hide(n -> n.water); }
    public static boolean inWall() { return hide(n -> n.inWall); }
    public static boolean totem() { return hide(n -> n.totem); }
    public static boolean weather() { return hide(n -> n.weather); }
    public static boolean particles() { NoRender n = on(); return n != null && n.particles.on(); }

    /** Nebel an/aus (Minecraft hat dafür einen Umschalter, der den neuen Zustand zurückgibt). */
    private static void setFog(boolean enabled) {
        try {
            boolean now = net.minecraft.client.renderer.fog.FogRenderer.toggleFog();
            if (now != enabled) net.minecraft.client.renderer.fog.FogRenderer.toggleFog();
        } catch (Throwable ignored) {}
    }

    @Override
    public void onTick() {
        boolean want = fog.on();
        if (want != fogOff) { setFog(!want); fogOff = want; }
    }

    @Override
    protected void onDisable() { if (fogOff) { setFog(true); fogOff = false; } }
}
