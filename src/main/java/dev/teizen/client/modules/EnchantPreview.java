package dev.teizen.client.modules;

import dev.teizen.client.TeizenClient;
import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Zeigt am Zaubertisch für alle 3 Möglichkeiten ALLE Verzauberungen, die herauskommen (nicht nur den Hinweis).
 * Dafür braucht es den Zauber-Code des Spielers: in der eigenen Welt direkt, auf Servern per /data (braucht OP).
 */
public final class EnchantPreview extends Module {
    private final Setting askServer = add(Setting.bool("server", "Auf Servern den Zauber-Code per /data holen (braucht OP)", true));
    private static final Pattern NUMBER = Pattern.compile("-?\\d+");

    /** Voller Zauber-Code (Seed) oder null, solange unbekannt. */
    private Integer seed;
    private int syncedSeed = Integer.MIN_VALUE;
    private boolean requested;
    private int requestTick;
    private boolean warned;

    /** Vorhersage je Möglichkeit (Selbsttest + Anzeige). */
    @SuppressWarnings("unchecked")
    public final List<EnchantmentInstance>[] predicted = new List[]{List.of(), List.of(), List.of()};
    public boolean seedKnown;

    public EnchantPreview() {
        super("EnchantPreview", Category.SICHT, "Zeigt am Zaubertisch alle kommenden Verzauberungen.");
    }

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof EnchantmentScreen es)) return;
            ScreenEvents.afterExtract(screen).register((s, g, mx, my, delta) -> {
                EnchantPreview ep = TeizenClient.modules().get(EnchantPreview.class);
                if (ep != null && ep.isEnabled()) ep.draw(es, g);
            });
        });
        // die Antwort auf "/data get entity @s XpSeed" holt ServerQuery (und versteckt sie im Chat)
    }

    @Override
    protected void onEnable() {
        seed = null;
        syncedSeed = Integer.MIN_VALUE;
        requested = false;
        warned = false;
    }

    @Override
    public void onTick() {
        if (!inGame() || !(mc.gui.screen() instanceof EnchantmentScreen es)) return;
        EnchantmentMenu menu = es.getMenu();
        int synced = menu.getEnchantmentSeed();
        if (synced != syncedSeed) {
            // neuer Code (z.B. nach dem Verzaubern) -> neu holen
            syncedSeed = synced;
            seed = null;
            requested = false;
        }
        if (seed == null) {
            if (synced < Short.MIN_VALUE || synced > Short.MAX_VALUE) {
                seed = synced; // Server schickt den ganzen Code mit
            } else {
                IntegratedServer server = mc.getSingleplayerServer();
                if (server != null) {
                    if (!requested) {
                        requested = true;
                        java.util.UUID id = mc.player.getUUID();
                        server.execute(() -> {
                            var sp = server.getPlayerList().getPlayer(id);
                            if (sp != null) {
                                int v = sp.getEnchantmentSeed();
                                mc.execute(() -> seed = v);
                            }
                        });
                    }
                } else if (askServer.on() && !requested) {
                    requested = true;
                    requestTick = mc.player.tickCount;
                    dev.teizen.client.util.ServerQuery.data("@s", "XpSeed", txt -> {
                        Matcher m = NUMBER.matcher(txt);
                        if (m.find()) {
                            try { seed = Integer.parseInt(m.group()); } catch (NumberFormatException ignored) { }
                        }
                    }, null);
                } else if (requested && mc.player.tickCount - requestTick > 60 && !warned) {
                    warned = true;
                    dev.teizen.client.util.Chat.error("EnchantPreview: für die ganze Liste braucht man OP (Befehl /data). Ohne OP nur der normale Hinweis.");
                }
            }
        }
        seedKnown = seed != null;
        ItemStack stack = menu.getSlot(0).getItem();
        for (int i = 0; i < 3; i++) predicted[i] = seed == null ? List.of() : list(stack, i, menu.costs[i], seed);
    }

    /** Genau wie der Server: Zufall mit (Code + Möglichkeit), dann Verzauberungen auswählen. */
    public static List<EnchantmentInstance> list(ItemStack stack, int slot, int cost, int seed) {
        if (stack.isEmpty() || cost <= 0) return List.of();
        RandomSource random = RandomSource.create(seed + slot);
        Registry<Enchantment> reg = mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        Optional<HolderSet.Named<Enchantment>> table = reg.get(EnchantmentTags.IN_ENCHANTING_TABLE);
        if (table.isEmpty()) return List.of();
        List<EnchantmentInstance> list = new ArrayList<>(EnchantmentHelper.selectEnchantment(random, stack, cost, table.get().stream()));
        if (stack.is(Items.BOOK) && list.size() > 1) list.remove(random.nextInt(list.size()));
        return list;
    }

    /** Stimmt der Hinweis des Servers mit unserer Vorhersage überein? */
    public boolean clueMatches(EnchantmentMenu menu, int slot) {
        if (menu.enchantClue[slot] < 0 || predicted[slot].isEmpty()) return false;
        Registry<Enchantment> reg = mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        for (EnchantmentInstance e : predicted[slot]) {
            if (reg.getId(e.enchantment().value()) == menu.enchantClue[slot] && e.level() == menu.levelClue[slot]) return true;
        }
        return false;
    }

    public static String roman(int n) {
        String[] r = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return n >= 0 && n < r.length ? r[n] : String.valueOf(n);
    }

    public static String name(EnchantmentInstance e) {
        Holder<Enchantment> h = e.enchantment();
        return h.value().description().getString() + (h.value().getMaxLevel() > 1 ? " " + roman(e.level()) : "");
    }

    private void draw(EnchantmentScreen es, GuiGraphicsExtractor g) {
        EnchantmentMenu menu = es.getMenu();
        var font = mc.font;
        int left = (es.width - 176) / 2, top = (es.height - 166) / 2;
        List<String> lines = new ArrayList<>();
        lines.add("§d§lVerzauberungen:");
        for (int i = 0; i < 3; i++) {
            if (menu.costs[i] <= 0) continue;
            lines.add("§e" + (i + 1) + ") §7" + menu.costs[i] + " Level, " + (i + 1) + " Lapis" + (seed != null && !clueMatches(menu, i) ? " §c?" : ""));
            if (seed == null) {
                lines.add("  §8(Code unbekannt – braucht OP)");
                continue;
            }
            for (EnchantmentInstance e : predicted[i]) lines.add("  §f" + name(e));
        }
        if (lines.size() == 1) lines.add("§7Item und Lapis hineinlegen");
        int w = 0;
        for (String l : lines) w = Math.max(w, font.width(l));
        int x = left + 176 + 4;
        if (x + w + 6 > es.width) x = Math.max(2, left - w - 10);
        int y = top;
        g.fill(x - 3, y - 3, x + w + 3, y + lines.size() * 10 + 1, 0xE0100820);
        for (String l : lines) {
            g.text(font, l, x, y, 0xFFFFFFFF, false);
            y += 10;
        }
    }

    @Override
    public String info() { return seed != null ? "✓" : ""; }
}
