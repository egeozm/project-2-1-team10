package io.github.cantstop.model.ai.AI_ANN;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Random;

/**
 * Small MLP for Can’t Stop imitation learning.
 *
 * Architecture (baseline):
 *  - trunk: 49 -> 128 -> 64 (ReLU)
 *  - roll head: 64 -> 1
 *  - move head: (64+26=90) -> 64 -> 1
 */
public final class AnnNetwork {

    // W are stored row-major: outDim x inDim
    public final float[] W1 = new float[AnnConstants.TRUNK_H1 * AnnConstants.STATE_DIM];
    public final float[] b1 = new float[AnnConstants.TRUNK_H1];
    public final float[] W2 = new float[AnnConstants.TRUNK_H2 * AnnConstants.TRUNK_H1];
    public final float[] b2 = new float[AnnConstants.TRUNK_H2];

    public final float[] Wr = new float[AnnConstants.TRUNK_H2];
    public float br = 0f;

    public final float[] W3 = new float[AnnConstants.MOVE_H1 * AnnConstants.MOVE_INPUT_DIM];
    public final float[] b3 = new float[AnnConstants.MOVE_H1];
    public final float[] W4 = new float[AnnConstants.MOVE_H1];
    public float b4 = 0f;

    public AnnNetwork(Random rnd) {
        initXavier(rnd);
    }

    private AnnNetwork() {
        // for load()
    }

    public void initXavier(Random rnd) {
        if (rnd == null) rnd = new Random();
        xavierUniform(rnd, W1, AnnConstants.STATE_DIM, AnnConstants.TRUNK_H1);
        zeros(b1);
        xavierUniform(rnd, W2, AnnConstants.TRUNK_H1, AnnConstants.TRUNK_H2);
        zeros(b2);

        xavierUniform(rnd, Wr, AnnConstants.TRUNK_H2, 1);
        br = 0f;

        xavierUniform(rnd, W3, AnnConstants.MOVE_INPUT_DIM, AnnConstants.MOVE_H1);
        zeros(b3);
        xavierUniform(rnd, W4, AnnConstants.MOVE_H1, 1);
        b4 = 0f;
    }

    public float[] embedState(float[] state) {
        float[] z1 = new float[AnnConstants.TRUNK_H1];
        float[] a1 = new float[AnnConstants.TRUNK_H1];
        float[] z2 = new float[AnnConstants.TRUNK_H2];
        float[] emb = new float[AnnConstants.TRUNK_H2];
        forwardTrunk(state, z1, a1, z2, emb);
        return emb;
    }

    public float rollLogit(float[] state) {
        float[] emb = embedState(state);
        float sum = br;
        for (int i = 0; i < emb.length; i++) sum += Wr[i] * emb[i];
        return sum;
    }

    public float rollProb(float[] state) {
        return AnnMath.sigmoid(rollLogit(state));
    }

    public float moveLogit(float[] state, float[] moveFeat) {
        float[] emb = embedState(state);
        return moveLogitFromEmbedding(emb, moveFeat);
    }

    public float moveLogitFromEmbedding(float[] embedding64, float[] moveFeat26) {
        float[] u = new float[AnnConstants.MOVE_INPUT_DIM];
        System.arraycopy(embedding64, 0, u, 0, AnnConstants.TRUNK_H2);
        System.arraycopy(moveFeat26, 0, u, AnnConstants.TRUNK_H2, AnnConstants.MOVE_DIM);

        float[] z3 = new float[AnnConstants.MOVE_H1];
        float[] a3 = new float[AnnConstants.MOVE_H1];
        forwardDenseRelu(W3, b3, AnnConstants.MOVE_H1, AnnConstants.MOVE_INPUT_DIM, u, z3, a3);
        float logit = b4;
        for (int i = 0; i < AnnConstants.MOVE_H1; i++) logit += W4[i] * a3[i];
        return logit;
    }

    // ---------------- Training helpers ----------------

