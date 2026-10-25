package dev.teizen.client.util;

import dev.teizen.client.TeizenClient;

/**
 * Manche Mods (SeedCrackerX, SeedMapper ...) lassen beim Beenden Thread-Pools offen. Dann bleibt Java nach dem
 * Schließen von Minecraft hängen und nach ein paar Sekunden schreibt Minecraft einen Crash-Report.
 * Hier: wenn das Spiel schon fertig ist und nur noch untätige Thread-Pools übrig sind, Java sauber beenden.
 */
public final class CleanExit {
    private static boolean started;

    private CleanExit() {}

    /** Beim Beenden von Minecraft aufrufen (im Render-Thread). */
    public static void arm() {
        if (started) return;
        started = true;
        Thread main = Thread.currentThread();
        Thread t = new Thread(() -> {
            try {
                main.join(20000);
                if (main.isAlive()) return; // Spiel läuft noch -> nichts tun
                Thread.sleep(1500);
                StringBuilder idle = new StringBuilder();
                for (var e : Thread.getAllStackTraces().entrySet()) {
                    Thread th = e.getKey();
                    if (th.isDaemon() || !th.isAlive() || th == Thread.currentThread() || th.getName().equals("DestroyJavaVM")) continue;
                    if (!idlePoolThread(th, e.getValue())) {
                        TeizenClient.LOG.info("Beenden: " + th.getName() + " arbeitet noch – warte auf normales Ende");
                        return;
                    }
                    idle.append(th.getName()).append(' ');
                }
                if (idle.isEmpty()) return;
                TeizenClient.LOG.info("Beenden: nur noch untätige Threads anderer Mods offen (" + idle.toString().trim() + ") – Java wird sauber beendet");
                System.exit(0);
            } catch (InterruptedException ignored) {
                // Ende
            }
        }, "teizen-clean-exit");
        t.setDaemon(true);
        t.start();
    }

    /** Wartet der Thread nur in einem Thread-Pool auf neue Arbeit? */
    private static boolean idlePoolThread(Thread th, StackTraceElement[] stack) {
        Thread.State s = th.getState();
        if (s != Thread.State.WAITING && s != Thread.State.TIMED_WAITING) return false;
        for (StackTraceElement f : stack) {
            if (f.getClassName().equals("java.util.concurrent.ThreadPoolExecutor") && f.getMethodName().equals("getTask")) return true;
        }
        return false;
    }
}
