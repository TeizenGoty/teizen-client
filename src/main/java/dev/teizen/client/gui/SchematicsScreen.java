package dev.teizen.client.gui;

import dev.teizen.client.compat.LitematicaCompat;
import dev.teizen.client.modules.AutoBuild;
import dev.teizen.client.TeizenClient;
import dev.teizen.client.util.Chat;
import dev.teizen.client.util.Downloader;
import dev.teizen.client.util.SchematicPaste;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

/**
 * Schematic-Fenster: alle Schematics durchsuchen, per Link herunterladen und mit einem Klick bauen
 * (Hologramm + AutoBuild) oder sofort einfügen (mit OP). Öffnen mit ///schematics.
 */
public final class SchematicsScreen extends Screen {
    private static final int ROW_H = 16, TOP = 56;

    private final Screen parent;
    private final SearchBox search = new SearchBox();
    private String url = "";
    private boolean urlFocused;
    private List<String> all = List.of();
    private List<String> shown = List.of();
    private int scroll;
    // Klick-Bereiche (im render gesetzt)
    private int urlX, urlY, urlW, loadX, loadW, listBottom;

    public SchematicsScreen(Screen parent) {
        super(Component.literal("Schematics"));
        this.parent = parent;
    }

    @Override
    protected void init() { refresh(); }

    private void refresh() {
        all = FabricLoader.getInstance().isModLoaded("litematica") ? LitematicaCompat.listSchematics() : List.of();
        filter();
    }

    private void filter() {
        String q = search.query.toLowerCase(Locale.ROOT).trim();
        shown = q.isEmpty() ? all : all.stream().filter(n -> n.toLowerCase(Locale.ROOT).contains(q)).toList();
        scroll = 0;
    }

    private int rowCount() { return Math.max(0, (listBottom - TOP) / ROW_H); }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        Font font = getFont();
        g.fill(0, 0, width, height, 0xE0140A26);
        listBottom = height - 18;

        // Kopf
        g.fill(0, 0, width, 40, 0xF01A0E30);
        g.text(font, "§5§lTeizen §7Schematics", 8, 8, 0xFFFFFFFF, true);
        if (!FabricLoader.getInstance().isModLoaded("litematica"))
            g.text(font, "§cBrauchst Litematica, um Schematics zu bauen/einzufügen.", 8, 24, 0xFFFFFFFF, true);
        else
            g.text(font, "§7" + shown.size() + " Schematic(s) · Link einfügen zum Herunterladen · klick Bauen/Einfügen", 8, 24, 0xFFFFFFFF, true);

        // URL-Feld + Laden-Knopf
        urlY = 42; urlX = 8; loadW = 60; loadX = width - loadW - 8; urlW = loadX - urlX - 6;
        g.fill(urlX - 1, urlY - 1, urlX + urlW + 1, urlY + 13, urlFocused ? 0xFFFFD040 : 0xFF5B1FB8);
        g.fill(urlX, urlY, urlX + urlW, urlY + 12, 0xF0100820);
        String shownUrl = url.isEmpty() && !urlFocused ? "§8Download-Link einfügen (.litematic / .schem) …"
                : "§f" + fitTail(font, url, urlW - 10) + (urlFocused && (System.currentTimeMillis() / 500) % 2 == 0 ? "§e_" : "");
        g.text(font, shownUrl, urlX + 3, urlY + 2, 0xFFFFFFFF, false);
        boolean lh = in(mouseX, mouseY, loadX, urlY, loadW, 13);
        g.fill(loadX, urlY, loadX + loadW, urlY + 13, lh ? 0xFF7A2CF0 : 0xE0362060);
        g.centeredText(font, "§fLaden", loadX + loadW / 2, urlY + 3, 0xFFFFFFFF);

        // Suche
        search.render(g, font, 8, TOP - 14, width - 16, mouseX, mouseY);

        // Liste
        int y = TOP;
        int start = scroll;
        for (int i = start; i < shown.size() && y + ROW_H <= listBottom; i++) {
            String name = shown.get(i);
            boolean hover = in(mouseX, mouseY, 8, y, width - 16, ROW_H - 1);
            g.fill(8, y, width - 8, y + ROW_H - 1, hover ? 0xE0362060 : 0xC0201038);
            g.text(font, "§f" + fit(font, name, width - 16 - 150), 12, y + 4, 0xFFFFFFFF, false);
            int bEin = width - 8 - 78, bBau = bEin - 62;
            boolean hb = in(mouseX, mouseY, bBau, y + 1, 58, ROW_H - 3), he = in(mouseX, mouseY, bEin, y + 1, 74, ROW_H - 3);
            g.fill(bBau, y + 1, bBau + 58, y + ROW_H - 2, hb ? 0xFF2E9A50 : 0xD0245038);
            g.centeredText(font, "§fBauen", bBau + 29, y + 4, 0xFFFFFFFF);
            g.fill(bEin, y + 1, bEin + 74, y + ROW_H - 2, he ? 0xFF7A2CF0 : 0xD0402080);
            g.centeredText(font, "§fEinfügen", bEin + 37, y + 4, 0xFFFFFFFF);
            y += ROW_H;
        }
        if (shown.isEmpty()) g.centeredText(font, "§7Keine Schematics gefunden. Leg welche in den schematics-Ordner oder lad sie per Link.", width / 2, TOP + 20, 0xFFFFFFFF);

