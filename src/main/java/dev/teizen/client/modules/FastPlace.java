package dev.teizen.client.modules;

import dev.teizen.client.core.Module;
import dev.teizen.client.mixin.MinecraftAccessor;

/** Kein Warten zwischen zwei Rechtsklicks – Blöcke setzen so schnell wie möglich. */
public final class FastPlace extends Module {
    public FastPlace() {
        super("FastPlace", Category.BAUEN, "Blöcke ohne Verzögerung setzen.");
    }

    @Override
    public void onTick() {
        if (!inGame()) return;
        ((MinecraftAccessor) mc).teizen$setRightClickDelay(0);
    }
}
