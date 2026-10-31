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
        if (!inGame()) return;
        BeaconMenu menu = null;
        if (mc.player.containerMenu instanceof BeaconMenu bm) menu = bm;
        else if (mc.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> cs && cs.getMenu() instanceof BeaconMenu bm) menu = bm;
        if (menu == null) { lastMenu = -1; return; }
        if (dev.teizen.client.SelfTest.ACTIVE && mc.player.tickCount % 20 == 0)
            dev.teizen.client.TeizenClient.LOG.info("BetterBeacons: Menü " + menu.containerId + " bezahlt=" + menu.hasPayment() + " Slots=" + menu.slots.size() + " Slot0=" + menu.getSlot(0).getItem());
        if (menu.containerId == lastMenu) return;
        if (!menu.hasPayment()) {
            // Bezahl-Item selbst reinlegen (Eisen, Gold, Smaragd, Diamant oder Netherit)
            for (int i = 1; i < menu.slots.size(); i++) {
                var st = menu.getSlot(i).getItem();
                if (payment(st)) {
                    // Shift-Klick geht nur bei genau 1 Item -> Stapel nehmen, 1 reinlegen (Rechtsklick), Rest zurück
                    var PU = net.minecraft.world.inventory.ContainerInput.PICKUP;
                    mc.gameMode.handleContainerInput(menu.containerId, i, 0, PU, mc.player);
                    mc.gameMode.handleContainerInput(menu.containerId, 0, 1, PU, mc.player);
                    mc.gameMode.handleContainerInput(menu.containerId, i, 0, PU, mc.player);
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
