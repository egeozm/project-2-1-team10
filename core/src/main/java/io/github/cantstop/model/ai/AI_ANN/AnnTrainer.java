package io.github.cantstop.model.ai.AI_ANN;

import java.io.File;
import java.io.IOException;
import java.util.Random;

/**
 * Java-only trainer for the ANN imitation model.
 *
 * Trains:
 * - roll/stop head using roll dataset
 * - move head using move-pair dataset (pairwise logistic loss)
 *
 * Args:
 * 0 roll_stop.bin
 * 1 move_pairs.bin
 * 2 out_weights.annw
 * 3 epochs (default 5)
 * 4 batchSize (default 256)
 * 5 lr (default 1e-3)
 * 6 l2 (default 1e-5)
 * 7 seed (default nanoTime)
 */
public final class AnnTrainer {

    public static void main(String[] args) throws Exception {
        File rollFile = new File(argOr(args, 0, "ann_data/roll_stop.bin"));
        File pairFile = new File(argOr(args, 1, "ann_data/move_pairs.bin"));
        File outFile = new File(argOr(args, 2, "ann_weights.annw"));

        int epochs = intArgOr(args, 3, 5);
        int batchSize = intArgOr(args, 4, 256);
        float lr = floatArgOr(args, 5, 1e-3f);
        float l2 = floatArgOr(args, 6, 1e-5f);
        long seed = longArgOr(args, 7, System.nanoTime());

        System.out.println("ANN training");
        System.out.println("- roll: " + rollFile.getAbsolutePath());
        System.out.println("- pairs: " + pairFile.getAbsolutePath());
        System.out.println("- out: " + outFile.getAbsolutePath());
        System.out.println("- epochs: " + epochs + " batch: " + batchSize + " lr: " + lr + " l2: " + l2);

        AnnNetwork net = new AnnNetwork(new Random(seed));
        Adam opt = new Adam(net, lr, l2);

        for (int e = 1; e <= epochs; e++) {
            EpochStats rollStats = trainRollEpoch(net, opt, rollFile, batchSize);
            EpochStats pairStats = trainPairEpoch(net, opt, pairFile, batchSize);

            System.out.printf("Epoch %d/%d  rollLoss=%.4f (n=%d)  pairLoss=%.4f (n=%d)%n",
                e, epochs,
                rollStats.lossAvg(), rollStats.count,
                pairStats.lossAvg(), pairStats.count);
        }

        net.save(outFile);
        System.out.println("Saved weights: " + outFile.getAbsolutePath());
    }

