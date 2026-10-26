package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.util.Loot;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.ArrayList;
import java.util.List;

/**
 * Leert geöffnete Kisten automatisch in dein Inventar – standardmäßig alles sofort im selben Moment.
 * Die wertvollsten Sachen kommen zuerst (wichtig, wenn das Inventar voll wird), Müll aus der Liste "nie" bleibt liegen.
 * Shulker aus der Kiste werden danach gleich ausgeräumt (siehe {@link ShulkerUnpacker}).
 */
public final class ChestStealer extends Module {
    private final Setting delay = add(Setting.number("pause", "Ticks zwischen zwei Items (0 = alles sofort auf einmal)", 0, 0, 10));
    private final Setting close = add(Setting.bool("schliessen", "Kiste danach schließen", true));
    public final Setting sort = add(Setting.bool("sortieren", "Beste Sachen zuerst nehmen (Totem, Netherite, Diamanten …)", true));
    public final Setting filter = add(Setting.bool("filter", "Sachen aus der Liste 'nie' liegen lassen", true));
    public final Setting never = add(Setting.text("nie", "Diese Items nie nehmen (Komma-Liste, * = beliebig, z.B. *_seeds)", Loot.DEFAULT_NEVER));
    public final Setting first = add(Setting.text("zuerst", "Diese Items noch vor allem anderen nehmen (Komma-Liste)", ""));
    public final Setting shulker = add(Setting.bool("shulker", "Shulker aus Kisten gleich ausräumen (kurz hinstellen, gute Sachen nehmen, wieder einpacken)", true));
    private final Setting unpackNow = add(Setting.action("inventar", "Alle Shulker im Inventar jetzt ausräumen (auch ///shulker)", "Jetzt ausräumen",
            ShulkerUnpacker::startInventory));
    private int wait;
    public int taken;
    /** Welche Kiste gerade offen ist (-1 = keine) und wo vorher schon Shulker lagen. */
    private int menuId = -1;
    private final boolean[] shulkerBefore = new boolean[36];

    public ChestStealer() {
        super("ChestStealer", Category.SONSTIGES, "Räumt Kisten automatisch aus – beste Sachen zuerst, Müll bleibt liegen.");
    }

    /** Aktuelle Einstellungen für Rangliste und Liste "nie" (auch für ///shulker, wenn ChestStealer aus ist). */
    public Loot.Prefs prefs() {
        return new Loot.Prefs(first.selected(), never.selected(), sort.on(), filter.on());
    }

    @Override
    protected void onDisable() {
        menuId = -1;
    }

    @Override
    public void onTick() {
        if (!inGame()) return;
        // diesen Shulker räumt gerade der Shulker-Helfer selbst aus
        if (ShulkerUnpacker.ownsScreen()) return;
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> scr) || scr instanceof InventoryScreen) {
            if (menuId != -1) {
                menuId = -1;
                handOverShulkers();
            }
            return;
        }
        AbstractContainerMenu menu = mc.player.containerMenu;
        int containerSlots = menu.slots.size() - 36;
        if (containerSlots <= 0) return;
        if (menu.containerId != menuId) {
            menuId = menu.containerId;
            wait = 0;
            for (int i = 0; i < 36; i++) shulkerBefore[i] = Loot.isShulker(mc.player.getInventory().getItem(i));
        }
        if (wait-- > 0) return;
        Loot.Prefs p = prefs();
        if (delay.integer() > 0) {
            // ein Stapel pro Pause: immer der wertvollste, der noch da ist
            int moved = Loot.steal(menu, p, 1, 0);
            taken += moved;
            if (moved > 0) {
                wait = delay.integer();
                return;
            }
            // nichts mehr zu holen (oder Inventar voll) -> schließen
            if (close.on()) mc.player.closeContainer();
            return;
        }
        taken += Loot.steal(menu, p, 0, 0);
        if (close.on()) mc.player.closeContainer();
    }

    /** Shulker, die eben aus der Kiste kamen, an den Shulker-Helfer geben. */
    private void handOverShulkers() {
        if (!shulker.on() || mc.player == null) return;
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < 36; i++) {
            if (!shulkerBefore[i] && Loot.isShulker(mc.player.getInventory().getItem(i))
                    && Loot.hasWanted(mc.player.getInventory().getItem(i), prefs())) slots.add(i);
        }
        ShulkerUnpacker.queue(slots);
    }
}
