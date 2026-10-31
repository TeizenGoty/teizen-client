package dev.teizen.client.modules;

import dev.teizen.client.TeizenClient;
import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import net.minecraft.core.BlockPos;

import java.util.Arrays;
import java.util.Locale;

/** NoInteract: bestimmte Blöcke beim Rechtsklick nicht öffnen/benutzen (z.B. damit du an Kisten/Werkbänken bauen kannst). */
public final class NoInteract extends Module {
    private final Setting blocks = add(Setting.text("bloecke", "Diese Blöcke nicht anklicken (Komma-Liste, * = Teil vom Namen)",
            "crafting_table,*chest,*barrel,furnace,*shulker_box,anvil,*bed,*door,*trapdoor,*button,lever,enchanting_table"));
    private final Setting sneak = add(Setting.bool("schleichen", "Beim Schleichen trotzdem öffnen", true));
    public int blocked;

    public NoInteract() {
        super("NoInteract", Category.BAUEN, "Kisten, Werkbänke usw. öffnen sich beim Rechtsklick nicht aus Versehen.");
    }

    private boolean listed(String id) {
        return Arrays.stream(blocks.str().toLowerCase(Locale.ROOT).split(",")).map(String::trim).filter(s -> !s.isEmpty())
                .anyMatch(s -> s.startsWith("*") ? id.endsWith(s.substring(1)) || id.contains(s.substring(1)) : id.equals(s));
    }

    /** Mixin (useItemOn): true = diesen Block nicht benutzen/öffnen. */
    public static boolean skip(BlockPos pos) {
        if (TeizenClient.modules() == null || mc.level == null) return false;
        NoInteract n = TeizenClient.modules().get(NoInteract.class);
        if (n == null || !n.isEnabled() || mc.player == null) return false;
        if (n.sneak.on() && mc.player.isShiftKeyDown()) return false;
        if (!mc.options.keyUse.isDown()) return false; // nur deine eigenen Klicks (AutoBuild & Co. dürfen weiter)
        String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(pos).getBlock()).getPath();
        if (!n.listed(id)) return false;
        n.blocked++;
        return true;
    }
}
