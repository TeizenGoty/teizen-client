package dev.teizen.client.modules;

import com.mojang.authlib.GameProfile;
import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;

import java.util.UUID;

/** FakePlayer: eine Testpuppe (nur bei dir sichtbar) – zum Ausprobieren von KillAura, AutoCrystal, ESP usw. */
public final class FakePlayer extends Module {
    private final Setting name = add(Setting.text("name", "Name der Puppe", "Testpuppe"));
    private final Setting copyArmor = add(Setting.bool("ruestung", "Deine Rüstung und Waffe kopieren", true));
    public RemotePlayer dummy;
    private Object level;

    public FakePlayer() {
        super("FakePlayer", Category.SONSTIGES, "Testpuppe (nur bei dir sichtbar) zum Ausprobieren.");
    }

    @Override
    protected void onEnable() {
        if (!inGame()) return;
        spawn();
    }

    private void spawn() {
        GameProfile gp = new GameProfile(UUID.nameUUIDFromBytes(("teizen-fake-" + name.str()).getBytes()), name.str().isBlank() ? "Testpuppe" : name.str());
        dummy = new RemotePlayer(mc.level, gp);
        dummy.snapTo(mc.player.getX(), mc.player.getY(), mc.player.getZ(), mc.player.getYRot(), mc.player.getXRot());
        dummy.setYHeadRot(mc.player.getYHeadRot());
        dummy.setHealth(20f);
        if (copyArmor.on())
            for (EquipmentSlot s : EquipmentSlot.values()) dummy.setItemSlot(s, mc.player.getItemBySlot(s).copy());
        mc.level.addEntity(dummy);
        level = mc.level;
    }

    @Override
    protected void onDisable() {
        if (dummy != null && mc.level != null && level == mc.level) mc.level.removeEntity(dummy.getId(), Entity.RemovalReason.DISCARDED);
        dummy = null;
    }

    @Override
    public void onTick() {
        if (!inGame()) return;
        if (dummy == null || level != mc.level) { dummy = null; spawn(); }
    }

    @Override
    public String info() { return name.str(); }
}
