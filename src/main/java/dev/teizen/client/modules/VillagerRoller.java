package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.util.Breaker;
import dev.teizen.client.util.Chat;
import dev.teizen.client.util.Inv;
import dev.teizen.client.util.Placer;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.EntityHitResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * VillagerRoller: baut den Arbeitsblock eines Dorfbewohners (z.B. Lesepult) ab und wieder auf,
 * bis er das gewünschte Angebot hat (z.B. Mending). Geht nur, solange noch nie mit ihm gehandelt wurde.
 * So geht's: neben Dorfbewohner + Arbeitsblock stellen, einschalten (z.B. ///villagerroller mending).
 */
public final class VillagerRoller extends Module {
    public final Setting wanted = add(Setting.text("suche", "Gesuchtes Angebot, z.B. mending, unbreaking 3, diamond (mehrere mit Komma)", "mending"));
    private final Setting maxPrice = add(Setting.number("maxpreis", "Höchstens so viele Smaragde (0 = egal)", 0, 0, 64));
    public final Setting maxTries = add(Setting.number("versuche", "Wie oft höchstens neu gewürfelt wird (0 = bis gefunden)", 0, 0, 10000));

    private static final Set<Block> JOB_BLOCKS = Set.of(Blocks.LECTERN, Blocks.COMPOSTER, Blocks.SMOKER, Blocks.BLAST_FURNACE,
            Blocks.CARTOGRAPHY_TABLE, Blocks.BREWING_STAND, Blocks.BARREL, Blocks.FLETCHING_TABLE, Blocks.CAULDRON,
            Blocks.STONECUTTER, Blocks.LOOM, Blocks.GRINDSTONE, Blocks.SMITHING_TABLE);

    private enum Stage { FIND, OPEN, WAIT_SCREEN, BREAK, WAIT_JOBLESS, PLACE, WAIT_JOB }

    private Stage stage = Stage.FIND;
    private Villager villager;
    private BlockPos jobPos;
    private Item jobItem;
    private int timer, openFails;

    /** Für Anzeige und Selbsttest. */
    public int tries, rolls;
    public boolean found;
    public final List<String> lastOffers = new ArrayList<>();
    public final List<String> lastOfferIds = new ArrayList<>();

    public VillagerRoller() {
        super("VillagerRoller", Category.SONSTIGES, "Würfelt Dorfbewohner-Angebote neu, bis das gewünschte dabei ist.");
    }

    @Override
    protected void onEnable() {
        stage = Stage.FIND;
        tries = 0;
        rolls = 0;
        found = false;
        openFails = 0;
        villager = null;
        jobPos = null;
    }

    @Override
    protected void onDisable() {
        stage = Stage.FIND;
    }

    private boolean jobless() {
        return villager.getVillagerData().profession().is(VillagerProfession.NONE);
    }

    private void stop(String msg, boolean error) {
        if (error) Chat.error("VillagerRoller: " + msg);
        else Chat.ok("VillagerRoller: " + msg);
        setEnabled(false);
    }

    @Override
    public void onTick() {
        if (!inGame()) return;
        if (stage != Stage.FIND && (villager == null || !villager.isAlive())) { stop("Dorfbewohner ist weg.", true); return; }
        switch (stage) {
            case FIND -> find();
            case OPEN -> {
                if (jobless()) { stage = Stage.WAIT_JOB; timer = 600; return; }
                if (mc.gui.screen() != null) return;
                mc.gameMode.interact(mc.player, villager, new EntityHitResult(villager), InteractionHand.MAIN_HAND);
                stage = Stage.WAIT_SCREEN;
                timer = 60;
            }
            case WAIT_SCREEN -> {
                if (mc.gui.screen() instanceof MerchantScreen ms && !ms.getMenu().getOffers().isEmpty()) {
                    check(ms.getMenu());
                    return;
                }
                if (--timer <= 0) {
                    if (++openFails > 3) { stop("Handel öffnet sich nicht (Baby oder kein Beruf?).", true); return; }
                    stage = Stage.OPEN;
                }
            }
            case BREAK -> {
                BlockState st = mc.level.getBlockState(jobPos);
                if (st.isAir() || !st.is(jobBlock())) { stage = Stage.WAIT_JOBLESS; timer = 300; return; }
                Inv.ensureInHand(s -> s.getDestroySpeed(st) > 1.5f);
                Breaker.tick(jobPos, true, false);
            }
            case WAIT_JOBLESS -> {
                if (jobless()) { stage = Stage.PLACE; timer = 100; return; }
                if (--timer <= 0) stop("Der Dorfbewohner behält seinen Beruf (anderer Arbeitsblock in der Nähe?).", true);
            }
            case PLACE -> {
                if (mc.level.getBlockState(jobPos).is(jobBlock())) {
                    tries++;
                    stage = Stage.WAIT_JOB;
                    timer = 900;
                    return;
                }
                if (!Inv.ensureInHand(s -> s.is(jobItem))) {
                    if (--timer <= 0) stop("Kein " + new ItemStack(jobItem).getHoverName().getString() + " im Inventar.", true);
                    return;
                }
                Placer.place(jobPos, true);
            }
            case WAIT_JOB -> {
                if (!jobless()) { stage = Stage.OPEN; openFails = 0; return; }
                if (--timer <= 0) stop("Der Dorfbewohner nimmt den Beruf nicht an.", true);
            }
        }
    }

