/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.cellbroadcastreceiver;

import android.util.Log;

import java.util.ArrayList;

public class IntRangeManager {
    private final String TAG = "CBIntRangeManager";

    private ArrayList<IntRange> mIntRange;

    public IntRangeManager() {
        mIntRange = new ArrayList<IntRange>();
    }

    public ArrayList<IntRange> getIntRange() {
        return mIntRange;
    }

    public boolean including(final IntRange ir) {
        if (mIntRange == null || ir == null) {
            return false;
        }
        if (ir.getStartId() > ir.getEndId()) {
            Log.d(TAG,  ir.toString() + " is invalid, mStartId must <= mEndId");
            return false;
        }
        for (int i = mIntRange.size() - 1; i >= 0; i--) {
            IntRange current = mIntRange.get(i);
            if (current.including(ir)) {
                //ir was aleady included in mIntRange, do nothing & return
                Log.d(TAG, "mIntRange aleady include " + ir.toString());
                return true;
            }
        }
        return false;
    }

    public boolean including(final int mStartId, final int mEndId) {
        return including(new IntRange(mStartId, mEndId));
    }

    public int childsSize() {
        return (mIntRange == null ? 0 : mIntRange.size());
    }

    public boolean isEmpty() {
        return (childsSize() == 0);
    }

    public IntRange getChild(final int index) {
        if (isEmpty()) {
            return null;
        }
        if (index < 0 || index >= childsSize()) {
            return null;
        }
        return mIntRange.get(index);
    }

    public void add(final IntRange ir) {
        if (ir == null) {
            return;
        }
        Log.d(TAG, "add " + ir.toString() + " begin...");
        if (ir.getStartId() > ir.getEndId()) {
            Log.d(TAG,  ir.toString() + " is invalid, mStartId must <= mEndId");
            return;
        }
        if (mIntRange == null) {
            mIntRange = new ArrayList<IntRange>();
        }
        if (mIntRange.size() == 0) {
            mIntRange.add(ir);
            Log.d(TAG, "mIntRange is null or empty, add " + ir.toString() + " directly");
            return;
        }
        for (int i = mIntRange.size() - 1; i >= 0; i--) {
            IntRange current = mIntRange.get(i);
            if (current.including(ir)) {
                //ir was aleady included in mIntRange, do nothing & return
                Log.d(TAG, "mIntRange aleady include " + ir.toString() + ", do nothing!");
                return;
            }
            if (ir.including(current)) {
                mIntRange.remove(i);
            }
        }
        if (mIntRange.size() == 0) {
            mIntRange.add(ir);
            Log.d(TAG, "delete all mIntRange's elements, add " + ir.toString() + " directly");
            return;
        }
        //find the index of ir's startId and endId in mIntRange
        //first store the index of ir's startId in mIntRange
        //second store the index of ir's endId in mIntRange
        //first must <= second
        int first = 0;
        int second = 0;
        for (int i = 0; i < mIntRange.size(); i++) {
            IntRange current = mIntRange.get(i);
            if (current.getStartId() <= ir.getStartId()) {
                first = i;
            }
            if (current.getEndId() <= ir.getEndId()) {
                //if current is the last, force to set second as mIntRange.size() - 1
                second = ((i == mIntRange.size() - 1) ? i : i + 1);
            } else {
                break;  //finder complete its task
            }
        }
        Log.d(TAG, "find " + ir.toString() + "\'s position between " + first + " and " + second);
        IntRange fstIR = mIntRange.get(first);
        IntRange sndIR = mIntRange.get(second);
        Log.d(TAG, "first IntRange: " + fstIR.toString() + ", second IntRange: " + sndIR.toString());

        if (ir.getEndId() < fstIR.getStartId() -1) {
            //add before first
            mIntRange.add(0, ir);
            Log.d(TAG, "add " + ir.toString() + " at the first position.");
            return;
        }

        if (ir.getStartId() > sndIR.getEndId() + 1) {
            //add after last
            mIntRange.add(ir);
            Log.d(TAG, "add " + ir.toString() + " at the last position.");
            return;
        }

        if (second - first > 1) {
            for (int i = second - 1; i > first; i--) {
                mIntRange.remove(i);
            }
            second = first + 1;
        }

        final boolean rightJoin = fstIR.including(ir.getStartId()) || fstIR.rightJoin(ir.getStartId());
        final boolean leftJoin = sndIR.including(ir.getEndId()) || sndIR.leftJoin(ir.getEndId());
        if (!leftJoin && !rightJoin) {
            boolean rmvFirst = false;
            if (ir.including(fstIR)) {
                mIntRange.remove(fstIR);
                rmvFirst = true;
            }
            if (ir.including(sndIR)) {
                mIntRange.remove(sndIR);
            }
            if (rmvFirst) {
                mIntRange.add(first, ir);
            } else {
                mIntRange.add(second, ir);
            }
            Log.d(TAG, "add " + ir.toString() + " at [" + (rmvFirst ? first : second) + "]");
        } else if (rightJoin && !leftJoin) {
            Log.d(TAG, ir.toString() + " is only right join with " + fstIR.toString());
            fstIR.setEndId(ir.getEndId());
        } else if (!rightJoin && leftJoin) {
            Log.d(TAG, ir.toString() + " is only left join with " + sndIR.toString());
            sndIR.setStartId(ir.getStartId());
        } else {
            Log.d(TAG, ir.toString() + " is right & left join with " + fstIR.toString() + ", " + sndIR.toString());
            fstIR.setEndId(sndIR.getEndId());
            mIntRange.remove(sndIR);
        }
    }

