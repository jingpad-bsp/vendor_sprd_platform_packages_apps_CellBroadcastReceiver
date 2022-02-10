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

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.HandlerThread;
import android.os.Message;
import android.os.SystemProperties;
import android.preference.PreferenceManager;
import android.telephony.CellBroadcastMessage;
import android.telephony.SmsManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.cdma.sms.SmsEnvelope;
import com.android.internal.telephony.gsm.SmsCbConstants;

import static com.android.cellbroadcastreceiver.CellBroadcastReceiver.DBG;

/**
 * This service manages enabling and disabling ranges of message identifiers
 * that the radio should listen for. It operates independently of the other
 * services and runs at boot time and after exiting airplane mode.
 *
 * Note that the entire range of emergency channels is enabled. Test messages
 * and lower priority broadcasts are filtered out in CellBroadcastAlertService
 * if the user has not enabled them in settings.
 *
 * TODO: add notification to re-enable channels after a radio reset.
 */
public class CellBroadcastConfigService extends IntentService {
    private static final String TAG = "CellBroadcastConfigService";

    static final String ACTION_ENABLE_CHANNELS = "ACTION_ENABLE_CHANNELS";

    static final String EMERGENCY_BROADCAST_RANGE_GSM =
            "ro.cb.gsm.emergencyids";

    public static final String COUNTRY_ISRAEL = "ir";
    public static final String COUNTRY_TAIWAN = "tw";
    public static final String COUNTRY_BRAZIL = "br";
    public static final String COUNTRY_PERU = "pe";
    public static final String COUNTRY_CHL = "cl";
    public static final String COUNTRY_DUBAI = "ae";
    public static final String COUNTRY_NEWZEALAND = "nz";

    public CellBroadcastConfigService() {
        super(TAG);          // use class name for worker thread name
    }

    private static void setChannelRange(CellBroadcastConfigManager manager, String ranges, boolean enable) {
        if (DBG)log("setChannelRange: " + ranges);
        try {
            for (String channelRange : ranges.split(",")) {
                int dashIndex = channelRange.indexOf('-');
                if (dashIndex != -1) {
                    int startId = Integer.decode(channelRange.substring(0, dashIndex).trim());
                    int endId = Integer.decode(channelRange.substring(dashIndex + 1).trim());
                    manager.setGsmCellBroadcastChannelRange(enable, startId, endId);
                } else {
                    int messageId = Integer.decode(channelRange.trim());
                    manager.setGsmCellBroadcastChannel(enable, messageId);
                }
            }
        } catch (NumberFormatException e) {
            Log.e(TAG, "Number Format Exception parsing emergency channel range", e);
        }
    }

    /**
     * Returns true if this is a standard or operator-defined emergency alert message.
     * This includes all ETWS and CMAS alerts, except for AMBER alerts.
     * @param message the message to test
     * @return true if the message is an emergency alert; false otherwise
     */
    static boolean isEmergencyAlertMessage(CellBroadcastMessage message) {
        if (message == null) {
            return false;
        }
        if(message.getEtwsWarningInfo() !=null){
            if(!message.getEtwsWarningInfo().isPopupAlert() ||!message.getEtwsWarningInfo().isEmergencyUserAlert())
                return false;
        }
        if (message.isEmergencyAlertMessage()) {
            return true;
        }

        // Todo: Move the followings to CarrierConfig
        // Check for system property defining the emergency channel ranges to enable
        String emergencyIdRange = (CellBroadcastReceiver.phoneIsCdma()) ?
                "" : SystemProperties.get(EMERGENCY_BROADCAST_RANGE_GSM);

        if (TextUtils.isEmpty(emergencyIdRange)) {
            return false;
        }
        try {
            int messageId = message.getServiceCategory();
            for (String channelRange : emergencyIdRange.split(",")) {
                int dashIndex = channelRange.indexOf('-');
                if (dashIndex != -1) {
                    int startId = Integer.decode(channelRange.substring(0, dashIndex).trim());
                    int endId = Integer.decode(channelRange.substring(dashIndex + 1).trim());
                    if (messageId >= startId && messageId <= endId) {
                        return true;
                    }
                } else {
                    int emergencyMessageId = Integer.decode(channelRange.trim());
                    if (emergencyMessageId == messageId) {
                        return true;
                    }
                }
            }
        } catch (NumberFormatException e) {
            Log.e(TAG, "Number Format Exception parsing emergency channel range", e);
        }
        return false;
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        final int subId = intent.getExtras().getInt(PhoneConstants.SUBSCRIPTION_KEY);
        preCustomConfigs(CellBroadcastConfigService.this, subId);
        if (ACTION_ENABLE_CHANNELS.equals(intent.getAction())) {
            SmsManager manager = SmsManager.getSmsManagerForSubscriptionId(subId);
            //add for bug747983 start
            try{
                if (manager != null) {
                    setCellBroadcastOnSub(manager, subId, true);
                }else{
                    setCellBroadcastOnSub(manager, subId, false);
                }
            } catch (Exception ex) {
                Log.e(TAG, "exception enabling cell broadcast channels", ex);
            }
            //add for bug747983 end
        }
    }

