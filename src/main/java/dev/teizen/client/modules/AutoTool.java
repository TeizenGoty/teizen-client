package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.util.Inv;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * AutoTool: nimmt beim Abbauen automatisch das beste Werkzeug aus der Hotbar.
 * Dazu AutoWeapon: beim Schlagen (auch KillAura, TriggerBot, GodPvP) automatisch die beste Waffe.
 */
public final class AutoTool extends Module {
    private final Setting weapon = add(Setting.choice("waffe", "AutoWeapon beim Schlagen: schwert = bestes Schwert, axt = beste Axt, staerkste = meister Schaden, aus",
            "schwert", "schwert", "axt", "staerkste", "aus"));

    public AutoTool() {
        super("AutoTool", Category.ABBAU, "Bestes Werkzeug beim Abbauen und beste Waffe beim Schlagen.");
    }

    @Override
    public void onTick() {
        if (!inGame() || !mc.options.keyAttack.isDown()) return;
        if (!(mc.hitResult instanceof BlockHitResult bhr) || bhr.getType() != HitResult.Type.BLOCK) return;
        int t = Inv.bestTool(mc.level.getBlockState(bhr.getBlockPos()));
        if (t >= 0) Inv.select(t);
    }

    /** Angriffsschaden eines Items (aus seinen Werten). */
    public static double damage(ItemStack s) {
        if (s.isEmpty()) return 0;
        double d = 0;
        for (var e : s.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers()) {
            if (e.attribute().equals(Attributes.ATTACK_DAMAGE)) d += e.modifier().amount();
        }
        return d;
    }

    /** Hotbar-Platz der besten Waffe für die aktuelle Einstellung, oder -1 (dann nichts ändern). */
    public int bestWeaponSlot() {
        if (!isEnabled() || weapon.is("aus") || mc.player == null) return -1;
        int best = -1;
        double bestScore = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack s = Inv.inv().getItem(i);
            if (s.isEmpty()) continue;
            boolean sword = s.is(ItemTags.SWORDS), axe = s.is(ItemTags.AXES);
            double dmg = damage(s);
            if (dmg <= 0) continue;
            double score = dmg;
            if (weapon.is("schwert") && sword) score += 100;
            if (weapon.is("axt") && axe) score += 100;
            if (score > bestScore) { bestScore = score; best = i; }
        }
        return best;
    }

    @Override
    public String info() { return weapon.is("aus") ? "" : "+Waffe"; }
}
