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
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return out;
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

    public static void openFolder() {
        try {
            Browser.open(mc.getResourcePackDirectory().toUri().toString());
        } catch (Exception e) {
            Chat.error("Ordner ließ sich nicht öffnen: " + mc.getResourcePackDirectory());
        }
    }
}
