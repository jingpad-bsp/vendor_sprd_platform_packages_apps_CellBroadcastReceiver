/*
 * Copyright (C) 2012 The Android Open Source Project
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

import android.app.*;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.provider.Telephony;
import android.telephony.CellBroadcastMessage;
import android.telephony.SmsCbCmasInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.util.Linkify;
import android.util.Log;
import android.view.*;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;


import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Full-screen emergency alert with flashing warning icon.
 * Alert audio and text-to-speech handled by {@link CellBroadcastAlertAudio}.
 * Keyguard handling based on {@code AlarmAlertFullScreen} class from DeskClock app.
 */
public class CellBroadcastAlertFullScreen extends Activity {
    private static final String TAG = "CellBroadcastAlertFullScreen";

    /**
     * Intent extra for full screen alert launched from dialog subclass as a result of the
     * screen turning off.
     */
    static final String SCREEN_OFF_EXTRA = "screen_off";

    /**
     * Intent extra for non-emergency alerts sent when user selects the notification.
     */
    static final String FROM_NOTIFICATION_EXTRA = "from_notification";

    static final String OPEN_ALERT_DIALOG = "open_alert_dialog";

    /**
     * List of cell broadcast messages to display (oldest to newest).
     */
    protected ArrayList<CellBroadcastMessage> mMessageList;

    /**
     * Animation handler for the flashing warning icon (emergency alerts only).
     */
    private AlertAnimationHandler mAnimationHandler;

    /**
     * Handler to add and remove screen on flags for emergency alerts.
     */
    private KeepingScreenOnHandler mKeepingScreenOnHandler;
    private static final String PREFERENCE_NAME = "custom_config";
    private boolean mAllKeyActionDisabled;
    private MenuKeyBroadcastReceiver mMenuKeyBroadcastReceiver;  // Bug1074919
    private AlertDialog mAlertDialog;
    private boolean isOpenAlertDialog;
    protected Context mContext;
    private View mAlertDialogView;
    private boolean mUseLinkfy;

    /**
     * Returns the currently displayed message.
     */
    CellBroadcastMessage getLatestMessage() {
        int index = mMessageList.size() - 1;
        if (index >= 0) {
            return mMessageList.get(index);
        } else {
            return null;
        }
    }

    /**
     * Removes and returns the currently displayed message.
     */
    public CellBroadcastMessage removeLatestMessage() {
        int index = mMessageList.size() - 1;
        if (index >= 0) {
            return mMessageList.remove(index);
        } else {
            return null;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = this;
        Intent intent = getIntent();
        initCustomParms(intent);
        // Get message list from saved Bundle or from Intent.
        mMessageList = intent.getParcelableArrayListExtra(
                CellBroadcastMessage.SMS_CB_MESSAGE_EXTRA);
        Log.d(TAG, "onCreate getting message list from intent");
        // If we were started from a notification, dismiss it.
        clearNotification(intent);
        if ((mMessageList == null || mMessageList.size() == 0) && savedInstanceState != null) {
            Log.d(TAG, "onCreate getting message list from saved instance state");
            mMessageList = savedInstanceState.getParcelableArrayList(
                    CellBroadcastMessage.SMS_CB_MESSAGE_EXTRA);
        }

        if (mMessageList == null || mMessageList.size() == 0) {
            Log.e(TAG, "onCreate failed to get message list from saved Bundle");
            finish();
            return;
        }
        final Window win = getWindow();
        // We use a custom title, so remove the standard dialog title bar
        win.requestFeature(Window.FEATURE_NO_TITLE);

        // Bug1074919 begin
        IntentFilter filter = new IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        mMenuKeyBroadcastReceiver = new MenuKeyBroadcastReceiver();
        this.registerReceiver(mMenuKeyBroadcastReceiver, filter);
        // Bug1074919 end

        mKeepingScreenOnHandler = new KeepingScreenOnHandler(win);
        if (!isOpenAlertDialog) {
            // Full screen alerts display above the keyguard and when device is locked.
            win.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN
                    | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
            // For emergency alerts, keep screen on so the user can read it, unless this is a
            // full screen alert created by CellBroadcastAlertDialog when the screen turned off.
            CellBroadcastMessage message = getLatestMessage();
            if ((CellBroadcastConfigService.isEmergencyAlertMessage(message) || message.getServiceCategory() == 919 || (message.getServiceCategory() >= 4396 && message.getServiceCategory() <= 4399)) &&
                    (savedInstanceState != null || !getIntent().getBooleanExtra(SCREEN_OFF_EXTRA,
                            false))) {
                Log.d(TAG, "onCreate setting screen on timer for emergency alert");
                mKeepingScreenOnHandler.startScreenOnTimer();
            }
        }
        LayoutInflater inflater = LayoutInflater.from(this);
        setContentView(inflater.inflate(getLayoutResId(), null));
    }

    // Bug1074919 begin
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy...mMenuKeyBroadcastReceiver = " + mMenuKeyBroadcastReceiver);
        if (null != mMenuKeyBroadcastReceiver) {
            this.unregisterReceiver(mMenuKeyBroadcastReceiver);
        }
    }
    // Bug1074919 end

