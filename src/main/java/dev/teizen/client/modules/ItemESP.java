package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.util.Draw;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Zeigt gedroppte Items durch Wände. Items aus dem Filter (z.B. Diamanten, Totems) werden
 * hervorgehoben – mit Symbol, Linie und einer Liste am Rand – damit sie in einem großen Haufen auffallen.
 */
public final class ItemESP extends Module {
    public final Setting filter = add(Setting.text("filter", "Diese Items hervorheben (Komma-getrennt, Teil des Namens reicht)",
            "diamond,netherite,totem,elytra,enchanted_golden_apple,shulker_box,emerald,beacon,nether_star,trident,mace,heavy_core,enchanted_book"));
    private final Setting onlyFilter = add(Setting.bool("nurfilter", "Nur die Filter-Items anzeigen", false));
    private final Setting range = add(Setting.number("reichweite", "Bis zu welcher Entfernung (Blöcke)", 64, 4, 256));
    private final Setting names = add(Setting.bool("namen", "Name und Anzahl anzeigen", true));
    private final Setting tracers = add(Setting.bool("linien", "Linien zu hervorgehobenen Items", true));
    private final Setting list = add(Setting.bool("liste", "Liste der hervorgehobenen Items links am Rand", true));

    private record Entry(ItemEntity entity, boolean special) {}

    private final List<Entry> entries = new ArrayList<>();
    private String lastFilter = "";
    private String[] terms = new String[0];
    public int total, highlighted;

    public ItemESP() {
        super("ItemESP", Category.SICHT, "Gedroppte Items durch Wände, wichtige Items hervorgehoben.");
    }

    public boolean special(ItemStack s) {
        if (!filter.str().equals(lastFilter)) {
            lastFilter = filter.str();
            List<String> t = new ArrayList<>();
            for (String p : lastFilter.toLowerCase(Locale.ROOT).split(",")) if (!p.isBlank()) t.add(p.trim().replace("minecraft:", ""));
            terms = t.toArray(new String[0]);
        }
        String id = BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
        for (String t : terms) {
            if (id.contains(t)) return true;
            if ((t.equals("verzaubert") || t.equals("enchanted")) && s.isEnchanted()) return true;
        }
        return false;
    }

    @Override
    public void onTick() {
        if (!inGame() || mc.player.tickCount % 4 != 0) return;
        entries.clear();
        int sp = 0;
        double r = range.num();
        for (ItemEntity e : mc.level.getEntitiesOfClass(ItemEntity.class, mc.player.getBoundingBox().inflate(r))) {
            if (!e.isAlive() || mc.player.distanceTo(e) > r) continue;
            boolean special = special(e.getItem());
            if (onlyFilter.on() && !special) continue;
            entries.add(new Entry(e, special));
            if (special) sp++;
        }
        total = entries.size();
        highlighted = sp;
    }

    @Override
    public void onHud(GuiGraphicsExtractor g, float partial) {
        if (!inGame()) return;
        Vec3 eye = mc.player.getEyePosition();
        int labels = 0;
        // erst die normalen (unten), dann die besonderen (oben drauf)
        ItemEntity nearest = null;
        double nd = Double.MAX_VALUE;
        for (int pass = 0; pass < 2; pass++) {
            for (Entry en : entries) {
                if (en.special() != (pass == 1)) continue;
                ItemEntity e = en.entity();
                if (!e.isAlive()) continue;
                Vec3 p = e.getPosition(partial);
                AABB box = new AABB(p.x - 0.2, p.y, p.z - 0.2, p.x + 0.2, p.y + 0.4, p.z + 0.2);
                ItemStack st = e.getItem();
                if (!en.special()) {
                    double[] r = Draw.worldBox(g, box, 0xC0FFFFFF, false);
                    if (r != null && names.on() && labels++ < 40) {
                        String t = "§7" + st.getHoverName().getString() + (st.getCount() > 1 ? " x" + st.getCount() : "");
                        g.text(mc.font, t, (int) ((r[0] + r[2]) / 2) - mc.font.width(t) / 2, (int) r[1] - 9, 0xFFFFFFFF, true);
                    }
                    continue;
                }
                double[] r = Draw.worldBox(g, box, 0xFFFFC800, true);
                if (tracers.on()) Draw.tracer(g, p.x, p.y + 0.2, p.z, 0xFFFFC800);
                double dd = eye.distanceTo(p);
                if (dd < nd) { nd = dd; nearest = e; }
                if (r != null) {
                    int cx = (int) ((r[0] + r[2]) / 2), top = (int) r[1];
                    g.fill(cx - 10, top - 30, cx + 10, top - 10, 0xA0302000);
                    g.item(st, cx - 8, top - 28);
                    if (names.on()) {
                        String t = "§6★ §f" + st.getHoverName().getString() + (st.getCount() > 1 ? " §ex" + st.getCount() : "")
                                + " §7" + Math.round(eye.distanceTo(p)) + "m";
                        g.text(mc.font, t, cx - mc.font.width(t) / 2, top - 40, 0xFFFFFFFF, true);
                    }
                }
            }
        }
        if (tracers.on() && nearest != null) {
            Vec3 p = nearest.getPosition(partial);
            Draw.tracerNearest(g, p.x, p.y + 0.2, p.z, 0xFFFFC800, nearest.getItem().getHoverName().getString() + " " + Math.round(nd) + "m");
        }
        if (list.on() && highlighted > 0) {
            Map<String, int[]> sum = new LinkedHashMap<>(); // Name -> {Anzahl, nächste Entfernung}
            for (Entry en : entries) {
                if (!en.special()) continue;
                ItemStack st = en.entity().getItem();
                int d = (int) Math.round(eye.distanceTo(en.entity().position()));
                int[] v = sum.computeIfAbsent(st.getHoverName().getString(), k -> new int[]{0, Integer.MAX_VALUE});
                v[0] += st.getCount();
                v[1] = Math.min(v[1], d);
            }
            int y = 16;
            g.text(mc.font, "§6★ Wichtige Items:", 3, y, 0xFFFFFFFF, true);
            for (Map.Entry<String, int[]> e : sum.entrySet()) {
                y += 10;
                if (y > g.guiHeight() / 2) break;
                g.text(mc.font, "§f" + e.getValue()[0] + "x " + e.getKey() + " §7(" + e.getValue()[1] + "m)", 3, y, 0xFFFFFFFF, true);
            }
        }
    }

    @Override
    public String info() { return highlighted + "/" + total; }
}
