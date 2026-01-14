package io.github.cantstop.model.ai.AI_ANN;

public final class AnnMath {
    private AnnMath() {}

    public static float relu(float x) { return x > 0f ? x : 0f; }
    public static float reluGrad(float preAct) { return preAct > 0f ? 1f : 0f; }

    public static float sigmoid(float x) {
        // Stable-ish for float
        if (x >= 0f) {
            float z = (float) Math.exp(-x);
            return 1f / (1f + z);
        } else {
            float z = (float) Math.exp(x);
            return z / (1f + z);
        }
    }

    /** Binary cross-entropy with logits (stable). */
    public static float bceWithLogits(float logit, float y01) {
        // max(0,z) - z*y + log(1+exp(-abs(z)))
        float z = logit;
        float abs = Math.abs(z);
        float term = (float) Math.log1p(Math.exp(-abs));
        float max0 = Math.max(0f, z);
        return max0 - z * y01 + term;
    }
}

