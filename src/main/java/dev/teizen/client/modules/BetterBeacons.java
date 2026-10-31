package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.util.Chat;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ServerboundSetBeaconPacket;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.inventory.BeaconMenu;

import java.util.Optional;

/**
 * BetterBeacons: jedes Leuchtfeuer-Effekt wählbar – auch Stärke/Regeneration schon bei einer kleinen Pyramide.
 * Effekte im Menü einstellen, Leuchtfeuer öffnen, Bezahl-Item (z.B. Eisenbarren) reinlegen – fertig.
 */
public final class BetterBeacons extends Module {
    private static final String[] NAMES = {"tempo", "eile", "resistenz", "sprungkraft", "staerke", "regeneration", "keiner"};
    private final Setting primary = add(Setting.choice("primaer", "Erster Effekt", "staerke", NAMES));
    private final Setting secondary = add(Setting.choice("sekundaer", "Zweiter Effekt (keiner = nur einer)", "regeneration", NAMES));
    private int lastMenu = -1;
    public int set;

    public BetterBeacons() {
        super("BetterBeacons", Category.SONSTIGES, "Jeden Leuchtfeuer-Effekt wählen – auch bei kleiner Pyramide.");
    }

    private static Optional<Holder<MobEffect>> effect(String n) {
        return Optional.ofNullable(switch (n) {
            case "tempo" -> MobEffects.SPEED;
            case "eile" -> MobEffects.HASTE;
            case "resistenz" -> MobEffects.RESISTANCE;
            case "sprungkraft" -> MobEffects.JUMP_BOOST;
            case "staerke" -> MobEffects.STRENGTH;
            case "regeneration" -> MobEffects.REGENERATION;
            default -> null;
        });
    }

    private static boolean payment(net.minecraft.world.item.ItemStack st) {
        if (st.isEmpty()) return false;
        String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem()).getPath();
        return id.equals("iron_ingot") || id.equals("gold_ingot") || id.equals("emerald") || id.equals("diamond") || id.equals("netherite_ingot");
    }

    @Override
    public void onTick() {
        if (!inGame() || !(mc.player.containerMenu instanceof BeaconMenu menu)) { lastMenu = -1; return; }
        if (menu.containerId == lastMenu) return;
        if (!menu.hasPayment()) {
            // Bezahl-Item selbst reinlegen (Eisen, Gold, Smaragd, Diamant oder Netherit)
            for (int i = 1; i < menu.slots.size(); i++) {
                var st = menu.getSlot(i).getItem();
                if (payment(st)) {
                    mc.gameMode.handleContainerInput(menu.containerId, i, 0, net.minecraft.world.inventory.ContainerInput.QUICK_MOVE, mc.player);
                    return;
                }
            }
            return;
        }
        var p = effect(primary.str());
        if (p.isEmpty()) return;
        mc.player.connection.send(new ServerboundSetBeaconPacket(p, effect(secondary.str())));
        lastMenu = menu.containerId;
        set++;
        Chat.ok("Leuchtfeuer: §f" + primary.str() + (effect(secondary.str()).isPresent() ? " §a+ §f" + secondary.str() : "") + "§a gesetzt.");
        mc.player.closeContainer();
    }
}
