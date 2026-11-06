package quasaris.sharedfate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;

public final class SharedFateConfig {
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("shared-fate.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Data data = new Data();

    public static class Data {
        public int preparationSeconds = 300;
        public ChunkySettings chunky = new ChunkySettings();
        public int downloadPort = 25600;
        public String publicAddress = "127.0.0.1";
    }

    public static class ChunkySettings {
        public String world = "minecraft:overworld";
        public String shape = "square";
        public double centerX = 0.0;
        public double centerZ = 0.0;
        public double radius = 512.0;
        public String pattern = "spiral";
    }

    public static void load() {
        if (Files.exists(CONFIG_PATH)) {
            try {
                String json = Files.readString(CONFIG_PATH);
                Data parsed = GSON.fromJson(json, Data.class);
                if (parsed != null) {
                    data = parsed;
                }
                JsonObject obj = GSON.fromJson(json, JsonObject.class);
                if (obj != null && obj.has("preparationMinutes")) {
                    data.preparationSeconds = obj.get("preparationMinutes").getAsInt() * 60;
                }
            } catch (Exception ignored) {
            }
        }
        save();
    }

    public static void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, GSON.toJson(data));
        } catch (IOException ignored) {
        }
    }

    public static long getDurationMs() {
        return Math.max(0, data.preparationSeconds) * 1000L;
    }

    public static int getDownloadPort() {
        return data.downloadPort;
    }

    public static String getPublicAddress() {
        return data.publicAddress != null && !data.publicAddress.isBlank() ? data.publicAddress : "127.0.0.1";
    }

    public static String getChunkyWorld() {
        return data.chunky.world != null ? data.chunky.world : "minecraft:overworld";
    }

    public static String getChunkyShape() {
        return data.chunky.shape != null ? data.chunky.shape : "square";
    }

    public static double getChunkyCenterX() {
        return data.chunky.centerX;
    }

    public static double getChunkyCenterZ() {
        return data.chunky.centerZ;
    }

    public static double getChunkyRadius() {
        return Math.max(0.0, data.chunky.radius);
    }

    public static String getChunkyPattern() {
        return data.chunky.pattern != null ? data.chunky.pattern : "spiral";
    }

    private SharedFateConfig() {}
}