    void forwardTrunk(float[] x, float[] z1, float[] a1, float[] z2, float[] emb) {
        forwardDenseRelu(W1, b1, AnnConstants.TRUNK_H1, AnnConstants.STATE_DIM, x, z1, a1);
        forwardDenseRelu(W2, b2, AnnConstants.TRUNK_H2, AnnConstants.TRUNK_H1, a1, z2, emb);
    }

    static void forwardDenseRelu(float[] W, float[] b, int outDim, int inDim, float[] x, float[] z, float[] a) {
        for (int o = 0; o < outDim; o++) {
            int row = o * inDim;
            float sum = b[o];
            for (int i = 0; i < inDim; i++) sum += W[row + i] * x[i];
            z[o] = sum;
            a[o] = AnnMath.relu(sum);
        }
    }

    // ---------------- Serialization ----------------

    private static final byte[] MAGIC = "ANNW".getBytes(StandardCharsets.US_ASCII);
    private static final int VERSION = 1;

    public void save(File f) throws IOException {
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(f)))) {
            out.write(MAGIC);
            out.writeInt(VERSION);
            out.writeInt(AnnConstants.STATE_DIM);
            out.writeInt(AnnConstants.MOVE_DIM);
            out.writeInt(AnnConstants.TRUNK_H1);
            out.writeInt(AnnConstants.TRUNK_H2);
            out.writeInt(AnnConstants.MOVE_H1);

            writeArr(out, W1); writeArr(out, b1);
            writeArr(out, W2); writeArr(out, b2);
            writeArr(out, Wr); out.writeFloat(br);
            writeArr(out, W3); writeArr(out, b3);
            writeArr(out, W4); out.writeFloat(b4);
        }
    }

    public static AnnNetwork load(File f) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(f)))) {
            byte[] got = in.readNBytes(MAGIC.length);
            for (int i = 0; i < MAGIC.length; i++) {
                if (got[i] != MAGIC[i]) throw new IOException("Bad ANN weights header");
            }
            int ver = in.readInt();
            if (ver != VERSION) throw new IOException("Unsupported ANN weights version: " + ver);

            int stateDim = in.readInt();
            int moveDim = in.readInt();
            int h1 = in.readInt();
            int h2 = in.readInt();
            int mh1 = in.readInt();

            if (stateDim != AnnConstants.STATE_DIM || moveDim != AnnConstants.MOVE_DIM ||
                h1 != AnnConstants.TRUNK_H1 || h2 != AnnConstants.TRUNK_H2 || mh1 != AnnConstants.MOVE_H1) {
                throw new IOException("Weights dims mismatch with code constants");
            }

            AnnNetwork net = new AnnNetwork();
            readArr(in, net.W1); readArr(in, net.b1);
            readArr(in, net.W2); readArr(in, net.b2);
            readArr(in, net.Wr); net.br = in.readFloat();
            readArr(in, net.W3); readArr(in, net.b3);
            readArr(in, net.W4); net.b4 = in.readFloat();
            return net;
        }
    }

    private static void writeArr(DataOutputStream out, float[] a) throws IOException {
        out.writeInt(a.length);
        for (float v : a) out.writeFloat(v);
    }

    private static void readArr(DataInputStream in, float[] a) throws IOException {
        int n = in.readInt();
        if (n != a.length) throw new IOException("Array length mismatch: file=" + n + " expected=" + a.length);
        for (int i = 0; i < a.length; i++) a[i] = in.readFloat();
    }

    private static void zeros(float[] a) {
        for (int i = 0; i < a.length; i++) a[i] = 0f;
    }

    private static void xavierUniform(Random rnd, float[] a, int fanIn, int fanOut) {
        float limit = (float) Math.sqrt(6.0 / (fanIn + fanOut));
        for (int i = 0; i < a.length; i++) {
            a[i] = (rnd.nextFloat() * 2f - 1f) * limit;
        }
    }
}

