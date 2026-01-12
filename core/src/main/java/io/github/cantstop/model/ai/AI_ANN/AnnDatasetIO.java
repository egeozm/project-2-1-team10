package io.github.cantstop.model.ai.AI_ANN;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * Binary dataset IO for ANN imitation learning.
 *
 * Format is intentionally simple: fixed-size records, read until EOF.
 */
public final class AnnDatasetIO {
    private AnnDatasetIO() {}

    private static final int VERSION = 1;

    private static final byte[] MAGIC_ROLL = "ANNR".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] MAGIC_PAIR = "ANNP".getBytes(StandardCharsets.US_ASCII);

    public static RollWriter openRollWriter(File f) throws IOException {
        return new RollWriter(f);
    }

    public static MovePairWriter openMovePairWriter(File f) throws IOException {
        return new MovePairWriter(f);
    }

    public static RollReader openRollReader(File f) throws IOException {
        return new RollReader(f);
    }

    public static MovePairReader openMovePairReader(File f) throws IOException {
        return new MovePairReader(f);
    }

    public static final class RollWriter implements Closeable, Flushable {
        private final DataOutputStream out;
        private long count = 0;

        private RollWriter(File f) throws IOException {
            this.out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(f)));
            writeHeader(out, MAGIC_ROLL);
            out.writeInt(VERSION);
            out.writeInt(AnnConstants.STATE_DIM);
        }

        public void write(float[] state, boolean roll) throws IOException {
            if (state.length != AnnConstants.STATE_DIM) {
                throw new IllegalArgumentException("state dim mismatch");
            }
            for (float v : state) out.writeFloat(v);
            out.writeByte(roll ? 1 : 0);
            count++;
        }

        public long count() { return count; }

        @Override public void flush() throws IOException { out.flush(); }
        @Override public void close() throws IOException { out.close(); }
    }

    public static final class MovePairWriter implements Closeable, Flushable {
        private final DataOutputStream out;
        private long count = 0;

        private MovePairWriter(File f) throws IOException {
            this.out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(f)));
            writeHeader(out, MAGIC_PAIR);
            out.writeInt(VERSION);
            out.writeInt(AnnConstants.STATE_DIM);
            out.writeInt(AnnConstants.MOVE_DIM);
        }

        public void write(float[] state, float[] move, boolean chosen) throws IOException {
            if (state.length != AnnConstants.STATE_DIM) throw new IllegalArgumentException("state dim mismatch");
            if (move.length != AnnConstants.MOVE_DIM) throw new IllegalArgumentException("move dim mismatch");
            for (float v : state) out.writeFloat(v);
            for (float v : move) out.writeFloat(v);
            out.writeByte(chosen ? 1 : 0);
            count++;
        }

        public long count() { return count; }

        @Override public void flush() throws IOException { out.flush(); }
        @Override public void close() throws IOException { out.close(); }
    }

    public static final class RollReader implements Closeable {
        private final DataInputStream in;

        private RollReader(File f) throws IOException {
            this.in = new DataInputStream(new BufferedInputStream(new FileInputStream(f)));
            readAndValidateHeader(in, MAGIC_ROLL);
            int v = in.readInt();
            if (v != VERSION) throw new IOException("Unsupported roll dataset version: " + v);
            int stateDim = in.readInt();
            if (stateDim != AnnConstants.STATE_DIM) {
                throw new IOException("STATE_DIM mismatch: file=" + stateDim + " code=" + AnnConstants.STATE_DIM);
            }
        }

        /** @return next sample or null on EOF */
        public RollSample next() throws IOException {
            try {
                float[] s = new float[AnnConstants.STATE_DIM];
                for (int i = 0; i < s.length; i++) s[i] = in.readFloat();
                boolean roll = in.readByte() != 0;
                return new RollSample(s, roll);
            } catch (EOFException eof) {
                return null;
            }
        }

        @Override public void close() throws IOException { in.close(); }
    }

    public static final class MovePairReader implements Closeable {
        private final DataInputStream in;

        private MovePairReader(File f) throws IOException {
            this.in = new DataInputStream(new BufferedInputStream(new FileInputStream(f)));
            readAndValidateHeader(in, MAGIC_PAIR);
            int v = in.readInt();
            if (v != VERSION) throw new IOException("Unsupported pair dataset version: " + v);
            int stateDim = in.readInt();
            int moveDim = in.readInt();
            if (stateDim != AnnConstants.STATE_DIM) {
                throw new IOException("STATE_DIM mismatch: file=" + stateDim + " code=" + AnnConstants.STATE_DIM);
            }
            if (moveDim != AnnConstants.MOVE_DIM) {
                throw new IOException("MOVE_DIM mismatch: file=" + moveDim + " code=" + AnnConstants.MOVE_DIM);
            }
        }

        /** @return next sample or null on EOF */
        public MovePairSample next() throws IOException {
            try {
                float[] s = new float[AnnConstants.STATE_DIM];
                float[] m = new float[AnnConstants.MOVE_DIM];
                for (int i = 0; i < s.length; i++) s[i] = in.readFloat();
                for (int i = 0; i < m.length; i++) m[i] = in.readFloat();
                boolean chosen = in.readByte() != 0;
                return new MovePairSample(s, m, chosen);
            } catch (EOFException eof) {
                return null;
            }
        }

        @Override public void close() throws IOException { in.close(); }
    }

    public record RollSample(float[] state, boolean roll) {}
    public record MovePairSample(float[] state, float[] move, boolean chosen) {}

    private static void writeHeader(DataOutputStream out, byte[] magic) throws IOException {
        out.write(magic);
    }

    private static void readAndValidateHeader(DataInputStream in, byte[] expectedMagic) throws IOException {
        byte[] got = in.readNBytes(expectedMagic.length);
        if (got.length != expectedMagic.length) throw new EOFException("Missing dataset header");
        for (int i = 0; i < expectedMagic.length; i++) {
            if (got[i] != expectedMagic[i]) {
                throw new IOException("Bad dataset magic header");
            }
        }
    }
}

