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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.os.UserManager;
import android.preference.PreferenceManager;
import android.provider.Telephony;
import android.telephony.CellBroadcastMessage;
import android.telephony.ServiceState;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.cdma.CdmaSmsCbProgramData;
import android.util.Log;
import com.android.internal.telephony.PhoneConstants;

import com.android.internal.telephony.ITelephony;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import com.android.internal.telephony.cdma.sms.SmsEnvelope;
import com.android.internal.telephony.TelephonyIntents;


public class CellBroadcastReceiver extends BroadcastReceiver {
       // add for bug 916677 start
        private class PhoneState {
        private Map mStateMap = null;

        public PhoneState() {
            mStateMap = new HashMap();
        }

        public int getServiceState(final int subId) {
            if (mStateMap == null || mStateMap.isEmpty()) {
                return -1;
            }
            for(Object key: mStateMap.keySet()) {
                if ((int)key == subId) {
                    return (int)mStateMap.get(key);
                }
            }
            return -1;
        }

        public void setSerivceState(final int subId, final int serviceState) {
            if (mStateMap == null) {
                return;
            }
            mStateMap.put(subId, serviceState);
        }
    };
    // add for bug 916677 end

    private static final String TAG = "CellBroadcastReceiver";
    static final boolean DBG = true;    // STOPSHIP: change to false before ship
    private static PhoneState mServiceState = null;