    /**
     * Called by {@link CellBroadcastAlertService} to add a new alert to the stack.
     *
     * @param intent The new intent containing one or more {@link CellBroadcastMessage}s.
     */
    @Override
    protected void onNewIntent(Intent intent) {
        ArrayList<CellBroadcastMessage> newMessageList = intent.getParcelableArrayListExtra(
                CellBroadcastMessage.SMS_CB_MESSAGE_EXTRA);
        initCustomParms(intent);
        if (newMessageList != null) {
            Log.d(TAG, "onNewIntent called with message list of size " + newMessageList.size());
            if (mMessageList.size() == 0) {
                mMessageList.addAll(newMessageList);
            } else {
                for (int i = 0; i < newMessageList.size(); i++) {
                    final CellBroadcastMessage n = newMessageList.get(i);
                    boolean dup = false;
                    for (int j = 0; j < mMessageList.size(); j++) {
                        final CellBroadcastMessage m = mMessageList.get(j);
                        dup = isDupCbm(n, m);
                        if (dup) {
                            break;
                        }
                    }
                    if (!dup) {
                        mMessageList.add(n);
                    }
                }
            }

        } else {
            Log.e(TAG, "onNewIntent called without SMS_CB_MESSAGE_EXTRA, ignoring");
        }
        clearNotification(intent);
    }

    private boolean isDupCbm(final CellBroadcastMessage n, final CellBroadcastMessage m) {
        if (n.getDeliveryTime() == m.getDeliveryTime()
                && n.getSerialNumber() == m.getSerialNumber()
                && n.getServiceCategory() == m.getServiceCategory()
                && n.getMessageBody().equals(m.getMessageBody())) {
            return true;
        }
        return false;
    }