    /**
     * Enable/disable cell broadcast messages id on one subscription
     * This includes all ETWS and CMAS alerts.
     * @param manager SMS manager
     * @param subId Subscription id
     * @param enableForSub True if want to enable messages on this sub (e.g default SMS). False
     *                     will disable all messages
     */
    private void setCellBroadcastOnSub(SmsManager manager, int subId, boolean enableForSub) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        Resources res = getResources();
        // boolean for each user preference checkbox, true for checked, false for unchecked
        // Note: If enableEmergencyAlerts is false, it disables ALL emergency broadcasts
        // except for CMAS presidential. i.e. to receive CMAS severe alerts, both
        // enableEmergencyAlerts AND enableCmasSevereAlerts must be true.
        final boolean enableEmergencyAlerts = enableForSub && SubscriptionManager.getBooleanSubscriptionProperty(
                subId, SubscriptionManager.CB_EMERGENCY_ALERT, true, this);
        // Todo: Move this to CarrierConfig later.
        String emergencyIdRange = (CellBroadcastReceiver.phoneIsCdma()) ?
                "" : SystemProperties.get(EMERGENCY_BROADCAST_RANGE_GSM);

        boolean enableEtwsAlerts = enableEmergencyAlerts;

        // CMAS Presidential must be always on (See 3GPP TS 22.268 Section 6.2) regardless
        // user's preference
        boolean enablePresidential = enableForSub;

        boolean enableCmasExtremeAlerts = enableEmergencyAlerts && SubscriptionManager
                .getBooleanSubscriptionProperty(subId,
                        SubscriptionManager.CB_EXTREME_THREAT_ALERT, true, this);

        boolean enableCmasSevereAlerts = enableEmergencyAlerts && SubscriptionManager.getBooleanSubscriptionProperty(
                subId, SubscriptionManager.CB_SEVERE_THREAT_ALERT, true, this);

        boolean enableCmasAmberAlerts = enableEmergencyAlerts && SubscriptionManager.getBooleanSubscriptionProperty(
                subId, SubscriptionManager.CB_AMBER_ALERT, true, this);

        // Check if ETWS/CMAS test message is forced disabled on the device.
        boolean forceDisableEtwsCmasTest =
                CellBroadcastSettings.isEtwsCmasTestMessageForcedDisabled(this, subId);

        boolean enableEtwsTestAlerts = !forceDisableEtwsCmasTest &&
                enableEmergencyAlerts &&
                SubscriptionManager.getBooleanSubscriptionProperty(subId, SubscriptionManager.CB_ETWS_TEST_ALERT, false, this);
        boolean enableCmasTestAlerts = !forceDisableEtwsCmasTest &&
                enableEmergencyAlerts &&
                SubscriptionManager.getBooleanSubscriptionProperty(
                        subId, SubscriptionManager.CB_CMAS_TEST_ALERT, false, this);

        TelephonyManager tm = (TelephonyManager) getSystemService(
                Context.TELEPHONY_SERVICE);

        boolean enableChannel50Support = enableEmergencyAlerts && res.getBoolean(R.bool.show_brazil_settings) &&
                COUNTRY_BRAZIL.equals(tm.getSimCountryIso(subId));

