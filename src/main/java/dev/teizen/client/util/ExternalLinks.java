package dev.teizen.client.util;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Link zu PhantomMarket (Schematic-Marktplatz). Es wird nichts von der Seite ausgelesen – Teizen öffnet sie nur
 * im Browser.
 * Für "Send to Minecraft" von PhantomMarket gibt es deren offizielles Mod "PhantomMarket Link": die Datei landet
 * dann im schematics-Ordner und erscheint im Teizen-Schematic-Fenster (Bauen / Einfügen).
 */
public final class ExternalLinks {
    public static final String PHANTOM_MARKET = "https://market.phantom-node.com";
    public static final String PHANTOM_LINK_MOD = "https://www.curseforge.com/minecraft/mc-mods/phantommarket-link";

    /** Zuletzt geöffnete Adresse (Selbsttest). */
    public static String lastUrl;

    private ExternalLinks() {}

    /** Ist das offizielle PhantomMarket-Link-Mod installiert? */
    public static boolean phantomLinkInstalled() {
        for (String id : new String[]{"phantommarket-link", "phantommarket_link", "phantommarketlink", "phantommarket"})
            if (FabricLoader.getInstance().isModLoaded(id)) return true;
        return false;
    }

    /** Im Browser öffnen (im Selbsttest nur merken). @return true wenn geöffnet */
    public static boolean open(String url) {
        lastUrl = url;
        if (dev.teizen.client.SelfTest.ACTIVE) return true;
        try {
            Browser.open(url);
            return true;
        } catch (Exception e) {
            Chat.error("Browser ließ sich nicht öffnen: " + e.getMessage() + " – Adresse: " + url);
            return false;
        }
    }

    /** PhantomMarket im Browser öffnen. */
    public static void openPhantomMarket() {
        if (open(PHANTOM_MARKET)) {
            if (phantomLinkInstalled())
                Chat.ok("PhantomMarket geöffnet. Bei einem Build §f\"Send to Minecraft\"§a drücken – er erscheint im Schematic-Fenster (///schematics) zum Bauen.");
            else
                Chat.info("PhantomMarket geöffnet. Tipp: Mit dem offiziellen Mod §fPhantomMarket Link§7 kommt \"Send to Minecraft\" direkt ins Spiel – oder Download-Link im Schematic-Fenster einfügen.");
        }
    }
}
