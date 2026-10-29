package dev.teizen.client.core;

import com.mojang.blaze3d.platform.InputConstants;
import dev.teizen.client.TeizenClient;
import dev.teizen.client.gui.ClickGui;
import dev.teizen.client.gui.Overview;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import dev.teizen.client.util.Chat;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import dev.teizen.client.compat.K;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * Tasten in Minecraft unter Optionen → Steuerung → Tastenbelegung, Kategorie "Teizen Client":
 * Menü, Übersicht und jede einzelne Funktion.
 *
 * Wird dort eine Taste geändert, übernimmt der Client sie (und umgekehrt: ///bind, Menü und Mod Menu
 * ändern auch die Taste in den Minecraft-Einstellungen).
 */
public final class KeyBinds {
    public static final int OVERVIEW_DEFAULT = K.O;

    private record Link(KeyMapping mapping, IntSupplier get, IntConsumer set, Runnable onMouseClick) {}

    private static final List<Link> links = new ArrayList<>();
    private static final Map<KeyMapping, Integer> lastBound = new LinkedHashMap<>();
    private static final Map<KeyMapping, Integer> lastValue = new LinkedHashMap<>();
    private static KeyMapping menu, overview, elytra;

    /** Taste für ElytraReset (Elytra zu/auf), Standard: Strg links. */
    public static KeyMapping elytra() { return elytra; }

    private KeyBinds() {}

    public static KeyMapping overview() { return overview; }
    public static KeyMapping menu() { return menu; }

    /** In onInitializeClient aufrufen (nach Config.load, vor dem Laden der Minecraft-Optionen). */
    public static void register() {
        KeyMapping.Category cat = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("teizenclient", "main"));

        menu = add(new KeyMapping("key.teizenclient.menu", dev.teizen.client.compat.V.KEYBOARD, raw(TeizenClient.menuKey), cat),
                () -> TeizenClient.menuKey, v -> TeizenClient.menuKey = v,
                () -> { Minecraft mc = Minecraft.getInstance(); if (mc.player != null && mc.gui.screen() == null) mc.gui.setScreen(Lock.menu(ClickGui::new)); });

        // Übersicht: hat keinen eigenen Wert im Client – die Minecraft-Taste ist der Wert
        overview = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.teizenclient.overview", dev.teizen.client.compat.V.KEYBOARD, OVERVIEW_DEFAULT, cat));

        elytra = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.teizenclient.elytrareset", dev.teizen.client.compat.V.KEYBOARD, K.LEFT_CONTROL, cat));

