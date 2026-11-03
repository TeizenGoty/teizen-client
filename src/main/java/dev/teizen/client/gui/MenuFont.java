package dev.teizen.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

/**
 * Weiche Menü-Schrift (Inter, im Mod mitgeliefert, Lizenz: assets/teizenclient/licenses/inter-license.txt).
 * Solange {@link #active} an ist (nur während das Teizen-Menü zeichnet), schreiben Menü-Teile damit statt mit der
 * Minecraft-Pixelschrift. Klappt das Laden nicht, wird einfach die normale Schrift genommen.
 */
public final class MenuFont {
    private MenuFont() {}

    /** Gerade im Teizen-Menü am Zeichnen (dann die weiche Schrift nehmen). */
    public static boolean active;
    /** Ein/aus per ///menuschrift. */
    public static boolean enabled = true;

    private static Object regular, bold;
    private static boolean tried;

    /** FontDescription.Resource(id) per Reflection (Klassen-Form kann sich zwischen Versionen ändern). */
    private static Object description(String path) {
        try {
            Identifier id = Identifier.fromNamespaceAndPath("teizenclient", path);
            for (Class<?> c : net.minecraft.network.chat.FontDescription.class.getDeclaredClasses()) {
                if (!c.getSimpleName().equals("Resource")) continue;
                for (var k : c.getDeclaredConstructors())
                    if (k.getParameterCount() == 1 && k.getParameterTypes()[0] == Identifier.class) { k.setAccessible(true); return k.newInstance(id); }
            }
        } catch (Throwable t) {
            dev.teizen.client.TeizenClient.LOG.warn("Menü-Schrift nicht verfügbar: " + t);
        }
        return null;
    }

    private static void init() {
        if (tried) return;
        tried = true;
        regular = description("menu");
        bold = description("menu_bold");
    }

    private static boolean on() { return active && enabled; }

    /** Text als Component, im Menü mit weicher Schrift. "§l" am Anfang = fette Schrift. */
    public static Component comp(String s) {
        if (!on()) return Component.literal(s);
        init();
        Object fd = regular;
        if (s.startsWith("§l") && bold != null) { fd = bold; s = s.substring(2); }
        if (fd == null) return Component.literal(s);
        final Object f = fd;
        try {
            return Component.literal(s).withStyle(st -> st.withFont((net.minecraft.network.chat.FontDescription) f));
        } catch (Throwable t) {
            return Component.literal(s);
        }
    }

    public static void text(GuiGraphicsExtractor g, Font font, String s, int x, int y, int color, boolean shadow) {
        if (!on()) { g.text(font, s, x, y, color, shadow); return; }
        g.text(font, comp(s), x, y, color, shadow);
    }

    public static void centered(GuiGraphicsExtractor g, Font font, String s, int cx, int y, int color) {
        if (!on()) { g.centeredText(font, s, cx, y, color); return; }
        g.centeredText(font, comp(s), cx, y, color);
    }

    public static int width(Font font, String s) {
        if (!on()) return font.width(s);
        return font.width(comp(s));
    }

    /** Auf Breite kürzen (mit …). */
    public static String cut(Font font, String s, int w) {
        if (width(font, s) <= w) return s;
        String t = s;
        while (!t.isEmpty() && width(font, t + "…") > w) t = t.substring(0, t.length() - 1);
        return t + "…";
    }
}
