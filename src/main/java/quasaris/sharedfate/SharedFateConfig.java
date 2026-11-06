package quasaris.sharedfate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

public final class SharedFateConfig {
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("shared-fate.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Data data = new Data();

    public static class Data {
        public int preparationSeconds = 300;
        public ChunkySettings chunky = new ChunkySettings();
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
                if (json.contains("preparationMinutes")) {
                    com.google.gson.JsonObject obj = GSON.fromJson(json, com.google.gson.JsonObject.class);
                    if (obj != null && obj.has("preparationMinutes")) {
                        int mins = obj.get("preparationMinutes").getAsInt();
                        data.preparationSeconds = mins * 60;
                    }
                }
            } catch (Exception e) {
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
        int secs = Math.max(0, data.preparationSeconds);
        return secs * 1000L;
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
