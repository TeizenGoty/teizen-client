package dev.teizen.client.util;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Block-Namen <-> Blöcke. */
public final class Ids {
    private Ids() {}

    public static String id(Block b) {
        return BuiltInRegistries.BLOCK.getKey(b).toString();
    }

    public static String path(Block b) {
        return BuiltInRegistries.BLOCK.getKey(b).getPath();
    }

    /** "diamond" -> alle *_diamond_ore Varianten, "minecraft:stone" -> stone. */
    public static Set<Block> resolve(String query) {
        Set<Block> out = new HashSet<>();
        String q = query.toLowerCase(Locale.ROOT).trim();
        if (q.isEmpty()) return out;
        Identifier exact = Identifier.tryParse(q.contains(":") ? q : "minecraft:" + q);
        if (exact != null && BuiltInRegistries.BLOCK.containsKey(exact)) {
            out.add(BuiltInRegistries.BLOCK.getValue(exact));
            if (!q.endsWith("_ore")) {
                // "iron" passt auch zu iron_ore + deepslate_iron_ore
                addOres(out, q.replace("minecraft:", ""));
            }
            return out;
        }
        addOres(out, q.replace("minecraft:", ""));
        if (out.isEmpty()) {
            for (Block b : BuiltInRegistries.BLOCK) {
                if (path(b).contains(q)) out.add(b);
            }
        }
        return out;
    }

    private static void addOres(Set<Block> out, String name) {
        for (Block b : BuiltInRegistries.BLOCK) {
            String p = path(b);
            if ((p.endsWith("_ore") && p.contains(name)) || (name.equals("debris") && p.equals("ancient_debris"))
                    || (name.equals("netherite") && p.equals("ancient_debris"))) {
                out.add(b);
            }
        }
    }

    public static boolean isOre(BlockState s) {
        String p = path(s.getBlock());
        return p.endsWith("_ore") || p.equals("ancient_debris");
    }

    /** Kurzer deutscher Name für ein Erz (für Pfeile und Linien). */
    public static String oreLabel(Block b) {
        String p = path(b);
        if (p.contains("diamond")) return "Diamant";
        if (p.contains("emerald")) return "Smaragd";
        if (p.contains("gold")) return "Gold";
        if (p.contains("iron")) return "Eisen";
        if (p.contains("redstone")) return "Redstone";
        if (p.contains("lapis")) return "Lapis";
        if (p.contains("coal")) return "Kohle";
        if (p.contains("copper")) return "Kupfer";
        if (p.contains("quartz")) return "Quarz";
        if (p.contains("debris")) return "Antiker Schrott";
        return b.getName().getString();
    }

    /** Farbe für ein Erz (für X-Ray). */
    public static int oreColor(Block b) {
        String p = path(b);
        if (p.contains("diamond")) return 0xFF33EBFF;
        if (p.contains("emerald")) return 0xFF22FF55;
        if (p.contains("gold")) return 0xFFFFD21F;
        if (p.contains("iron")) return 0xFFE8B998;
        if (p.contains("redstone")) return 0xFFFF2A2A;
        if (p.contains("lapis")) return 0xFF2A55FF;
        if (p.contains("coal")) return 0xFF505050;
        if (p.contains("copper")) return 0xFFE07A3C;
        if (p.contains("quartz")) return 0xFFF2EEE6;
        if (p.contains("debris")) return 0xFFB05A3C;
        return 0xFFC08CFF;
    }
}
