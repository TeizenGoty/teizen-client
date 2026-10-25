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
    public static int swaps, weaponSwitches;

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
        // AutoWeapon (AutoTool): zuerst die beste Waffe in die Hand (bleibt danach drin)
        var at = mm.get(dev.teizen.client.modules.AutoTool.class);
        if (at != null) {
            int w = at.bestWeaponSlot();
            if (w >= 0 && w != Inv.selected()) { Inv.select(w); weaponSwitches++; }
        }
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
                // Der Server sticht in Richtung der Kopf-Drehung – und die zieht er erst ein paar Ticks später nach.
                // Darum: unsichtbar zum Gegner drehen und erst nach LEAD Ticks zustechen (danach beim selben Gegner sofort).
                int now = mc.player.tickCount;
                if (aimId != target.getId()) { aimId = target.getId(); aimSince = now; }
                lastWanted = now;
                if (now - aimSince < LEAD) {
                    aimAt(target);
                    stabWanted = true;
                    return true;
                }
                stabWanted = false;
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

    /** So viele Ticks vorher muss der Server die Drehung zum Gegner kennen (gemessen: ab 4 Ticks trifft es sicher). */
    public static final int LEAD = 4;
    private static int aimId = -1, aimSince, lastWanted;
    private static boolean stabWanted;

    private static void aimAt(Entity e) {
        float[] r = Rot.to(e.getBoundingBox().getCenter());
        Rot.sendSilent(r[0], r[1]);
    }

    /** Jeden Tick (SpearSwap): Kopf beim Gegner halten und den vorbereiteten Stich ausführen. */
    public static void tickSpear() {
        if (aimId < 0 || mc.player == null || mc.level == null) return;
        int now = mc.player.tickCount;
        Entity e = mc.level.getEntity(aimId);
        if (e == null || !e.isAlive() || now - lastWanted > 20 || now < aimSince) { aimId = -1; stabWanted = false; return; }
        aimAt(e);
        if (stabWanted && now - aimSince >= LEAD) {
            mc.gameMode.attack(mc.player, e);
            mc.player.swing(InteractionHand.MAIN_HAND);
        }
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