        boolean enableChannel50Alerts = enableEmergencyAlerts && enableChannel50Support &&
                SubscriptionManager.getBooleanSubscriptionProperty(subId,
                        SubscriptionManager.CB_CHANNEL_50_ALERT, true, this);

        // Current Israel requires enable certain CMAS messages ids.
        // Todo: Move this to CarrierConfig later.
        boolean supportIsraelPwsAlerts = enableEmergencyAlerts && (COUNTRY_ISRAEL.equals(tm.getSimCountryIso(subId))
                || COUNTRY_ISRAEL.equals(tm.getNetworkCountryIso(subId)));

        boolean supportChilePwsAlerts = res.getBoolean(R.bool.show_chile_settings) && (COUNTRY_CHL.equals(tm.getSimCountryIso(subId)) || COUNTRY_CHL.equals(tm.getNetworkCountryIso(subId)));
        boolean supportPeruPwsAlerts = res.getBoolean(R.bool.show_peru_settings) && (COUNTRY_PERU.equals(tm.getSimCountryIso(subId)) || COUNTRY_PERU.equals(tm.getNetworkCountryIso(subId)));
        boolean enableDubaiPwsSupport = res.getBoolean(R.bool.show_dubai_settings) &&
                (COUNTRY_DUBAI.equals(tm.getSimCountryIso(subId)) || COUNTRY_DUBAI.equals(tm.getNetworkCountryIso(subId)));
        boolean enableTaiWanPwsSupport = res.getBoolean(R.bool.show_taiwan_settings) &&
                (COUNTRY_TAIWAN.equals(tm.getSimCountryIso(subId)) || COUNTRY_TAIWAN.equals(tm.getNetworkCountryIso(subId)));
        boolean enableNzPwsSupport = res.getBoolean(R.bool.show_newzealand_settings) &&
                (COUNTRY_NEWZEALAND.equals(tm.getSimCountryIso(subId)) || COUNTRY_NEWZEALAND.equals(tm.getNetworkCountryIso(subId)));

        // add for peru pws start
        boolean enablePws_4370_919 = prefs.getBoolean(CellBroadcastSettings.KEY_ENABLE_CHANNEL_4370_919, false);
        boolean enablePws_4383 = prefs.getBoolean(CellBroadcastSettings.KEY_ENABLE_CHANNEL_4383, false);
        boolean enablePws_4382 = prefs.getBoolean(CellBroadcastSettings.KEY_ENABLE_CHANNEL_4382, false);
        boolean enablePws_4380_519 = prefs.getBoolean(CellBroadcastSettings.KEY_ENABLE_CHANNEL_4380_519, false);
        boolean enablePws_4381_519 = prefs.getBoolean(CellBroadcastSettings.KEY_ENABLE_CHANNEL_4381_519, false);
        boolean enablePws4396_4399 = prefs.getBoolean(CellBroadcastSettings.KEY_ENABLE_CHANNEL_4396_4399, false);
        // add for peru pws end

        //add for Dubai PWS setting
        CellBroadcastResources.setContext(this);

        boolean enablePws4379_4392 = enableDubaiPwsSupport &&
                prefs.getBoolean(CellBroadcastSettings.KEY_ENABLE_CHANNEL_4379_4392, true);
        boolean enablePws4380_4393 = enableDubaiPwsSupport &&
                prefs.getBoolean(CellBroadcastSettings.KEY_ENABLE_CHANNEL_4380_4393, false);
        boolean enablePws4396_4397 = enableDubaiPwsSupport &&
                prefs.getBoolean(CellBroadcastSettings.KEY_ENABLE_CHANNEL_4396_4397, true);
        //end for Dubai PWS setting


