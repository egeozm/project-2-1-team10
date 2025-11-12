package io.github.cantstop.backend;

final class BustTable {
    // For any bitmask S over sums 2..12, P[mask] = probability the next roll busts
    static final double[] P = new double[1 << 11];

    static {
        for (int mask = 0; mask < (1 << 11); mask++) {
            int safe = 0;
            for (int d0 = 1; d0 <= 6; d0++)
                for (int d1 = 1; d1 <= 6; d1++)
                    for (int d2 = 1; d2 <= 6; d2++)
                        for (int d3 = 1; d3 <= 6; d3++) {
                            int s1 = d0 + d1, t1 = d2 + d3, s2 = d0 + d2, t2 = d1 + d3, s3 = d0 + d3, t3 = d1 + d2;
                            boolean ok =
                                allowed(mask, s1) && allowed(mask, t1) ||
                                    allowed(mask, s2) && allowed(mask, t2) ||
                                    allowed(mask, s3) && allowed(mask, t3);
                            if (ok) safe++;
                        }
            P[mask] = 1.0 - (safe / 1296.0);
        }
    }

    static boolean allowed(int mask, int sum) {
        return sum >= 2 && sum <= 12 && ((mask >>> (sum - 2)) & 1) != 0;
    }
}
