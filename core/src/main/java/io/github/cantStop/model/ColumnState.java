package io.github.cantStop.model;

import io.github.cantStop.utensils.ConstantsBE;

import static io.github.cantStop.utensils.ConstantsBE.*;

public final class ColumnState {
    private ColumnStatus status = ColumnStatus.OPEN;
    private int redHeightPerm = 0;
    private int blueHeightPerm = 0;
    private Integer tempHeight = null; // null = no runners

    public ColumnStatus status() {
        return status;
    }

    public int permHeightFor(Player p) {
        if (p == Player.RED) {
            return redHeightPerm;
        } else {
            return blueHeightPerm;
        }
    }

    public Integer tempHeight() {
        return tempHeight;
    }

    public boolean isLocked() {
        return status.isLocked();
    }

    public void lockFor(Player p, int sumForThisColumn) {
        int max = ConstantsBE.maxHeight(sumForThisColumn);

        if (isLocked() || permHeightFor(p) < max) {return;}

        if (p == Player.RED) {
            status = ColumnStatus.LOCKED_RED;
        } else {
            status = ColumnStatus.LOCKED_BLUE;
        }

        tempHeight = null;
    }



    public void clearTemp() {
        tempHeight = null;
    }

    public void applyTempAdvance(Player p, int sumForThisColumn) {
        int max = maxHeight(sumForThisColumn);
        int base = permHeightFor(p);
        int current;
        if (tempHeight == null) {
            current = base;
        } else {
            current = tempHeight;
        }
        if (current + 1 < max) {
            tempHeight = current + 1;
        } else {
            tempHeight = max;
        }
    }

    public void commitTempToPerm(Player p, int sumForThisColumn) {
        if (tempHeight == null) return;
        if (p == Player.RED) {
            if (tempHeight > redHeightPerm) {
                redHeightPerm = tempHeight;
            }
        } else {
            if (tempHeight > blueHeightPerm) {
                blueHeightPerm = tempHeight;
            }
        }
        tempHeight = null;
    }
}
