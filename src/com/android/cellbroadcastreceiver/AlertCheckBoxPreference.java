package com.android.cellbroadcastreceiver;

import android.content.Context;
import android.content.res.TypedArray;
import android.preference.CheckBoxPreference;
import android.util.AttributeSet;

import java.util.ArrayList;
import android.util.Log;

public class AlertCheckBoxPreference extends CheckBoxPreference {
    private ArrayList<IntRange> mIntRange;

    public AlertCheckBoxPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        mIntRange = new ArrayList<IntRange>();
        final TypedArray typedAttributes =
                context.obtainStyledAttributes(attrs, R.styleable.AlertCheckBoxPreference);
        final String channels = typedAttributes.getString(R.styleable.AlertCheckBoxPreference_channels);
        convertToChannelList(channels);
        typedAttributes.recycle();
    }

    public ArrayList<IntRange> getChannels() {
        return mIntRange;
    }

    public String toString() {
        if (mIntRange == null || mIntRange.size() == 0) {
            return "";
        }

        if (mIntRange.size() == 1) {
            return "channels is " + mIntRange.toString();
        }

        StringBuffer sb = new StringBuffer("channels is [");
        for (int i = 0; i < mIntRange.size(); i++) {
            if (i == 0) {
                sb.append(mIntRange.get(i).toString());
            } else {
                sb.append(", " + mIntRange.get(i).toString());
            }
        }
        sb.append("]");
        return sb.toString();
    }

    private void convertToChannelList(final String channels) {
        if (channels == null || channels.isEmpty()) {
            return;
        }
        String[] sq = channels.split(",");
        for (int i = 0; i < sq.length; i++) {
            String[] tmp = sq[i].trim().split("-");
            Log.d("weicn", "" + tmp.length);
            IntRange ir = null;
            try {
                if (tmp.length == 1) {
                    int num = Integer.parseInt(tmp[0].trim());
                    ir = new IntRange(num);
                } else if (tmp.length == 2) {
                    int num1 = Integer.parseInt(tmp[0].trim());
                    int num2 = Integer.parseInt(tmp[1].trim());
                    ir = new IntRange(num1, num2);
                } else {
                    throw new AssertionError("length of array must be 1 or 2.");
                }
                if (ir != null) {
                    mIntRange.add(ir);
                }
            } catch (Exception e) {
                throw new AssertionError("wrong format, right format is XX or XX-XX or XX,XX or XX,XX-XX, and XX must be in [0, 65535]");
            }
        }
    }
}
