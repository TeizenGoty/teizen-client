package dev.teizen.client.modules;

import dev.teizen.client.core.Friends;
import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.util.Projector;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Große Namensschilder über Spielern – auch durch Wände und auf große Entfernung. */
public final class Nametags extends Module {
    private final Setting size = add(Setting.number("groesse", "Schriftgröße (1 = normal)", 1.5, 0.5, 4));
    private final Setting range = add(Setting.number("reichweite", "Bis zu welcher Entfernung (Blöcke)", 512, 8, 1024));
    private final Setting health = add(Setting.bool("leben", "Leben anzeigen", true));
    private final Setting distance = add(Setting.bool("abstand", "Entfernung anzeigen", true));
    private final Setting item = add(Setting.bool("item", "Gegenstand in der Hand anzeigen", true));
    private final Setting mobs = add(Setting.bool("mobs", "Auch Mobs mit Namensschild", false));

    public int drawn;

    public Nametags() {
        super("Nametags", Category.SICHT, "Große Namen über Spielern, auch weit weg und durch Wände.");
    }

    /** Wie hoch das Schild über dem Kopf ist (für ArmorESP, damit nichts übereinander liegt). */
    public int tagHeight(LivingEntity e) {
        boolean hasItem = item.on() && !e.getMainHandItem().isEmpty();
        return (int) Math.ceil((hasItem ? 21 : 11) * size.num());
    }

    public List<LivingEntity> targets() {
        List<LivingEntity> out = new ArrayList<>();
        double r = range.num();
        for (Player p : mc.level.players()) {
            if (p == mc.player || p.isSpectator() || mc.player.distanceTo(p) > r) continue;
            out.add(p);
        }
        if (mobs.on()) {
            for (Entity e : mc.level.entitiesForRendering()) {
                if (e instanceof Mob m && m.hasCustomName() && mc.player.distanceTo(m) <= r) out.add(m);
            }
        }
        return out;
    }

    @Override
    public void onHud(GuiGraphicsExtractor g, float partial) {
        if (!inGame()) return;
        int n = 0;
        float sc = size.flt();
        for (LivingEntity e : targets()) {
            Vec3 pos = e.getPosition(partial).add(0, e.getBbHeight() + 0.5, 0);
            double[] s = Projector.project(pos.x, pos.y, pos.z);
            if (s == null) continue;
            String name = e instanceof Player p ? p.getGameProfile().name() : e.getDisplayName().getString();
            boolean friend = e instanceof Player && Friends.is(name);
            StringBuilder t = new StringBuilder(friend ? "§a" : "§f").append(name);
            if (health.on()) {
                float hp = e.getHealth() + e.getAbsorptionAmount();
                float max = e.getMaxHealth();
                String c = hp > max * 0.66 ? "§a" : hp > max * 0.33 ? "§e" : "§c";
                t.append(' ').append(c).append(Math.round(hp)).append("❤");
            }
            if (distance.on()) t.append(" §7").append(Math.round(mc.player.distanceTo(e))).append("m");
            String txt = t.toString();
            int w = mc.font.width(txt);
            g.pose().pushMatrix();
            g.pose().translate((float) s[0], (float) s[1]);
            g.pose().scale(sc, sc);
            g.fill(-w / 2 - 2, -11, w / 2 + 2, 0, 0x90000000);
            g.text(mc.font, txt, -w / 2, -10, 0xFFFFFFFF, false);
            if (item.on() && !e.getMainHandItem().isEmpty()) {
                String it = "§7" + e.getMainHandItem().getHoverName().getString();
                int iw = mc.font.width(it);
                g.fill(-iw / 2 - 2, -21, iw / 2 + 2, -11, 0x70000000);
                g.text(mc.font, it, -iw / 2, -20, 0xFFFFFFFF, false);
            }
            g.pose().popMatrix();
            n++;
        }
        drawn = n;
    }

    @Override
    public String info() { return Setting.fmt(size.num()) + "x"; }
}
