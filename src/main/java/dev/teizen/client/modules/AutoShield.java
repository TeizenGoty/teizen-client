package dev.teizen.client.modules;

import dev.teizen.client.TeizenClient;
import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.util.Inv;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Hält ein Schild in der linken Hand und blockt automatisch bei JEDEM Angriff, der dir schaden würde:
 * Schläge von Spielern/Monstern, Pfeile, Feuerbälle, Dreizacke, Creeper, TNT und End-Kristalle in der Nähe von Gegnern.
 * Dreht sich dabei (nur für den Server) zur Gefahr, damit das Schild auch von hinten schützt.
 */
public final class AutoShield extends Module {
    private final Setting mode = add(Setting.choice("wann", "alles = bei jedem Angriff, pfeile = nur Geschosse, gegner = Gegner in der Nähe, aus = nur Schild halten", "alles", "alles", "pfeile", "gegner", "aus"));
    private final Setting range = add(Setting.number("reichweite", "Gegner in dieser Entfernung (Blöcke)", 4.5, 1, 16));
    private final Setting face = add(Setting.bool("drehen", "Zur Gefahr drehen (nur für den Server), damit das Schild immer trifft", true));
    private final Setting totemFirst = add(Setting.bool("totemzuerst", "Wenn AutoTotem an ist: Totem hat Vorrang vor dem Schild", true));

    public boolean blockedOnce;
    private boolean blocking;
    private int pause;

    public AutoShield() {
        super("AutoShield", Category.KAMPF, "Schild immer links und automatisch blocken.");
    }

    public static boolean isShield(ItemStack s) {
        return !s.isEmpty() && s.has(DataComponents.BLOCKS_ATTACKS);
    }

    @Override
    protected void onDisable() { stopBlocking(); }

    @Override
    public void onTick() {
        if (!inGame()) return;
        ItemStack off = mc.player.getOffhandItem();
        // 1) Schild in die linke Hand
        AutoTotem at = TeizenClient.modules().get(AutoTotem.class);
        boolean totemWins = totemFirst.on() && at != null && at.isEnabled()
                && (off.is(Items.TOTEM_OF_UNDYING) || Inv.findAny(s -> s.is(Items.TOTEM_OF_UNDYING)) >= 0);
        boolean screenOk = mc.gui.screen() == null || mc.gui.screen() instanceof InventoryScreen;
        if (!totemWins && !isShield(off) && screenOk && mc.player.containerMenu == mc.player.inventoryMenu) {
            int slot = Inv.findAny(AutoShield::isShield);
            if (slot >= 0) Inv.swapToOffhand(slot);
            return;
        }
        // 2) Blocken
        boolean want = false;
        if (isShield(off) && !mode.is("aus") && mc.gui.screen() == null) {
            boolean busy = mc.player.isUsingItem() && mc.player.getUsedItemHand() == InteractionHand.MAIN_HAND;
            if (!busy) {
                threat = null;
                if (mode.is("alles")) threat = anyThreat();
                else {
                    if (mode.is("gegner")) { var t = Targets.nearest(range.num(), true, true, false, false); if (t != null) threat = t.getEyePosition(); }
                    if (threat == null) threat = projectileIncoming();
                }
                want = threat != null;
            }
            if (mc.options.keyAttack.isDown()) pause = 6; // selbst schlagen geht vor
            if (pause > 0) { pause--; want = false; }
        }
        if (want) {
            if (face.on() && threat != null) {
                float[] r = dev.teizen.client.util.Rot.to(threat);
                dev.teizen.client.util.Rot.sendSilent(r[0], r[1]);
            }
            if (!mc.player.isUsingItem()) mc.gameMode.useItem(mc.player, InteractionHand.OFF_HAND);
            mc.options.keyUse.setDown(true);
            blocking = true;
            if (mc.player.isUsingItem() && isShield(mc.player.getUseItem())) blockedOnce = true;
        } else if (blocking) {
            stopBlocking();
        }
    }

