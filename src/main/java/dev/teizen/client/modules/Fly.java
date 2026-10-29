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
    /** Anti-Kick: noch so viele Bewegungs-Pakete kurz absenken. */
    private int floatStage;
    /** Gerade selbst abgesenkt -> dafür kein Fallschutz-Paket schicken. */
    private boolean dipped;
    /** So viel geht's nach dem Absenken wieder hoch (damit man beim Stillstehen auf der Höhe bleibt). */
    private double riseLeft;
    /** Höhe beim letzten Bewegungs-Paket. */
    private static double sentY = Double.NaN;

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
        riseLeft = 0;
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
        if (dipped) dipped = false;
        else if (noFall.on() && !Double.isNaN(lastY) && y < lastY - 0.001 && !mc.player.isCreative()) groundPacket();
        lastY = y;
        if (antiKick.on() && floatStage == 0 && !mc.player.onGround() && ++ticks % 20 == 0) floatStage = 3;
    }

    /**
     * Gegen den Server-Rauswurf "Flying is not enabled on this server!" (schwebt zu lange), wie bei Wurst:
     * alle 20 Ticks sinkt man 3 Ticks lang ECHT ein kleines Stück (je 0.05 unter das letzte Paket) und danach genauso viel wieder hoch, direkt bevor Minecraft
     * sein normales Bewegungs-Paket schickt. Der Server sieht eine Abwärtsbewegung und setzt seinen
     * Schwebe-Zähler zurück. Nur das normale Paket – nie zwei pro Tick (26.3 wirft sonst raus).
     * Aus LocalPlayer.sendPosition (Mixin, Anfang).
     */
    public static void beforeSendPosition() {
        var p = mc.player;
        if (p == null) return;
        Fly f = dev.teizen.client.TeizenClient.modules().get(Fly.class);
        if (f != null && f.floatStage > 0) {
            // 3 Ticks hintereinander: der Server wertet pro Tick nur das letzte Paket aus und bekommt manchmal
            // zwei auf einmal – so endet sicher mindestens ein Server-Tick mit einer Abwärtsbewegung
            f.floatStage--;
            if (!f.isEnabled() || !p.getAbilities().flying || p.onGround()) f.floatStage = 0;
            else if (!Double.isNaN(sentY)) {
                double dy = Math.min(p.getY(), sentY) - 0.05 - p.getY();
                if (mc.level != null && mc.level.noCollision(p, p.getBoundingBox().move(0, dy, 0))) {
                    p.setPos(p.getX(), p.getY() + dy, p.getZ());
                    f.dipped = true;
                    f.riseLeft += 0.05;
                    if (f.floatStage == 0) f.protectedPackets++;
                    if (dev.teizen.client.SelfTest.ACTIVE)
                        dev.teizen.client.TeizenClient.LOG.info(String.format(java.util.Locale.ROOT, "Fly-AntiKick: abgesenkt um %.3f auf %.3f (letztes Paket %.3f)", -dy, p.getY(), sentY));
                }
            }
        } else if (f != null && f.riseLeft > 0) {
            // danach genauso viel wieder hoch (Steigen ist für den Server kein Problem)
            if (!f.isEnabled() || !p.getAbilities().flying || p.onGround()) f.riseLeft = 0;
            else {
                double up = Math.min(f.riseLeft, 0.05);
                f.riseLeft -= up;
                if (mc.level != null && mc.level.noCollision(p, p.getBoundingBox().move(0, up, 0)))
                    p.setPos(p.getX(), p.getY() + up, p.getZ());
            }
        }
        sentY = p.getY();
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
