package dev.teizen.client.util;

import java.util.Locale;

/** Adresse im Browser öffnen (geht in 26.2 und 26.3 gleich). */
public final class Browser {
    private Browser() {}

    public static void open(String url) throws Exception {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String[] cmd;
        if (os.contains("mac")) cmd = new String[]{"open", url};
        else if (os.contains("win")) cmd = new String[]{"rundll32", "url.dll,FileProtocolHandler", url};
        else cmd = new String[]{"xdg-open", url};
        new ProcessBuilder(cmd).start();
    }
}
