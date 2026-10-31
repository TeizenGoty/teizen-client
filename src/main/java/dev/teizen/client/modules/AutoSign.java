package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.world.level.block.entity.SignBlockEntity;

/** AutoSign: setzt du ein Schild, wird es sofort mit deinem Text beschrieben (Zeilen mit | trennen). */
public final class AutoSign extends Module {
    private final Setting text = add(Setting.text("text", "Text auf dem Schild – Zeilen mit | trennen (max. 4)", "Teizen|war|hier|"));
    public int signs;

    public AutoSign() {
        super("AutoSign", Category.BAUEN, "Beschreibt gesetzte Schilder automatisch mit deinem Text.");
    }

    @Override
    public void onTick() {
        if (!inGame() || !(mc.gui.screen() instanceof AbstractSignEditScreen scr)) return;
        SignBlockEntity sign = null;
        boolean front = true;
        // Felder per Typ suchen (Namen ändern sich zwischen Versionen)
        try {
            for (Class<?> c = scr.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                for (var f : c.getDeclaredFields()) {
                    if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                    if (SignBlockEntity.class.isAssignableFrom(f.getType())) { f.setAccessible(true); sign = (SignBlockEntity) f.get(scr); }
                    else if (f.getType() == boolean.class && f.getName().toLowerCase().contains("front")) { f.setAccessible(true); front = f.getBoolean(scr); }
                }
            }
        } catch (Throwable t) {
            dev.teizen.client.TeizenClient.LOG.warn("AutoSign: " + t);
        }
        if (sign == null) return;
        String[] l = (text.str() + "| | | ").split("\\|", -1);
        Object pkt = packet(sign.getBlockPos(), front, l);
        if (pkt == null) { dev.teizen.client.TeizenClient.LOG.warn("AutoSign: Schild-Paket nicht gefunden"); return; }
        mc.player.connection.send((ServerboundSignUpdatePacket) pkt);
        signs++;
        mc.gui.setScreen(null);
    }

    /** Schild-Paket bauen – 26.2: (pos, vorne?, 4 Zeilen), 26.3: (pos, Liste der Zeilen, Seite). */
    private static Object packet(net.minecraft.core.BlockPos pos, boolean front, String[] l) {
        try {
            for (var c : ServerboundSignUpdatePacket.class.getConstructors()) {
                Class<?>[] ps = c.getParameterTypes();
                if (ps.length == 6 && ps[1] == boolean.class) return c.newInstance(pos, front, l[0], l[1], l[2], l[3]);
                if (ps.length == 3 && java.util.List.class.isAssignableFrom(ps[1]) && ps[2].isEnum()) {
                    Object side = null;
                    for (Object k : ps[2].getEnumConstants()) if (k.toString().toUpperCase().contains(front ? "FRONT" : "BACK")) side = k;
                    if (side == null) side = ps[2].getEnumConstants()[front ? 0 : 1];
                    return c.newInstance(pos, java.util.List.of(l[0], l[1], l[2], l[3]), side);
                }
            }
        } catch (Throwable t) {
            dev.teizen.client.TeizenClient.LOG.warn("AutoSign Paket: " + t);
        }
        return null;
    }

    @Override
    public String info() { return String.valueOf(signs); }
}
