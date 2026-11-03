package dev.teizen.client.modules;

import dev.teizen.client.TeizenClient;
import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.SpawnerBlock;
import net.minecraft.world.level.block.TrialSpawnerBlock;
import net.minecraft.world.level.block.VaultBlock;

/**
 * SpawnerProtect: schützt Spawner (und optional Trial-Spawner/Tresore) vor dem Abbauen. Weder von Hand noch mit
 * AutoMine/Nuker/VeinMiner/AutoBuild wird so ein Block kaputt gemacht. Gut, damit ein wertvoller Spawner nicht aus
 * Versehen weg ist.
 */
public final class SpawnerProtect extends Module {
    private final Setting mob = add(Setting.bool("spawner", "Monster-Spawner schützen", true));
    private final Setting trial = add(Setting.bool("trialspawner", "Trial-Spawner schützen", true));
    private final Setting vault = add(Setting.bool("tresor", "Tresore (Vault) schützen", true));

    public SpawnerProtect() {
        super("SpawnerProtect", Category.ABBAU, "Verhindert, dass Spawner aus Versehen abgebaut werden.");
    }

    /** Ist dieser Block gerade geschützt? (für Abbau-Stellen überall) */
    public static boolean protects(BlockPos pos) {
        if (TeizenClient.modules() == null) return false;
        SpawnerProtect m = TeizenClient.modules().get(SpawnerProtect.class);
        if (m == null || !m.isEnabled() || mc.level == null) return false;
        var b = mc.level.getBlockState(pos).getBlock();
        if (b instanceof SpawnerBlock) return m.mob.on();
        if (b instanceof TrialSpawnerBlock) return m.trial.on();
        if (b instanceof VaultBlock) return m.vault.on();
        return false;
    }
}
