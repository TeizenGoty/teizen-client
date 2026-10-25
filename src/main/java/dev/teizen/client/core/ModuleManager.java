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
        add(new God());
        add(new HitMarker());
        add(new CombatLog());
        // Abbau
        add(new XRay());
        add(new AutoMine());
        add(new Nuker());
        add(new AutoTool());
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
        // Sicht
        add(new PlayerESP());
        add(new SeeInvisible());
        add(new EnchantPreview());
        add(new Nametags());
        add(new ArmorESP());
        add(new Tracers());
        add(new ItemESP());
        add(new StorageESP());
        add(new ShulkerESP());
        add(new PortalFinder());
        add(new StructureFinder());
        add(new Fullbright());
        // Bauen
        add(new AutoBuild());
        add(new Scaffold());
        add(new FastPlace());
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
        for (Module m : all) if (m.name.toLowerCase(Locale.ROOT).equals(n)) return m;
        for (Module m : all) if (m.name.toLowerCase(Locale.ROOT).startsWith(n)) return m;
        return null;
    }

    public List<Module> all() { return Collections.unmodifiableList(all); }

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