    public void add(final int startId, final int endId) {
        add(new IntRange(startId, endId));
    }

    public void add(final IntRangeManager irm) {
        if (irm == null || irm.childsSize() == 0) {
            return;
        }
        for (int i = 0; i < irm.childsSize(); i++) {
            add(irm.getChild(i));
        }
    }

    public void remove(final IntRange ir) {
        if (ir == null) {
            return;
        }
        Log.d(TAG, "remove " + ir.toString() + " begin...");
        if (ir.getStartId() > ir.getEndId()) {
            Log.d(TAG,  ir.toString() + " is invalid, mStartId must <= mEndId");
            return;
        }
        if (mIntRange == null || mIntRange.size() == 0) {
            Log.d(TAG, "mIntRange is null or empty, do nothing!");
            return;
        }

        for (int i = 0; i < mIntRange.size(); i++) {
            IntRange current = mIntRange.get(i);
            if (ir.equals(current)) {
                mIntRange.remove(current);
                Log.d(TAG, "remove " + ir.toString() + " directly.");
                return;
            }
            if (ir.including(current)) {
                mIntRange.remove(current);
                Log.d(TAG, "remove " + current.toString() + " directly.");
                i -= 1;
                continue;
            }
            final boolean currentIncludeIrStartId = current.including(ir.getStartId());
            final boolean currentIncludeIrEndId = current.including(ir.getEndId());
            if (currentIncludeIrStartId && currentIncludeIrEndId) {
                if (current.getStartId() == ir.getStartId()) {
                    current.setStartId(ir.getEndId() + 1);
                } else if (current.getEndId() == ir.getEndId()) {
                    current.setEndId(ir.getStartId() - 1);
                } else {
                    IntRange tmp = new IntRange(ir.getEndId() + 1, current.getEndId());
                    mIntRange.add(i + 1, tmp);
                    current.setEndId(ir.getStartId() - 1);
                }
                return;
            }
            if (!currentIncludeIrStartId && currentIncludeIrEndId) {
                current.setStartId(ir.getEndId() + 1);
                return;
            }
            if (currentIncludeIrStartId && !currentIncludeIrEndId) {
                current.setEndId(ir.getStartId() - 1);
                continue;
            }
        }
    }

    public void remove(final int startId, final int endId) {
        remove(new IntRange(startId, endId));
    }

    public String toString() {
        if (mIntRange == null) {
            return "null";
        } else {
            StringBuffer sb = new StringBuffer();
            sb.append("[");
            for (int i = 0; i < mIntRange.size(); i++) {
                if (i != 0) {
                    sb.append(", ");
                }
                sb.append(mIntRange.get(i).toString());
            }
            sb.append("]");
            return sb.toString();
        }
    }
}