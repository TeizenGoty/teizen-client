package dev.teizen.client.compat;

/** Welche Minecraft-Version läuft? (ohne Minecraft-Klassen, darf auch früh im Mixin-Plugin benutzt werden) */
public final class Mc {
    private Mc() {}

    /** "26.3" wenn Minecraft 26.3 läuft, sonst "26.2". */
    public static String running() {
        try {
            String v = net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("minecraft")
                    .map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("26.2");
            return v.startsWith("26.3") ? "26.3" : "26.2";
        } catch (Throwable t) {
            return "26.2";
        }
    }
}