        if (DBG) {
            log("enableEmergencyAlerts = " + enableEmergencyAlerts);
            log("enableEtwsAlerts = " + enableEtwsAlerts);
            log("enablePresidential = " + enablePresidential);
            log("enableCmasExtremeAlerts = " + enableCmasExtremeAlerts);
            log("enableCmasSevereAlerts = " + enableCmasSevereAlerts);
            log("enableCmasAmberAlerts = " + enableCmasAmberAlerts);
            log("forceDisableEtwsCmasTest = " + forceDisableEtwsCmasTest);
            log("enableEtwsTestAlerts = " + enableEtwsTestAlerts);
            log("enableCmasTestAlerts = " + enableCmasTestAlerts);
            log("enableChannel50Alerts = " + enableChannel50Alerts);
            log("supportIsraelPwsAlerts = " + supportIsraelPwsAlerts);
            log("supportChilePwsAlerts = " + supportChilePwsAlerts);
            log("supportPeruPwsAlerts = " + supportPeruPwsAlerts);
            log("enableDubaiPwsSupport = " + enableDubaiPwsSupport);
            log("enableNzPwsSupport = " + enableNzPwsSupport);
            log("subId = " + subId);
            if (supportPeruPwsAlerts){
                log("enablePws_4370_919 = " + enablePws_4370_919);
                log("enablePws_4383 = " + enablePws_4383);
                log("enablePws_4382 = " + enablePws_4382);
                log("enablePws_4380_519 = " + enablePws_4380_519);
                log("enablePws_4381_519 = " + enablePws_4381_519);
                log("enablePws4396_4399 = " + enablePws4396_4399);
            }
            if (enableDubaiPwsSupport){
                log("enablePws4379_4392 = " + enablePws4379_4392);
                log("enablePws4380_4393 = " + enablePws4380_4393);
                log("enablePws4396_4397 = " + enablePws4396_4397);
            }
        }

        final CellBroadcastConfigManager cbcMgr = CellBroadcastConfigManager.getInstance(subId);

        if (enableEmergencyAlerts) {
            if (DBG) log("Enable CellBroadcast with carrier defined message id ranges.");
            if (!TextUtils.isEmpty(emergencyIdRange)) {
                setChannelRange(cbcMgr, emergencyIdRange, true);
            }
        }
        else {
            if (DBG) log("Disable CellBroadcast with carrier defined message id ranges.");
            if (!TextUtils.isEmpty(emergencyIdRange)) {
                setChannelRange(cbcMgr, emergencyIdRange, false);
            }
        }
        /** Enable CDMA CMAS series messages. */

        cbcMgr.setGsmCellBroadcastLanguageRange(true, 0, 255);

        // Enable/Disable CDMA Presidential messages.
        cbcMgr.setCdmaCellBroadcastChannelRange(manager, enablePresidential,
                SmsEnvelope.SERVICE_CATEGORY_CMAS_PRESIDENTIAL_LEVEL_ALERT,
                SmsEnvelope.SERVICE_CATEGORY_CMAS_PRESIDENTIAL_LEVEL_ALERT);

        // Enable/Disable CDMA CMAS extreme messages.
        cbcMgr.setCdmaCellBroadcastChannelRange(manager, enableCmasExtremeAlerts,
                SmsEnvelope.SERVICE_CATEGORY_CMAS_EXTREME_THREAT,
                SmsEnvelope.SERVICE_CATEGORY_CMAS_EXTREME_THREAT);

        // Enable/Disable CDMA CMAS severe messages.
        cbcMgr.setCdmaCellBroadcastChannelRange(manager, enableCmasSevereAlerts,
                SmsEnvelope.SERVICE_CATEGORY_CMAS_SEVERE_THREAT,
                SmsEnvelope.SERVICE_CATEGORY_CMAS_SEVERE_THREAT);

        // Enable/Disable CDMA CMAS amber alert messages.
        cbcMgr.setCdmaCellBroadcastChannelRange(manager, enableCmasAmberAlerts,
                SmsEnvelope.SERVICE_CATEGORY_CMAS_CHILD_ABDUCTION_EMERGENCY,
                SmsEnvelope.SERVICE_CATEGORY_CMAS_CHILD_ABDUCTION_EMERGENCY);

        // Enable/Disable CDMA CMAS test messages.
        cbcMgr.setCdmaCellBroadcastChannelRange(manager, enableCmasTestAlerts,
                SmsEnvelope.SERVICE_CATEGORY_CMAS_TEST_MESSAGE,
                SmsEnvelope.SERVICE_CATEGORY_CMAS_TEST_MESSAGE);

        /** Enable GSM ETWS series messages. */

