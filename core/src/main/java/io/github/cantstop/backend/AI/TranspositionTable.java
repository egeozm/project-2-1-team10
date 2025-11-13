package io.github.cantstop.backend.AI;

final class TTEntry {
    final double value;
    final int depth;
    final byte nodeType;   // 0=max, 1=min, 2=chance
    final byte boundType;  // 0=exact (kept for future use)
    final int epoch;

    TTEntry(double v, int d, byte nt, byte bt, int epoch) {
        this.value = v;
        this.depth = d;
        this.nodeType = nt;
        this.boundType = bt;
        this.epoch = epoch;
    }
}

final class TranspositionTable {
    private final TTEntry[] table;
    private final long[] keys;

    TranspositionTable(int sizePow2) {
        int n = 1 << sizePow2;
        this.table = new TTEntry[n];
        this.keys = new long[n];
    }

    TTEntry get(long key) {
        int i = index(key);
        return keys[i] == key ? table[i] : null;
    }

    void put(long key, TTEntry e) {
        int i = index(key);
        keys[i] = key;
        table[i] = e;
    }

    private int index(long key) {
        return (int) ((key ^ (key >>> 32)) & (table.length - 1));
    }
}
