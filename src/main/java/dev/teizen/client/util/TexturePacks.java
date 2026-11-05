package dev.teizen.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;

import java.util.ArrayList;
import java.util.List;

/** Texturpakete aus dem resourcepacks-Ordner direkt an/aus schalten (wie im Minecraft-Menü, nur mit einem Klick). */
public final class TexturePacks {
    private static final Minecraft mc = Minecraft.getInstance();

    private TexturePacks() {}

    /** Eigene Pakete (aus dem resourcepacks-Ordner). */
    public static List<String> list() {
        PackRepository repo = mc.getResourcePackRepository();
        repo.reload();
        List<String> out = new ArrayList<>();
        for (String id : repo.getAvailableIds()) if (id.startsWith("file/")) out.add(id);
        out.sort((x, y) -> {
            boolean tx = isTeizen(x), ty = isTeizen(y);
            if (tx != ty) return tx ? -1 : 1; // Teizens Pack immer ganz oben
            return x.compareToIgnoreCase(y);
        });
        return out;
    }

    /** Teizens eigenes Texturpaket? */
    public static boolean isTeizen(String id) {
        return id.toLowerCase(java.util.Locale.ROOT).contains("teizen");
    }

    /** Teizens Texture Pack an/aus. @return false wenn es nicht im Ordner liegt */
    public static boolean toggleTeizen() {
        for (String id : list()) if (isTeizen(id)) { toggle(id); return true; }
        Chat.error("Teizens Texture Pack liegt nicht im resourcepacks-Ordner. ///textur → \"Ordner öffnen\" und die .zip reinlegen.");
        return false;
    }

    public static String title(String id) {
        Pack p = mc.getResourcePackRepository().getPack(id);
        String t = p != null ? p.getTitle().getString() : id;
        return t.startsWith("file/") ? t.substring(5) : t;
    }

    public static boolean on(String id) {
        return mc.getResourcePackRepository().getSelectedIds().contains(id);
    }

    /** An/aus und neu laden. */
    public static void toggle(String id) {
        PackRepository repo = mc.getResourcePackRepository();
        List<String> sel = new ArrayList<>(repo.getSelectedIds());
        boolean was = sel.remove(id);
        if (!was) sel.add(id); // zuletzt = ganz oben (wichtigstes)
        repo.setSelected(sel);
        mc.options.updateResourcePacks(repo);
        Chat.ok("Texturpaket §f" + title(id) + "§a " + (was ? "aus" : "an") + " – lädt neu …");
    }

    /** Paket per Name (Teil reicht) an/aus. */
    public static boolean toggleByName(String name) {
        String n = name.toLowerCase(java.util.Locale.ROOT);
        for (String id : list())
            if (title(id).toLowerCase(java.util.Locale.ROOT).contains(n)) { toggle(id); return true; }
        return false;
    }

    /** Texturpaket per direktem Link (.zip) in den resourcepacks-Ordner laden und gleich anmachen. */
    public static void download(String url) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) { Chat.error("Das ist kein Link (muss mit http anfangen)."); return; }
        Chat.info("Lade Texturpaket …");
        java.nio.file.Path dir = mc.getResourcePackDirectory();
        new Thread(() -> {
            try {
                java.nio.file.Files.createDirectories(dir);
                var client = java.net.http.HttpClient.newBuilder().followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
                        .connectTimeout(java.time.Duration.ofSeconds(15)).build();
                var req = java.net.http.HttpRequest.newBuilder(java.net.URI.create(url)).header("User-Agent", "TeizenClient")
                        .timeout(java.time.Duration.ofSeconds(120)).GET().build();
                var res = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofByteArray());
                byte[] d = res.body();
                if (res.statusCode() / 100 != 2) { mc.execute(() -> Chat.error("Download fehlgeschlagen (Code " + res.statusCode() + ").")); return; }
                if (d.length < 4 || d[0] != 'P' || d[1] != 'K') { mc.execute(() -> Chat.error("Das ist keine .zip – nimm den direkten Download-Link.")); return; }
                if (d.length > 300L * 1024 * 1024) { mc.execute(() -> Chat.error("Datei zu groß.")); return; }
                String name = url.replaceAll("[?#].*$", "");
                name = name.substring(name.lastIndexOf('/') + 1);
                name = java.net.URLDecoder.decode(name, java.nio.charset.StandardCharsets.UTF_8).replaceAll("[\\\\/:*?\"<>|]", "_");
                if (name.isBlank()) name = "texturpaket";
                if (!name.toLowerCase(java.util.Locale.ROOT).endsWith(".zip")) name += ".zip";
                java.nio.file.Path out = dir.resolve(name);
                java.nio.file.Files.write(out, d);
                String id = "file/" + out.getFileName();
                mc.execute(() -> {
                    Chat.ok("Texturpaket geladen: §f" + out.getFileName());
                    if (list().contains(id) && !on(id)) toggle(id);
                    if (mc.gui.screen() instanceof dev.teizen.client.gui.TexturePackScreen s) s.refresh();
                });
            } catch (Exception e) {
                dev.teizen.client.TeizenClient.LOG.warn("Texturpaket-Download: " + url, e);
                mc.execute(() -> Chat.error("Download ging nicht: " + e.getClass().getSimpleName()));
            }
        }, "teizen-pack-download").start();
    }

    public static void openFolder() {
        try {
            Browser.open(mc.getResourcePackDirectory().toUri().toString());
        } catch (Exception e) {
            Chat.error("Ordner ließ sich nicht öffnen: " + mc.getResourcePackDirectory());
        }
    }
}
