package dev.teizen.client.modules;

import dev.teizen.client.TeizenClient;
import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.util.Chat;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Notifier: Meldungen im Chat – Totem geplatzt (mit Zähler), Spieler kommt/geht, Spieler in der Nähe gestorben. */
public final class Notifier extends Module {
    private final Setting pops = add(Setting.bool("totems", "Wenn bei jemandem ein Totem platzt (mit Anzahl)", true));
    private final Setting joins = add(Setting.bool("joinleave", "Wenn jemand den Server betritt oder verlässt", true));
    private final Setting deaths = add(Setting.bool("tode", "Wenn ein Spieler in deiner Nähe stirbt", true));

    private final Map<UUID, Integer> popCount = new HashMap<>();
    private Set<UUID> lastTab;
    private final Map<UUID, String> names = new HashMap<>();
    private final Set<Integer> deadSeen = new HashSet<>();
    private Object lastLevel;
    public int messages;

    public Notifier() {
        super("Notifier", Category.SONSTIGES, "Chat-Meldungen: Totem-Pops, wer kommt/geht, wer stirbt.");
    }

    @Override
    protected void onEnable() { lastTab = null; popCount.clear(); deadSeen.clear(); }

    /** Aus ClientPacketListener.handleEntityEvent (Mixin): 35 = Totem geplatzt. */
    public static void onEntityEvent(Entity e, byte id) {
        if (TeizenClient.modules() == null || e == null) return;
        if (id == 35) PopChams.onPop(e);
        Notifier n = TeizenClient.modules().get(Notifier.class);
        if (n == null || !n.isEnabled() || !(e instanceof Player p) || !n.pops.on()) return;
        if (id != 35) return;
        int c = n.popCount.merge(p.getUUID(), 1, Integer::sum);
        String who = p == mc.player ? "§cDu" : "§f" + p.getGameProfile().name();
        Chat.info(who + " §7hat " + (p == mc.player ? "dein" : "sein") + " §e" + c + ". Totem§7 verbraucht.");
        n.messages++;
    }

    @Override
    public void onTick() {
        if (!inGame()) return;
        if (lastLevel != mc.level) { lastLevel = mc.level; lastTab = null; deadSeen.clear(); }
        // Kommt/geht
        Set<UUID> now = new HashSet<>();
        for (var info : mc.player.connection.getOnlinePlayers()) {
            UUID id = info.getProfile().id();
            now.add(id);
            names.put(id, info.getProfile().name());
        }
        if (lastTab != null && joins.on()) {
            for (UUID id : now) if (!lastTab.contains(id)) { Chat.info("§a+ §f" + names.get(id) + " §7ist online gekommen."); messages++; }
            for (UUID id : lastTab) if (!now.contains(id)) { Chat.info("§c- §f" + names.getOrDefault(id, "?") + " §7ist offline gegangen."); messages++; popCount.remove(id); }
        }
        lastTab = now;
        // Tode in der Nähe
        if (deaths.on()) {
            for (Player p : mc.level.players()) {
                if (p == mc.player) continue;
                if (p.isDeadOrDying()) {
                    if (deadSeen.add(p.getId())) {
                        int c = popCount.getOrDefault(p.getUUID(), 0);
                        Chat.info("§4☠ §f" + p.getGameProfile().name() + " §7ist gestorben" + (c > 0 ? " (nach " + c + " Totems)" : "") + ".");
                        messages++;
                        popCount.remove(p.getUUID());
                    }
                } else deadSeen.remove(p.getId());
            }
        }
    }
}
