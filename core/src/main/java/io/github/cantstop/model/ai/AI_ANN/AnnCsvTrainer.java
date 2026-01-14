package io.github.cantstop.model.ai.AI_ANN;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Random;

/**
 * Trains the ANN roll/value head directly from a CSV export (e.g., mcts_training_data.csv).
 * The CSV is expected to have the header:
 *   red_col_2..red_col_12, blue_col_2..blue_col_12, temp_col_2..temp_col_12,
 *   current_player, runners_remaining, did_win_label
 *
 * All column values are normalized [0,1]. current_player is assumed 0 = RED, 1 = BLUE.
 * did_win_label is the target (1 if current player eventually won, else 0).
 *
 * Usage:
 *   java ... AnnCsvTrainer <csvPath> <outWeights> [epochs] [batch] [lr] [l2] [seed]
 */
public final class AnnCsvTrainer {

    private static final int COLS = 11; // sums 2..12
    private static final double LOCK_THRESH = 0.999;
    private static final double TEMP_THRESH = 1e-6;

    public static void main(String[] args) throws Exception {
        String csvPath = argOr(args, 0, "core/src/main/java/io/github/cantstop/results/mcts_training_data.csv");
        String outPath = argOr(args, 1, "ann_weights_from_csv.annw");
        int epochs = intArgOr(args, 2, 5);
        int batchSize = intArgOr(args, 3, 256);
        float lr = floatArgOr(args, 4, 1e-3f);
        float l2 = floatArgOr(args, 5, 1e-5f);
        long seed = longArgOr(args, 6, System.nanoTime());

        System.out.println("ANN CSV training");
        System.out.println("- csv: " + csvPath);
        System.out.println("- out: " + outPath);
        System.out.println("- epochs=" + epochs + " batch=" + batchSize + " lr=" + lr + " l2=" + l2 + " seed=" + seed);

        AnnNetwork net = new AnnNetwork(new Random(seed));
        Adam opt = new Adam(net, lr, l2);

        for (int e = 1; e <= epochs; e++) {
            EpochStats stats = trainEpoch(csvPath, batchSize, net, opt);
            System.out.printf("Epoch %d/%d rollLoss=%.4f (n=%d)%n", e, epochs, stats.lossAvg(), stats.count);
        }

        net.save(new File(outPath));
        System.out.println("Saved weights: " + outPath);
    }

    private static EpochStats trainEpoch(String csvPath, int batchSize, AnnNetwork net, Adam opt) throws Exception {
        float[][] stateBatch = new float[batchSize][];
        float[] yBatch = new float[batchSize];
        int n = 0;
        float lossSum = 0f;
        long count = 0;

        try (BufferedReader br = new BufferedReader(new FileReader(csvPath))) {
            String header = br.readLine(); // skip header
            if (header == null) throw new IllegalArgumentException("Empty CSV: " + csvPath);

            String line;
            while ((line = br.readLine()) != null) {
                String[] toks = line.split(",");
                if (toks.length < 36) continue; // malformed

                float[] state = buildState(toks);
                float label = parseF(toks[35]); // did_win_label

                stateBatch[n] = state;
                yBatch[n] = label;
                n++;

                if (n == batchSize) {
                    lossSum += opt.stepRollBatch(stateBatch, yBatch, n);
                    count += n;
                    n = 0;
                }
            }
        }

        if (n > 0) {
            lossSum += opt.stepRollBatch(stateBatch, yBatch, n);
            count += n;
        }

        return new EpochStats(lossSum, count);
    }

    private static float[] buildState(String[] toks) {
        // toks: 0..10 red, 11..21 blue, 22..32 temp, 33 current_player, 34 runners_remaining, 35 label
        boolean redTurn = parseF(toks[33]) < 0.5f; // assume 0=RED, 1=BLUE

        float[] mePerm = new float[COLS];
        float[] oppPerm = new float[COLS];
        float[] temp = new float[COLS];

        for (int i = 0; i < COLS; i++) {
            float r = parseF(toks[i]);
            float b = parseF(toks[11 + i]);
            float t = parseF(toks[22 + i]);
            temp[i] = t;
            if (redTurn) {
                mePerm[i] = r;
                oppPerm[i] = b;
            } else {
                mePerm[i] = b;
                oppPerm[i] = r;
            }
        }

        float[] out = new float[AnnConstants.STATE_DIM];
        int o = 0;

        int activeTemp = 0;
        float progressValue = 0f;
        int meLocks = 0;
        int oppLocks = 0;

        for (int i = 0; i < COLS; i++) {
            float m = mePerm[i];
            float op = oppPerm[i];
            float t = temp[i];
            float tempGain = Math.max(0f, t - m);

            out[o++] = m;
            out[o++] = op;
            out[o++] = tempGain;

            boolean locked = m >= LOCK_THRESH || op >= LOCK_THRESH;
            out[o++] = locked ? 1f : 0f;
            if (locked) {
                if (m >= LOCK_THRESH) meLocks++;
                if (op >= LOCK_THRESH) oppLocks++;
            }

            if (t > TEMP_THRESH) activeTemp++;
            progressValue += tempGain;
        }

        progressValue /= COLS;

        out[o++] = activeTemp / 3f;
        out[o++] = 0f; // bustProb unknown from CSV; leave 0
        out[o++] = progressValue;
        out[o++] = meLocks / 3f;
        out[o++] = oppLocks / 3f;

        return out;
    }

