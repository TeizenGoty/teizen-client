package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.util.Inv;
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
    private final Setting maxSpeed = add(Setting.number("maxtempo", "Höchstens so viele Blöcke pro Tick – schneller lädt der Server nicht mit und setzt dich zurück", 4, 1, 9));
    private final Setting lavaGuard = add(Setting.bool("lavaschutz", "Beim Fliegen nie in Lava sinken; drin = sofort raus nach oben", true));
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
        // nur wenn die Sprint-Taste WIRKLICH gedrückt ist (sonst war man zufällig schnell: Sprint vom Boden mitgenommen,
        // 2x W getippt oder AutoSprint)
        boolean sprint = mc.options.keySprint.isDown();
        if (a.flying && !sprint && mc.player.isSprinting()) mc.player.setSprinting(false);
        double f = sprint ? Math.max(1, sprintFactor.num() / 2.0) : 1;
        a.setFlyingSpeed((float) (0.05 * speed.num() * f));
        // in Lava geraten (z.B. vom Server zurückgesetzt): weiterfliegen und sofort nach oben raus
        if (lavaGuard.on() && mc.player.isInLava()) {
            a.flying = true;
            var dv = mc.player.getDeltaMovement();
            mc.player.setDeltaMovement(dv.x, Math.max(dv.y, 0.8), dv.z);
        }
        if (!a.flying) {
            // nicht am Fliegen (z.B. nach 2x Leertaste in der Luft): Fallschaden verhindert beforeSendPosition
            if (noFall.on()) mc.player.fallDistance = 0;
            return;
        }
        var p = mc.player;
        var v = p.getDeltaMovement();
        // Tempo begrenzen: sonst setzt der Server dich zurück ("buggt", plötzlich woanders)
        double cap = maxSpeed.num(), h = Math.sqrt(v.x * v.x + v.z * v.z);
        if (h > cap) { v = new net.minecraft.world.phys.Vec3(v.x * cap / h, v.y, v.z * cap / h); p.setDeltaMovement(v); }
        if (Math.abs(v.y) > cap) p.setDeltaMovement(v.x, Math.signum(v.y) * cap, v.z);
        if (antiKick.on() && floatStage == 0 && !mc.player.onGround() && ++ticks % 20 == 0) floatStage = 3;
    }

    /**
     * Gegen den Server-Rauswurf "Flying is not enabled on this server!" (schwebt zu lange), wie bei Wurst:
     * alle 20 Ticks meldet das Bewegungs-Paket 3 Ticks lang eine etwas tiefere Höhe (je 0.05 unter das letzte Paket) – man selbst bewegt sich nicht, direkt bevor Minecraft
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
                if (mc.level != null && mc.level.noCollision(p, p.getBoundingBox().move(0, dy, 0))
                        && !fluidIn(p.getBoundingBox().move(0, dy - 0.1, 0), false)) { // nie in Wasser/Lava absenken
                    // nur für dieses Paket tiefer – nach dem Senden sofort zurück (man selbst bewegt sich NICHT)
                    restoreY = p.getY();
                    p.setPos(p.getX(), p.getY() + dy, p.getZ());
                    f.dipped = true;
                    dipPacket = true;
                    if (f.floatStage == 0) f.protectedPackets++;
                    if (dev.teizen.client.SelfTest.ACTIVE)
                        dev.teizen.client.TeizenClient.LOG.info(String.format(java.util.Locale.ROOT, "Fly-AntiKick: abgesenkt um %.3f auf %.3f (letztes Paket %.3f)", -dy, p.getY(), sentY));
                }
            }
        }
        // Lavaschutz: nach der Bewegung dieses Ticks, BEVOR das Paket rausgeht – ist man in/kurz über Lava,
        // sofort knapp über die Oberfläche setzen (der Server sieht einen nie in der Lava)
        // (nicht nur beim Fliegen: berührt man den Boden unter der Lava, schaltet Minecraft das Fliegen kurz aus)
        if (f != null && f.isEnabled() && f.lavaGuard.on() && !p.isCreative()) {
            double top = lavaTop(p.getBoundingBox());
            if (!Double.isNaN(top) && p.getY() < top + LAVA_GAP) {
                var up = p.getBoundingBox().move(0, top + LAVA_GAP - p.getY(), 0);
                if (dev.teizen.client.SelfTest.ACTIVE && f.lavaSaves % 5 == 0)
                    dev.teizen.client.TeizenClient.LOG.info(String.format(java.util.Locale.ROOT, "Lavaschutz: y %.3f -> %.3f (Oberkante %.2f, frei=%s, gesendet zuletzt %.3f, gedippt=%s)",
                            p.getY(), top + LAVA_GAP, top, mc.level.noCollision(p, up), sentY, !Double.isNaN(restoreY)));
                if (mc.level.noCollision(p, up)) {
                    p.setPos(p.getX(), top + LAVA_GAP, p.getZ());
                    p.setDeltaMovement(p.getDeltaMovement().x, Math.max(0, p.getDeltaMovement().y), p.getDeltaMovement().z);
                    p.clearFire();
                    f.lavaSaves++;
                }
            }
        }
        // Fallschutz: beim Runtergehen sagt das NORMALE Bewegungs-Paket "am Boden" -> der Server zählt keine
        // Fallhöhe (vorher ein Extra-Paket pro Tick: mit Sprint fällt man >3 Blöcke/Tick -> trotzdem Schaden)
        // Die Anti-Kick-Absenkung immer als "am Boden" melden: sonst sammelt der Server beim Schweben Fallhöhe an
        // und man stirbt beim nächsten Runterfliegen mitten in der Luft (vorher so passiert)
        // Der Server rechnet Fallschaden pro Paket: mehr als 3 Blöcke in EINEM Tick = Schaden, auch mit "am Boden".
        // Darum beim Fliegen mit Fallschutz höchstens 2.9 Blöcke pro Tick sinken.
        if (f != null && f.isEnabled() && f.noFall.on() && !Double.isNaN(sentY) && sentY - p.getY() > 2.9 && !p.isFallFlying()) {
            p.setPos(p.getX(), sentY - 2.9, p.getZ());
            p.setDeltaMovement(p.getDeltaMovement().x, Math.max(p.getDeltaMovement().y, -2.9), p.getDeltaMovement().z);
        }
        boolean falling = !p.onGround() && !Double.isNaN(sentY) && p.getY() < sentY - 0.001;
        if (!falling) fallStartY = Double.NaN;
        else if (Double.isNaN(fallStartY)) fallStartY = sentY;
        // NoFall nutzt denselben Weg (vorher Extra-Paket erst ab 2 Blöcken -> bei schnellem Fall trotzdem Schaden in der Luft)
        NoFall nf = dev.teizen.client.TeizenClient.modules().get(NoFall.class);
        // Fallhöhe, die der Server VOR diesem Paket schon gespeichert hat (Client rechnet genauso mit)
        double storedFall = Math.max(0, p.fallDistance - (Double.isNaN(sentY) ? 0 : sentY - p.getY()));
        MaceSwap ms = dev.teizen.client.TeizenClient.modules().get(MaceSwap.class);
        boolean maceDive = ms != null && ms.isEnabled() && Inv.findHotbar(dev.teizen.client.util.WeaponSwap::isMace) >= 0;
        // nur "am Boden" melden, solange gespeichert < 3 Blöcke (= kein Schaden). Schon mehr gespeichert (z.B. nach
        // einem Elytra-Sturz) -> nichts tun, sonst gäbe es den Fallschaden sofort MITTEN IN DER LUFT. Mace-PvP: der
        // Streitkolben braucht die Fallhöhe, der Treffer setzt sie beim Server selbst zurück.
        boolean noFallWants = nf != null && nf.isEnabled() && falling && !maceDive && storedFall < 2.9
                && fallStartY - p.getY() >= nf.from();
        boolean flyWants = f != null && (f.noFall.on() || dipPacket) && (f.isEnabled() || f.guardUntilGround);
        // nie beim Elytra-Gleiten ("am Boden" würde das Gleiten beim Server beenden)
        if (noFallWants && sentY - p.getY() > 2.9 && mc.level.noCollision(p, p.getBoundingBox().move(0, sentY - 2.9 - p.getY(), 0))) {
            // sehr langer Sturz (> 3 Blöcke pro Tick) -> etwas bremsen, sonst Schaden pro Paket
            p.setPos(p.getX(), sentY - 2.9, p.getZ());
            p.setDeltaMovement(p.getDeltaMovement().x, -2.9, p.getDeltaMovement().z);
        }
        if ((flyWants || noFallWants) && !p.isCreative() && !p.isSpectator() && !p.isFallFlying() && falling) {
            p.setOnGround(true);
            groundSpoofed = true;
            if (f != null) f.groundPackets++;
            if (noFallWants) nf.saved++;
        }
        dipPacket = false;
        if (logPackets > 0) {
            logPackets--;
            dev.teizen.client.TeizenClient.LOG.info(String.format(java.util.Locale.ROOT, "Fly-Paket: y=%.3f amBoden=%s vy=%.3f fallDist=%.2f",
                    p.getY(), p.onGround(), p.getDeltaMovement().y, (double) p.fallDistance));
        }
        sentY = p.getY();
    }

    /** Selbsttest: so viele Pakete mitloggen. */
    public static int logPackets;
    private static boolean dipPacket;
    /** Höhe, bei der der aktuelle Fall angefangen hat (für NoFall "ab"). */
    private static double fallStartY = Double.NaN;
    /** Beim Fliegen kein Sprinten ohne gedrückte Sprint-Taste (Entity.setSprinting). */
    public static boolean blockSprint(boolean on) {
        if (!on || mc.player == null || dev.teizen.client.TeizenClient.modules() == null) return false;
        Fly f = dev.teizen.client.TeizenClient.modules().get(Fly.class);
        return f != null && f.isEnabled() && mc.player.getAbilities().flying && !mc.options.keySprint.isDown();
    }

    /** Wie oft ein Server-Ruck nach unten (bei Schaden) ignoriert wurde (Selbsttest). */
    public int pullsBlocked;

    /** Nach einem Bewegungs-Paket vom Server (Schaden/Rückstoß): beim Fliegen nicht nach unten reißen lassen. */
    public static void onServerMotion(net.minecraft.world.phys.Vec3 before) {
        var p = mc.player;
        if (p == null || before == null || p.isFallFlying() || dev.teizen.client.TeizenClient.modules() == null) return;
        Fly f = dev.teizen.client.TeizenClient.modules().get(Fly.class);
        if (f == null || !f.isEnabled() || !p.getAbilities().flying) return;
        var v = p.getDeltaMovement();
        if (v.y < before.y && v.y < 0) {
            p.setDeltaMovement(v.x, Math.max(before.y, 0), v.z);
            f.pullsBlocked++;
        }
    }

    /** Abstand über der Lava: der Server brennt einen schon knapp (0.3) über der Lava an -> 1 Block Abstand. */
    private static final double LAVA_GAP = 1.0;
    /** Wie oft die Bewegung vor der Lava gestoppt wurde (Selbsttest). */
    public int lavaStops;

    /** Vor dem Bewegen: nach unten höchstens bis knapp über die Lava (über Entity.move). */
    public static net.minecraft.world.phys.Vec3 clampLava(net.minecraft.world.phys.Vec3 mv) {
        if (mv.y >= 0 || mc.player == null || mc.player.isCreative() || mc.player.isSpectator()) return mv;
        Fly f = dev.teizen.client.TeizenClient.modules().get(Fly.class);
        if (f == null || !f.isEnabled() || !f.lavaGuard.on()) return mv;
        var b = mc.player.getBoundingBox();
        double top = lavaTop(b.expandTowards(mv.x, mv.y, mv.z));
        if (Double.isNaN(top)) return mv;
        double floor = top + LAVA_GAP;
        double y = mc.player.getY();
        if (y < floor - 1e-4) return mv; // schon drin -> beforeSendPosition holt einen raus
        if (y + mv.y >= floor) return mv;
        f.lavaStops++;
        return new net.minecraft.world.phys.Vec3(mv.x, floor - y, mv.z);
    }

    /** Wie oft der Lavaschutz einen gerettet hat (Selbsttest). */
    public int lavaSaves;

    /** Oberkante der Lava in/unter diesem Bereich (bis 1 Block unter den Füßen), oder NaN. */
    private static double lavaTop(net.minecraft.world.phys.AABB b) {
        if (mc.level == null) return Double.NaN;
        double top = Double.NaN;
        for (net.minecraft.core.BlockPos q : net.minecraft.core.BlockPos.betweenClosed(
                (int) Math.floor(b.minX), (int) Math.floor(b.minY - LAVA_GAP - 1.0), (int) Math.floor(b.minZ),
                (int) Math.floor(b.maxX - 1e-7), (int) Math.floor(b.maxY - 1e-7), (int) Math.floor(b.maxZ - 1e-7))) {
            var fs = mc.level.getFluidState(q);
            if (fs.isEmpty() || !fs.is(net.minecraft.tags.FluidTags.LAVA)) continue;
            // ganze Lava-Säule nach oben verfolgen
            net.minecraft.core.BlockPos t = q.immutable();
            while (mc.level.getFluidState(t.above()).is(net.minecraft.tags.FluidTags.LAVA)) t = t.above();
            double y = t.getY() + 1.0;
            if (Double.isNaN(top) || y > top) top = y;
        }
        return top;
    }

    /** Ist in diesem Bereich Flüssigkeit (nur Lava, wenn lavaOnly)? */
    private static boolean fluidIn(net.minecraft.world.phys.AABB b, boolean lavaOnly) {
        if (mc.level == null) return false;
        for (net.minecraft.core.BlockPos q : net.minecraft.core.BlockPos.betweenClosed(
                (int) Math.floor(b.minX), (int) Math.floor(b.minY), (int) Math.floor(b.minZ),
                (int) Math.floor(b.maxX - 1e-7), (int) Math.floor(b.maxY - 1e-7), (int) Math.floor(b.maxZ - 1e-7))) {
            var fs = mc.level.getFluidState(q);
            if (fs.isEmpty()) continue;
            if (!lavaOnly || fs.is(net.minecraft.tags.FluidTags.LAVA)) return true;
        }
        return false;
    }

    private static boolean groundSpoofed;
    /** Wie oft beim Runtergehen "am Boden" gemeldet wurde (Selbsttest). */
    public int groundPackets;

    /** Ende von LocalPlayer.sendPosition (Mixin): "am Boden" nur für das Paket, danach wieder normal. */
    public static void afterSendPosition() {
        var p = mc.player;
        if (p == null) { groundSpoofed = false; restoreY = Double.NaN; return; }
        if (!Double.isNaN(restoreY)) {
            // Anti-Kick: wieder auf die echte Höhe (die Absenkung war nur im Paket)
            p.setPos(p.getX(), restoreY, p.getZ());
            restoreY = Double.NaN;
        }
        if (!groundSpoofed) return;
        groundSpoofed = false;
        p.setOnGround(false);
        p.fallDistance = 0;
    }

    private static double restoreY = Double.NaN;

    @Override
    public String info() { return Setting.fmt(speed.num()) + (mc.player != null && !mc.player.getAbilities().flying ? " §8(2x Leertaste)" : ""); }

    /** Läuft jeden Tick (auch wenn Fly aus ist): Schutz nach dem Ausschalten in der Luft. */
    public void fallGuard() {
        if (!guardUntilGround || isEnabled() || !inGame()) return;
        if (mc.player.onGround() || mc.player.isInWater() || mc.player.getAbilities().flying) { guardUntilGround = false; return; }
        mc.player.fallDistance = 0; // die Pakete meldet beforeSendPosition als "am Boden"
    }
}
