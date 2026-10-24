package dev.teizen.client.core;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Freunde werden von KillAura & Co. nicht angegriffen. */
public final class Friends {
    public static final Set<String> NAMES = new LinkedHashSet<>();

    private Friends() {}

    public static boolean is(String name) {
        return NAMES.contains(name.toLowerCase(Locale.ROOT));
    }

    public static boolean add(String name) { return NAMES.add(name.toLowerCase(Locale.ROOT)); }

    public static boolean remove(String name) { return NAMES.remove(name.toLowerCase(Locale.ROOT)); }
}