    private static float parseF(String s) {
        return Float.parseFloat(s);
    }

    private record EpochStats(float lossSum, long count) {
        float lossAvg() {
            return count == 0 ? 0f : lossSum / (float) count;
        }
    }

    // Minimal Adam (roll head + trunk only), reused from AnnTrainer with move-head parts removed.
    private static final class Adam {
        private static final float B1 = 0.9f;
        private static final float B2 = 0.999f;
        private static final float EPS = 1e-8f;

        private final AnnNetwork net;
        private final float lr;
        private final float l2;
        private long t = 0;

        private final float[] mW1, vW1, mb1, vb1;
        private final float[] mW2, vW2, mb2, vb2;
        private final float[] mWr, vWr;
        private float mbr = 0f, vbr = 0f;

        private final float[] gW1, gb1, gW2, gb2, gWr;
        private float gbr = 0f;

        Adam(AnnNetwork net, float lr, float l2) {
            this.net = net;
            this.lr = lr;
            this.l2 = l2;

            this.mW1 = new float[net.W1.length];
            this.vW1 = new float[net.W1.length];
            this.mb1 = new float[net.b1.length];
            this.vb1 = new float[net.b1.length];

            this.mW2 = new float[net.W2.length];
            this.vW2 = new float[net.W2.length];
            this.mb2 = new float[net.b2.length];
            this.vb2 = new float[net.b2.length];

            this.mWr = new float[net.Wr.length];
            this.vWr = new float[net.Wr.length];

            this.gW1 = new float[net.W1.length];
            this.gb1 = new float[net.b1.length];
            this.gW2 = new float[net.W2.length];
            this.gb2 = new float[net.b2.length];
            this.gWr = new float[net.Wr.length];
        }

        float stepRollBatch(float[][] state, float[] y, int n) {
            zeroGrads();
            float lossSum = 0f;
            float invN = 1f / Math.max(1, n);

            float[] z1 = new float[AnnConstants.TRUNK_H1];
            float[] a1 = new float[AnnConstants.TRUNK_H1];
            float[] z2 = new float[AnnConstants.TRUNK_H2];
            float[] emb = new float[AnnConstants.TRUNK_H2];
            float[] dEmb = new float[AnnConstants.TRUNK_H2];
            float[] dZ2 = new float[AnnConstants.TRUNK_H2];
            float[] dA1 = new float[AnnConstants.TRUNK_H1];
            float[] dZ1 = new float[AnnConstants.TRUNK_H1];

            for (int idx = 0; idx < n; idx++) {
                float[] x = state[idx];
                float y01 = y[idx];

                net.forwardTrunk(x, z1, a1, z2, emb);

                float logit = net.br;
                for (int i = 0; i < AnnConstants.TRUNK_H2; i++) logit += net.Wr[i] * emb[i];
                lossSum += AnnMath.bceWithLogits(logit, y01);

                float p = AnnMath.sigmoid(logit);
                float dLogit = p - y01;

                for (int i = 0; i < AnnConstants.TRUNK_H2; i++) {
                    gWr[i] += dLogit * emb[i];
                    dEmb[i] = dLogit * net.Wr[i];
                }
                gbr += dLogit;

                for (int i = 0; i < AnnConstants.TRUNK_H2; i++) dZ2[i] = dEmb[i] * AnnMath.reluGrad(z2[i]);
                outerAdd(gW2, dZ2, a1, AnnConstants.TRUNK_H2, AnnConstants.TRUNK_H1);
                addVec(gb2, dZ2);
                matTVec(net.W2, dZ2, dA1, AnnConstants.TRUNK_H2, AnnConstants.TRUNK_H1);
                for (int i = 0; i < AnnConstants.TRUNK_H1; i++) dZ1[i] = dA1[i] * AnnMath.reluGrad(z1[i]);
                outerAdd(gW1, dZ1, x, AnnConstants.TRUNK_H1, AnnConstants.STATE_DIM);
                addVec(gb1, dZ1);
            }

            scale(gW1, invN); scale(gb1, invN);
            scale(gW2, invN); scale(gb2, invN);
            scale(gWr, invN); gbr *= invN;

            applyAdam();
            return lossSum;
        }

