package quasaris.sharedfate.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import quasaris.sharedfate.DebugLog;

/** Writes a new display name into a world's level.dat. Call only when the world is not loaded. */
public final class LevelRenamer {
    public static void rename(Minecraft mc, String levelId, String newName) {
        try {
            Path dataFile = mc.getLevelSource().getBaseDir().resolve(levelId).resolve("level.dat");
            if (!Files.exists(dataFile)) {
                DebugLog.info("rename skipped, no level.dat for " + levelId);
                return;
            }
            CompoundTag root = NbtIo.readCompressed(dataFile, NbtAccounter.uncompressedQuota());
            CompoundTag data = root.getCompound("Data").orElse(root);
            data.putString("LevelName", newName);
            NbtIo.writeCompressed(root, dataFile);
            DebugLog.info("world " + levelId + " renamed to: " + newName);
        } catch (IOException | RuntimeException e) {
            DebugLog.error("rename of world " + levelId + " failed", e);
        }
    }

    private LevelRenamer() {}
}
