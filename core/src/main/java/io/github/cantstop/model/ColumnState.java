package io.github.cantstop.model;

import io.github.cantstop.utensils.ConstantsBE;

import static io.github.cantstop.utensils.ConstantsBE.*;

public final class ColumnState {
    private ColumnStatus status = ColumnStatus.OPEN;
    private int redHeightPerm = 0;
    private int blueHeightPerm = 0;
    private Integer tempHeight = null; // null = no runners
    private Player lockedBy = null;
    private Player tempOwner = null; // new: keeps track of who placed the temp marker



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

    public Player lockedBy() {
        return lockedBy; // returns null if not locked
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
        lockedBy = p;
    }



    public void clearTemp() {
        tempHeight = null;
        tempOwner = null;
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

        if (tempOwner == null) {
            tempOwner = p;
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

    public Player tempOwner() {
        return tempOwner;
    }

}
