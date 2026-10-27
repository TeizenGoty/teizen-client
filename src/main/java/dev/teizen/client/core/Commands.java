package dev.teizen.client.core;

import dev.teizen.client.TeizenClient;
import dev.teizen.client.gui.ClickGui;
import dev.teizen.client.modules.AutoBuild;
import dev.teizen.client.modules.AutoMine;
import dev.teizen.client.modules.Reach;
import dev.teizen.client.modules.XRay;
import dev.teizen.client.util.Chat;
import dev.teizen.client.util.Keys;
import net.minecraft.client.Minecraft;

import java.util.Arrays;
import java.util.Locale;

/** Alle Befehle beginnen mit ///  – sie werden nie an den Server geschickt. */
public final class Commands {
    public static final String PREFIX = "///";
    private static final Minecraft mc = Minecraft.getInstance();

    private Commands() {}

    /** @return true wenn die Nachricht ein Teizen-Befehl war (und nicht gesendet werden darf). */
    /** Wie oft ein Befehl wirklich ausgeführt wurde (für den Selbsttest). */
    public static int executed;
    private static String lastMessage;
    private static long lastTime;

    public static boolean handle(String message) {
        if (!message.startsWith(PREFIX)) return false;
        // Derselbe Befehl kann über zwei Wege ankommen (Chat-Abfangen + Befehls-Vorschläge) – nur einmal ausführen.
        long now = System.currentTimeMillis();
        if (message.equals(lastMessage) && now - lastTime < 150) return true;
        lastMessage = message;
        lastTime = now;
        executed++;
        String body = message.substring(PREFIX.length()).trim();
        if (!Lock.ok()) {
            // gesperrt (Key-Mod fehlt): kein Befehl geht, ///gui zeigt den Fehler im Menü
            String c = body.isEmpty() ? "" : body.split("\\s+")[0].toLowerCase(Locale.ROOT);
            if (c.equals("gui") || c.equals("menu") || c.equals("menü")) mc.execute(() -> mc.gui.setScreen(new dev.teizen.client.gui.LockedScreen(null)));
            else Lock.complain();
            return true;
        }
        try {
            run(body);
        } catch (Throwable t) {
            TeizenClient.LOG.error("Befehl fehlgeschlagen: " + body, t);
            Chat.error("Fehler: " + t.getMessage());
        }
        return true;
    }

