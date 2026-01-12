package io.github.cantstop.model.ai.AI_ANN;

import io.github.cantstop.model.*;
import io.github.cantstop.model.ai.AI_Expectiminimax.BustTable;

import java.util.List;

/**
 * Deterministic feature extraction for ANN policy/value approximation.
 *
 * Perspective-normalized: "me" refers to {@code state.getCurrentPlayer()} and "opp" to opponent.
 */
public final class AnnFeatureExtractor {

    public float[] extractState(GameState s) {
        float[] x = new float[AnnConstants.STATE_DIM];
        fillStateInPlace(s, x);
        return x;
    }

    public void fillStateInPlace(GameState s, float[] out) {
        if (out.length != AnnConstants.STATE_DIM) {
            throw new IllegalArgumentException("Expected STATE_DIM=" + AnnConstants.STATE_DIM + " got=" + out.length);
        }

        final Player me = s.getCurrentPlayer();
        final Player opp = me.opponent();

        int o = 0;

        // Per-column features
        for (int col = 0; col < GameConstants.NUM_COLS; col++) {
            int sum = GameConstants.columnToSum(col);
            float maxH = GameConstants.maxHeight(sum);

            float mePerm = s.getMarkerHeight(me, col);
            float oppPerm = s.getMarkerHeight(opp, col);
            float temp = s.tempAtCol(col);
            float tempGain = Math.max(0f, temp - mePerm);

            out[o++] = mePerm / maxH;
            out[o++] = oppPerm / maxH;
            out[o++] = tempGain / maxH;
            out[o++] = s.isColumnLocked(col) ? 1f : 0f;
        }

        // Global features
        out[o++] = s.countActiveColumns() / (float) GameConstants.MAX_TEMP_RUNNERS;
        out[o++] = computeBustChance(s);
        out[o++] = computeProgressValue(s);
        out[o++] = countCompletedColumns(s, me) / (float) GameConstants.TO_WIN;
        out[o++] = countCompletedColumns(s, opp) / (float) GameConstants.TO_WIN;

        if (o != AnnConstants.STATE_DIM) {
            throw new IllegalStateException("State feature fill mismatch: " + o + " != " + AnnConstants.STATE_DIM);
        }
    }

    public float[] extractMove(GameState s, Move m) {
        float[] x = new float[AnnConstants.MOVE_DIM];
        fillMoveInPlace(s, m, x);
        return x;
    }

    public void fillMoveInPlace(GameState s, Move m, float[] out) {
        if (out.length != AnnConstants.MOVE_DIM) {
            throw new IllegalArgumentException("Expected MOVE_DIM=" + AnnConstants.MOVE_DIM + " got=" + out.length);
        }

        int o = 0;

        // one-hot sumA (2..12) in [0..10]
        o = oneHotSum(out, o, m.sumA());
        // one-hot sumB
        o = oneHotSum(out, o, m.sumB());

        boolean isSingle = m.isSingle();
        out[o++] = isSingle ? 1f : 0f;

        float opens = opensNewRunnerCount(s, m) / 2.0f;
        out[o++] = clamp01(opens);

        out[o++] = closesColumnNow(s, m) ? 1f : 0f;

        // Helps distinguish "double" moves that target same column (sumA==sumB)
        boolean sameCol = (m.sumA() > 0 && m.sumA() == m.sumB());
        out[o++] = sameCol ? 1f : 0f;

        if (o != AnnConstants.MOVE_DIM) {
            throw new IllegalStateException("Move feature fill mismatch: " + o + " != " + AnnConstants.MOVE_DIM);
        }
    }

    public int argmaxLegalMove(GameState s, List<Move> legalMoves, AnnNetwork net) {
        if (legalMoves == null || legalMoves.isEmpty()) return -1;

        float[] state = extractState(s);
        float[] emb = net.embedState(state);

        float best = Float.NEGATIVE_INFINITY;
        int bestIdx = 0;
        float[] mv = new float[AnnConstants.MOVE_DIM];
        for (int i = 0; i < legalMoves.size(); i++) {
            fillMoveInPlace(s, legalMoves.get(i), mv);
            float score = net.moveLogitFromEmbedding(emb, mv);
            if (score > best) {
                best = score;
                bestIdx = i;
            }
        }
        return bestIdx;
    }

    private static int oneHotSum(float[] out, int offset, int sum) {
        // length = 11
        for (int i = 0; i < AnnConstants.NUM_COLS; i++) out[offset + i] = 0f;
        if (sum >= GameConstants.COL_MIN && sum <= GameConstants.COL_MAX) {
            out[offset + (sum - GameConstants.COL_MIN)] = 1f;
        }
        return offset + AnnConstants.NUM_COLS;
    }

    private static float computeBustChance(GameState state) {
        int mask = 0;
        for (int sum = GameConstants.COL_MIN; sum <= GameConstants.COL_MAX; sum++) {
            if (TurnManager.isSinglePlayable(state, sum)) {
                mask |= (1 << (sum - GameConstants.COL_MIN));
            }
        }
        return (float) BustTable.P[mask]; // already [0,1]
    }

    // Matches RuleBasedPlayer.computeProgressValue() semantics: average normalized gain across columns.
    private static float computeProgressValue(GameState state) {
        float progressValue = 0f;
        Player me = state.getCurrentPlayer();
        for (int col = 0; col < GameConstants.NUM_COLS; col++) {
            float maxH = GameConstants.maxHeight(GameConstants.columnToSum(col));
            float mePerm = state.getMarkerHeight(me, col);
            float temp = state.tempAtCol(col);
            float gain = Math.max(temp - mePerm, 0f);
            progressValue += gain / maxH;
        }
        return progressValue / GameConstants.NUM_COLS;
    }

    private static int countCompletedColumns(GameState s, Player p) {
        int c = 0;
        for (int col = 0; col < GameConstants.NUM_COLS; col++) {
            int maxH = GameConstants.maxHeight(GameConstants.columnToSum(col));
            if (s.getMarkerHeight(p, col) >= maxH) c++;
        }
        return c;
    }

    private static int opensNewRunnerCount(GameState s, Move m) {
        int a = m.sumA();
        int b = m.sumB();
        int count = 0;

        if (a > 0) count += opensNewRunnerForSum(s, a) ? 1 : 0;
        if (b > 0) {
            // same column shouldn't double count
            if (a <= 0 || GameConstants.sumToColumnID(a) != GameConstants.sumToColumnID(b)) {
                count += opensNewRunnerForSum(s, b) ? 1 : 0;
            }
        }
        return count;
    }

    private static boolean opensNewRunnerForSum(GameState s, int sum) {
        int col = GameConstants.sumToColumnID(sum);
        if (s.isColumnLocked(col)) return false;
        return !s.isColumnActive(col);
    }

    private static boolean closesColumnNow(GameState s, Move m) {
        Player me = s.getCurrentPlayer();
        int[] sums = { m.sumA(), m.sumB() };
        for (int sum : sums) {
            if (sum <= 0) continue;
            int col = GameConstants.sumToColumnID(sum);
            if (s.isColumnLocked(col)) continue;

            int maxH = GameConstants.maxHeight(sum);
            int base = s.getMarkerHeight(me, col);
            int cur = s.isColumnActive(col) ? s.tempAtCol(col) : base;
            int next = Math.min(cur + 1, maxH);
            if (next >= maxH) return true;
        }
        return false;
    }

    private static float clamp01(float v) {
        if (v < 0f) return 0f;
        if (v > 1f) return 1f;
        return v;
    }
}