    public static final String CELLBROADCAST_START_CONFIG_ACTION =
            "android.cellbroadcastreceiver.START_CONFIG";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (mServiceState == null) {
            mServiceState = new PhoneState();
        }
        onReceiveWithPrivilege(context, intent, false);
    }

    protected void onReceiveWithPrivilege(Context context, Intent intent, boolean privileged) {
        if (DBG) log("onReceive " + intent);

        String action = intent.getAction();

        if (TelephonyIntents.ACTION_SERVICE_STATE_CHANGED.equals(action)) {
            if (DBG) log("Intent ACTION_SERVICE_STATE_CHANGED");
            int subId = intent.getExtras().getInt(PhoneConstants.SUBSCRIPTION_KEY);
            Log.d(TAG, "subscriptionId = " + subId);
            if (!SubscriptionManager.isValidSubscriptionId(subId)) {
                return;
            }
            ServiceState serviceState = ServiceState.newFromBundle(intent.getExtras());
            if (serviceState != null) {
                int newState = serviceState.getState();
                if (newState != mServiceState.getServiceState(subId)) {
                    Log.d(TAG, "Service state changed! " + newState + " Full: " + serviceState +
                            " Current state=" + mServiceState.getServiceState(subId));
                    mServiceState.setSerivceState(subId, newState);
                    if (((newState == ServiceState.STATE_IN_SERVICE) ||
                            (newState == ServiceState.STATE_EMERGENCY_ONLY)) &&
                            (UserManager.get(context).isSystemUser())) {
                        startConfigService(context.getApplicationContext(), subId);
                    }
                }
            }
        } else if (TelephonyIntents.ACTION_DEFAULT_SMS_SUBSCRIPTION_CHANGED.equals(action) ||
                CELLBROADCAST_START_CONFIG_ACTION.equals(action)) {
            // Todo: Add the service state check once the new get service state API is done.
            // Do not rely on mServiceState as it gets reset to -1 time to time because
            // the process of CellBroadcastReceiver gets killed every time once the job is done.
            if (UserManager.get(context).isSystemUser()) {
			List<SubscriptionInfo> subscriptionInfoList = SubscriptionManager.from(
                        context).getActiveSubscriptionInfoList();
                if (subscriptionInfoList != null) {
                    for (SubscriptionInfo subInfo : subscriptionInfoList) {
                startConfigService(context.getApplicationContext(), subInfo.getSubscriptionId());
				}
			  }
            }
            else {
                Log.e(TAG, "Not system user. Ignored the intent " + action);
            }
        } else if (Telephony.Sms.Intents.SMS_EMERGENCY_CB_RECEIVED_ACTION.equals(action) ||
                Telephony.Sms.Intents.SMS_CB_RECEIVED_ACTION.equals(action)) {
            // If 'privileged' is false, it means that the intent was delivered to the base
            // no-permissions receiver class.  If we get an SMS_CB_RECEIVED message that way, it
            // means someone has tried to spoof the message by delivering it outside the normal
            // permission-checked route, so we just ignore it.
            if (privileged) {
                intent.setClass(context, CellBroadcastAlertService.class);
                context.startService(intent);
            } else {
                loge("ignoring unprivileged action received " + action);
            }
        } else if (Telephony.Sms.Intents.SMS_SERVICE_CATEGORY_PROGRAM_DATA_RECEIVED_ACTION
                .equals(action)) {
            if (privileged) {
                CdmaSmsCbProgramData[] programDataList = (CdmaSmsCbProgramData[])
                        intent.getParcelableArrayExtra("program_data_list");
                if (programDataList != null) {
                    int subId = intent.getExtras().getInt(PhoneConstants.SUBSCRIPTION_KEY);
                    Log.d(TAG, "subscriptionId = " + subId);
                    handleCdmaSmsCbProgramData(context, programDataList, subId);
                } else {
                    loge("SCPD intent received with no program_data_list");
                }
            } else {
                loge("ignoring unprivileged action received " + action);
            }
        }else {
            Log.w(TAG, "onReceive() unexpected action " + action);
        }
    }

    /**
     * Handle Service Category Program Data message.
     * TODO: Send Service Category Program Results response message to sender
     *
     * @param context
     * @param programDataList
     */
    private void handleCdmaSmsCbProgramData(Context context,
            CdmaSmsCbProgramData[] programDataList, int subId) {
        for (CdmaSmsCbProgramData programData : programDataList) {
            switch (programData.getOperation()) {
                case CdmaSmsCbProgramData.OPERATION_ADD_CATEGORY:
                    tryCdmaSetCategory(context, programData.getCategory(), true, subId);
                    break;

                case CdmaSmsCbProgramData.OPERATION_DELETE_CATEGORY:
                    tryCdmaSetCategory(context, programData.getCategory(), false, subId);
                    break;

                case CdmaSmsCbProgramData.OPERATION_CLEAR_CATEGORIES:
                    tryCdmaSetCategory(context,
                            SmsEnvelope.SERVICE_CATEGORY_CMAS_EXTREME_THREAT, false, subId);
                    tryCdmaSetCategory(context,
                            SmsEnvelope.SERVICE_CATEGORY_CMAS_SEVERE_THREAT, false, subId);
                    tryCdmaSetCategory(context,
                            SmsEnvelope.SERVICE_CATEGORY_CMAS_CHILD_ABDUCTION_EMERGENCY, false,
                            subId);
                    tryCdmaSetCategory(context,
                            SmsEnvelope.SERVICE_CATEGORY_CMAS_TEST_MESSAGE, false, subId);
                    break;

                default:
                    loge("Ignoring unknown SCPD operation " + programData.getOperation());
            }
        }
    }

    private void tryCdmaSetCategory(Context context, int category, boolean enable, int subId) {
        switch (category) {
            case SmsEnvelope.SERVICE_CATEGORY_CMAS_EXTREME_THREAT:
                SubscriptionManager.setSubscriptionProperty(subId,
                        SubscriptionManager.CB_EXTREME_THREAT_ALERT,
                        (enable ? "1" : "0"));
                break;

            case SmsEnvelope.SERVICE_CATEGORY_CMAS_SEVERE_THREAT:
                SubscriptionManager.setSubscriptionProperty(subId,
                        SubscriptionManager.CB_SEVERE_THREAT_ALERT,
                        (enable ? "1" : "0"));
                break;

            case SmsEnvelope.SERVICE_CATEGORY_CMAS_CHILD_ABDUCTION_EMERGENCY:
                SubscriptionManager.setSubscriptionProperty(subId,
                        SubscriptionManager.CB_AMBER_ALERT,
                        (enable ? "1" : "0"));
                break;

            case SmsEnvelope.SERVICE_CATEGORY_CMAS_TEST_MESSAGE:
                SubscriptionManager.setSubscriptionProperty(subId,
                        SubscriptionManager.CB_CMAS_TEST_ALERT,
                        (enable ? "1" : "0"));
                break;

            default:
                Log.w(TAG, "Ignoring SCPD command to " + (enable ? "enable" : "disable")
                        + " alerts in category " + category);
        }
    }

    /**
     * Tell {@link CellBroadcastConfigService} to enable the CB channels.
     * @param context the broadcast receiver context
     */
    static void startConfigService(Context context, int subId) {
        Intent serviceIntent = new Intent(CellBroadcastConfigService.ACTION_ENABLE_CHANNELS,
                null, context, CellBroadcastConfigService.class);
        serviceIntent.putExtra(PhoneConstants.SUBSCRIPTION_KEY, subId);
        Log.d(TAG, "Start Cell Broadcast configuration.");
        context.startService(serviceIntent);
    }

    /**
     * @return true if the phone is a CDMA phone type
     */
    static boolean phoneIsCdma() {
        boolean isCdma = false;

        int subId = SubscriptionManager.getDefaultSmsSubscriptionId();
        if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            subId = SubscriptionManager.getDefaultSubscriptionId();
        }

        TelephonyManager tm = TelephonyManager.getDefault();
        if (tm != null) {
            isCdma = (tm.getCurrentPhoneType(subId) == TelephonyManager.PHONE_TYPE_CDMA);
        }
        return isCdma;
    }

    private static void log(String msg) {
        Log.d(TAG, msg);
    }

    private static void loge(String msg) {
        Log.e(TAG, msg);
    }
}
