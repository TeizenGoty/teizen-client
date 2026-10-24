package dev.teizen.client.modules;

import dev.teizen.client.TeizenClient;
import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.util.Projector;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Zeigt die Rüstung (und Waffen) anderer Spieler als Symbole über ihrem Kopf, mit Haltbarkeit. */
public final class ArmorESP extends Module {
    private static final EquipmentSlot[] ORDER = {EquipmentSlot.MAINHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST,
            EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.OFFHAND};

    private final Setting range = add(Setting.number("reichweite", "Bis zu welcher Entfernung (Blöcke)", 128, 8, 1024));
    private final Setting hands = add(Setting.bool("haende", "Auch Gegenstände in den Händen", true));
    private final Setting durability = add(Setting.bool("haltbarkeit", "Haltbarkeit in % anzeigen", true));
    private final Setting size = add(Setting.number("groesse", "Größe der Symbole", 1, 0.5, 3));
    private final Setting others = add(Setting.bool("mobs", "Auch Mobs und Rüstungsständer", false));

    public int drawn;

    public ArmorESP() {
        super("ArmorESP", Category.SICHT, "Zeigt die Rüstung anderer Spieler über ihrem Kopf.");
    }

    @Override
    public void onHud(GuiGraphicsExtractor g, float partial) {
        if (!inGame()) return;
        Nametags tags = TeizenClient.modules().get(Nametags.class);
        double r = range.num();
        List<LivingEntity> list = new ArrayList<>();
        for (Player p : mc.level.players()) if (p != mc.player && !p.isSpectator() && mc.player.distanceTo(p) <= r) list.add(p);
        if (others.on()) {
            for (Entity e : mc.level.entitiesForRendering()) {
                if (e instanceof LivingEntity le && !(e instanceof Player) && mc.player.distanceTo(e) <= r
                        && (e instanceof ArmorStand || e instanceof net.minecraft.world.entity.Mob)) list.add(le);
            }
        }
        int n = 0;
        float sc = size.flt();
        for (LivingEntity e : list) {
            List<ItemStack> items = new ArrayList<>();
            for (EquipmentSlot slot : ORDER) {
                if (!hands.on() && (slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND)) continue;
                ItemStack s = e.getItemBySlot(slot);
                if (!s.isEmpty()) items.add(s);
            }
            if (items.isEmpty()) continue;
            Vec3 pos = e.getPosition(partial).add(0, e.getBbHeight() + 0.5, 0);
            double[] s = Projector.project(pos.x, pos.y, pos.z);
            if (s == null) continue;
            int lift = tags != null && tags.isEnabled() && tags.targets().contains(e) ? tags.tagHeight(e) + 2 : 0;
            int w = items.size() * 18;
            g.pose().pushMatrix();
            g.pose().translate((float) s[0], (float) s[1] - lift);
            g.pose().scale(sc, sc);
            int x0 = -w / 2, y0 = durability.on() ? -28 : -19;
            g.fill(x0 - 2, y0 - 2, x0 + w, y0 + (durability.on() ? 27 : 18), 0x70000000);
            for (int i = 0; i < items.size(); i++) {
                ItemStack st = items.get(i);
                int x = x0 + i * 18;
                g.item(st, x, y0);
                g.itemDecorations(mc.font, st, x, y0);
                if (durability.on() && st.isDamageableItem() && st.getMaxDamage() > 0) {
                    int pct = Math.round(100f * (st.getMaxDamage() - st.getDamageValue()) / st.getMaxDamage());
                    String t = pct + "%";
                    int c = pct > 60 ? 0xFF55FF55 : pct > 25 ? 0xFFFFFF55 : 0xFFFF5555;
                    g.pose().pushMatrix();
                    g.pose().translate(x + 8, y0 + 18);
                    g.pose().scale(0.6f, 0.6f);
                    g.text(mc.font, t, -mc.font.width(t) / 2, 0, c, true);
                    g.pose().popMatrix();
                }
            }
            g.pose().popMatrix();
            n++;
        }
        drawn = n;
    }
}
