package com.android.cellbroadcastreceiver;

public class IntRange {
    private int mStartId;
    private int mEndId;

    IntRange(int startId, int endId) {
        if (!isValid(startId, endId)) {
            fail("[" + startId + ", " + endId + "] is a pair of invalid channels, valid number must be in [0, 65535] and startId <= endId");
        }
        mStartId = startId;
        mEndId = endId;
    }

    IntRange(final int startId) {
        if (!isValid(startId)) {
            fail("[" + startId + "] is one invalid channel, valid number must be in [0, 65535]");
        }
        mStartId = startId;
        mEndId = startId;
    }

    public void setStartId(final int id) {
        this.mStartId = id;
    }

    public int getStartId() {
        return this.mStartId;
    }

    public void setEndId(final int id) {
        this.mEndId = id;
    }

    public int getEndId() {
        return this.mEndId;
    }

    public boolean equals(final IntRange ir) {
        return (this.mStartId == ir.mStartId && this.mEndId == ir.mEndId);
    }

    public boolean including(final IntRange ir) {
        return (this.mStartId <= ir.mStartId && this.mEndId >= ir.mEndId);
    }

    public boolean including(final int id) {
        return (this.mStartId <= id && this.mEndId >=id);
    }

    public boolean leftJoin(final int id) {
        return (this.mStartId - 1 == id);
    }

    public boolean rightJoin(final int id) {
        return (this.mEndId + 1 == id);
    }

    public String toString() {
        if (mStartId == mEndId) {
            return "[" + this.mStartId + "]";
        } else {
            return "[" + this.mStartId + "-" + this.mEndId + "]";
        }
    }

    private boolean isValid(final int startId) {
        if (startId >= 0 && startId <= 65535) {
            return true;
        }
        return false;
    }

    private boolean isValid(final int startId, final int endId) {
        if (!isValid(startId) || !isValid(endId)) {
            return false;
        }
        if (startId > endId) {
            return false;
        }
        return true;
    }

    private void fail(final String message) {
        throw new AssertionError(message);
    }
}
