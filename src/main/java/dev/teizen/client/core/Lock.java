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
 * <p>
 * Der Key ist an einen Spielernamen gebunden: teizen-key.txt enthält den Namen und eine Ed25519-Unterschrift.
 * Unterschreiben kann nur, wer den geheimen Schlüssel hat (keys/ im privaten Repo, nicht in der Mod).
 * Hier steckt nur der öffentliche Schlüssel zum Prüfen – damit kann man keine Keys fälschen.
 */
public final class Lock {
    public static final String KEY_MOD = "teizenkey";
    private static final String KEY_FILE = "teizen-key.txt";
    /** Öffentlicher Schlüssel (Ed25519, X.509) zum Prüfen der Key-Unterschrift. */
    private static final String PUBLIC_KEY = "MCowBQYDK2VwAyEAJEojqz0MlHClHFr06U7dyoTrnMtXCrhHSfj3AYz8xBo=";

    /** Ist das die Version, die die Key-Mod braucht? */
    public static final boolean REQUIRED = readRequired();

    /** Nur für den Selbsttest: so tun, als wäre es die Version mit Key bzw. als fehle die Key-Mod (null = echt). */
    public static Boolean testRequired, testKeyMissing;
    /** Nur für den Selbsttest: so tun, als wäre man mit diesem Namen eingeloggt (null = echt). */
    public static String testName;

    /** Für wen der gefundene Key ist (oder null, wenn keiner/kaputt). */
    public static String keyName;
    private static String checkedFor;

    private static Boolean keyOk;
    private static long lastComplain;

    private Lock() {}

    public static boolean required() { return testRequired != null ? testRequired : REQUIRED; }

    /** Liegt die passende Key-Mod im mods-Ordner? */
    public static boolean keyPresent() {
        if (Boolean.TRUE.equals(testKeyMissing)) return false;
        String me = myName();
        if (keyOk == null || !java.util.Objects.equals(checkedFor, me)) {
            keyOk = checkKey(me);
            checkedFor = me;
        }
        return keyOk;
    }

    /** Darf der Client benutzt werden? */
    public static boolean ok() { return !required() || keyPresent(); }

    /** Was im Menü und im Chat steht, wenn es gesperrt ist. */
    public static String message() {
        if (keyName != null && !keyName.equalsIgnoreCase(String.valueOf(myName())))
            return "Teizen ist gesperrt: der Key gehört zu " + keyName + ", du spielst als " + myName() + ". Jeder braucht seinen eigenen Key.";
        return "Teizen ist gesperrt: die Key-Mod (teizen-key-<Name>.jar) fehlt oder ist ungültig. Ohne sie geht keine Funktion.";
    }

    /** Name, mit dem man gerade in Minecraft eingeloggt ist. */
    public static String myName() {
        if (testName != null) return testName;
        try {
            var u = net.minecraft.client.Minecraft.getInstance().getUser();
            return u == null ? null : u.getName();
        } catch (Throwable t) {
            return null;
        }
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

    private static boolean checkKey(String me) {
        keyName = null;
        try {
            Optional<ModContainer> c = FabricLoader.getInstance().getModContainer(KEY_MOD);
            if (c.isEmpty()) return false;
            Optional<Path> p = c.get().findPath(KEY_FILE);
            if (p.isEmpty()) return false;
            String name = null, sig = null;
            for (String line : Files.readAllLines(p.get(), StandardCharsets.UTF_8)) {
                line = line.trim();
                if (line.startsWith("name=")) name = line.substring(5).trim();
                else if (line.startsWith("sig=")) sig = line.substring(4).trim();
            }
            if (name == null || sig == null || !verify(name, sig)) return false;
            keyName = name;
            return me != null && name.equalsIgnoreCase(me);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Ist die Unterschrift für diesen Namen echt? */
    static boolean verify(String name, String sigB64) {
        try {
            java.security.PublicKey pk = java.security.KeyFactory.getInstance("Ed25519")
                    .generatePublic(new java.security.spec.X509EncodedKeySpec(java.util.Base64.getDecoder().decode(PUBLIC_KEY)));
            java.security.Signature v = java.security.Signature.getInstance("Ed25519");
            v.initVerify(pk);
            v.update(("teizen-key-v2|" + name.toLowerCase(java.util.Locale.ROOT)).getBytes(StandardCharsets.UTF_8));
            return v.verify(java.util.Base64.getDecoder().decode(sigB64));
        } catch (Throwable t) {
            return false;
        }
    }

    /** Für den Selbsttest: Unterschrift prüfen. */
    public static boolean verifyForTest(String name, String sigB64) { return verify(name, sigB64); }

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