    private static EpochStats trainRollEpoch(AnnNetwork net, Adam opt, File rollFile, int batchSize) throws IOException {
        float[][] stateBatch = new float[batchSize][];
        float[] yBatch = new float[batchSize];
        int n = 0;

        float lossSum = 0f;
        long count = 0;

        try (AnnDatasetIO.RollReader r = AnnDatasetIO.openRollReader(rollFile)) {
            for (;;) {
                AnnDatasetIO.RollSample s = r.next();
                if (s == null) break;
                stateBatch[n] = s.state();
                yBatch[n] = s.roll() ? 1f : 0f;
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

    private static EpochStats trainPairEpoch(AnnNetwork net, Adam opt, File pairFile, int batchSize) throws IOException {
        float[][] stateBatch = new float[batchSize][];
        float[][] moveBatch = new float[batchSize][];
        float[] yBatch = new float[batchSize];
        int n = 0;

        float lossSum = 0f;
        long count = 0;

        try (AnnDatasetIO.MovePairReader r = AnnDatasetIO.openMovePairReader(pairFile)) {
            for (;;) {
                AnnDatasetIO.MovePairSample s = r.next();
                if (s == null) break;
                stateBatch[n] = s.state();
                moveBatch[n] = s.move();
                yBatch[n] = s.chosen() ? 1f : 0f;
                n++;

                if (n == batchSize) {
                    lossSum += opt.stepMovePairBatch(stateBatch, moveBatch, yBatch, n);
                    count += n;
                    n = 0;
                }
            }
        }

        if (n > 0) {
            lossSum += opt.stepMovePairBatch(stateBatch, moveBatch, yBatch, n);
            count += n;
        }

        return new EpochStats(lossSum, count);
    }

    private record EpochStats(float lossSum, long count) {
        float lossAvg() { return count == 0 ? 0f : lossSum / (float) count; }
    }

    // ---------------- Adam optimizer over this specific network ----------------

    private static final class Adam {
        private static final float B1 = 0.9f;
        private static final float B2 = 0.999f;
        private static final float EPS = 1e-8f;

        private final AnnNetwork net;
        private final float lr;
        private final float l2;

        private long t = 0;

        // moment buffers
        private final float[] mW1;
        private final float[] vW1;
        private final float[] mb1;
        private final float[] vb1;

        private final float[] mW2;
        private final float[] vW2;
        private final float[] mb2;
        private final float[] vb2;

        private final float[] mWr;
        private final float[] vWr;
        private float mbr = 0f, vbr = 0f;

        private final float[] mW3;
        private final float[] vW3;
        private final float[] mb3;
        private final float[] vb3;

        private final float[] mW4;
        private final float[] vW4;
        private float mb4 = 0f, vb4 = 0f;

        // gradient buffers (reused each step)
        private final float[] gW1;
        private final float[] gb1;
        private final float[] gW2;
        private final float[] gb2;
        private final float[] gWr;
        private float gbr = 0f;
        private final float[] gW3;
        private final float[] gb3;
        private final float[] gW4;
        private float gb4 = 0f;

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

            this.mW3 = new float[net.W3.length];
            this.vW3 = new float[net.W3.length];
            this.mb3 = new float[net.b3.length];
            this.vb3 = new float[net.b3.length];

            this.mW4 = new float[net.W4.length];
            this.vW4 = new float[net.W4.length];

            this.gW1 = new float[net.W1.length];
            this.gb1 = new float[net.b1.length];
            this.gW2 = new float[net.W2.length];
            this.gb2 = new float[net.b2.length];
            this.gWr = new float[net.Wr.length];
            this.gW3 = new float[net.W3.length];
            this.gb3 = new float[net.b3.length];
            this.gW4 = new float[net.W4.length];
        }

        float stepRollBatch(float[][] state, float[] y, int n) {
            zeroGrads();

            float lossSum = 0f;
            float invN = 1f / Math.max(1, n);

            // per-sample scratch
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
                float dLogit = (p - y01); // dL/dlogit

                // roll head grads
                for (int i = 0; i < AnnConstants.TRUNK_H2; i++) {
                    gWr[i] += dLogit * emb[i];
                    dEmb[i] = dLogit * net.Wr[i];
                }
                gbr += dLogit;

                // backprop trunk
                for (int i = 0; i < AnnConstants.TRUNK_H2; i++) dZ2[i] = dEmb[i] * AnnMath.reluGrad(z2[i]);
                // W2, b2
                outerAdd(gW2, dZ2, a1, AnnConstants.TRUNK_H2, AnnConstants.TRUNK_H1);
                addVec(gb2, dZ2);
                // dA1 = W2^T * dZ2
                matTVec(net.W2, dZ2, dA1, AnnConstants.TRUNK_H2, AnnConstants.TRUNK_H1);
                for (int i = 0; i < AnnConstants.TRUNK_H1; i++) dZ1[i] = dA1[i] * AnnMath.reluGrad(z1[i]);
                outerAdd(gW1, dZ1, x, AnnConstants.TRUNK_H1, AnnConstants.STATE_DIM);
                addVec(gb1, dZ1);
            }

            // average grads
            scale(gW1, invN); scale(gb1, invN);
            scale(gW2, invN); scale(gb2, invN);
            scale(gWr, invN); gbr *= invN;

            applyAdam();
            return lossSum;
        }

        float stepMovePairBatch(float[][] state, float[][] move, float[] y, int n) {
            zeroGrads();

            float lossSum = 0f;
            float invN = 1f / Math.max(1, n);

            float[] z1 = new float[AnnConstants.TRUNK_H1];
            float[] a1 = new float[AnnConstants.TRUNK_H1];
            float[] z2 = new float[AnnConstants.TRUNK_H2];
            float[] emb = new float[AnnConstants.TRUNK_H2];

            float[] u = new float[AnnConstants.MOVE_INPUT_DIM];
            float[] z3 = new float[AnnConstants.MOVE_H1];
            float[] a3 = new float[AnnConstants.MOVE_H1];

            float[] dEmb = new float[AnnConstants.TRUNK_H2];
            float[] dZ2 = new float[AnnConstants.TRUNK_H2];
            float[] dA1 = new float[AnnConstants.TRUNK_H1];
            float[] dZ1 = new float[AnnConstants.TRUNK_H1];

            float[] dA3 = new float[AnnConstants.MOVE_H1];
            float[] dZ3 = new float[AnnConstants.MOVE_H1];
            float[] dU = new float[AnnConstants.MOVE_INPUT_DIM];

            for (int idx = 0; idx < n; idx++) {
                float[] x = state[idx];
                float[] mv = move[idx];
                float y01 = y[idx];

                net.forwardTrunk(x, z1, a1, z2, emb);

                System.arraycopy(emb, 0, u, 0, AnnConstants.TRUNK_H2);
                System.arraycopy(mv, 0, u, AnnConstants.TRUNK_H2, AnnConstants.MOVE_DIM);

                AnnNetwork.forwardDenseRelu(net.W3, net.b3, AnnConstants.MOVE_H1, AnnConstants.MOVE_INPUT_DIM, u, z3, a3);
                float logit = net.b4;
                for (int i = 0; i < AnnConstants.MOVE_H1; i++) logit += net.W4[i] * a3[i];
                lossSum += AnnMath.bceWithLogits(logit, y01);

                float p = AnnMath.sigmoid(logit);
                float dLogit = (p - y01);

                // W4, b4 grads
                for (int i = 0; i < AnnConstants.MOVE_H1; i++) {
                    gW4[i] += dLogit * a3[i];
                    dA3[i] = dLogit * net.W4[i];
                }
                gb4 += dLogit;

                for (int i = 0; i < AnnConstants.MOVE_H1; i++) dZ3[i] = dA3[i] * AnnMath.reluGrad(z3[i]);
                outerAdd(gW3, dZ3, u, AnnConstants.MOVE_H1, AnnConstants.MOVE_INPUT_DIM);
                addVec(gb3, dZ3);

                // dU = W3^T * dZ3
                matTVec(net.W3, dZ3, dU, AnnConstants.MOVE_H1, AnnConstants.MOVE_INPUT_DIM);
                System.arraycopy(dU, 0, dEmb, 0, AnnConstants.TRUNK_H2);

                // backprop trunk
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
            scale(gW3, invN); scale(gb3, invN);
            scale(gW4, invN); gb4 *= invN;

            applyAdam();
            return lossSum;
        }

        private void zeroGrads() {
            zero(gW1); zero(gb1);
            zero(gW2); zero(gb2);
            zero(gWr); gbr = 0f;
            zero(gW3); zero(gb3);
            zero(gW4); gb4 = 0f;
        }

        private void applyAdam() {
            t++;
            float b1t = (float) (1.0 - Math.pow(B1, t));
            float b2t = (float) (1.0 - Math.pow(B2, t));

            // Weight decay (L2) on weights only (not biases)
            addL2(gW1, net.W1, l2);
            addL2(gW2, net.W2, l2);
            addL2(gWr, net.Wr, l2);
            addL2(gW3, net.W3, l2);
            addL2(gW4, net.W4, l2);

            adamUpdate(net.W1, gW1, mW1, vW1, lr, b1t, b2t);
            adamUpdate(net.b1, gb1, mb1, vb1, lr, b1t, b2t);
            adamUpdate(net.W2, gW2, mW2, vW2, lr, b1t, b2t);
            adamUpdate(net.b2, gb2, mb2, vb2, lr, b1t, b2t);
            adamUpdate(net.Wr, gWr, mWr, vWr, lr, b1t, b2t);
            net.br = adamUpdateScalar(net.br, gbr, lr, b1t, b2t, true);

            adamUpdate(net.W3, gW3, mW3, vW3, lr, b1t, b2t);
            adamUpdate(net.b3, gb3, mb3, vb3, lr, b1t, b2t);
            adamUpdate(net.W4, gW4, mW4, vW4, lr, b1t, b2t);
            net.b4 = adamUpdateScalar(net.b4, gb4, lr, b1t, b2t, false);
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

        private float adamUpdateScalar(float w, float g, float lr, float b1t, float b2t, boolean isBr) {
            if (isBr) {
                mbr = B1 * mbr + (1f - B1) * g;
                vbr = B2 * vbr + (1f - B2) * g * g;
                float mh = mbr / b1t;
                float vh = vbr / b2t;
                return w - lr * mh / ((float) Math.sqrt(vh) + EPS);
            } else {
                mb4 = B1 * mb4 + (1f - B1) * g;
                vb4 = B2 * vb4 + (1f - B2) * g * g;
                float mh = mb4 / b1t;
                float vh = vb4 / b2t;
                return w - lr * mh / ((float) Math.sqrt(vh) + EPS);
            }
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
            // out = W^T * vIn, where W is outDim x inDim
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

    // ---------------- args helpers ----------------

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