        for (Module m : TeizenClient.modules().all()) {
            String id = "key.teizenclient.module." + m.name.toLowerCase(Locale.ROOT);
            add(new KeyMapping(id, dev.teizen.client.compat.V.KEYBOARD, raw(m.key), cat),
                    () -> m.key, v -> m.key = v,
                    () -> { Minecraft mc = Minecraft.getInstance(); if (mc.player != null && mc.gui.screen() == null) { m.toggle(); Config.save(); } });
        }
    }

    /** "keine Taste" (-1) als Minecraft-Wert (26.3: SDL kennt kein -1, sonst Absturz). */
    private static int raw(int v) { return v >= 0 ? v : InputConstants.UNKNOWN.getValue(); }

    private static KeyMapping add(KeyMapping k, IntSupplier get, IntConsumer set, Runnable mouse) {
        KeyMapping reg = KeyMappingHelper.registerKeyMapping(k);
        links.add(new Link(reg, get, set, mouse));
        int v = get.getAsInt();
        lastBound.put(reg, v);
        lastValue.put(reg, v);
        return reg;
    }

    /** Code einer Belegung: Tastatur = GLFW-Code, Maus = -1000 - Knopf, keine = -1. */
    private static int encode(InputConstants.Key key) {
        if (key == null || key.getValue() < 0 || key.equals(InputConstants.UNKNOWN)) return -1;
        if (key.getType() == InputConstants.Type.MOUSE) return -1000 - key.getValue();
        return key.getValue();
    }

    private static int bound(KeyMapping k) {
        return encode(KeyMappingHelper.getBoundKeyOf(k));
    }

    /** Jeden Tick aufrufen: Tasten abgleichen, Übersicht öffnen, Maus-Belegungen ausführen. */
    private static boolean migrated;

    private static boolean conflictsChecked;
    /** Für den Selbsttest: welche Funktionen ihre Taste wegen Doppelbelegung verloren haben. */
    public static final List<String> removedConflicts = new java.util.ArrayList<>();

    /**
     * Teizen-Tasten, die auf wichtigen Spieltasten liegen (Laufen, Springen, Klicken, Inventar ...), entfernen –
     * sonst schaltet man die Funktion beim normalen Spielen ständig an und aus (alte Configs / options.txt).
     */
    private static boolean fixConflicts(Minecraft mc) {
        KeyMapping[] vital = {mc.options.keyUp, mc.options.keyLeft, mc.options.keyDown, mc.options.keyRight, mc.options.keyJump,
                mc.options.keyShift, mc.options.keySprint, mc.options.keyInventory, mc.options.keyAttack, mc.options.keyUse,
                mc.options.keyChat, mc.options.keyCommand, mc.options.keyDrop};
        java.util.Map<Integer, String> taken = new java.util.HashMap<>();
        for (KeyMapping v : vital) { int b = bound(v); if (b != -1) taken.put(b, net.minecraft.client.resources.language.I18n.get(v.getName())); }
        for (KeyMapping v : mc.options.keyHotbarSlots) { int b = bound(v); if (b != -1) taken.put(b, net.minecraft.client.resources.language.I18n.get(v.getName())); }
        boolean changed = false;
        for (Link l : links) {
            if (!l.mapping.getName().startsWith("key.teizenclient.module.")) continue;
            int b = bound(l.mapping);
            if (b == -1 || !taken.containsKey(b)) continue;
            String mod = l.mapping.getName().substring("key.teizenclient.module.".length());
            l.mapping.setKey(InputConstants.UNKNOWN);
            l.set.accept(-1);
            lastBound.put(l.mapping, -1);
            lastValue.put(l.mapping, -1);
            removedConflicts.add(mod);
            Chat.info("Taste von §f" + mod + "§7 entfernt – sie lag auf §f" + taken.get(b) + "§7 und hat die Funktion beim Spielen ständig an/aus geschaltet. Neu setzen: §f///bind " + mod + " <Taste>");
            changed = true;
        }
        return changed;
    }

    /** Selbsttest: Doppelbelegungen beim nächsten Tick nochmal prüfen. */
    public static void recheck() { conflictsChecked = false; }

    public static void tick(Minecraft mc) {
        if (menu == null) return;
        boolean reset = false, save = false;
        if (!conflictsChecked && mc.player != null && mc.options != null) {
            conflictsChecked = true;
            if (fixConflicts(mc)) { reset = true; save = true; }
        }
        if (!migrated) {
            // neue Standard-Tasten (z.B. AutoCrystal = J) auch dann setzen, wenn in options.txt noch "keine Taste" steht
            migrated = true;
            for (Link l : links) {
                String n = l.mapping.getName();
                String mod = n.startsWith("key.teizenclient.module.") ? n.substring("key.teizenclient.module.".length()) : "";
                int v = l.get.getAsInt();
                if (Config.keepDefaultKey.contains(mod) && bound(l.mapping) < 0 && v >= 0) {
                    l.mapping.setKey(dev.teizen.client.compat.V.KEYBOARD.getOrCreate(v));
                    lastBound.put(l.mapping, v);
                    lastValue.put(l.mapping, v);
                    reset = true;
                }
            }
        }
        for (Link l : links) {
            KeyMapping k = l.mapping;
            int b = bound(k);
            int v = l.get.getAsInt();
            if (b != lastBound.get(k)) {
                // in Minecraft-Steuerung geändert -> übernehmen
                int nv = b >= 0 ? b : -1; // Maus-Tasten macht die Minecraft-Taste selbst
                l.set.accept(nv);
                lastBound.put(k, b);
                lastValue.put(k, nv);
                save = true;
            } else if (v != lastValue.get(k)) {
                // im Client geändert (///bind, Menü, Mod Menu) -> in Minecraft eintragen
                k.setKey(v >= 0 ? dev.teizen.client.compat.V.KEYBOARD.getOrCreate(v) : InputConstants.UNKNOWN);
                lastBound.put(k, v);
                lastValue.put(k, v);
                reset = true;
            }
            // Klicks abholen; bei Maus-Belegung hier ausführen (Tastatur macht TeizenClient.handleKeys)
            boolean mouse = b <= -1000;
            while (k.consumeClick()) if (mouse) l.onMouseClick.run();
        }
        if (reset) {
            KeyMapping.resetMapping();
            try { mc.options.save(); } catch (Throwable ignored) {}
        }
        if (save) Config.save();

        while (overview.consumeClick()) {
            if (mc.player != null && mc.gui.screen() == null) mc.gui.setScreen(Lock.menu(Overview::new));
        }
    }

    /** Name der Übersichts-Taste für Anzeigen. */
    public static String overviewName() {
        if (overview == null) return "O";
        return overview.getTranslatedKeyMessage().getString();
    }
}
