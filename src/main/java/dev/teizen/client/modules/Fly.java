package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import net.minecraft.world.entity.player.Abilities;

/**
 * Fliegen genau wie im Kreativmodus: zweimal schnell Leertaste = losfliegen / landen,
 * Leertaste = hoch, Shift = runter, Sprinten = 8x so schnell (einstellbar).
 */
public final class Fly extends Module {
    private final Setting speed = add(Setting.number("tempo", "Flug-Tempo (1 = wie Kreativ, 0.5 = halb so schnell)", 2, 0.1, 10));
    private final Setting doubleJump = add(Setting.bool("doppelsprung", "Wie im Kreativ: 2x Leertaste zum Fliegen (aus = immer fliegen)", true));
    private final Setting sprintFactor = add(Setting.number("sprint", "Mit Sprinten so viel schneller", 8, 1, 20));
    private final Setting antiKick = add(Setting.bool("antikick", "Gegen den Server-Rauswurf beim Fliegen ('Flying is not enabled') – zeigt dem Server kurz eine Abwärtsbewegung", true));
    private final Setting noFall = add(Setting.bool("fallschutz", "Kein Fallschaden beim Landen und nach dem Ausschalten in der Luft", true));
    /** Nach dem Ausschalten in der Luft: bis zur Landung vor Fallschaden schützen. */
    private boolean guardUntilGround;
    private double lastY = Double.NaN;
    public int protectedPackets;
    private int ticks;
    private boolean hadMayfly;
    /** Anti-Kick über zwei Ticks (nie zwei Pakete in einem Tick – sonst wirft 26.3 raus). */
    private int floatStage;
    private double floatY;

    public Fly() {
        super("Fly", Category.BEWEGUNG, "Du kannst fliegen wie im Kreativmodus.");
    }

    @Override
    protected void onEnable() {
        if (!inGame()) return;
        hadMayfly = mc.player.getAbilities().mayfly;
        // schon in der Luft? Dann gleich fliegen, damit man nicht runterfällt
        if (!mc.player.onGround()) mc.player.getAbilities().flying = true;
    }

    @Override
    protected void onDisable() {
        if (!inGame()) return;
        guardUntilGround = noFall.on() && !mc.player.onGround();
        floatStage = 0;
        Abilities a = mc.player.getAbilities();
        if (!mc.player.isCreative() && !mc.player.isSpectator()) {
            a.flying = false;
            a.mayfly = hadMayfly;
        }
        a.setFlyingSpeed(0.05f);
    }

    @Override
    public void onTick() {
        if (!inGame()) return;
        Abilities a = mc.player.getAbilities();
        a.mayfly = true;
        if (!doubleJump.on()) a.flying = true;
        // Minecraft verdoppelt beim Sprinten selbst -> den Rest hier dazu
        boolean sprint = mc.options.keySprint.isDown() || mc.player.isSprinting();
        double f = sprint ? Math.max(1, sprintFactor.num() / 2.0) : 1;
        a.setFlyingSpeed((float) (0.05 * speed.num() * f));
        if (!a.flying) {
            // nicht am Fliegen (z.B. nach 2x Leertaste in der Luft): Fallschaden verhindern
            lastY = mc.player.getY();
            if (noFall.on() && !mc.player.onGround() && mc.player.fallDistance > 1.5 && !mc.player.isCreative()) {
                groundPacket();
                mc.player.fallDistance = 0;
            }
            return;
        }
        // Beim Runterfliegen dem Server sagen, dass wir stehen: so sammelt sich keine Fallhöhe an -> kein Fallschaden beim Landen
        double y = mc.player.getY();
        if (noFall.on() && !Double.isNaN(lastY) && y < lastY - 0.001 && !mc.player.isCreative()) groundPacket();
        lastY = y;
        if (antiKick.on() && floatStage == 0 && !mc.player.onGround() && ++ticks % 15 == 0) floatStage = 1;
    }

    /**
     * Gegen den Server-Rauswurf "Flying is not enabled on this server!" (schwebt zu lange):
     * über zwei Ticks dem Server eine echte Abwärtsbewegung zeigen. Dabei wird das normale Bewegungs-Paket
     * ERSETZT (nie zwei in einem Tick – sonst wirft 26.3 mit "Invalid move player packet" raus).
     * Die Abwärtsbewegung ist absolut (Tick 2 liegt fest unter Tick 1), darum wirkt sie auch beim Steigen.
     * Sichtbar bewegt sich nichts – nur das Paket "lügt" kurz. Aus LocalPlayer.sendPosition (Mixin).
     * @return true = normales Paket in diesem Tick nicht schicken.
     */
    public static boolean onSendPositionFloat() {
        Fly f = dev.teizen.client.TeizenClient.modules().get(Fly.class);
        if (f == null || !f.isEnabled() || !f.inGame() || f.floatStage == 0) return false;
        var p = mc.player;
        if (p == null || p.onGround() || !p.getAbilities().flying) { f.floatStage = 0; return false; }
        double x = p.getX(), z = p.getZ();
        boolean hc = p.horizontalCollision;
        if (f.floatStage == 1) {
            f.floatY = p.getY();
            p.connection.send(new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Pos(x, f.floatY, z, false, hc));
            f.floatStage = 2;
            return true;
        }
        p.connection.send(new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Pos(x, f.floatY - 0.1, z, false, hc));
        f.floatStage = 0;
        f.protectedPackets++;
        return true;
    }

    @Override
    public String info() { return Setting.fmt(speed.num()) + (mc.player != null && !mc.player.getAbilities().flying ? " §8(2x Leertaste)" : ""); }

    private void groundPacket() {
        mc.player.connection.send(new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.StatusOnly(true, mc.player.horizontalCollision));
        protectedPackets++;
    }

    /** Läuft jeden Tick (auch wenn Fly aus ist): Schutz nach dem Ausschalten in der Luft. */
    public void fallGuard() {
        if (!guardUntilGround || isEnabled() || !inGame()) return;
        if (mc.player.onGround() || mc.player.isInWater() || mc.player.getAbilities().flying) { guardUntilGround = false; return; }
        if (mc.player.fallDistance > 1.5) {
            groundPacket();
            mc.player.fallDistance = 0;
        }
    }
}
