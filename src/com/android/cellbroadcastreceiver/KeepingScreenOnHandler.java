package com.android.cellbroadcastreceiver;

import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.WindowManager;
import android.view.Window;

import java.util.concurrent.atomic.AtomicInteger;

class KeepingScreenOnHandler extends Handler {
    private final String TAG = "KeepingScreenOnHandler";

    /**
     * Handler to add {@code FLAG_KEEP_SCREEN_ON} for emergency alerts. After a short delay,
     * remove the flag so the screen can turn off to conserve the battery.
     */

    /** Length of time to keep the screen turned on. */
    private static final int KEEP_SCREEN_ON_DURATION_MSEC = 180000;//3minute

    /** Latest {@code message.what} value for detecting old messages. */
    private final AtomicInteger mCount = new AtomicInteger();

    private Window mWindow;

    /** Package local constructor (called from outer class). */
    KeepingScreenOnHandler(final Window win) {
        mWindow = win;
    }

    /** Add screen on window flags and queue a delayed message to remove them later. */
    public void startScreenOnTimer() {
        addWindowFlags();
        int msgWhat = mCount.incrementAndGet();
        removeMessages(msgWhat - 1);    // Remove previous message, if any.
        sendEmptyMessageDelayed(msgWhat, KEEP_SCREEN_ON_DURATION_MSEC);//if prohibitAllKeyEvent,keep the screen on alltime
        Log.d(TAG, "added FLAG_KEEP_SCREEN_ON, queued screen off message id " + msgWhat);
    }

    /** Remove the screen on window flags and any queued screen off message. */
    public void stopScreenOnTimer() {
        removeMessages(mCount.get());
        clearWindowFlags();
    }

    /** Set the screen on window flags. */
    private void addWindowFlags() {
        mWindow.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    /** Clear the screen on window flags. */
    private void clearWindowFlags() {
        mWindow.clearFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    public void handleMessage(Message msg) {
        int msgWhat = msg.what;
        if (msgWhat == mCount.get()) {
            clearWindowFlags();
            Log.d(TAG, "removed FLAG_KEEP_SCREEN_ON with id " + msgWhat);
        } else {
            Log.e(TAG, "discarding screen off message with id " + msgWhat);
        }
    }
}