    private static void run(String body) {
        if (body.isEmpty()) { help(); return; }
        String[] a = body.split("\\s+");
        String cmd = a[0].toLowerCase(Locale.ROOT);
        ModuleManager mm = TeizenClient.modules();

        switch (cmd) {
            case "help", "hilfe", "?" -> help();
            case "list", "liste" -> {
                StringBuilder sb = new StringBuilder("§7Funktionen: ");
                for (Module m : mm.all()) sb.append(m.isEnabled() ? "§a" : "§c").append(m.name).append("§7, ");
                Chat.info(sb.substring(0, sb.length() - 2));
            }
            case "gui", "menu", "menü" -> mc.execute(() -> mc.gui.setScreen(new ClickGui()));
            case "toggle", "t" -> {
                if (a.length < 2) { Chat.error("///toggle <Funktion>"); return; }
                Module m = need(mm, a[1]);
                if (m != null) m.toggle();
            }
            case "set" -> {
                if (a.length < 4) { Chat.error("///set <Funktion> <Einstellung> <Wert>"); return; }
                Module m = need(mm, a[1]);
                if (m == null) return;
                Setting s = m.setting(a[2]);
                if (s == null) { Chat.error(m.name + " hat keine Einstellung '" + a[2] + "'. Möglich: " + String.join(", ", m.settings.keySet())); return; }
                String err = s.parse(String.join(" ", Arrays.copyOfRange(a, 3, a.length)));
                if (err != null) Chat.error(err);
                else { Chat.ok(m.name + " " + s.name + " = " + s.display()); Config.save(); }
            }
            case "info", "settings" -> {
                if (a.length < 2) { Chat.error("///info <Funktion>"); return; }
                Module m = need(mm, a[1]);
                if (m == null) return;
                Chat.info("§f" + m.name + "§7: " + m.description + (m.key >= 0 ? " §8[Taste " + Keys.name(m.key) + "]" : ""));
                for (Setting s : m.settings.values()) Chat.raw("  §d" + s.name + " §7= §f" + s.display() + " §8– " + s.description);
            }
            case "menutaste", "menukey", "menütaste" -> {
                if (a.length < 2) { Chat.info("Menü-Taste: §f" + dev.teizen.client.gui.SettingsScreen.keyName(TeizenClient.menuKey) + "§7 – ändern mit §f///menutaste <Taste>§7 (z.B. m, f6, rshift)"); return; }
                int k = Keys.parse(a[1]);
                if (k == Keys.INVALID || k < 0) { Chat.error("Unbekannte Taste: " + a[1] + " (z.B. m, f6, rshift, rctrl, tab)"); return; }
                TeizenClient.menuKey = k;
                Chat.ok("Das Menü geht jetzt mit §f" + dev.teizen.client.gui.SettingsScreen.keyName(k) + "§a auf.");
                Config.save();
            }
            case "bind" -> {
                if (a.length < 3) { Chat.error("///bind <Funktion> <Taste>   z.B. ///bind killaura r   (///bind killaura none zum Löschen)"); return; }
                Module m = need(mm, a[1]);
                if (m == null) return;
                int k = Keys.parse(a[2]);
                if (k == Keys.INVALID) { Chat.error("Unbekannte Taste: " + a[2]); return; }
                m.key = k;
                Chat.ok(m.name + (k < 0 ? " hat keine Taste mehr" : " liegt jetzt auf " + Keys.name(k)));
                Config.save();
            }
            case "reach" -> {
                Reach r = mm.get(Reach.class);
                if (a.length >= 2) {
                    String err = r.fight.parse(a[1]);
                    if (err != null) { Chat.error(err); return; }
                    if (a.length >= 3) r.build.parse(a[2]);
                }
                if (!r.isEnabled()) r.setEnabled(true);
                Chat.ok("Reach an: Kampf " + r.fight.display() + ", Bauen " + r.build.display());
                Config.save();
            }
            case "mine" -> {
                AutoMine m = mm.get(AutoMine.class);
                if (a.length >= 2 && (a[1].equalsIgnoreCase("stop") || a[1].equalsIgnoreCase("aus"))) {
                    m.setEnabled(false);
                    Chat.info("AutoMine §caus");
                    return;
                }
                if (a.length >= 2) {
                    m.ores.setText(String.join(",", Arrays.copyOfRange(a, 1, a.length)));
                    if (m.isEnabled()) m.setEnabled(false);
                }
                m.setEnabled(true);
                Config.save();
            }
            case "xray" -> {
                XRay x = mm.get(XRay.class);
                String sub = a.length >= 2 ? a[1].toLowerCase(Locale.ROOT) : "";
                if (a.length >= 3 && (sub.equals("nur") || sub.equals("only"))) {
                    // ///xray nur diamond [gold ...]  -> nur diese Erze
                    x.ores.setText(String.join(",", Arrays.copyOfRange(a, 2, a.length)).replace(",,", ","));
                    if (!x.isEnabled()) x.setEnabled(true);
                    Chat.ok("X-Ray zeigt jetzt nur: §f" + x.ores.str());
                } else if (sub.equals("alle") || sub.equals("all") || sub.equals("reset")) {
                    x.ores.setText(String.join(",", XRay.ALL_ORES));
                    if (!x.isEnabled()) x.setEnabled(true);
                    Chat.ok("X-Ray zeigt alle Erze: §f" + x.ores.str());
                } else if (sub.equals("lava") || sub.equals("wasser") || sub.equals("water") || sub.equals("hoehlen") || sub.equals("höhlen") || sub.equals("caves")) {
                    Setting s = sub.equals("lava") ? x.lava : sub.startsWith("w") ? x.water : x.caves;
                    if (a.length >= 3) { String err = s.parse(a[2]); if (err != null) { Chat.error(err); return; } }
                    else s.setBool(!s.on());
                    if (s.on() && !x.isEnabled()) x.setEnabled(true);
                    Chat.ok("X-Ray " + s.name + ": " + (s.on() ? "§aan" : "§caus"));
                } else if (a.length >= 3 && (a[1].equals("add") || a[1].equals("dazu"))) {
                    x.ores.setText(x.ores.str() + "," + a[2]);
                    Chat.ok("X-Ray zeigt jetzt auch " + a[2]);
                } else if (a.length >= 3 && (a[1].equals("remove") || a[1].equals("weg"))) {
                    StringBuilder sb = new StringBuilder();
                    for (String p : x.ores.str().split(",")) if (!p.trim().equalsIgnoreCase(a[2])) sb.append(p.trim()).append(",");
                    x.ores.setText(sb.length() > 0 ? sb.substring(0, sb.length() - 1) : "");
                    Chat.ok("X-Ray zeigt " + a[2] + " nicht mehr");
                } else {
                    x.toggle();
                }
                Config.save();
            }
            case "autobuild", "build", "printer" -> {
                AutoBuild b = mm.get(AutoBuild.class);
                if (a.length >= 2 && (a[1].equalsIgnoreCase("laden") || a[1].equalsIgnoreCase("load"))) {
                    if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("litematica")) { Chat.error("Dafür brauchst du die Mod Litematica."); return; }
                    if (a.length < 3) { Chat.error("///autobuild laden <Dateiname>  (Datei aus dem schematics-Ordner)"); return; }
                    String file = String.join(" ", java.util.Arrays.copyOfRange(a, 2, a.length));
                    String err = dev.teizen.client.compat.LitematicaCompat.loadAndPlace(file, mc.player.blockPosition());
                    if (err != null) Chat.error(err);
                    else Chat.ok("Schematic §f" + file + "§a bei dir platziert. Starte mit §f///autobuild");
                    return;
                }
                if (a.length >= 3) {
                    String key = a[1].equals("speed") ? "tempo" : a[1].equals("range") ? "reichweite" : a[1];
                    Setting s = b.setting(key);
                    if (s == null) { Chat.error("///autobuild speed <1-10> | range <2-6>"); return; }
                    String err = s.parse(a[2]);
                    if (err != null) Chat.error(err); else Chat.ok("AutoBuild " + s.name + " = " + s.display());
                    Config.save();
                } else b.toggle();
            }
            case "find", "finde", "suche" -> find(a);
            case "cheststealer" -> chestStealer(mm, a, body);
            case "shulker", "shulkers" -> dev.teizen.client.modules.ShulkerUnpacker.startInventory();
            case "fav", "favorit", "favoriten" -> {
                if (a.length < 2) {
                    var favs = mm.favorites();
                    StringBuilder sb = new StringBuilder();
                    for (Module f : favs) sb.append(f.isEnabled() ? "§a" : "§c").append(f.name).append("§7, ");
                    Chat.info("Favoriten: " + (favs.isEmpty() ? "§fkeine §8(///fav <Funktion> zum Markieren)" : sb.substring(0, sb.length() - 2)));
                    return;
                }
                Module m = need(mm, a[1]);
                if (m == null) return;
                m.favorite = !m.favorite;
                Chat.ok(m.name + (m.favorite ? " ist jetzt §6★ Favorit§a (steht im Menü und in der Übersicht oben)" : " ist kein Favorit mehr"));
                Config.save();
            }
            case "velocity" -> {
                Module v = mm.byName("velocity");
                if (a.length == 1) { v.toggle(); Config.save(); return; }
                String err = v.setting("horizontal").parse(a[1]);
                if (err == null && a.length >= 3) err = v.setting("vertikal").parse(a[2]);
                else if (err == null) v.setting("vertikal").parse(a[1]);
                if (err != null) { Chat.error(err); return; }
                if (!v.isEnabled()) v.setEnabled(true);
                Chat.ok("Velocity an: zur Seite " + v.setting("horizontal").display() + "%, nach oben " + v.setting("vertikal").display() + "%");
                Config.save();
            }
            case "structurefinder", "strukturfinder", "struktur" -> {
                Module sf = mm.byName("structurefinder");
                if (a.length == 1) { sf.toggle(); Config.save(); return; }
                if (a[1].equalsIgnoreCase("aus") || a[1].equalsIgnoreCase("stop")) { sf.setEnabled(false); Chat.info("StructureFinder §caus"); return; }
                java.util.List<String> names = new java.util.ArrayList<>();
                for (String n : Arrays.copyOfRange(a, 1, a.length)) {
                    for (String part : n.split(",")) {
                        if (part.isBlank()) continue;
                        if (part.equalsIgnoreCase("alle") || part.equalsIgnoreCase("all")) { names.addAll(dev.teizen.client.core.Locator.names()); continue; }
                        String p = dev.teizen.client.core.Locator.primary(part);
                        if (!names.contains(p)) names.add(p);
                    }
                }
                sf.setting("strukturen").setText(String.join(",", names));
                if (sf.isEnabled()) sf.setEnabled(false);
                sf.setEnabled(true);
                Config.save();
            }
            case "friend", "freund" -> {
                if (a.length < 3) {
                    Chat.info("Freunde: §f" + (Friends.NAMES.isEmpty() ? "keine" : String.join(", ", Friends.NAMES)) + " §8(///friend add|remove <Name>)");
                    return;
                }
                if (a[1].equals("add") || a[1].equals("dazu")) { Friends.add(a[2]); Chat.ok(a[2] + " ist jetzt dein Freund (wird nicht angegriffen)"); }
                else if (a[1].equals("remove") || a[1].equals("weg")) { Friends.remove(a[2]); Chat.ok(a[2] + " ist kein Freund mehr"); }
                Config.save();
            }
            case "save", "speichern" -> { Config.save(); Chat.ok("Gespeichert."); }
            case "seed" -> {
                if (a.length >= 2) {
                    try {
                        long sd = Long.parseLong(a[1]);
                        Seed.set(sd);
                        dev.teizen.client.compat.SeedMapCompat.giveSeed(sd, true);
                        Chat.ok("Seed gespeichert: §f" + sd + "§a. ///seedmap öffnet die Karte.");
                    } catch (NumberFormatException e) {
                        Chat.error("///seed <Zahl>  – z.B. ///seed -4172144997902289642");
                    }
                    return;
                }
                Seed.fetch(sd -> {
                    if (sd == null) Chat.error("Seed unbekannt. " + Seed.whyUnknown());
                    else {
                        dev.teizen.client.compat.SeedMapCompat.giveSeed(sd, false);
                        Chat.ok("Seed: §f" + sd + "§a (///seedmap öffnet die Karte)");
                    }
                });
            }
            case "seedmap", "seedkarte", "karte" -> {
                if (a.length >= 2) {
                    Setting ms = mm.byName("structurefinder").setting("seedmap");
                    String err = ms.parse(a[1]);
                    if (err != null) { Chat.error(err); return; }
                    Config.save();
                }
                mm.get(dev.teizen.client.modules.StructureFinder.class).openSeedMap();
            }
            case "opcheats", "op" -> {
                boolean hide = a.length >= 2 ? (a[1].equalsIgnoreCase("aus") || a[1].equalsIgnoreCase("verstecken") || a[1].equalsIgnoreCase("weg"))
                        : !ModuleManager.hideOp;
                ModuleManager.hideOp = hide;
                Config.save();
                Chat.ok(hide ? "OP-Cheats sind im Menü versteckt (" + mm.hiddenCount() + "). Wieder zeigen: ///opcheats an"
                        : "OP-Cheats werden im Menü wieder gezeigt.");
            }
            case "hud" -> {
                if (a.length >= 2 && (a[1].equalsIgnoreCase("verschieben") || a[1].equalsIgnoreCase("bearbeiten") || a[1].equalsIgnoreCase("edit"))) {
                    mc.execute(() -> mc.gui.setScreen(new dev.teizen.client.gui.HudEditorScreen(null)));
                    return;
                }
                if (a.length >= 2 && (a[1].equalsIgnoreCase("reset") || a[1].equalsIgnoreCase("zuruecksetzen"))) {
                    dev.teizen.client.gui.HudLayout.resetAll(); Config.save(); Chat.ok("Alle HUD-Teile wieder an ihrem Standard-Platz."); return;
                }
                if (a.length < 2) { Chat.info("Liste oben rechts: Größe §f" + dev.teizen.client.gui.Hud.scale + "§7. Ändern: ///hud 0.5 (0 = aus, 1 = normal)"); return; }
                try {
                    float v = Float.parseFloat(a[1].replace(',', '.'));
                    if (a[1].equalsIgnoreCase("aus")) v = 0;
                    dev.teizen.client.gui.Hud.scale = Math.max(0f, Math.min(2f, v));
                    Config.save();
                    Chat.ok(dev.teizen.client.gui.Hud.scale == 0 ? "Liste oben rechts ausgeblendet." : "Liste oben rechts: Größe " + dev.teizen.client.gui.Hud.scale);
                } catch (NumberFormatException ex) {
                    if (a[1].equalsIgnoreCase("aus")) { dev.teizen.client.gui.Hud.scale = 0; Config.save(); Chat.ok("Liste oben rechts ausgeblendet."); }
                    else if (a[1].equalsIgnoreCase("an")) { dev.teizen.client.gui.Hud.scale = 0.6f; Config.save(); Chat.ok("Liste oben rechts wieder an."); }
                    else Chat.error("///hud <Zahl>, z.B. ///hud 0.5");
                }
            }
            case "profil", "profile", "profiles" -> {
                if (a.length < 2) { Profiles.list(); return; }
                String sub = a[1].toLowerCase(Locale.ROOT);
                switch (sub) {
                    case "speichern", "save", "neu" -> { if (a.length < 3) Chat.error("///profil speichern <Name>"); else Profiles.save(a[2]); }
                    case "loeschen", "löschen", "delete", "weg" -> { if (a.length < 3) Chat.error("///profil loeschen <Name>"); else Profiles.delete(a[2]); }
                    case "aus", "off" -> Profiles.off(true);
                    case "liste", "list" -> Profiles.list();
                    default -> Profiles.toggle(a[1]);
                }
            }
            case "hudedit", "hudeditor" -> mc.execute(() -> mc.gui.setScreen(new dev.teizen.client.gui.HudEditorScreen(null)));
            case "panic", "alleaus", "panik" -> {
                int n = dev.teizen.client.modules.PanicButton.panic();
                Chat.ok("Alle Funktionen aus (" + n + " ausgeschaltet).");
            }
            default -> {
                // ///killaura = einfach umschalten
                Module m = mm.byName(cmd);
                if (m != null) {
                    String quick = quickSetting(m);
                    Setting qs = quick == null ? null : m.setting(quick);
                    if (qs != null && (qs.type == Setting.Type.TEXT || qs.type == Setting.Type.MULTI) && a.length >= 2 && m.setting(a[1]) == null) {
                        // ///itemesp diamond totem  -> Text-Einstellung setzen und einschalten
                        // (Chat-Nachricht und Handels-Suche mit Leerzeichen, Listen mit Komma)
                        boolean spaces = m.name.equals("ChatSpammer") || m.name.equals("VillagerRoller");
                        String rest = body.substring(body.indexOf(' ') + 1).trim();
                        qs.setText(spaces ? rest : String.join(",", Arrays.copyOfRange(a, 1, a.length)));
                        if (m.isEnabled()) m.setEnabled(false);
                        m.setEnabled(true);
                        Chat.ok(m.name + " an: " + quick + " = " + qs.display());
                    } else if (a.length == 2 && quick != null) {
                        // ///fly 3  ///speed 0.5  ///highjump 5  -> Wert setzen und einschalten
                        String err = m.setting(quick).parse(a[1]);
                        if (err != null) {
                            // vielleicht eine Auswahl, z.B. ///moredurability unzerstoerbar
                            for (Setting c : m.settings.values()) {
                                if (c.type == Setting.Type.CHOICE && c.parse(a[1]) == null) {
                                    if (!m.isEnabled()) m.setEnabled(true);
                                    Chat.ok(m.name + " an: " + c.name + " = " + c.display());
                                    Config.save();
                                    return;
                                }
                            }
                            Chat.error(err);
                            return;
                        }
                        if (!m.isEnabled()) m.setEnabled(true);
                        Chat.ok(m.name + " an: " + quick + " = " + m.setting(quick).display());
                    } else if (a.length >= 3) run("set " + body);
                    else m.toggle();
                    Config.save();
                } else {
                    Chat.error("Unbekannter Befehl: " + cmd + " – ///help zeigt alle Befehle.");
                }
            }
        }
    }

    /** Einstellung, die man direkt mit ///<Funktion> <Wert> setzen kann. */
    private static String quickSetting(Module m) {
        return switch (m.name) {
            case "Fly", "Speed", "NoClip", "Freecam", "DolphinMode" -> "tempo";
            case "HighJump", "Step" -> "hoehe";
            case "KillAura", "AimAssist", "AutoCrystal", "AutoReplant" -> m.name.equals("AimAssist") ? "staerke" : "reichweite";
            case "God" -> "herzen";
            case "Nametags", "ArmorESP" -> "groesse";
            case "ItemESP" -> "filter";
            case "Tracers" -> "distanz";
            case "Nuker", "StorageESP", "PortalFinder" -> "radius";
            case "FastBreak" -> "faktor";
            case "FakeLag" -> "verzoegerung";
            case "AntiKnockback" -> "rest";
            case "AutoReconnect", "FastEat" -> "sekunden";
            case "AutoEat" -> "hunger";
            case "NoFall" -> "ab";
            case "ChatSpammer" -> "nachricht";
            case "VillagerRoller" -> "suche";
            case "CombatLog" -> "herzen";
            case "MoreDurability" -> "faktor";
            case "ProjectileDodge" -> "reichweite";
            case "HitMarker" -> "groesse";
            default -> null;
        };
    }

    /** ///cheststealer [nie|zuerst add|weg <Items>] [liste] [shulker an|aus] – sonst wie bisher (an/aus, Einstellung setzen). */
    private static void chestStealer(ModuleManager mm, String[] a, String body) {
        dev.teizen.client.modules.ChestStealer cs = mm.get(dev.teizen.client.modules.ChestStealer.class);
        String sub = a.length >= 2 ? a[1].toLowerCase(Locale.ROOT) : "";
        switch (sub) {
            case "nie", "never", "zuerst", "first" -> {
                Setting s = sub.equals("nie") || sub.equals("never") ? cs.never : cs.first;
                String what = s == cs.never ? "Nie nehmen" : "Zuerst nehmen";
                if (a.length < 3) {
                    Chat.info(what + ": §f" + (s.selected().isEmpty() ? "nichts" : String.join(", ", s.selected()))
                            + " §8(///cheststealer " + s.name + " add|weg <Item>)");
                    return;
                }
                String op = a[2].toLowerCase(Locale.ROOT);
                java.util.List<String> list = s.selected();
                java.util.List<String> items = new java.util.ArrayList<>();
                for (int i = 3; i < a.length; i++) for (String part : a[i].split(",")) if (!part.isBlank()) items.add(dev.teizen.client.util.Loot.clean(part));
                switch (op) {
                    case "add", "dazu", "+" -> {
                        if (items.isEmpty()) { Chat.error("///cheststealer " + s.name + " add <Item>  z.B. dirt oder *_seeds"); return; }
                        java.util.List<String> unknown = new java.util.ArrayList<>();
                        for (String it : items) {
                            if (!dev.teizen.client.util.Loot.known(it)) { unknown.add(it); continue; }
                            if (!list.contains(it)) list.add(it);
                        }
                        if (!unknown.isEmpty()) Chat.error("Unbekannte Items: " + String.join(", ", unknown) + " (englische Namen, z.B. cobblestone)");
                    }
                    case "weg", "remove", "-" -> {
                        if (items.isEmpty()) { Chat.error("///cheststealer " + s.name + " weg <Item>"); return; }
                        list.removeAll(items);
                    }
                    case "leer", "clear" -> list.clear();
                    case "standard", "reset" -> {
                        list.clear();
                        if (s == cs.never) list.addAll(java.util.List.of(dev.teizen.client.util.Loot.DEFAULT_NEVER.split(",")));
                    }
                    default -> { Chat.error("///cheststealer " + s.name + " add|weg|leer|standard <Item>"); return; }
                }
                s.setText(String.join(",", list));
                Chat.ok(what + ": §f" + (list.isEmpty() ? "nichts" : String.join(", ", list)));
                Config.save();
            }
            case "liste", "list" -> {
                Chat.info("§fChestStealer§7: " + (cs.isEnabled() ? "§aan" : "§caus") + "§7, beste Sachen zuerst: " + cs.sort.display()
                        + ", Shulker gleich ausräumen: " + cs.shulker.display());
                Chat.raw("  §dzuerst §7= §f" + (cs.first.selected().isEmpty() ? "nichts" : String.join(", ", cs.first.selected())));
                Chat.raw("  §dnie §7= §f" + (cs.never.selected().isEmpty() ? "nichts" : String.join(", ", cs.never.selected()))
                        + (cs.filter.on() ? "" : " §c(Filter aus)"));
                Chat.raw("  §7Rangliste: Totem, Elytra, verz. Goldapfel › Netherite › Shulker, Dreizack, Zauberbücher › Diamant, Goldapfel › Erfahrung, Vorlagen › Perlen, Raketen, Tränke › Smaragd, Obsidian, Bogen › Eisen, Gold, Essen › Holz, Kohle, Kupfer › Rest (verzaubert zählt mehr)");
            }
            case "shulker" -> {
                if (a.length >= 3) {
                    String err = cs.shulker.parse(a[2]);
                    if (err != null) { Chat.error(err); return; }
                } else cs.shulker.setBool(!cs.shulker.on());
                Chat.ok("Shulker aus Kisten gleich ausräumen: " + (cs.shulker.on() ? "§aan" : "§caus") + "§a  (///shulker räumt die im Inventar aus)");
                Config.save();
            }
            default -> {
                // wie bisher: ///cheststealer = an/aus, ///cheststealer pause 2 = Einstellung setzen
                if (a.length >= 3) run("set " + body);
                else { cs.toggle(); Config.save(); }
            }
        }
    }

    private static Module need(ModuleManager mm, String name) {
        Module m = mm.byName(name);
        if (m == null) Chat.error("Keine Funktion namens '" + name + "'. ///list zeigt alle.");
        return m;
    }

    private static int intArg(String[] a, int i, int def) {
        if (a.length <= i) return def;
        try { return Integer.parseInt(a[i]); } catch (NumberFormatException e) { return def; }
    }

    private static void find(String[] a) {
        if (a.length < 2) {
            Chat.info("///find block <Name> [Chunks] | chest [Chunks] | biome <Name> [Blöcke] | structure <Name> | clear");
            return;
        }
        switch (a[1].toLowerCase(Locale.ROOT)) {
            case "block", "blöcke", "erz", "ore" -> {
                if (a.length < 3) { Chat.error("///find block <Name> [Chunks]"); return; }
                Finder.findBlocks(a[2], Math.min(32, Math.max(1, intArg(a, 3, 8))));
            }
            case "chest", "chests", "kiste", "kisten", "storage" -> Finder.findStorage(Math.min(32, Math.max(1, intArg(a, 2, 8))));
            case "biome", "biom" -> {
                if (a.length < 3) { Chat.error("///find biome <Name> [Blöcke]"); return; }
                Finder.findBiome(a[2], Math.min(20000, Math.max(64, intArg(a, 3, 6400))));
            }
            case "structure", "struktur" -> {
                if (a.length < 3) { Chat.error("///find structure <Name>"); return; }
                Finder.findStructure(a[2]);
            }
            case "clear", "weg", "aus" -> { Finder.clear(); Chat.ok("Markierungen entfernt."); }
            default -> Finder.findBlocks(a[1], Math.min(32, Math.max(1, intArg(a, 2, 8))));
        }
    }

    private static void help() {
        Chat.raw("§5§l===== Teizen Client – Befehle =====");
        Chat.raw("§d///gui §7– Menü öffnen (oder Taste §f" + dev.teizen.client.gui.SettingsScreen.keyName(TeizenClient.menuKey) + "§7)");
        Chat.raw("§d///menutaste <Taste> §7– Taste fürs Menü ändern, z.B. §f///menutaste m §7(auch in Mod Menu)");
        Chat.raw("§d///<Funktion> §7– Funktion an/aus, z.B. §f///killaura");
        Chat.raw("§d///list §7– alle Funktionen   §d///info <Funktion> §7– Einstellungen");
        Chat.raw("§d///set <Funktion> <Einstellung> <Wert> §7– z.B. §f///set killaura reichweite 5");
        Chat.raw("§d///bind <Funktion> <Taste> §7– Taste festlegen, z.B. §f///bind fly f");
        Chat.raw("§d///reach <Kampf> [Bauen] §7– z.B. §f///reach 6 7 §7oder §f///reach 2");
        Chat.raw("§d///fly <Tempo> §7· §d///speed <Tempo> §7· §d///highjump <Blöcke> §7– z.B. §f///speed 0.5§7, §f///highjump 5");
        Chat.raw("§d///mine <Erze...> §7– Auto-Miner, z.B. §f///mine diamond iron §7| §f///mine stop");
        Chat.raw("§d///xray §7– an/aus, §f///xray nur diamond §7(nur Dias), §f///xray alle§7, §f///xray add/remove <Erz>");
        Chat.raw("§d///xray lava §7· §d///xray wasser §7· §d///xray hoehlen §7– zusätzlich anzeigen (an/aus)");
        Chat.raw("§d///fav <Funktion> §7– Favorit an/aus (steht oben) · §d///fav §7– alle Favoriten");
        Chat.raw("§d///structurefinder <Strukturen...> §7– z.B. §fvillage stronghold ancient_city§7, §falle §7(oder im Menü anhaken; Server: OP)");
        Chat.raw("§d///playerfinder §7– Pfeile zu allen Spielern (§f///set playerfinder stil rand§7 = am Bildschirmrand) · §d///xray §7zeigt Pfeile zu den Erzen");
        Chat.raw("§d///radar §7– alle Spieler auf Xaero's Minimap/World Map, auch weit weg (braucht Xaero's Minimap)");
        Chat.raw("§d///velocity <seitlich%> [hoch%] §7· §d///god <Herzen> §7· §d///aimassist <Stärke> §7· §d///nametags <Größe>");
        Chat.raw("§d///freecam <Tempo> §7(unbegrenzt weit; Grenze: §f///set freecam maxabstand 100§7) · §d///itemesp <Items...>");
        Chat.raw("§d///aimassist §7– zielt auch mit Bogen/Armbrust und schießt automatisch (§f///set aimassist autoschuss an§7)");
        Chat.raw("§d///chatspammer <Text> §7· §d///villagerroller mending §7· §d///moredurability 10 §7| §funzerstoerbar §7| §freparieren");
        Chat.raw("§7Menü/Übersicht: §ftippen = suchen (Lupe)§7, §fRechtsklick = Einstellungen direkt aufklappen§7, §fMausrad = scrollen§7, §fZahlen anklicken und eintippen");
        Chat.raw("§d///enchantpreview §7– Zaubertisch zeigt alle Verzauberungen (Server: braucht OP)");
        Chat.raw("§d///combatlog <Herzen> §7· §d///projectiledodge §7· §d///inventorywalk §7· §d///seeinvisible §7· §d///elytrareset §7· §d///hitmarker");
        Chat.raw("§d///cheststealer §7– Kisten ausräumen: beste Sachen zuerst, Müll (Dirt, Cobblestone …) bleibt liegen · §d///cheststealer liste");
        Chat.raw("§d///cheststealer nie add|weg <Item> §7· §d///cheststealer zuerst add|weg <Item> §7– z.B. §f///cheststealer nie add *_seeds");
        Chat.raw("§d///shulker §7– alle Shulker im Inventar ausräumen (kurz hinstellen, gute Sachen nehmen, wieder einpacken)");
        Chat.raw("§d///autobuild §7– Litematica-Schematic automatisch bauen (§f///autobuild speed 3§7)");
        Chat.raw("§d///autobuild laden <Datei> §7– Schematic aus dem schematics-Ordner genau bei dir platzieren");
        Chat.raw("§d///find block <Name> [Chunks] §7– z.B. §f///find block diamond 10");
        Chat.raw("§d///find chest [Chunks] §7| §d///find biome <Name> §7| §d///find structure <Name> §7| §d///find clear");
        Chat.raw("§d///friend add <Name> §7– wird nicht angegriffen");
        Chat.raw("§d///seedmap §7– Seed holen und Seed-Karte mit allen Strukturen öffnen (SeedMapper / Seed Atlas / Chunkbase) · §d///seed [Zahl]");
        Chat.raw("§d///opcheats §7– Cheats, die OP brauchen, im Menü verstecken/zeigen (auch Knopf oben rechts)");
        Chat.raw("§d///hud <Größe> §7– Liste oben rechts kleiner/größer (z.B. 0.5, 0 = aus, 1 = normal)");
        Chat.raw("§d///profil <Name> §7– Profil an/aus (PvP, Farmen, Bauen, Abbauen, Erkunden) · §d///profil speichern <Name> §7– jetzige Cheats als eigenes Profil");
        Chat.raw("§d///hud verschieben §7– HUD-Teile mit der Maus verschieben (auch Knopf in der Übersicht) · §d///hud reset");
        Chat.raw("§d///panic §7– alles aus   §d///save §7– speichern");
    }
}
