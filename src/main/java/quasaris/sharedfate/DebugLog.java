package quasaris.sharedfate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loader.api.FabricLoader;

/** Logs to the normal log and to logs/shared-fate.log next to the game, so events survive restarts. */
public final class DebugLog {
    private static final Logger LOG = LoggerFactory.getLogger("SharedFate");

    public static void info(String msg) {
        LOG.info(msg);
        write(msg);
    }

    public static void error(String msg, Throwable t) {
        LOG.error(msg, t);
        write(msg + ": " + t);
    }

    private static void write(String msg) {
        try {
            Path p = FabricLoader.getInstance().getGameDir().resolve("logs").resolve("shared-fate.log");
            Files.createDirectories(p.getParent());
            Files.writeString(p, LocalDateTime.now() + " " + msg + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException | RuntimeException ignored) {
        }
    }

    private DebugLog() {}
}
