package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import net.minecraft.world.InteractionHand;

/** AntiAFK: bewegt dich ab und zu ein bisschen (drehen, springen, Arm schwingen), damit der Server dich nicht wegen AFK kickt. */
public final class AntiAFK extends Module {
    private final Setting interval = add(Setting.number("intervall", "Alle so viele Sekunden etwas tun", 15, 3, 120));
    private final Setting jump = add(Setting.bool("springen", "Kurz springen", true));
    private final Setting turn = add(Setting.bool("drehen", "Kopf ein Stück drehen", true));
    private final Setting swing = add(Setting.bool("schwingen", "Arm schwingen", true));
    private final Setting chat = add(Setting.text("nachricht", "Nachricht in den Chat (leer = nichts schreiben)", ""));
    private int timer;
    private int jumpTicks;
    public int actions;

    public AntiAFK() {
        super("AntiAFK", Category.SONSTIGES, "Bewegt dich ab und zu, damit du nicht wegen AFK gekickt wirst.");
    }

    @Override
    protected void onEnable() { timer = 0; }

    @Override
    protected void onDisable() { if (mc.options != null && jumpTicks > 0) mc.options.keyJump.setDown(false); jumpTicks = 0; }

    @Override
    public void onTick() {
        if (!inGame()) return;
        if (jumpTicks > 0 && --jumpTicks == 0) mc.options.keyJump.setDown(false);
        if (++timer < interval.num() * 20) return;
        timer = 0;
        actions++;
        if (turn.on()) mc.player.setYRot(mc.player.getYRot() + (float) (Math.random() * 60 - 30));
        if (jump.on() && mc.player.onGround()) { mc.options.keyJump.setDown(true); jumpTicks = 2; }
        if (swing.on()) mc.player.swing(InteractionHand.MAIN_HAND);
        if (!chat.str().isBlank()) mc.player.connection.sendChat(chat.str());
    }

    @Override
    public String info() { return interval.integer() + "s"; }
}
