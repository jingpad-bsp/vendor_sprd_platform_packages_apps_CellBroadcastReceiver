package com.android.cellbroadcastreceiver;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import java.util.concurrent.atomic.AtomicInteger;

public class AlertAnimationHandler extends Handler {
    private final String TAG = "AlertAnimationHandler";
    /**
     * Animation handler for the flashing warning icon (emergency alerts only).
     */
    
    /** Length of time for the warning icon to be visible. */
    private static final int WARNING_ICON_ON_DURATION_MSEC = 800;

    /** Length of time for the warning icon to be off. */
    private static final int WARNING_ICON_OFF_DURATION_MSEC = 800;

    /** Latest {@code message.what} value for detecting old messages. */
    private final AtomicInteger mCount = new AtomicInteger();

    /** Warning icon state: visible == true, hidden == false. */
    private boolean mWarningIconVisible;

    /** The warning icon Drawable. */
    private Drawable mWarningIcon;

    /** The View containing the warning icon. */
    private ImageView mWarningIconView;

    private Context mContext;

    /** Package local constructor (called from outer class). */
    AlertAnimationHandler(final Context context, final ImageView iconResId) {
        mContext = context;
        mWarningIconView = iconResId;
    }

    /** Start the warning icon animation. */
    public void startIconAnimation() {
        if (!initDrawableAndImageView()) {
            return;     // init failure
        }
        mWarningIconVisible = true;
        mWarningIconView.setVisibility(View.VISIBLE);
        updateIconState();
        queueAnimateMessage();
    }

    /** Stop the warning icon animation. */
    public void stopIconAnimation() {
        // Increment the counter so the handler will ignore the next message.
        mCount.incrementAndGet();
        if (mWarningIconView != null) {
            mWarningIconView.setVisibility(View.GONE);
        }
    }

    /** Update the visibility of the warning icon. */
    private void updateIconState() {
        mWarningIconView.setImageAlpha(mWarningIconVisible ? 255 : 0);
        mWarningIconView.invalidateDrawable(mWarningIcon);
    }

    /** Queue a message to animate the warning icon. */
    private void queueAnimateMessage() {
        int msgWhat = mCount.incrementAndGet();
        sendEmptyMessageDelayed(msgWhat, mWarningIconVisible ? WARNING_ICON_ON_DURATION_MSEC
                : WARNING_ICON_OFF_DURATION_MSEC);
        // Log.d(TAG, "queued animation message id = " + msgWhat);
    }

    @Override
    public void handleMessage(Message msg) {
        if (msg.what == mCount.get()) {
            mWarningIconVisible = !mWarningIconVisible;
            updateIconState();
            queueAnimateMessage();
        }
    }

    /**
     * Initialize the Drawable and ImageView fields.
     * @return true if successful; false if any field failed to initialize
     */
    private boolean initDrawableAndImageView() {
        if (mWarningIcon == null) {
            try {
                mWarningIcon = mContext.getResources().getDrawable(R.drawable.ic_warning_large);
            } catch (Resources.NotFoundException e) {
                Log.e(TAG, "warning icon resource not found", e);
                return false;
            }
        }
        if (mWarningIconView != null) {
            mWarningIconView.setImageDrawable(mWarningIcon);
        } else {
            Log.e(TAG, "failed to get ImageView for warning icon");
            return false;
        }
        return true;
    }
}
