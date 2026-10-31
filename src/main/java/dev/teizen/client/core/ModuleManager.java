package dev.teizen.client.core;

import dev.teizen.client.modules.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Alle Funktionen an einem Ort. */
public final class ModuleManager {
    private final Map<Class<? extends Module>, Module> byClass = new LinkedHashMap<>();
    private final List<Module> all = new ArrayList<>();

    public ModuleManager() {
        // Kampf
        add(new KillAura());
        add(new TriggerBot());
        add(new AimAssist());
        add(new AutoCrystal());
        add(new Reach());
        add(new Criticals());
        add(new AntiKnockback());
        add(new Velocity());
        add(new AutoTotem());
        add(new AutoArmor());
        add(new AutoShield());
        add(new ShieldBreaker());
        add(new MaceSwap());
        add(new SpearSwap());
        add(new God());
        add(new GodPvP());
        add(new NoCooldown());
        add(new HitMarker());
        add(new CombatLog());
        // Abbau
        add(new XRay());
        add(new AutoMine());
        add(new Nuker());
        add(new AutoTool());
        add(new FastBreak());
        // Bewegung
        add(new Fly());
        add(new Speed());
        add(new NoFall());
        add(new Step());
        add(new AutoSprint());
        add(new Jesus());
        add(new HighJump());
        add(new NoClip());
        add(new Freecam());
        add(new DolphinMode());
        add(new NoSlowdown());
        add(new SafeWalk());
        add(new InventoryWalk());
        add(new ProjectileDodge());
        add(new ElytraReset());
        add(new ElytraFly());
        add(new BoatFly());
        add(new BoatNoclip());
        add(new Spider());
        add(new ClickTP());
        add(new AutoClutch());
        add(new TridentBoost());
        // Sicht
        add(new PlayerESP());
        add(new SeeInvisible());
        add(new AntiVanish());
        add(new EnchantPreview());
        add(new Nametags());
        add(new ArmorESP());
        add(new Tracers());
        add(new ItemESP());
        add(new StorageESP());
        add(new PlayerFinder());
        add(new Radar());
        add(new PotionHUD());
        add(new PortalFinder());
        add(new StructureFinder());
        add(new Fullbright());
        add(new TargetESP());
        add(new Trajectories());
        add(new HoleESP());
        add(new LowHealthWarning());
        add(new ChunkFinder());
        add(new TimeChanger());
        add(new WeatherChanger());
        // Bauen
        add(new AutoBuild());
        add(new Scaffold());
        add(new FastPlace());
        add(new AirPlace());
        // Sonstiges
        add(new AutoEat());
        add(new FastEat());
        add(new AutoRespawn());
        add(new AutoReconnect());
        add(new ChestStealer());
        add(new AutoFish());
        add(new AutoReplant());
        add(new VillagerRoller());
        add(new MoreDurability());
        add(new ChatSpammer());
        add(new FakeLag());
        add(new AutoReplenish());
        add(new FastUse());
        add(new AutoClicker());
        add(new AutoMend());
        add(new AutoRescue());
        add(new NameProtect());
        add(new SkinChanger());
        add(new AirJump());
        add(new AntiAFK());
        add(new AntiHunger());
        add(new AutoWasp());
        add(new EndermanLook());
        add(new FastClimb());
        add(new GhostHand());
        add(new Hitboxes());
        add(new LightOverlay());
        add(new LiquidFiller());
        add(new NoInteract());
        add(new Parkour());
        add(new ReverseStep());
        add(new Trail());
        add(new VeinMiner());
        add(new Timer());
        add(new Notifier());
        add(new PopChams());
        add(new BreakIndicators());
        add(new FakePlayer());
        add(new EntityOwner());
        add(new AutoBreed());
        add(new AutoSign());
        add(new Multitask());
        add(new Portals());
        add(new BetterTab());
        add(new BetterChat());
        add(new BookBot());
        add(new AutoBrewer());
        add(new EntityControl());
        add(new NoRender());
        add(new WallHack());
        add(new HighwayBuilder());
        add(new Swarm());
        add(new PanicButton());
    }

    private void add(Module m) {
        byClass.put(m.getClass(), m);
        all.add(m);
    }

    @SuppressWarnings("unchecked")
    public <T extends Module> T get(Class<T> c) {
        return (T) byClass.get(c);
    }

    public Module byName(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        // alte / andere Namen
        switch (n) {
            case "shulkeresp", "chestesp", "kistenesp" -> n = "storageesp";
            case "panic", "panik" -> n = "panicbutton";
            case "radar", "minimap" -> n = "radar";
            case "potionhud", "traenke", "effekte" -> n = "potionhud";
            case "nocd", "kein_cooldown" -> n = "nocooldown";
            default -> { }
        }
        for (Module m : all) if (m.name.toLowerCase(Locale.ROOT).equals(n)) return m;
        for (Module m : all) if (m.name.toLowerCase(Locale.ROOT).startsWith(n)) return m;
        return null;
    }

    public List<Module> all() { return Collections.unmodifiableList(all); }

    /** Knopf im Menü: Funktionen, die OP brauchen, ausblenden. */
    public static boolean hideOp;

    /** Wird die Funktion im Menü / in der Übersicht gezeigt? */
    public static boolean shown(Module m) { return !hideOp || !m.needsOp(); }

    /** Wie viele Funktionen gerade versteckt sind. */
    public int hiddenCount() {
        int n = 0;
        for (Module m : all) if (!shown(m)) n++;
        return n;
    }

    /** Favoriten in der normalen Reihenfolge. */
    public List<Module> favorites() {
        List<Module> out = new ArrayList<>();
        for (Module m : all) if (m.favorite) out.add(m);
        return out;
    }

    /** Alle Funktionen, Favoriten zuerst (für die Übersicht). */
    public List<Module> favoritesFirst() {
        List<Module> out = favorites();
        for (Module m : all) if (!m.favorite) out.add(m);
        return out;
    }

    public List<Module> inCategory(Module.Category c) {
        List<Module> out = new ArrayList<>();
        for (Module m : all) if (m.category == c) out.add(m);
        return out;
    }

    /** Cheats, die wegen eines Fehlers ausgeschaltet wurden (Selbsttest). */
    public final java.util.Set<String> crashed = new java.util.HashSet<>();

    public void tick() {
        for (Module m : all) {
            if (!m.isEnabled()) continue;
            try {
                m.onTick();
            } catch (Throwable t) {
                dev.teizen.client.TeizenClient.LOG.error("Fehler in " + m.name, t);
                crashed.add(m.name);
                m.setEnabled(false);
                dev.teizen.client.util.Chat.error(m.name + " wurde wegen eines Fehlers ausgeschaltet: " + t);
            }
        }
    }
}