        private void zeroGrads() {
            zero(gW1); zero(gb1);
            zero(gW2); zero(gb2);
            zero(gWr); gbr = 0f;
        }

        private void applyAdam() {
            t++;
            float b1t = (float) (1.0 - Math.pow(B1, t));
            float b2t = (float) (1.0 - Math.pow(B2, t));

            addL2(gW1, net.W1, l2);
            addL2(gW2, net.W2, l2);
            addL2(gWr, net.Wr, l2);

            adamUpdate(net.W1, gW1, mW1, vW1, lr, b1t, b2t);
            adamUpdate(net.b1, gb1, mb1, vb1, lr, b1t, b2t);
            adamUpdate(net.W2, gW2, mW2, vW2, lr, b1t, b2t);
            adamUpdate(net.b2, gb2, mb2, vb2, lr, b1t, b2t);
            adamUpdate(net.Wr, gWr, mWr, vWr, lr, b1t, b2t);
            net.br = adamUpdateScalar(net.br, gbr, lr, b1t, b2t);
        }

        private void adamUpdate(float[] w, float[] g, float[] m, float[] v, float lr, float b1t, float b2t) {
            for (int i = 0; i < w.length; i++) {
                float gi = g[i];
                m[i] = B1 * m[i] + (1f - B1) * gi;
                v[i] = B2 * v[i] + (1f - B2) * gi * gi;
                float mh = m[i] / b1t;
                float vh = v[i] / b2t;
                w[i] -= lr * mh / ((float) Math.sqrt(vh) + EPS);
            }
        }

        private float adamUpdateScalar(float w, float g, float lr, float b1t, float b2t) {
            mbr = B1 * mbr + (1f - B1) * g;
            vbr = B2 * vbr + (1f - B2) * g * g;
            float mh = mbr / b1t;
            float vh = vbr / b2t;
            return w - lr * mh / ((float) Math.sqrt(vh) + EPS);
        }

        private static void addL2(float[] g, float[] w, float l2) {
            if (l2 <= 0f) return;
            for (int i = 0; i < g.length; i++) g[i] += l2 * w[i];
        }

        private static void outerAdd(float[] dstW, float[] a, float[] x, int outDim, int inDim) {
            for (int o = 0; o < outDim; o++) {
                int row = o * inDim;
                float ao = a[o];
                for (int i = 0; i < inDim; i++) dstW[row + i] += ao * x[i];
            }
        }

        private static void matTVec(float[] W, float[] vIn, float[] out, int outDim, int inDim) {
            for (int i = 0; i < inDim; i++) out[i] = 0f;
            for (int o = 0; o < outDim; o++) {
                int row = o * inDim;
                float vo = vIn[o];
                for (int i = 0; i < inDim; i++) out[i] += W[row + i] * vo;
            }
        }

        private static void addVec(float[] dst, float[] src) {
            for (int i = 0; i < dst.length; i++) dst[i] += src[i];
        }

        private static void scale(float[] a, float s) {
            for (int i = 0; i < a.length; i++) a[i] *= s;
        }

        private static void zero(float[] a) {
            for (int i = 0; i < a.length; i++) a[i] = 0f;
        }
    }

    private static String argOr(String[] args, int idx, String def) {
        return (args != null && args.length > idx && args[idx] != null && !args[idx].isBlank()) ? args[idx] : def;
    }

    private static int intArgOr(String[] args, int idx, int def) {
        try { return Integer.parseInt(argOr(args, idx, String.valueOf(def))); } catch (Exception e) { return def; }
    }

    private static long longArgOr(String[] args, int idx, long def) {
        try { return Long.parseLong(argOr(args, idx, String.valueOf(def))); } catch (Exception e) { return def; }
    }

    private static float floatArgOr(String[] args, int idx, float def) {
        try { return Float.parseFloat(argOr(args, idx, String.valueOf(def))); } catch (Exception e) { return def; }
    }
}

