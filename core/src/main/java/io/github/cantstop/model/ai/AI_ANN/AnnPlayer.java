package io.github.cantstop.model.ai.AI_ANN;

import io.github.cantstop.controller.IPlayerController;
import io.github.cantstop.model.GameState;
import io.github.cantstop.model.Move;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import java.io.File;
import java.util.List;

/**
 * Runtime ANN agent: fast inference, conforms to IPlayerController.
 */
public final class AnnPlayer implements IPlayerController {
    private final AnnFeatureExtractor fe = new AnnFeatureExtractor();
    private final AnnNetwork net;
    private final float rollThreshold;

    /**
     * @param weightsFile ANN weights file produced by {@link AnnTrainer}; if missing/unreadable, agent falls back to simple defaults.
     * @param rollThreshold Roll if sigmoid(logit) >= threshold.
     */
    public AnnPlayer(File weightsFile, float rollThreshold) {
        this.rollThreshold = rollThreshold;
        AnnNetwork loaded = tryLoad(weightsFile);
        if (loaded == null && weightsFile != null) {
            FileHandle fh = Gdx.files.internal(weightsFile.getName());
            if (fh != null && fh.exists()) {
                loaded = tryLoad(fh.file());
            }
        }
        this.net = loaded;
        if (this.net == null) {
            System.err.println("[ANN] Failed to load weights from " + (weightsFile == null ? "<null>" : weightsFile.getAbsolutePath()) + ". Using fallback (always roll when stop illegal).");
        } else {
            System.out.println("[ANN] Loaded weights from " + weightsFile.getAbsolutePath() + " thr=" + rollThreshold);
        }
    }

    private static AnnNetwork tryLoad(File f) {
        if (f == null) return null;
        try {
            if (!f.exists()) return null;
            return AnnNetwork.load(f);
        } catch (Exception e) {
            System.err.println("Failed to load ANN weights: " + f + " (" + e.getMessage() + ")");
            return null;
        }
    }

    @Override
    public Boolean rollOrStop(GameState state) {
        // If there are no active temp runners, stopping is illegal anyway.
        if (state.countActiveColumns() == 0) return true;

        if (net == null) {
            // Safe default: stop when you have anything to bank ~half the time (very weak, but non-crashing).
            return true;
        }
        float pRoll = net.rollProb(fe.extractState(state));
        return pRoll >= rollThreshold;
    }

    @Override
    public Move selectMove(GameState state, List<Move> legalMoves) {
        if (legalMoves == null || legalMoves.isEmpty()) return null;
        if (net == null) return legalMoves.get(0);

        int idx = fe.argmaxLegalMove(state, legalMoves, net);
        if (idx < 0 || idx >= legalMoves.size()) return legalMoves.get(0);
        return legalMoves.get(idx);
    }
}

