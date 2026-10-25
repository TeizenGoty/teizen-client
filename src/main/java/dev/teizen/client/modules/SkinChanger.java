package dev.teizen.client.modules;

import dev.teizen.client.TeizenClient;
import dev.teizen.client.core.Module;
import dev.teizen.client.core.Setting;
import dev.teizen.client.mixin.DefaultPlayerSkinAccessor;
import dev.teizen.client.util.Chat;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * SkinChanger: dein Skin wird – nur bei dir – durch einen anderen ersetzt: entweder einen Standard-Skin
 * (steve, alex, ari, efe, kai, makena, noor, sunny, zuri) oder den Skin eines beliebigen Spielers (Namen eintippen).
 * Andere sehen weiter deinen echten Skin.
 */
public final class SkinChanger extends Module {
    private final Setting skin = add(Setting.text("skin", "Name eines Spielers oder Standard-Skin (steve, alex, ari, efe, kai, makena, noor, sunny, zuri)", "alex"));
    private final Setting slim = add(Setting.bool("duenn", "Bei Standard-Skins: dünne Arme (Alex-Form)", false));

    private volatile PlayerSkin loaded;
    private String loadedFor = "";
    public String status = "";

    public SkinChanger() {
        super("SkinChanger", Category.SONSTIGES, "Anderer Skin für dich (nur bei dir sichtbar).");
    }

    /** Mixin (Skin eines Spielers): für dich den gewählten Skin, sonst null. */
    public static PlayerSkin skinFor(AbstractClientPlayer p) {
        if (TeizenClient.modules() == null || mc.player == null || !p.getUUID().equals(mc.player.getUUID())) return null;
        SkinChanger m = TeizenClient.modules().get(SkinChanger.class);
        if (m == null || !m.isEnabled()) return null;
        return m.loaded;
    }

    @Override
    protected void onEnable() { loadedFor = ""; loaded = null; }

    @Override
    public void onTick() {
        if (!inGame()) return;
        String want = skin.str().trim().toLowerCase(Locale.ROOT) + (slim.on() ? "#slim" : "");
        if (!want.equals(loadedFor)) load(want);
    }

    private static PlayerSkin builtin(String name, boolean slim) {
        PlayerSkin any = null;
        for (PlayerSkin s : DefaultPlayerSkinAccessor.teizen$skins()) {
            String path = s.body().id().getPath();
            if (!path.endsWith("/" + name)) continue;
            boolean isSlim = path.contains("/slim/");
            if (isSlim == slim) return s;
            any = s;
        }
        return any;
    }

    private void load(String want) {
        loadedFor = want;
        loaded = null;
        String name = want.replace("#slim", "");
        if (name.isEmpty()) return;
        PlayerSkin b = builtin(name, want.endsWith("#slim"));
        if (b != null) {
            loaded = b;
            status = "Standard-Skin " + name;
            Chat.ok("SkinChanger: Standard-Skin §f" + name + "§a.");
            return;
        }
        status = "lädt " + name + " ...";
        CompletableFuture.supplyAsync(() -> mc.services().profileResolver().fetchByName(name))
                .thenCompose(opt -> opt.isPresent() ? mc.getSkinManager().get(opt.get()) : CompletableFuture.completedFuture(Optional.<PlayerSkin>empty()))
                .whenComplete((res, err) -> mc.execute(() -> {
                    if (!want.equals(loadedFor)) return;
                    if (err != null || res == null || res.isEmpty()) {
                        status = "nicht gefunden";
                        Chat.error("SkinChanger: Spieler \"" + name + "\" nicht gefunden (oder kein Internet).");
                        return;
                    }
                    loaded = res.get();
                    status = "Skin von " + name;
                    Chat.ok("SkinChanger: du hast jetzt den Skin von §f" + name + "§a (nur bei dir).");
                }));
    }

    /** Für den Selbsttest. */
    public PlayerSkin current() { return loaded; }

    @Override
    public String info() { return skin.str(); }
}
