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
        add(new Reach());
        add(new Criticals());
        add(new AntiKnockback());
        add(new AutoTotem());
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
        // Sicht
        add(new PlayerESP());
        add(new Tracers());
        add(new StorageESP());
        add(new Fullbright());
        // Bauen
        add(new AutoBuild());
        add(new Scaffold());
        add(new FastPlace());
        // Sonstiges
        add(new AutoEat());
        add(new AutoRespawn());
        add(new ChestStealer());
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

    public List<Module> inCategory(Module.Category c) {
        List<Module> out = new ArrayList<>();
        for (Module m : all) if (m.category == c) out.add(m);
        return out;
    }

    public void tick() {
        for (Module m : all) {
            if (!m.isEnabled()) continue;
            try {
                m.onTick();
            } catch (Throwable t) {
                dev.teizen.client.TeizenClient.LOG.error("Fehler in " + m.name, t);
                m.setEnabled(false);
                dev.teizen.client.util.Chat.error(m.name + " wurde wegen eines Fehlers ausgeschaltet: " + t);
            }
        }
    }
}
