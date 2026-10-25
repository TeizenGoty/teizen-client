package dev.teizen.client.util;

import dev.teizen.client.TeizenClient;
import dev.teizen.client.mixin.MultiPlayerGameModeAccessor;
import dev.teizen.client.modules.MaceSwap;
import dev.teizen.client.modules.ShieldBreaker;
import dev.teizen.client.modules.SpearSwap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Waffe nur für einen Schlag wechseln (ShieldBreaker = Axt, MaceSwap = Streitkolben, SpearSwap = Speer).
 * Wird vor jedem Schlag aufgerufen (auch bei KillAura, TriggerBot, GodPvP) und danach sofort zurückgewechselt.
 * Der Server bekommt erst den Waffenwechsel und dann den Schlag – beides im selben Moment.
 */
public final class WeaponSwap {
    private static final Minecraft mc = Minecraft.getInstance();
    private static int restore = -1;
    /** Für Selbsttest/Anzeige: womit zuletzt gewechselt geschlagen wurde. */
    public static Item lastWeapon;
    public static int swaps;

    private WeaponSwap() {}

    public static boolean isAxe(ItemStack s) { return !s.isEmpty() && s.is(ItemTags.AXES); }

    public static boolean isSpear(ItemStack s) { return !s.isEmpty() && s.has(DataComponents.PIERCING_WEAPON); }

    public static boolean isMace(ItemStack s) { return !s.isEmpty() && s.is(Items.MACE); }

    /**
     * Vor einem Schlag.
     * @return true wenn der Schlag schon anders ausgeführt wurde (Speer-Stich) – dann den normalen Schlag abbrechen
     */
    public static boolean before(Entity target) {
        if (mc.player == null || mc.gameMode == null || restore >= 0 || TeizenClient.modules() == null) return false;
        var mm = TeizenClient.modules();
        ItemStack held = mc.player.getMainHandItem();

        ShieldBreaker sb = mm.get(ShieldBreaker.class);
        if (sb != null && sb.isEnabled() && ShieldBreaker.blocking(target) && !isAxe(held)) {
            int s = Inv.findHotbar(WeaponSwap::isAxe);
            if (s >= 0) { swapFor(s); sb.swaps++; return false; }
        }
        MaceSwap ms = mm.get(MaceSwap.class);
        if (ms != null && ms.isEnabled() && ms.wants() && !isMace(held)) {
            int s = Inv.findHotbar(WeaponSwap::isMace);
            if (s >= 0) { swapFor(s); ms.swaps++; return false; }
        }
        SpearSwap sp = mm.get(SpearSwap.class);
        if (sp != null && sp.isEnabled() && sp.wants(target) && !isSpear(held)) {
            int s = Inv.findHotbar(WeaponSwap::isSpear);
            if (s >= 0) {
                // Der Server sticht in Richtung der Kopf-Drehung, und die zieht er erst im nächsten Tick nach.
                // Schaust du den Gegner nicht schon an: jetzt unsichtbar hindrehen, im nächsten Tick zustechen.
                int now = mc.player.tickCount;
                boolean looking = mc.crosshairPickEntity == target;
                boolean prepared = pendingId == target.getId() && now - pendingTick == 1;
                if (!looking && !prepared) {
                    float[] r = Rot.to(target.getBoundingBox().getCenter());
                    Rot.sendSilent(r[0], r[1]);
                    pendingId = target.getId();
                    pendingTick = now;
                    return true;
                }
                pendingId = -1;
                int prev = Inv.selected();
                Inv.select(s);
                ItemStack spear = mc.player.getMainHandItem();
                lastWeapon = spear.getItem();
                swaps++;
                // zum Ziel drehen (nur für den Server), Speer schicken, zustechen, zurück
                float[] r = Rot.to(target.getBoundingBox().getCenter());
                Rot.sendSilent(r[0], r[1]);
                ((MultiPlayerGameModeAccessor) mc.gameMode).teizen$sendCarriedItem();
                var pw = spear.get(DataComponents.PIERCING_WEAPON);
                if (pw != null) mc.gameMode.piercingAttack(pw);
                mc.player.swing(InteractionHand.MAIN_HAND);
                Inv.select(prev);
                sp.swaps++;
                return true;
            }
        }
        return false;
    }

    private static int pendingId = -1, pendingTick;

    /** Jeden Tick (SpearSwap): vorbereiteten Speer-Stich ausführen. */
    public static void tickPending() {
        if (pendingId < 0 || mc.player == null || mc.level == null) return;
        int now = mc.player.tickCount;
        if (now - pendingTick < 1) return;           // erst im nächsten Tick
        if (now - pendingTick > 1) { pendingId = -1; return; }
        Entity e = mc.level.getEntity(pendingId);
        if (e == null || !e.isAlive()) { pendingId = -1; return; }
        mc.gameMode.attack(mc.player, e);
        mc.player.swing(InteractionHand.MAIN_HAND);
    }

    private static void swapFor(int slot) {
        restore = Inv.selected();
        Inv.select(slot);
        lastWeapon = mc.player.getMainHandItem().getItem();
        swaps++;
    }

    /** Nach dem Schlag: wieder die alte Waffe in die Hand. */
    public static void after() {
        if (restore >= 0 && mc.player != null) Inv.select(restore);
        restore = -1;
    }
}