    /**
     * Save the list of messages so the state can be restored later.
     *
     * @param outState Bundle in which to place the saved state.
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelableArrayList(CellBroadcastMessage.SMS_CB_MESSAGE_EXTRA, mMessageList);
        Log.d(TAG, "onSaveInstanceState saved message list to bundle");
    }

    /**
     * Start animating warning icon.
     */
    @Override
    protected void onResume() {
        Log.d(TAG, "onResume called");
        super.onResume();
        CellBroadcastMessage message = getLatestMessage();
        if (message == null) {
            finish();
            return;
        }
        disableAllKeyAction();
        if (!isOpenAlertDialog) {
            createAlertDialog();
        }
        Log.d(TAG, "onResume showAlertMessage");
        showAlertMessage(message);
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop...");

        if (mAnimationHandler != null) {
            mAnimationHandler.stopIconAnimation();
        }
        //stop audio vibration and when OptOutDialog is show
        if (mMessageList == null || mMessageList.size() == 0) {
            stopService(new Intent(this, CellBroadcastAlertAudio.class));
        }

    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        CellBroadcastMessage message = getLatestMessage();
        if (message != null && !message.isEtwsMessage()) {
            switch (event.getKeyCode()) {
                // Volume keys and camera keys mute the alert sound/vibration (except ETWS).
                case KeyEvent.KEYCODE_VOLUME_UP:
                case KeyEvent.KEYCODE_VOLUME_DOWN:
                case KeyEvent.KEYCODE_VOLUME_MUTE:
                case KeyEvent.KEYCODE_CAMERA:
                case KeyEvent.KEYCODE_FOCUS:
                    // Stop playing alert sound/vibration/speech (if started)
                    stopService(new Intent(this, CellBroadcastAlertAudio.class));
                    return true;

                default:
                    break;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    /**
     * Ignore the back button for emergency alerts (overridden by alert dialog so that the dialog
     * is dismissed).
     */
    @Override
    public void onBackPressed() {
        super.onBackPressed();
        dismissAlertDialog();
    }

    /**
     * Returns the resource ID for either the full screen or dialog layout.
     */
    protected int getLayoutResId() {
        return R.layout.cell_broadcast_alert_fullscreen;
    }

    /**
     * Stop animating warning icon and stop the {@link CellBroadcastAlertAudio}
     * service if necessary.
     */
    protected void dismissAlertDialog() {
        Log.d(TAG, "dismissAlertDialog:  ");
        stopService(new Intent(mContext, CellBroadcastAlertAudio.class));
        // Cancel any pending alert reminder
        CellBroadcastAlertReminder.cancelAlertReminder();
        CellBroadcastMessage lastMessage = getLatestMessage();
        if (null != lastMessage) {
            markAsReaded(lastMessage);
            removeLatestMessage();
            enableAllKeyAction();
            Log.d(TAG, " there is last Message");

            if (mMessageList.size() > 0) {
                Log.d(TAG, " there is last Message 1111");
                showAlertMessage(getLatestMessage());
                return;
            }
            closeAlertDialog();
            showAlertOptDialog(lastMessage);
        }
        Log.d(TAG, "finish ");
        finish();
    }

    //Bug 1068419 begin
    private class MenuKeyBroadcastReceiver extends BroadcastReceiver {
        private final String SYSTEM_REASON = "reason";
        private final String[] systemKeys = {"homekey", "recentapps"};
        private final static int FLAGACTION_NONE = -1;
        private final static int FLAGACTION_KEY_PRESS = 1;
        private final static int FLAGACTION_SCREEN_OFF = 2;

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, "action is " + action);
            int actionFlag = FLAGACTION_NONE;
            if (action.equals(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)) {
                String systemReason = intent.getStringExtra(SYSTEM_REASON);
                if (systemReason != null && systemKeys.length > 0) {
                    Log.d(TAG, "key [" + systemReason + "] pressed");
                    for (final String systemKey : systemKeys) {
                        if (systemKey.equals(systemReason)) {
                            if (!isOpenAlertDialog) {
                                stopService(new Intent(getApplicationContext(),
                                        CellBroadcastAlertAudio.class));
                            }
                            if (mAllKeyActionDisabled) {
                                actionFlag = FLAGACTION_KEY_PRESS;
                            }
                        }
                    }
                }
            } else if (action.equals(Intent.ACTION_SCREEN_OFF)) {
                Log.d(TAG, "receive broadcast ACTION_SCREEN_OFF");
                if (!isOpenAlertDialog) {
                    stopService(new Intent(getApplicationContext(), CellBroadcastAlertAudio.class));
                }
                if (mAllKeyActionDisabled) {
                    actionFlag = FLAGACTION_SCREEN_OFF;
                }
            }
            //Bug 1118924 begin
            //if carrier request is dialog can't disappear before user click close button in
            // alert dialog
            //but at sometimes, user pressed down powerkey before emergency message will be
            // received suddenly, this dialog maybe disappear
            if (actionFlag > FLAGACTION_NONE) {
                closeAlertDialog();
                finish();
                final Intent alertIntent =
                        new Intent(CellBroadcastAlertService.SHOW_NEW_ALERT_ACTION);
                alertIntent.setClass(context, CellBroadcastAlertService.class);
                alertIntent.putExtra("message", getLatestMessage());
                Log.d(TAG," new message : ");
                startService(alertIntent);
            }
            //Bug 1118924 end
        }
    }
    //Bug 1068419 end

    protected void createAlertDialog() {
        Log.d(TAG," createAlertDialog  mAlertDialog is null ? " +(mAlertDialog==null));
        if(mAlertDialog == null){
            mAlertDialogView = View.inflate(this, R.layout.cell_broadcast_alert, null);
            mAlertDialogView.findViewById(R.id.cellbroad_dialog_content).setBackgroundResource(R.drawable.alert_fullscreen_bg);
            ((Button) mAlertDialogView.findViewById(R.id.dismissButton)).setOnClickListener(
                    new Button.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            dismissAlertDialog();
                        }
                    });
            AlertDialog.Builder builder = new AlertDialog.Builder(mContext, R.style.AlertDialog);
            builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    dismissAlertDialog();
                    finish();
                }
            });
            mAlertDialog = builder.create();
            mAlertDialog.setCanceledOnTouchOutside(false);
            Window win = mAlertDialog.getWindow();

            if (mAllKeyActionDisabled) {
                win.setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
            }
            win.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN
                    | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            win.setDimAmount(0f);
            win.setLayout(LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);

        }
        if(!mAlertDialog.isShowing()){
            mAlertDialog.show();
            mAlertDialog.setContentView(mAlertDialogView);
        }
    }

    protected void showAlertMessage(final CellBroadcastMessage message) {
        if (message == null) {
            return;
        }
        Log.d(TAG,"showAlertMessage :");
        updateAlertText(mAlertDialogView, message);
        executeAnimationHandler(mAlertDialogView, message);
    }

    private void closeAlertDialog() {
        if (mAlertDialog != null && mAlertDialog.isShowing()) {
            mAlertDialog.dismiss();
            forceEnableAllKeyAction();
        }
    }

    /**
     * Update alert text when a new emergency alert arrives.
     */
    protected void updateAlertText(final View warningView, final CellBroadcastMessage message) {
        Log.d(TAG," updateAlertText : ");
        final SharedPreferences sp = mContext.getSharedPreferences(PREFERENCE_NAME, MODE_PRIVATE);
        final boolean showTimestamp = sp.getBoolean("isRequestFixedTimeshow", false);
        final boolean limitContentLength = sp.getBoolean("isRequestPopContentdigit", false);
        int titleId = CellBroadcastResources.getDialogTitleResource(message, mContext);

        CharSequence channelName = mContext.getText(titleId);
        String szChannelName =
                mContext.getResources().getString(R.string.cb_other_message_identifiers);
        if (szChannelName.equals(channelName.toString())) {
            channelName = message.getServiceCategory() + "";
            ((TextView) warningView.findViewById(R.id.alertTitle)).setText(channelName);
        } else {
            ((TextView) warningView.findViewById(R.id.alertTitle)).setText(titleId);
        }
        if (showTimestamp) {
            SimpleDateFormat format = new SimpleDateFormat("dd-MM-yyyy/HH:mm");
            String time = format.format(message.getDeliveryTime());
            ((TextView) warningView.findViewById(R.id.date)).setText(time);
        }

        String messageBody = message.getMessageBody();
        TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        boolean supportPeru = "pe".equals(tm.getSimCountryIso(message.getSubId()))
                 || "pe".equals(tm.getNetworkCountryIso(message.getSubId()));
        if (limitContentLength && messageBody != null && messageBody.length() >= 90) {
            if (supportPeru) {
                messageBody = messageBody.substring(0, 82);
            } else {
                messageBody = messageBody.substring(0, 90);
            }
        }

        final TextView msgView = (TextView) warningView.findViewById(R.id.message);
        msgView.setText(messageBody);
        if (mUseLinkfy) {
            Linkify.addLinks(msgView, Linkify.EMAIL_ADDRESSES | Linkify.PHONE_NUMBERS |
                    Linkify.WEB_URLS);
        }

        boolean supportAreas =
                "ae".equals(tm.getSimCountryIso(message.getSubId())) || "pe".equals(tm.getSimCountryIso(message.getSubId()))
                        || "cl".equals(tm.getSimCountryIso(message.getSubId()));
        if (supportAreas) {
            int buttonId = CellBroadcastResources.getDialogButtonResource(mContext, message);
            final Button btn = (Button) warningView.findViewById(R.id.dismissButton);
            if (btn != null) {
                btn.setText(buttonId);
            }
        }
    }

    protected void executeAnimationHandler(final View alertView,
                                           final CellBroadcastMessage message) {
        // If the new intent was sent from a notification, dismiss it.
        if (message == null)
            return;
        final int subId = message.getSubId();
        final int categoryId = message.getServiceCategory();
        final TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        final String simIso = tm.getSimCountryIso(subId);
        final String networkIso = tm.getNetworkCountryIso(subId);
        final boolean isChileArea = CellBroadcastConfigService.COUNTRY_CHL.equals(simIso);
        final boolean isPeruArea = CellBroadcastConfigService.COUNTRY_PERU.equals(simIso);
        Log.d(TAG, "subId = " + subId + ", simIso = " + simIso + ", networkIso = " + networkIso);
        if (CellBroadcastConfigService.isEmergencyAlertMessage(message) || (isPeruArea && categoryId == 50) || (categoryId == 919 && (isChileArea || isPeruArea)) || (categoryId >= 4396 && categoryId <= 4399)) {
            if (mAnimationHandler != null) {
                mAnimationHandler.stopIconAnimation();
            }
            mAnimationHandler = new AlertAnimationHandler(this.getApplicationContext(),
                    (ImageView) (alertView.findViewById(R.id.icon)));
            mAnimationHandler.startIconAnimation();
        } else {
            if (null != mAnimationHandler)
                mAnimationHandler.stopIconAnimation();
        }
    }

    private void forceEnableAllKeyAction() {
        enableAllKeyAction();
    }

    private void enableAllKeyAction() {
        if (mAllKeyActionDisabled) {
            Settings.System.putInt(getContentResolver(), Settings.System.TIGO_CMAS_STATUS, 0);
            StatusBarManager statusBarManager =
                    (StatusBarManager) mContext.getSystemService(Context.STATUS_BAR_SERVICE);
            statusBarManager.disable(StatusBarManager.DISABLE_NONE);
            // Remove pending screen-off messages (animation messages are removed in onPause()).
            mKeepingScreenOnHandler.stopScreenOnTimer();
        }
    }

    private void disableAllKeyAction() {
        if (mAllKeyActionDisabled) {
            StatusBarManager statusBarManager =
                    (StatusBarManager) mContext.getSystemService(Context.STATUS_BAR_SERVICE);
            statusBarManager.disable(StatusBarManager.DISABLE_EXPAND | StatusBarManager.DISABLE_RECENT | StatusBarManager.DISABLE_BACK | StatusBarManager.DISABLE_HOME);
            Settings.System.putInt(getContentResolver(), Settings.System.TIGO_CMAS_STATUS, 1);
        }
    }

    private void showAlertOptDialog(final CellBroadcastMessage message) {
        // Set the opt-out dialog flag if this is a CMAS alert (other than Presidential Alert).
        if (!isOpenAlertDialog && message.isCmasMessage() && message.getCmasMessageClass() !=
                SmsCbCmasInfo.CMAS_CLASS_PRESIDENTIAL_LEVEL_ALERT) {
            boolean boolResult = SubscriptionManager.getBooleanSubscriptionProperty(
                    message.getSubId(), SubscriptionManager.CB_OPT_OUT_DIALOG, true, this);

            if (boolResult) {
                // Clear the flag so the user will only see the opt-out dialog once.
                Log.d(TAG, "subscriptionId of last message = " + message.getSubId());
                SubscriptionManager.setSubscriptionProperty(message.getSubId(),
                        SubscriptionManager.CB_OPT_OUT_DIALOG, "0");
                KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
                if (km.inKeyguardRestrictedInputMode()) {
                    Log.d(TAG, "Showing opt-out dialog in new activity (secure keyguard)");
                    Intent intent = new Intent(this, CellBroadcastOptOutActivity.class);
                    startActivity(intent);
                } else {
                    Log.d(TAG, "Showing opt-out dialog in current activity");
                    CellBroadcastOptOutActivity.showOptOutDialog(new WeakReference<>(this));
                    return; // don't call finish() until user dismisses the dialog
                }
            }
        }
    }

    private void markAsReaded(final CellBroadcastMessage message) {
        if (message == null) {
            return;
        }
        final long deliveryTime = message.getDeliveryTime();
        // Mark broadcast as read on a background thread.
        new CellBroadcastContentProvider.AsyncCellBroadcastTask(getContentResolver())
                .execute(new CellBroadcastContentProvider.CellBroadcastOperation() {
                    @Override
                    public boolean execute(CellBroadcastContentProvider provider) {
                        return provider.markBroadcastRead(
                                Telephony.CellBroadcasts.DELIVERY_TIME, deliveryTime);
                    }
                });
    }

    private void initCustomParms(final Intent intent) {
        isOpenAlertDialog = intent.getBooleanExtra(OPEN_ALERT_DIALOG, false);
        SharedPreferences sp = this.getSharedPreferences(PREFERENCE_NAME, MODE_PRIVATE);
        mAllKeyActionDisabled = !isOpenAlertDialog && sp.getBoolean("mProhibitAllKeyEvent", false);
        mUseLinkfy = sp.getBoolean("mUseLinkfy", false);
        Log.d(TAG, "initCustomParms: isOpenAlertDialog = " + isOpenAlertDialog
                + ", mAllKeyActionDisabled = " + mAllKeyActionDisabled + " mUseLinkfy :" + mUseLinkfy);

    }

    /**
     * Try to cancel any notification that may have started this activity.
     */
    private void clearNotification(Intent intent) {
        if (intent.getBooleanExtra(FROM_NOTIFICATION_EXTRA, false)) {
            Log.d(TAG, "Dismissing notification");
            NotificationManager notificationManager =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.cancel(CellBroadcastAlertService.NOTIFICATION_ID);
            CellBroadcastReceiverApp.clearNewMessageList();
        }
    }
}
