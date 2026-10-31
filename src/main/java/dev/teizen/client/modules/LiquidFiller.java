package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.util.Inv;
import dev.teizen.client.util.Placer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

import java.util.Set;

/** LiquidFiller: schüttet Lava/Wasser in der Nähe mit Blöcken zu. */
public final class LiquidFiller extends Module {
    private final Setting what = add(Setting.choice("was", "Was zuschütten", "lava", "lava", "wasser", "beides"));
    private final Setting sources = add(Setting.bool("nurquellen", "Nur Quellblöcke (nicht fließendes)", true));
    private final Setting perTick = add(Setting.number("tempo", "Blöcke pro Tick", 1, 1, 4));
    private static final Set<String> FILL = Set.of("cobblestone", "netherrack", "dirt", "cobbled_deepslate", "stone", "andesite", "diorite",
            "granite", "tuff", "blackstone", "basalt", "end_stone", "deepslate", "sand", "gravel");
    public int filled;

    public LiquidFiller() {
        super("LiquidFiller", Category.BAUEN, "Schüttet Lava/Wasser in deiner Nähe automatisch zu.");
    }

    private static boolean filler(ItemStack s) {
        return !s.isEmpty() && s.getItem() instanceof BlockItem
                && FILL.contains(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem()).getPath());
    }

    @Override
    public void onTick() {
        if (!inGame()) return;
        double r = mc.player.blockInteractionRange();
        int ri = (int) Math.ceil(r);
        BlockPos me = mc.player.blockPosition();
        var eye = mc.player.getEyePosition();
        int done = 0;
        for (BlockPos p : BlockPos.betweenClosed(me.offset(-ri, -ri, -ri), me.offset(ri, ri, ri))) {
            if (done >= perTick.integer()) break;
            var f = mc.level.getFluidState(p);
            if (f.isEmpty()) continue;
            boolean lava = f.is(FluidTags.LAVA);
            if (what.is("lava") && !lava || what.is("wasser") && lava) continue;
            if (sources.on() && !f.isSource()) continue;
            if (eye.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(p)) > r * r) continue;
            if (p.equals(me) || p.equals(me.above())) continue; // nicht in sich selbst
            if (Inv.findHotbar(LiquidFiller::filler) < 0) return;
            if (!Inv.ensureInHand(LiquidFiller::filler)) return;
            if (Placer.place(p.immutable(), true)) { done++; filled++; }
        }
    }

    @Override
    public String info() { return String.valueOf(filled); }
}
