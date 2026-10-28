package dev.teizen.client.compat;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.PiercingWeapon;

/** Versions-Weiche: eine Jar für 26.2 und 26.3. Wählt beim Start die passende Umsetzung. */
public final class V {
    private V() {}

    private static final Impl I = load();

    public static String running() { return Mc.running(); }

    private static Impl load() {
        String first = running().equals("26.3") ? "v263.Impl263" : "v262.Impl262";
        String second = first.startsWith("v263") ? "v262.Impl262" : "v263.Impl263";
        for (String n : new String[]{first, second}) {
            try {
                return (Impl) Class.forName("dev.teizen.client.compat." + n).getDeclaredConstructor().newInstance();
            } catch (ClassNotFoundException ignored) {
            } catch (Exception e) {
                throw new RuntimeException("Teizen: Versions-Code " + n + " kaputt", e);
            }
        }
        throw new IllegalStateException("Teizen: kein Versions-Code gefunden");
    }

    public static Impl impl() { return I; }

    public static final String MC = I.mc();
    public static final boolean MULTI_MOVE_PACKETS = I.multiMovePackets();
    public static final String FN = I.fn();
    public static final InputConstants.Type KEYBOARD = I.keyboard();

    public static void swing(InteractionHand hand) { I.swing(hand); }
    public static boolean isKeyDown(int code) { return code >= 0 && I.isKeyDown(code); }
    public static void piercingAttack(ItemStack weapon, PiercingWeapon pw) { I.piercingAttack(weapon, pw); }
    public static float attackAnim(LivingEntity e, float partial) { return I.attackAnim(e, partial); }
    public static String layoutName(int code) { return I.layoutName(code); }
    /** Maustaste -> 0 = links, 1 = rechts, 2 = Mitte (in 26.2 und 26.3 gleich). */
    public static int mouse(int raw) { return I.mouseButton(raw); }
    /** Für den Selbsttest: 0 = links, 1 = rechts, 2 = Mitte -> echter Wert der Version. */
    public static int rawMouse(int logical) { return I.rawMouse(logical); }

    /** Fenster-Handle (long). 26.2: Window.getWindow(), 26.3: Window.handle() – über Reflection, damit beides geht. */
    public static long windowHandle() {
        Object w = Minecraft.getInstance().getWindow();
        for (String m : new String[]{"handle", "getWindow"}) {
            try { return (Long) w.getClass().getMethod(m).invoke(w); } catch (Throwable ignored) {}
        }
        return 0L;
    }
}
