package dev.teizen.client.core;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.teizen.client.TeizenClient;
import dev.teizen.client.modules.XRay;
import dev.teizen.client.util.Keys;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Befehls-Vorschläge im Chat wie bei normalen Minecraft-Befehlen:
 * Wer "///" tippt, sieht alle Teizen-Befehle; danach passende Vorschläge (Tab übernimmt).
 *
 * Minecraft sieht "///xray" als Befehl "//xray". Deshalb wird für jeden Befehl ein
 * Client-Befehl "//xray" mit einem freien Text-Argument angemeldet. Ausgeführt wird
 * trotzdem alles über {@link Commands#handle(String)} – das Verhalten bleibt gleich.
 */
public final class CommandSuggestions {
    private static final Minecraft mc = Minecraft.getInstance();

    private static final List<String> ORES = List.of("diamond", "emerald", "gold", "iron", "redstone", "lapis",
            "copper", "coal", "quartz", "ancient_debris", "nether_gold");
    private static final List<String> FIND_BLOCKS = List.of("diamond", "ancient_debris", "emerald", "gold", "iron",
            "spawner", "chest", "shulker_box", "beacon", "end_portal_frame", "budding_amethyst", "obsidian");
    private static final List<String> STRUCTURES = List.of("village", "stronghold", "ancient_city", "fortress",
            "bastion_remnant", "monument", "mansion", "trial_chambers", "desert_pyramid", "jungle_pyramid",
            "pillager_outpost", "ruined_portal", "shipwreck", "buried_treasure", "mineshaft", "igloo",
            "swamp_hut", "end_city", "trail_ruins", "ocean_ruin");
    private static final List<String> FALLBACK_BIOMES = List.of("plains", "desert", "forest", "jungle", "taiga",
            "savanna", "badlands", "swamp", "mushroom_fields", "cherry_grove", "snowy_plains", "ocean", "deep_dark");

    private CommandSuggestions() {}

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> registerAll(dispatcher));
    }

    // ------------------------------------------------------------------ Anmeldung

    private static void registerAll(CommandDispatcher<FabricClientCommandSource> d) {
        simple(d, "help");
        simple(d, "list");
        simple(d, "gui");
        simple(d, "save");
        simple(d, "panic");

        withArgs(d, "toggle", t -> t.size() == 0 ? moduleNames() : List.of());
        withArgs(d, "info", t -> t.size() == 0 ? moduleNames() : List.of());
        withArgs(d, "set", t -> switch (t.size()) {
            case 0 -> moduleNames();
            case 1 -> settingNames(t.get(0));
            case 2 -> settingValues(t.get(0), t.get(1));
            default -> List.of();
        });
        withArgs(d, "bind", t -> switch (t.size()) {
            case 0 -> moduleNames();
            case 1 -> keyNames(true);
            default -> List.of();
        });
        withArgs(d, "menutaste", t -> t.size() == 0 ? keyNames(false) : List.of());
        withArgs(d, "reach", t -> switch (t.size()) {
            case 0 -> List.of("3", "4.5", "6", "8");
            case 1 -> List.of("4.5", "7", "10");
            default -> List.of();
        });
        withArgs(d, "mine", t -> {
            List<String> out = new ArrayList<>();
            if (t.isEmpty()) out.add("stop");
            for (String o : ORES) if (!t.contains(o)) out.add(o);
            return out;
        });
        withArgs(d, "xray", t -> {
            if (t.isEmpty()) return List.of("nur", "alle", "lava", "wasser", "hoehlen", "add", "remove");
            String first = t.get(0).toLowerCase(Locale.ROOT);
            if (first.equals("nur")) {
                List<String> out = new ArrayList<>();
                for (String o : ORES) if (!t.contains(o)) out.add(o);
                return out;
            }
            if (t.size() == 1 && (first.equals("lava") || first.equals("wasser") || first.equals("hoehlen"))) return List.of("an", "aus");
            if (t.size() == 1) return first.equals("remove") ? xrayOres() : first.equals("add") ? ORES : List.of();
            return List.of();
        });
        withArgs(d, "fav", t -> t.size() == 0 ? moduleNames() : List.of());
        withArgs(d, "velocity", t -> switch (t.size()) {
            case 0 -> List.of("0", "50", "100");
            case 1 -> List.of("0", "50", "100");
            default -> List.of();
        });
        withArgs(d, "structurefinder", t -> {
            List<String> out = new ArrayList<>();
            if (t.isEmpty()) out.add("aus");
            for (String s : Locator.names()) if (!t.contains(s)) out.add(s);
            return out;
        });
        withArgs(d, "autobuild", t -> switch (t.size()) {
            case 0 -> List.of("laden", "speed", "range");
            case 1 -> switch (t.get(0).toLowerCase(Locale.ROOT)) {
                case "laden", "load" -> schematicFiles();
                case "speed" -> List.of("1", "2", "3", "5", "10");
                case "range" -> List.of("2", "3", "4.5", "6");
                default -> List.of();
            };
            default -> List.of();
        });
        withArgs(d, "find", t -> switch (t.size()) {
            case 0 -> List.of("block", "chest", "biome", "structure", "clear");
            case 1 -> switch (t.get(0).toLowerCase(Locale.ROOT)) {
                case "block" -> FIND_BLOCKS;
                case "chest" -> List.of("8", "16", "32");
                case "biome" -> biomes();
                case "structure" -> Locator.names();
                default -> List.of();
            };
            case 2 -> t.get(0).equalsIgnoreCase("block") ? List.of("8", "16", "32") : List.of();
            default -> List.of();
        });
        withArgs(d, "friend", t -> switch (t.size()) {
            case 0 -> List.of("add", "remove");
            case 1 -> t.get(0).equalsIgnoreCase("remove") ? new ArrayList<>(Friends.NAMES)
                    : t.get(0).equalsIgnoreCase("add") ? playerNames() : List.of();
            default -> List.of();
        });

        // ///killaura usw. = Modul umschalten; danach optional Einstellung + Wert
        Set<String> taken = new LinkedHashSet<>(List.of("help", "list", "gui", "save", "panic", "toggle", "info", "set",
                "bind", "menutaste", "reach", "mine", "xray", "autobuild", "find", "friend", "fav", "velocity", "structurefinder"));
        for (Module m : TeizenClient.modules().all()) {
            String name = m.name.toLowerCase(Locale.ROOT);
            if (!taken.add(name)) continue;
            withArgs(d, name, t -> switch (t.size()) {
                case 0 -> settingNames(name);
                case 1 -> settingValues(name, t.get(0));
                default -> List.of();
            });
        }
    }

    /** Befehl ohne Argumente. */
    private static void simple(CommandDispatcher<FabricClientCommandSource> d, String name) {
        d.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("//" + name).executes(c -> run(name, "")));
    }

    /** Befehl mit beliebigen Argumenten und Vorschlägen je nach bisherigem Text. */
    private static void withArgs(CommandDispatcher<FabricClientCommandSource> d, String name, Function<List<String>, List<String>> suggest) {
        LiteralArgumentBuilder<FabricClientCommandSource> root = LiteralArgumentBuilder.<FabricClientCommandSource>literal("//" + name)
                .executes(c -> run(name, ""))
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("argumente", StringArgumentType.greedyString())
                        .suggests((ctx, builder) -> suggest(builder, suggest))
                        .executes(c -> run(name, StringArgumentType.getString(c, "argumente"))));
        d.register(root);
    }

    private static int run(String name, String args) {
        Commands.handle(Commands.PREFIX + name + (args.isBlank() ? "" : " " + args));
        return 1;
    }

    // ------------------------------------------------------------------ Vorschläge

    private static CompletableFuture<Suggestions> suggest(SuggestionsBuilder builder, Function<List<String>, List<String>> suggest) {
        String remaining = builder.getRemaining();
        int lastSpace = remaining.lastIndexOf(' ');
        String current = remaining.substring(lastSpace + 1).toLowerCase(Locale.ROOT);
        List<String> before = new ArrayList<>();
        if (lastSpace >= 0) {
            for (String s : remaining.substring(0, lastSpace).trim().split("\\s+")) if (!s.isEmpty()) before.add(s);
        }
        SuggestionsBuilder b = builder.createOffset(builder.getStart() + lastSpace + 1);
        List<String> options;
        try {
            options = suggest.apply(before);
        } catch (Throwable t) {
            options = List.of();
        }
        for (String o : options) {
            if (o.toLowerCase(Locale.ROOT).startsWith(current)) b.suggest(o);
        }
        return b.buildFuture();
    }

    private static List<String> moduleNames() {
        List<String> out = new ArrayList<>();
        for (Module m : TeizenClient.modules().all()) out.add(m.name.toLowerCase(Locale.ROOT));
        return out;
    }

    private static List<String> settingNames(String module) {
        Module m = TeizenClient.modules().byName(module);
        return m == null ? List.of() : new ArrayList<>(m.settings.keySet());
    }

    private static List<String> settingValues(String module, String setting) {
        Module m = TeizenClient.modules().byName(module);
        if (m == null) return List.of();
        Setting s = m.setting(setting);
        if (s == null) return List.of();
        return switch (s.type) {
            case BOOL -> List.of("an", "aus");
            case CHOICE -> s.choices;
            case NUMBER -> {
                Set<String> v = new LinkedHashSet<>();
                v.add(s.display());
                v.add(fmt(s.min));
                v.add(fmt(s.max));
                yield new ArrayList<>(v);
            }
            case TEXT -> s.str() == null || s.str().isEmpty() ? List.of() : List.of(s.str());
        };
    }

    private static String fmt(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
    }

    private static List<String> keyNames(boolean withNone) {
        List<String> out = new ArrayList<>();
        if (withNone) out.add("none");
        out.addAll(Keys.names());
        return out;
    }

    private static List<String> xrayOres() {
        try {
            XRay x = TeizenClient.modules().get(XRay.class);
            List<String> out = new ArrayList<>();
            for (String p : x.ores.str().split(",")) if (!p.isBlank()) out.add(p.trim());
            return out;
        } catch (Throwable t) {
            return List.of();
        }
    }

    private static List<String> playerNames() {
        List<String> out = new ArrayList<>();
        var con = mc.getConnection();
        if (con == null) return out;
        for (var info : con.getOnlinePlayers()) {
            String n = info.getProfile().name();
            if (mc.player == null || !n.equalsIgnoreCase(mc.player.getGameProfile().name())) out.add(n);
        }
        return out;
    }

    private static List<String> biomes() {
        try {
            if (mc.level != null) {
                List<String> out = new ArrayList<>();
                mc.level.registryAccess().lookupOrThrow(Registries.BIOME).keySet()
                        .forEach(id -> out.add(id.getPath()));
                if (!out.isEmpty()) { out.sort(String::compareTo); return out; }
            }
        } catch (Throwable ignored) {}
        return FALLBACK_BIOMES;
    }

    private static List<String> schematicFiles() {
        if (!FabricLoader.getInstance().isModLoaded("litematica")) return List.of();
        try {
            return SchematicList.files();
        } catch (Throwable t) {
            return List.of();
        }
    }

    /** Eigene Klasse, damit Litematica nur geladen wird, wenn es installiert ist. */
    private static final class SchematicList {
        static List<String> files() throws java.io.IOException {
            java.nio.file.Path dir = dev.teizen.client.compat.LitematicaCompat.schematicsDir();
            List<String> out = new ArrayList<>();
            if (dir == null || !java.nio.file.Files.isDirectory(dir)) return out;
            try (var s = java.nio.file.Files.list(dir)) {
                s.map(p -> p.getFileName().toString())
                 .filter(n -> n.toLowerCase(Locale.ROOT).endsWith(".litematic"))
                 .filter(n -> !n.contains(" "))
                 .sorted()
                 .forEach(out::add);
            }
            return out;
        }
    }

    /** Nur für den Selbsttest: welche Vorschläge kommen für diesen Chat-Text? */
    public static List<String> debugSuggest(String chatText) {
        try {
            var con = mc.getConnection();
            if (con == null) return List.of("keine Verbindung");
            @SuppressWarnings("unchecked")
            CommandDispatcher<net.minecraft.commands.SharedSuggestionProvider> disp =
                    (CommandDispatcher<net.minecraft.commands.SharedSuggestionProvider>) (Object) con.getCommands();
            String cmd = chatText.startsWith("/") ? chatText.substring(1) : chatText;
            var parse = disp.parse(cmd, con.getSuggestionsProvider());
            Suggestions s = disp.getCompletionSuggestions(parse).get(2, java.util.concurrent.TimeUnit.SECONDS);
            List<String> out = new ArrayList<>();
            s.getList().forEach(x -> out.add(x.getText()));
            return out;
        } catch (Throwable t) {
            return List.of("FEHLER: " + t);
        }
    }
}