    private void stopBlocking() {
        if (!blocking) return;
        blocking = false;
        mc.options.keyUse.setDown(false);
        if (mc.player != null && mc.player.isUsingItem() && mc.player.getUsedItemHand() == InteractionHand.OFF_HAND)
            mc.gameMode.releaseUsingItem(mc.player);
    }

    /** Wovon die Gefahr kommt (zum Hindrehen), oder null. */
    private Vec3 threat;
    /** Wie oft schon geblockt wurde, und wogegen zuletzt (Selbsttest). */
    public String lastThreat = "";

    /** Fliegt ein Pfeil/Feuerball usw. auf uns zu? @return Position des Geschosses oder null */
    private Vec3 projectileIncoming() {
        double r = range.num() + 8;
        Vec3 me = mc.player.getBoundingBox().getCenter();
        for (Projectile p : mc.level.getEntitiesOfClass(Projectile.class, mc.player.getBoundingBox().inflate(r))) {
            if (p.getOwner() == mc.player) continue;
            Vec3 v = p.getDeltaMovement();
            if (v.lengthSqr() < 0.04) continue; // liegt still (steckt z.B. im Boden)
            Vec3 to = me.subtract(p.position());
            if (to.length() > r) continue;
            if (v.normalize().dot(to.normalize()) > 0.75) { lastThreat = "Geschoss"; return p.position(); }
        }
        return null;
    }

    /** Alles, was gleich Schaden machen würde. */
    private Vec3 anyThreat() {
        Vec3 p = projectileIncoming();
        if (p != null) return p;
        Vec3 me = mc.player.getBoundingBox().getCenter();
        double r = range.num();
        // Schläge: Gegner (Spieler/Monster) in Schlagweite
        for (net.minecraft.world.entity.Entity e : mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(r + 1))) {
            if (e instanceof net.minecraft.world.entity.monster.Creeper c) {
                if (c.getSwelling(0f) > 0.05f && c.distanceTo(mc.player) < 7) { lastThreat = "Creeper"; return c.position(); }
                continue;
            }
            if (!Targets.valid(e, true, true, false)) continue;
            if (Targets.distanceToBox(e) > r) continue;
            if (e instanceof net.minecraft.world.entity.player.Player pl) {
                // Spieler: wenn er zu uns schaut oder gerade ausholt
                Vec3 look = pl.getViewVector(1f);
                Vec3 to = me.subtract(pl.getEyePosition()).normalize();
                if (look.dot(to) < 0.5 && pl.getAttackAnim(0f) <= 0) continue;
            }
            lastThreat = e.getName().getString();
            return ((net.minecraft.world.entity.LivingEntity) e).getEyePosition();
        }
        // Explosionen: TNT kurz vorm Hochgehen, End-Kristalle wenn ein Gegner in der Nähe ist
        for (net.minecraft.world.entity.item.PrimedTnt t : mc.level.getEntitiesOfClass(net.minecraft.world.entity.item.PrimedTnt.class, mc.player.getBoundingBox().inflate(8))) {
            if (t.getFuse() < 25) { lastThreat = "TNT"; return t.position(); }
        }
        var enemy = Targets.nearest(12, true, false, false, true);
        if (enemy != null) {
            for (net.minecraft.world.entity.boss.enderdragon.EndCrystal c : mc.level.getEntitiesOfClass(net.minecraft.world.entity.boss.enderdragon.EndCrystal.class, mc.player.getBoundingBox().inflate(6))) {
                lastThreat = "Kristall";
                return c.position();
            }
        }
        // gerade getroffen worden -> kurz weiterblocken
        if (mc.player.hurtTime > 0 && mc.player.getLastHurtByMob() != null && mc.player.getLastHurtByMob().distanceTo(mc.player) < 8) {
            lastThreat = "Treffer";
            return mc.player.getLastHurtByMob().getEyePosition();
        }
        return null;
    }

    @Override
    public String info() { return mode.str(); }
}
