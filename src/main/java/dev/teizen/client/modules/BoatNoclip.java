package dev.teizen.client.modules;

import dev.teizen.client.TeizenClient;
import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.util.Chat;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;

import java.util.UUID;

/**
 * BoatNoclip: mit dem Boot durch Blöcke fliegen (Steuerung wie BoatFly). Geht in der eigenen Welt (auch als LAN-Host).
 * Auf fremden Servern lässt Minecraft das nicht zu: dort fliegt das Boot nur, und an Wänden wird man zurückgesetzt.
 */
public final class BoatNoclip extends Module {
    private final Setting speed = add(Setting.number("tempo", "Tempo (Blöcke pro Tick)", 0.6, 0.1, 3));
    private final Setting up = add(Setting.number("hoch", "Tempo nach oben/unten (Blöcke pro Tick)", 0.4, 0.05, 2));

    private int ticks;
    private AbstractBoat lastBoat;
    /** Dein Spieler (für den eingebauten Server der eigenen Welt). */
    private static volatile UUID me;
    private static volatile boolean active;

    public BoatNoclip() {
        super("BoatNoclip", Category.BEWEGUNG, "Mit dem Boot durch Wände fliegen (eigene Welt).");
    }

    @Override
    protected void onEnable() {
        if (mc.player != null) me = mc.player.getUUID();
        active = true;
        if (inGame() && mc.getSingleplayerServer() == null)
            Chat.info("BoatNoclip: durch Wände geht nur in der eigenen Welt – hier fliegt das Boot nur.");
    }

    @Override
    protected void onDisable() {
        active = false;
        if (lastBoat != null) lastBoat.noPhysics = false;
        lastBoat = null;
        IntegratedServer server = mc.getSingleplayerServer();
        UUID id = me;
        if (server != null && id != null) {
            server.execute(() -> {
                ServerPlayer sp = server.getPlayerList().getPlayer(id);
                if (sp != null && sp.getRootVehicle() != sp) sp.getRootVehicle().noPhysics = false;
            });
        }
    }

    /** Eingebauter Server: gehört dieser Spieler zu uns und ist BoatNoclip an? */
    public static boolean serverActive(Player p) {
        UUID id = me;
        return active && id != null && p != null && id.equals(p.getUUID());
    }

    /** Eingebauter Server: vor jeder Boot-Bewegung das Boot durch Blöcke lassen. */
    public static void serverPrepare(ServerPlayer p) {
        if (!serverActive(p)) return;
        Entity v = p.getRootVehicle();
        if (v != p) v.noPhysics = true;
    }

    /** Kein Ersticken in der Wand, solange du mit BoatNoclip im Boot sitzt. */
    public static boolean noSuffocation(Entity e) {
        return e instanceof Player p && p.isPassenger() && serverActive(p);
    }

    @Override
    public void onTick() {
        if (mc.player != null) me = mc.player.getUUID();
        AbstractBoat b = BoatFly.myBoat();
        if (b == null) {
            if (lastBoat != null) { lastBoat.noPhysics = false; lastBoat = null; }
            return;
        }
        lastBoat = b;
        b.noPhysics = true;
        BoatFly bf = TeizenClient.modules().get(BoatFly.class);
        boolean kick = bf == null || bf.isEnabled() || mc.getSingleplayerServer() == null;
        BoatFly.drive(b, speed.num(), up.num(), kick, ++ticks);
    }

    @Override
    public String requirement() {
        return mc.player != null && mc.getSingleplayerServer() == null ? "durch Wände nur in der eigenen Welt" : null;
    }

    @Override
    public String info() { return BoatFly.myBoat() == null ? "§8(ins Boot)" : Setting.fmt(speed.num()); }
}
