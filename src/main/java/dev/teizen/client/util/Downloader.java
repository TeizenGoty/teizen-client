package dev.teizen.client.util;

import dev.teizen.client.TeizenClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Lädt eine Schematic-Datei von einem direkten Link herunter (läuft auf dem Spieler-PC, nicht auf dem Server).
 * Funktioniert mit direkten Datei-Links (.litematic/.schem/.schematic/.nbt). Läuft im Hintergrund-Thread.
 */
public final class Downloader {
    private static final long MAX = 30L * 1024 * 1024; // 30 MB
    private Downloader() {}

    /** Dateiname aus der URL raten. */
    public static String fileName(String url) {
        String u = url;
        int q = u.indexOf('?');
        if (q >= 0) u = u.substring(0, q);
        int slash = u.lastIndexOf('/');
        String name = slash >= 0 ? u.substring(slash + 1) : u;
        name = name.replaceAll("[^A-Za-z0-9._-]", "_");
        if (name.isBlank()) name = "download";
        String low = name.toLowerCase(Locale.ROOT);
        if (!(low.endsWith(".litematic") || low.endsWith(".schem") || low.endsWith(".schematic") || low.endsWith(".nbt")))
            name += ".litematic";
        return name;
    }

    /**
     * Lädt {@code url} in den schematics-Ordner. Die Antwort (Dateiname oder Fehlertext) kommt per {@code done}
     * zurück auf den Spiel-Thread.
     */
    public static void download(String url, Path dir, Consumer<String> done, Consumer<String> error) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) { error.accept("Das ist kein Link (muss mit http anfangen)."); return; }
        new Thread(() -> {
            try {
                Files.createDirectories(dir);
                HttpClient client = HttpClient.newBuilder()
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .connectTimeout(Duration.ofSeconds(15)).build();
                HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                        .header("User-Agent", "TeizenClient")
                        .timeout(Duration.ofSeconds(60)).GET().build();
                HttpResponse<byte[]> res = client.send(req, HttpResponse.BodyHandlers.ofByteArray());
                if (res.statusCode() / 100 != 2) { post(error, "Download fehlgeschlagen (Code " + res.statusCode() + "). Oft braucht die Seite Login – dann die Datei im Browser laden und in den schematics-Ordner legen."); return; }
                byte[] data = res.body();
                if (data.length == 0) { post(error, "Leere Datei – ist das ein direkter Download-Link?"); return; }
                if (data.length > MAX) { post(error, "Datei zu groß (" + (data.length / 1024 / 1024) + " MB)."); return; }
                // grob prüfen, dass es kein HTML ist (dann war der Link eine Webseite, keine Datei)
                if (looksLikeHtml(data)) { post(error, "Das war eine Webseite, keine Schematic-Datei. Nimm den direkten Download-Link (.litematic / .schem)."); return; }
                String name = fileName(url);
                Path out = uniq(dir, name);
                Files.write(out, data);
                post(done, dir.relativize(out).toString().replace('\\', '/'));
            } catch (Exception e) {
                TeizenClient.LOG.warn("Download fehlgeschlagen: " + url, e);
                post(error, "Download ging nicht: " + e.getClass().getSimpleName());
            }
        }, "teizen-download").start();
    }

    private static boolean looksLikeHtml(byte[] d) {
        int n = Math.min(d.length, 64);
        String head = new String(d, 0, n, java.nio.charset.StandardCharsets.US_ASCII).trim().toLowerCase(Locale.ROOT);
        return head.startsWith("<!doctype") || head.startsWith("<html") || head.startsWith("<?xml") && head.contains("html");
    }

    private static Path uniq(Path dir, String name) {
        Path p = dir.resolve(name);
        if (!Files.exists(p)) return p;
        String base = name, ext = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0) { base = name.substring(0, dot); ext = name.substring(dot); }
        for (int i = 2; i < 1000; i++) {
            Path c = dir.resolve(base + "-" + i + ext);
            if (!Files.exists(c)) return c;
        }
        return p;
    }

    private static <T> void post(Consumer<T> cb, T val) {
        net.minecraft.client.Minecraft.getInstance().execute(() -> cb.accept(val));
    }
}
