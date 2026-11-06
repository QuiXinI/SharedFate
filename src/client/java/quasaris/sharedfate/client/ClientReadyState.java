package quasaris.sharedfate.client;

import net.minecraft.network.chat.Component;
import quasaris.sharedfate.net.ReadyStatePayload;

public final class ClientReadyState {
    private static volatile boolean waiting, self;
    private static volatile int ready, total;
    private static volatile String deathLine = "";

    public static void update(ReadyStatePayload p) {
        waiting = p.waiting();
        self = p.self();
        ready = p.ready();
        total = p.total();
        deathLine = p.deathLine();
        ReadyUi.refresh();
    }

    public static void reset() {
        waiting = false;
        self = false;
        ready = 0;
        total = 0;
        deathLine = "";
    }

    public static boolean isWaiting() { return waiting; }
    public static boolean isSelfReady() { return self; }
    public static String deathLine() { return deathLine; }

    public static Component counter() {
        int displayTotal = Math.max(1, total);
        int displayReady = Math.min(ready, displayTotal);
        float r = Math.min(1f, (float) displayReady / displayTotal);
        int red = Math.round(0xFF + (0x55 - 0xFF) * r);
        int green = Math.round(0x55 + (0xFF - 0x55) * r);
        int rgb = (red << 16) | (green << 8) | 0x55;
        return Component.literal(displayReady + "/" + displayTotal).withStyle(s -> s.withColor(rgb));
    }

    private ClientReadyState() {}
}
