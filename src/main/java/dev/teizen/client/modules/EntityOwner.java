package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.util.Draw;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;

import java.util.UUID;

/** EntityOwner: zeigt über gezähmten Tieren (Hunde, Katzen, Pferde, Papageien …), wem sie gehören. */
public final class EntityOwner extends Module {
    public int shown;

    public EntityOwner() {
        super("EntityOwner", Category.SICHT, "Zeigt über gezähmten Tieren, wem sie gehören.");
    }

    private static UUID owner(Entity e) {
        if (e instanceof OwnableEntity o) {
            var ref = o.getOwnerReference();
            return ref == null ? null : ref.getUUID();
        }
        return null;
    }

    private static String name(UUID id) {
        if (mc.player != null && id.equals(mc.player.getUUID())) return "dir";
        var info = mc.getConnection() == null ? null : mc.getConnection().getPlayerInfo(id);
        if (info != null) return info.getProfile().name();
        var p = mc.level.getPlayerByUUID(id);
        if (p != null) return p.getGameProfile().name();
        return id.toString().substring(0, 8) + "… (offline)";
    }

    @Override
    public void onHud(GuiGraphicsExtractor g, float partial) {
        int n = 0;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e.distanceToSqr(mc.player) > 64 * 64) continue;
            UUID id = owner(e);
            if (id == null) continue;
            double[] r = Draw.worldBox(g, e.getBoundingBox(), 0x00000000, false);
            if (r == null) continue;
            String s = "§7gehört §f" + name(id);
            g.text(mc.font, s, (int) ((r[0] + r[2]) / 2) - mc.font.width(s) / 2, (int) r[1] - 12, 0xFFFFFFFF, true);
            n++;
        }
        shown = n;
    }
}