        // Make sure CMAS Presidential is enabled (See 3GPP TS 22.268 Section 6.2).
        // ...
        // Enable/Disable GSM ETWS messages.
        if (enableNzPwsSupport) {
            boolean enablePws_4371_4372 = prefs.getBoolean(CellBroadcastSettings.KEY_NZ_ENABLE_CHANNEL_4371_4372, false);
            boolean enablePws_4373_4378 = prefs.getBoolean(CellBroadcastSettings.KEY_NZ_ENABLE_CHANNEL_4373_4378, false);

            cbcMgr.setGsmCellBroadcastChannel(true, 4370);
            cbcMgr.setGsmCellBroadcastChannelRange(enablePws_4371_4372, 4371, 4372);
            cbcMgr.setGsmCellBroadcastChannelRange(enablePws_4373_4378, 4373, 4378);
            cbcMgr.setGsmCellBroadcastChannelRange(false, 4379, 4395);
        } else if(enableTaiWanPwsSupport){
            setTaiwanCellBroadcastOnSub(enableEmergencyAlerts,enableTaiWanPwsSupport,prefs,cbcMgr);
        }else if (enableDubaiPwsSupport) {
            cbcMgr.setGsmCellBroadcastChannelRange(true,
                    4370, 4378);
            cbcMgr.setGsmCellBroadcastChannelRange(true,
                    4383, 4391);
            cbcMgr.setGsmCellBroadcastChannelRange(true,
                    4381, 4381);
            cbcMgr.setGsmCellBroadcastChannelRange(true,
                    4394, 4394);
            cbcMgr.setGsmCellBroadcastChannelRange(true,
                    4398, 4399);
            if(enablePws4379_4392){
                cbcMgr.setGsmCellBroadcastChannelRange(true,
                        4379, 4379);
                cbcMgr.setGsmCellBroadcastChannelRange(true,
                        4392, 4392);
            }else{
                cbcMgr.setGsmCellBroadcastChannelRange(false,
                        4379, 4379);
                cbcMgr.setGsmCellBroadcastChannelRange(false,
                        4392, 4392);
            }
            if(enablePws4380_4393){
                cbcMgr.setGsmCellBroadcastChannelRange(true,
                        4380, 4380);
                cbcMgr.setGsmCellBroadcastChannelRange(true,
                        4393, 4393);
            }else{
                cbcMgr.setGsmCellBroadcastChannelRange(false,
                        4380, 4380);
                cbcMgr.setGsmCellBroadcastChannelRange(false,
                        4393, 4393);
            }
            if(enablePws4396_4397){
                cbcMgr.setGsmCellBroadcastChannelRange(true,
                        4396, 4397);
            }else{
                cbcMgr.setGsmCellBroadcastChannelRange(false,
                        4396, 4397);
            }
        }else{
            cbcMgr.setGsmCellBroadcastChannelRange(enableEtwsAlerts,
                    SmsCbConstants.MESSAGE_ID_ETWS_EARTHQUAKE_WARNING,
                    SmsCbConstants.MESSAGE_ID_ETWS_EARTHQUAKE_AND_TSUNAMI_WARNING);

            // Enable/Disable GSM ETWS test messages (4355).
            cbcMgr.setGsmCellBroadcastChannelRange(enableEtwsTestAlerts,
                    SmsCbConstants.MESSAGE_ID_ETWS_TEST_MESSAGE,
                    SmsCbConstants.MESSAGE_ID_ETWS_TEST_MESSAGE);
            // Enable/Disable GSM ETWS messages (4356).
            cbcMgr.setGsmCellBroadcastChannelRange(enableEtwsAlerts,
                    SmsCbConstants.MESSAGE_ID_ETWS_OTHER_EMERGENCY_TYPE,
                    SmsCbConstants.MESSAGE_ID_ETWS_OTHER_EMERGENCY_TYPE);

            /** Enable GSM CMAS series messages. */
            // modify for movistar for sismate
            if(supportPeruPwsAlerts){
                cbcMgr.setGsmCellBroadcastChannelRange(enablePws_4370_919,
                        4370, 4370);
                cbcMgr.setGsmCellBroadcastChannelRange(enablePws_4370_919,
                        50, 50);
                cbcMgr.setGsmCellBroadcastChannelRange(enablePws_4370_919,
                        919, 919);
                cbcMgr.setGsmCellBroadcastChannelRange(enablePws_4383,//for 4383
                        4383, 4383);
                cbcMgr.setGsmCellBroadcastChannelRange(enablePws_4382,
                        4382, 4382);
                cbcMgr.setGsmCellBroadcastChannelRange(enablePws_4380_519,
                        4380, 4380);
                cbcMgr.setGsmCellBroadcastChannelRange(enablePws_4381_519,
                        4381, 4381);
                cbcMgr.setGsmCellBroadcastChannelRange(enablePws_4380_519,
                        519, 519);
                cbcMgr.setGsmCellBroadcastChannelRange(enablePws4396_4399,
                        4396, 4399);
            } else {
                // Enable/Disable GSM CMAS presidential message (4370).
                cbcMgr.setGsmCellBroadcastChannelRange(enablePresidential,
                        SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL,
                        SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL);
                // Enable/Disable GSM CMAS test messages (4380~4382).
                cbcMgr.setGsmCellBroadcastChannelRange(enableCmasTestAlerts,
                        SmsCbConstants.MESSAGE_ID_CMAS_ALERT_REQUIRED_MONTHLY_TEST,
                        SmsCbConstants.MESSAGE_ID_CMAS_ALERT_OPERATOR_DEFINED_USE);
                /** Enable GSM CMAS series messages for additional languages. */

                // Enable/Disable GSM CMAS presidential messages for additional languages (4383).

                cbcMgr.setGsmCellBroadcastChannelRange(enablePresidential,
                        SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL_LANGUAGE,
                        SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL_LANGUAGE);
            }

            // Enable/Disable GSM CMAS extreme messages (4371~4372).
            cbcMgr.setGsmCellBroadcastChannelRange(enableCmasExtremeAlerts,
                    SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_OBSERVED,
                    SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_LIKELY);

            // Enable/Disable GSM CMAS severe messages (4373~4378).
            cbcMgr.setGsmCellBroadcastChannelRange(enableCmasSevereAlerts,
                    SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_EXPECTED_OBSERVED,
                    SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_EXPECTED_LIKELY);

            // Enable/Disable GSM CMAS amber alert messages (4379).
            cbcMgr.setGsmCellBroadcastChannelRange(enableCmasAmberAlerts,
                    SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY,
                    SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY);


            // Enable/Disable GSM CMAS extreme messages for additional languages (4384~4385).
            cbcMgr.setGsmCellBroadcastChannelRange(enableCmasExtremeAlerts,
                    SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_OBSERVED_LANGUAGE,
                    SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_LIKELY_LANGUAGE);

            // Enable/Disable GSM CMAS severe messages for additional languages (4386~4391).
            cbcMgr.setGsmCellBroadcastChannelRange(enableCmasSevereAlerts,
                    SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_EXPECTED_OBSERVED_LANGUAGE,
                    SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_EXPECTED_LIKELY_LANGUAGE);

            // Enable/Disable GSM CMAS amber alert messages for additional languages (4392).
            cbcMgr.setGsmCellBroadcastChannelRange(enableCmasAmberAlerts,
                    SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY_LANGUAGE,
                    SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY_LANGUAGE);

            // Enable/Disable GSM CMAS test messages for additional languages (4393~4395).
            cbcMgr.setGsmCellBroadcastChannelRange(enableCmasTestAlerts,
                    SmsCbConstants.MESSAGE_ID_CMAS_ALERT_REQUIRED_MONTHLY_TEST_LANGUAGE,
                    SmsCbConstants.MESSAGE_ID_CMAS_ALERT_OPERATOR_DEFINED_USE_LANGUAGE);
            if(supportChilePwsAlerts || supportPeruPwsAlerts) {
                cbcMgr.setGsmCellBroadcastChannelRange(true,
                        919, 919);
                cbcMgr.setGsmCellBroadcastChannelRange(true,
                        50, 50);
            }else{
                // Enable/Disable channel 50 messages for Brazil.
                cbcMgr.setGsmCellBroadcastChannelRange(enableChannel50Alerts, 50, 50);
            }

            if (supportIsraelPwsAlerts) {
                // Enable/Disable Israel PWS channels (919~928).
                cbcMgr.setGsmCellBroadcastChannelRange(enableEmergencyAlerts, 919, 928);
            }
        }
        //end for Dubai PWS setting
        // add for bug 916677 start
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                cbcMgr.setAllCustomConfigs(CellBroadcastConfigService.this, enableEmergencyAlerts);
            }
        });
        thread.start();
        // add for bug 916677 end
    }

    private void setTaiwanCellBroadcastOnSub(boolean enableEtwsAlerts, boolean enableTaiwanPwsSupport, SharedPreferences prefs,CellBroadcastConfigManager cbcMgr){
        boolean enablePws911_919 = enableEtwsAlerts &&
                prefs.getBoolean(CellBroadcastSettings.KEY_TAIWAN_ENABLE_CHANNEL_911_919, false);

        boolean enablePws4370_4383 = enableTaiwanPwsSupport &&
                prefs.getBoolean(CellBroadcastSettings.KEY_TAIWAN_ENABLE_CHANNEL_4370_4383, false);

        boolean enablePws4371_4379 = enableEtwsAlerts &&
                prefs.getBoolean(CellBroadcastSettings.KEY_TAIWAN_ENABLE_CHANNEL_4371_4379, false);

        boolean enablePws4384_4392 = enableEtwsAlerts &&
                prefs.getBoolean(CellBroadcastSettings.KEY_TAIWAN_ENABLE_CHANNEL_4384_4392, false);

        boolean enablePws4380_4393 = enableEtwsAlerts &&
                prefs.getBoolean(CellBroadcastSettings.KEY_TAIWAN_ENABLE_CHANNEL_4380_4393, false);

        cbcMgr.setGsmCellBroadcastChannelRange(enablePws911_919,911,911);
        cbcMgr.setGsmCellBroadcastChannelRange(enablePws911_919,919,919);
        cbcMgr.setGsmCellBroadcastChannelRange(enablePws4370_4383,4370,4370);
        cbcMgr.setGsmCellBroadcastChannelRange(enablePws4370_4383,4383,4383);
        cbcMgr.setGsmCellBroadcastChannelRange(enablePws4371_4379,4371,4379);
        cbcMgr.setGsmCellBroadcastChannelRange(enablePws4384_4392,4384,4392);
        cbcMgr.setGsmCellBroadcastChannelRange(enablePws4380_4393,4380,4380);
        cbcMgr.setGsmCellBroadcastChannelRange(enablePws4380_4393,4393,4393);
    }

    // add for prechannel start
    private void preCustomConfigs(Context context, int subId) {
        TelephonyManager tm = (TelephonyManager) context
                .getSystemService(context.TELEPHONY_SERVICE);
        String defaultMccMnc = "";
        if (tm != null) {
            defaultMccMnc = tm.getSimOperatorNumeric(subId);
        }
        Log.d(TAG, "prechannel Sim loaded !!!  defaultMccMnc = "
                + defaultMccMnc);
        int mcc = 0;
        int mnc = 0;
        try {
            //below modify for bug681795
            if(!"".equals(defaultMccMnc)){
                mcc = Integer.parseInt(defaultMccMnc.substring(0, 3));
                mnc = Integer.parseInt(defaultMccMnc.substring(3));
            }
            //end for bug681795
        } catch (NumberFormatException e) {
            Log.e(TAG, "[setMccMnc] - couldn't parse mcc/mnc: " + defaultMccMnc);
        }
        Log.d(TAG, "prechannel Sim loaded !!!  subId = " + subId + " mcc = " + mcc + " mnc = " + mnc);
        HandlerThread cbHandlerThread = new HandlerThread("cbHandlerThread");
        cbHandlerThread.start();
        CellBroadcastConfigHandler customConfigsHandler = new CellBroadcastConfigHandler(context, cbHandlerThread.getLooper());
        Message msg = customConfigsHandler.obtainMessage();
        Bundle bundle = new Bundle();
        bundle.putInt("subId", subId);
        bundle.putInt("mcc", mcc);
        bundle.putInt("mnc", mnc);
        msg.setData(bundle);
        msg.sendToTarget();
    }

    private static void log(String msg) {
        Log.d(TAG, msg);
    }
}