        // Fuß
        g.fill(0, height - 14, width, height, 0xC0000000);
        String foot = SchematicPaste.running() ? "§aFüge ein … noch " + SchematicPaste.remaining() + " Blöcke" : "§8Esc schließen · Mausrad scrollen · Bauen = AutoBuild · Einfügen = sofort (OP)";
        g.centeredText(font, foot, width / 2, height - 11, 0xFFFFFFFF);
    }

    private static String fit(Font font, String s, int w) {
        return font.width(s) <= w ? s : font.plainSubstrByWidth(s, w);
    }

    /** Zeigt das ENDE eines langen Textes (da steht der Cursor). */
    private static String fitTail(Font font, String s, int w) {
        if (font.width(s) <= w) return s;
        int i = 0;
        while (i < s.length() && font.width("…" + s.substring(i)) > w) i++;
        return "…" + s.substring(Math.min(i, s.length()));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        double mx = e.x(), my = e.y();
        if (in(mx, my, urlX, urlY, urlW, 13)) { urlFocused = true; search.focused = false; return true; }
        if (in(mx, my, loadX, urlY, loadW, 13)) { startDownload(); return true; }
        if (search.click(mx, my)) { urlFocused = false; filter(); return true; }
        urlFocused = false;
        int y = TOP;
        for (int i = scroll; i < shown.size() && y + ROW_H <= listBottom; i++) {
            int bEin = width - 8 - 78, bBau = bEin - 62;
            if (in(mx, my, bBau, y + 1, 58, ROW_H - 3)) { build(shown.get(i), false); return true; }
            if (in(mx, my, bEin, y + 1, 74, ROW_H - 3)) { build(shown.get(i), true); return true; }
            y += ROW_H;
        }
        return super.mouseClicked(e, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        int max = Math.max(0, shown.size() - rowCount());
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(sy)));
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent e) {
        if (urlFocused) {
            String c = e.codepointAsString();
            if (e.isAllowedChatCharacter() && url.length() < 300) url += c;
            return true;
        }
        if (search.charTyped(e)) { filter(); return true; }
        return super.charTyped(e);
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (urlFocused) {
            int k = e.key();
            if (k == dev.teizen.client.compat.K.BACKSPACE) { if (!url.isEmpty()) url = url.substring(0, url.length() - 1); return true; }
            if (k == dev.teizen.client.compat.K.ENTER || k == dev.teizen.client.compat.K.KP_ENTER) { startDownload(); return true; }
            if (k == dev.teizen.client.compat.K.ESCAPE) { urlFocused = false; return true; }
            if (k == dev.teizen.client.compat.K.DELETE) { url = ""; return true; }
            return true;
        }
        if (search.keyPressed(e)) { filter(); return true; }
        if (e.key() == dev.teizen.client.compat.K.ESCAPE) { onClose(); return true; }
        return super.keyPressed(e);
    }

    private void startDownload() {
        if (url.isBlank()) { Chat.error("Erst einen Download-Link einfügen."); return; }
        if (!FabricLoader.getInstance().isModLoaded("litematica")) { Chat.error("Brauchst Litematica."); return; }
        String link = url.trim();
        Chat.info("Lade herunter …");
        Downloader.download(link, LitematicaCompat.schematicsDir(),
                name -> { Chat.ok("Geladen: §f" + name); url = ""; refresh(); },
                err -> Chat.error(err));
    }

    private void build(String name, boolean paste) {
        if (mc.player == null) return;
        if (!name.toLowerCase(Locale.ROOT).endsWith(".litematic")) {
            Chat.info("§e" + name + "§7 ist kein .litematic. Lad sie einmal in Litematica (Taste §fM §7→ Schematik laden), dann geht sie hier direkt.");
            return;
        }
        String err = LitematicaCompat.loadAndPlace(name, mc.player.blockPosition());
        if (err != null) { Chat.error(err); return; }
        if (paste) {
            if (SchematicPaste.start() > 0) { onClose(); }
        } else {
            AutoBuild b = TeizenClient.modules().get(AutoBuild.class);
            if (!b.isEnabled()) b.setEnabled(true);
            Chat.ok("Baue §f" + name + "§a mit AutoBuild. Aus mit ///autobuild.");
            onClose();
        }
    }

    private final Minecraft mc = Minecraft.getInstance();

    // -------- Selbsttest-Hilfen --------
    /** Liste frisch holen und prüfen, ob ein Name vorkommt. */
    public boolean testListContains(String part) {
        refresh();
        String q = part.toLowerCase(Locale.ROOT);
        for (String n : shown) if (n.toLowerCase(Locale.ROOT).contains(q)) return true;
        return false;
    }

    /** Suche setzen und prüfen, dass die Liste danach filtert. */
    public int testSearch(String q) {
        search.query = q;
        filter();
        return shown.size();
    }

    @Override
    public void onClose() { Minecraft.getInstance().gui.setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return false; }
}
