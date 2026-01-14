package io.github.cantstop.model.ai.AI_ANN;

/**
 * Centralizes ANN dimensions so dataset generation, training, and inference agree.
 */
public final class AnnConstants {
    private AnnConstants() {}

    // Game constants
    public static final int NUM_COLS = 11; // sums 2..12

    // State feature layout:
    // per-col: mePerm, oppPerm, tempGain, isLocked => 4 * 11
    // globals: activeTempCols, bustProb, progressValue, meColsCompleted, oppColsCompleted => 5
    public static final int STATE_DIM = NUM_COLS * 4 + 5; // 49

    // Move features:
    // one-hot sumA(11) + one-hot sumB(11) + isSingle + opensNewRunnerCount + closesColumnNow + usesBothSumsSameCol
    public static final int MOVE_DIM = NUM_COLS * 2 + 4; // 26

    // Network sizes (baseline)
    public static final int TRUNK_H1 = 128;
    public static final int TRUNK_H2 = 64; // state embedding size
    public static final int MOVE_H1 = 64;

    // Derived
    public static final int MOVE_INPUT_DIM = TRUNK_H2 + MOVE_DIM; // 90
}

