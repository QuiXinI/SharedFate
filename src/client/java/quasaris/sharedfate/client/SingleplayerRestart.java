package quasaris.sharedfate.client;

import java.util.Random;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.client.multiplayer.ClientLevel;
import quasaris.sharedfate.DebugLog;
import quasaris.sharedfate.client.mixin.CreateWorldScreenAccessor;

/**
 * Singleplayer world reset (same idea as Scavenger): leave the world, open the create-world screen,
 * set a new random seed and the same game mode (hardcore or survival), then press Create.
 * Driven by a client tick with a timeout, so a stuck step can never block the next reset.
 */
public final class SingleplayerRestart {
    private static final long TIMEOUT_MS = 30_000L;

    private enum Step { IDLE, LEAVING, OPENING }

    private static Step step = Step.IDLE;
    private static boolean hardcore = false;
    private static String oldLevelId = "";
    private static String oldLabel = "";
    private static long stepStartedAt = 0L;

    public static void begin(Minecraft mc, boolean isHardcore, String levelId, String label) {
        if (step != Step.IDLE) {
            DebugLog.info("singleplayer restart requested while busy (" + step + "), ignored");
            return;
        }
        hardcore = isHardcore;
        oldLevelId = levelId;
        oldLabel = label;
        setStep(Step.LEAVING);
        DebugLog.info("singleplayer restart: leaving world (hardcore=" + hardcore + ")");
        mc.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE);
    }

    public static void tick(Minecraft mc) {
        if (step == Step.IDLE) {
            return;
        }
        if (System.currentTimeMillis() - stepStartedAt > TIMEOUT_MS) {
            DebugLog.info("singleplayer restart timed out in step " + step + ", resetting state");
            step = Step.IDLE;
            return;
        }

        if (step == Step.LEAVING && mc.gui.screen() instanceof TitleScreen) {
            setStep(Step.OPENING);
            LevelRenamer.rename(mc, oldLevelId, oldLabel);
            DebugLog.info("singleplayer restart: opening create-world screen");
            CreateWorldScreen.openFresh(mc, () -> mc.gui.setScreen(new TitleScreen()));
            return;
        }

        if (step == Step.OPENING && mc.gui.screen() instanceof CreateWorldScreen screen) {
            step = Step.IDLE;
            WorldCreationUiState ui = screen.getUiState();
            ui.setGameMode(hardcore
                    ? WorldCreationUiState.SelectedGameMode.HARDCORE
                    : WorldCreationUiState.SelectedGameMode.SURVIVAL);
            ui.setSeed(Long.toString(new Random().nextLong()));
            DebugLog.info("singleplayer restart: creating world");
            ((CreateWorldScreenAccessor) screen).sharedfate$onCreate();
        }
    }

    private static void setStep(Step next) {
        step = next;
        stepStartedAt = System.currentTimeMillis();
    }

    private SingleplayerRestart() {}
}
