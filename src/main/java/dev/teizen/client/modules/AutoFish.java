package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.mixin.FishingHookAccessor;
import dev.teizen.client.util.Inv;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;

/** Angelt automatisch: zieht ein, sobald ein Fisch anbeißt, und wirft wieder aus. */
public final class AutoFish extends Module {
    private final Setting recast = add(Setting.number("pause", "Ticks bis zum erneuten Auswerfen", 15, 2, 100));
    private final Setting autoCast = add(Setting.bool("auswerfen", "Angel automatisch auswerfen", true));
    private final Setting takeRod = add(Setting.bool("angelnehmen", "Angel aus der Hotbar in die Hand nehmen", true));

    public int catches;
    private int wait;
    private int castCooldown;

    public AutoFish() {
        super("AutoFish", Category.SONSTIGES, "Angelt automatisch (einziehen + neu auswerfen).");
    }

    private static boolean isRod(ItemStack s) {
        return s.getItem() instanceof FishingRodItem;
    }

    private InteractionHand rodHand() {
        if (isRod(mc.player.getMainHandItem())) return InteractionHand.MAIN_HAND;
        if (isRod(mc.player.getOffhandItem())) return InteractionHand.OFF_HAND;
        if (takeRod.on()) {
            int slot = Inv.findHotbar(AutoFish::isRod);
            if (slot >= 0) { Inv.select(slot); return InteractionHand.MAIN_HAND; }
        }
        return null;
    }

    private void use(InteractionHand hand) {
        mc.gameMode.useItem(mc.player, hand);
        mc.player.swing(hand);
    }

    @Override
    protected void onEnable() {
        wait = 0;
        castCooldown = 0;
    }

    @Override
    public void onTick() {
        if (!inGame() || mc.gui.screen() != null) return;
        FishingHook hook = mc.player.fishing;
        if (wait > 0) {
            wait--;
            return;
        }
        if (hook != null) {
            if (((FishingHookAccessor) hook).teizen$isBiting()) {
                InteractionHand hand = rodHand();
                if (hand == null) return;
                use(hand);
                catches++;
                wait = recast.integer();
                castCooldown = 0;
            }
            return;
        }
        if (!autoCast.on()) return;
        if (castCooldown > 0) { castCooldown--; return; }
        InteractionHand hand = rodHand();
        if (hand == null) return;
        use(hand);
        castCooldown = 40;
    }

    @Override
    public String info() { return String.valueOf(catches); }
}
