package com.sprd.cellbroadcastreceiver.ui;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.telephony.SubscriptionManager;
import android.util.Log;

import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.sprd.cellbroadcastreceiver.provider.ChannelTableDefine;
import com.sprd.cellbroadcastreceiver.util.Utils;

/**
 * Created by SPRD on 2018/11/16.
 */
public class CbBaseActivity extends Activity {
    private static final String TAG = "CbBaseActivity";

    private BaseBr baseBr = null;
    protected int mSubId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    private int mSlotId = SubscriptionManager.INVALID_SIM_SLOT_INDEX;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        mSlotId = Utils.getSimInfor(getIntent());
        if (null == baseBr) {
            baseBr = new BaseBr();
            IntentFilter intentFilter = new IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
            intentFilter.addAction(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
            registerReceiver(baseBr, intentFilter);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, " onDestroy ");
        if (baseBr != null) {
            unregisterReceiver(baseBr);
        }
    }

    private class BaseBr extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            String simStatus = intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE);
            int subIdEx = intent.getIntExtra(PhoneConstants.SUBSCRIPTION_KEY, -1);
            Log.d(TAG, "simStatus=" + simStatus + " subIdEx=" + subIdEx);
            if (action.equals(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)) {
                String reason = intent.getStringExtra("reason");
                if (reason != null && (reason.equals("homekey"))) { //|| reason.equals("recentapps"))) {
                    Log.d(TAG, "homekey action = Intent.ACTION_CLOSE_SYSTEM_DIALOGS " + reason);
                    finish();
                }
            } else if (intent.getAction() == TelephonyIntents.ACTION_SIM_STATE_CHANGED
                    && IccCardConstants.INTENT_VALUE_ICC_ABSENT.equals(simStatus)) {
                if (SubscriptionManager.isValidSubscriptionId(subIdEx)) {
                    if (subIdEx == mSubId) {
                        finish();
                    }
                    return;
                }
                int slotId = intent.getIntExtra("slot", -1);
                Log.d(TAG, "SIM ABSENT, slotId = " + slotId);
                if (mSlotId == slotId) {
                    finish();
                }
            }
        }
    }
}
