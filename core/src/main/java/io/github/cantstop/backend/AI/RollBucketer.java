package io.github.cantstop.backend.AI;

import java.util.*;

public final class RollBucketer {

    static final class Bucket {
        // 3 pairings; each pairing encodes the ordered pair (u<=v) as u*16+v (both in 2..12)
        final int a, b, c; // sorted nondecreasing for canonicality

        Bucket(int x, int y, int z) {
            int[] t = {x, y, z};
            Arrays.sort(t);
            this.a = t[0];
            this.b = t[1];
            this.c = t[2];
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Bucket rb)) return false;
            return a == rb.a && b == rb.b && c == rb.c;
        }

        @Override
        public int hashCode() {
            return a * 31 * 31 + b * 31 + c;
        }
    }

    static final class Entry {
        final Bucket bucket;
        final int frequency; // number of 6^4 outcomes mapping to this bucket

        Entry(Bucket b, int f) {
            this.bucket = b;
            this.frequency = f;
        }
    }

    static final List<Entry> ENTRIES;

    static {
        Map<Bucket, Integer> freq = new HashMap<>(1024);
        for (int d0 = 1; d0 <= 6; d0++)
            for (int d1 = 1; d1 <= 6; d1++)
                for (int d2 = 1; d2 <= 6; d2++)
                    for (int d3 = 1; d3 <= 6; d3++) {
                        int p1 = pack(Math.min(d0 + d1, d2 + d3), Math.max(d0 + d1, d2 + d3));
                        int p2 = pack(Math.min(d0 + d2, d1 + d3), Math.max(d0 + d2, d1 + d3));
                        int p3 = pack(Math.min(d0 + d3, d1 + d2), Math.max(d0 + d3, d1 + d2));
                        Bucket b = new Bucket(p1, p2, p3);
                        freq.merge(b, 1, Integer::sum);
                    }
        ENTRIES = new ArrayList<>(freq.size());
        for (var e : freq.entrySet()) ENTRIES.add(new Entry(e.getKey(), e.getValue()));
    }

    static int pack(int u, int v) {
        return (u << 4) | v;
    }          // u,v in [2..12]

    public static int unpackU(int p) {
        return (p >> 4) & 0xF;
    }

    public static int unpackV(int p) {
        return p & 0xF;
    }
}
