package dev.teizen.client.core;

import dev.teizen.client.util.Chat;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Properties;

/**
 * Key-Sperre: Es gibt zwei Versionen des Clients.
 * <ul>
 *   <li>normal ({@code key_required=false}): geht immer</li>
 *   <li>"mit Key" ({@code key_required=true}): geht nur, wenn die Key-Mod (teizen-key.jar) auch im mods-Ordner liegt.
 *       Ohne sie lässt sich keine Funktion einschalten, kein ///-Befehl ausführen und das Menü zeigt einen Fehler.</li>
 * </ul>
 * Welche Version es ist, steht in teizenclient-build.properties in der jar (wird beim Bauen gesetzt).
 */
public final class Lock {
    public static final String KEY_MOD = "teizenkey";
    private static final String KEY_FILE = "teizen-key.txt";
    /** SHA-256 des Schlüssels, der in der Key-Mod steckt. */
    private static final String KEY_HASH = "1f5972120c10dfa9dfc7e8196418f49b673654b388057a7fb02d17505e034db3";

    /** Ist das die Version, die die Key-Mod braucht? */
    public static final boolean REQUIRED = readRequired();

    /** Nur für den Selbsttest: so tun, als wäre es die Version mit Key bzw. als fehle die Key-Mod (null = echt). */
    public static Boolean testRequired, testKeyMissing;

    private static Boolean keyOk;
    private static long lastComplain;

    private Lock() {}

    public static boolean required() { return testRequired != null ? testRequired : REQUIRED; }

    /** Liegt die passende Key-Mod im mods-Ordner? */
    public static boolean keyPresent() {
        if (Boolean.TRUE.equals(testKeyMissing)) return false;
        if (keyOk == null) keyOk = checkKey();
        return keyOk;
    }

    /** Darf der Client benutzt werden? */
    public static boolean ok() { return !required() || keyPresent(); }

    /** Was im Menü und im Chat steht, wenn es gesperrt ist. */
    public static String message() {
        return "Teizen ist gesperrt: die Key-Mod (teizen-key.jar) fehlt. Ohne sie geht keine Funktion.";
    }

    /** Fehler im Chat (höchstens einmal pro Sekunde, damit es nicht spammt). */
    public static void complain() {
        long now = System.currentTimeMillis();
        if (now - lastComplain < 1000) return;
        lastComplain = now;
        Chat.error(message());
    }

    /** Menü öffnen: gesperrt -> Fehler-Bildschirm statt Menü. */
    public static net.minecraft.client.gui.screens.Screen menu(java.util.function.Supplier<net.minecraft.client.gui.screens.Screen> open) {
        return ok() ? open.get() : new dev.teizen.client.gui.LockedScreen(null);
    }

    private static boolean checkKey() {
        try {
            Optional<ModContainer> c = FabricLoader.getInstance().getModContainer(KEY_MOD);
            if (c.isEmpty()) return false;
            Optional<Path> p = c.get().findPath(KEY_FILE);
            if (p.isEmpty()) return false;
            String key = Files.readString(p.get(), StandardCharsets.UTF_8).trim();
            return sha256(key).equals(KEY_HASH);
        } catch (Throwable t) {
            return false;
        }
    }

    static String sha256(String s) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
    }

    private static boolean readRequired() {
        try {
            Optional<ModContainer> c = FabricLoader.getInstance().getModContainer("teizenclient");
            Optional<Path> p = c.flatMap(m -> m.findPath("teizenclient-build.properties"));
            if (p.isEmpty()) return false;
            Properties props = new Properties();
            try (InputStream in = Files.newInputStream(p.get())) {
                props.load(in);
            }
            return Boolean.parseBoolean(props.getProperty("key_required", "false").trim());
        } catch (Throwable t) {
            return false;
        }
    }
}