    private Block jobBlock() {
        return jobItem instanceof net.minecraft.world.item.BlockItem bi ? bi.getBlock() : Blocks.LECTERN;
    }

    private void find() {
        villager = null;
        double best = Double.MAX_VALUE;
        for (Villager v : mc.level.getEntitiesOfClass(Villager.class, mc.player.getBoundingBox().inflate(6))) {
            if (v.isBaby() || !v.isAlive()) continue;
            double d = v.distanceToSqr(mc.player);
            if (d < best) { best = d; villager = v; }
        }
        if (villager == null) { stop("Kein Dorfbewohner in der Nähe (bis 6 Blöcke).", true); return; }
        jobPos = null;
        double bestBlock = Double.MAX_VALUE;
        BlockPos c = villager.blockPosition();
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-4, -2, -4), c.offset(4, 2, 4))) {
            BlockState st = mc.level.getBlockState(p);
            if (!JOB_BLOCKS.contains(st.getBlock())) continue;
            double d = p.distSqr(c);
            if (d < bestBlock && Breaker.inReach(p)) { bestBlock = d; jobPos = p.immutable(); jobItem = st.getBlock().asItem(); }
        }
        if (jobPos == null) { stop("Kein Arbeitsblock (z.B. Lesepult) neben dem Dorfbewohner in deiner Reichweite.", true); return; }
        Chat.info("VillagerRoller: suche §f" + wanted.str() + "§7, Arbeitsblock §f" + new ItemStack(jobItem).getHoverName().getString());
        stage = Stage.OPEN;
    }

    /** Text, in dem gesucht wird: Item-ID, Name, Verzauberungen mit Stufe. */
    private static String describe(ItemStack s) {
        StringBuilder sb = new StringBuilder();
        sb.append(BuiltInRegistries.ITEM.getKey(s.getItem()).getPath()).append(" | ").append(s.getHoverName().getString());
        for (var comp : new ItemEnchantments[]{s.get(DataComponents.STORED_ENCHANTMENTS), s.get(DataComponents.ENCHANTMENTS)}) {
            if (comp == null) continue;
            for (var e : comp.entrySet()) {
                String id = e.getKey().unwrapKey().map(k -> k.identifier().getPath()).orElse("?");
                String name = e.getKey().value().description().getString();
                sb.append(" | ").append(id).append(' ').append(e.getIntValue()).append(" | ").append(name).append(' ').append(e.getIntValue());
            }
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    private boolean matches(MerchantOffer o) {
        String text = describe(o.getResult());
        if (maxPrice.integer() > 0) {
            ItemStack cost = o.getCostA();
            if (cost.is(Items.EMERALD) && cost.getCount() > maxPrice.integer()) return false;
        }
        for (String term : wanted.str().toLowerCase(Locale.ROOT).split(",")) {
            String t = term.trim().replaceAll("\\s+", " ");
            if (!t.isEmpty() && text.contains(t)) return true;
        }
        return false;
    }

    private void check(MerchantMenu menu) {
        rolls++;
        lastOffers.clear();
        lastOfferIds.clear();
        MerchantOffer hit = null;
        for (MerchantOffer o : menu.getOffers()) {
            ItemStack r = o.getResult();
            String line = o.getCostA().getCount() + "x " + o.getCostA().getHoverName().getString() + " -> " + r.getCount() + "x " + describe(r).split(" \\| ")[1];
            String ench = describe(r);
            int i = ench.indexOf(" | ", ench.indexOf(" | ") + 3);
            if (i > 0) line += " (" + ench.substring(i + 3).replace(" | ", ", ") + ")";
            lastOffers.add(line);
            lastOfferIds.add(BuiltInRegistries.ITEM.getKey(r.getItem()).getPath());
            if (hit == null && matches(o)) hit = o;
        }
        Chat.info("Wurf " + rolls + ": §f" + String.join("§7; §f", lastOffers));
        if (hit != null) {
            found = true;
            mc.player.playSound(SoundEvents.PLAYER_LEVELUP, 1f, 1f);
            stop("§6Gefunden nach " + tries + " Mal neu würfeln: §f" + lastOffers.get(menu.getOffers().indexOf(hit)) + "§a – jetzt handeln!", false);
            return;
        }
        if (menu.getTraderXp() > 0 || menu.getTraderLevel() > 1) {
            mc.player.closeContainer();
            stop("Mit diesem Dorfbewohner wurde schon gehandelt – seine Angebote ändern sich nicht mehr.", true);
            return;
        }
        if (maxTries.integer() > 0 && tries >= maxTries.integer()) {
            mc.player.closeContainer();
            stop("Nach " + tries + " Versuchen nicht gefunden.", true);
            return;
        }
        mc.player.closeContainer();
        stage = Stage.BREAK;
    }

    @Override
    public String info() { return tries > 0 ? String.valueOf(tries) : ""; }
}
