/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.cellbroadcastreceiver;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.telephony.CellBroadcastMessage;
import android.telephony.SmsCbCmasInfo;
import android.telephony.SmsCbEtwsInfo;
import android.telephony.TelephonyManager;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.StyleSpan;
import android.util.Log;
import static com.android.cellbroadcastreceiver.CellBroadcastReceiver.DBG;


/**
 * Returns the string resource ID's for CMAS and ETWS emergency alerts.
 */
public class CellBroadcastResources {

    private static final String COUNTRY_CHILE = "cl";
    private static final String COUNTRY_PERU = "pe";
    private static final String COUNTRY_DUBAI = "ae";
    private static final String ARED_TAIWAN = "tw";
    private static final String ARED_NEWZEALAND = "nz";
    private static final String TAG = "CellBroadcastResources";
    private CellBroadcastResources() {
    }
    private static Context mContext;
    private static final String PREFERENCE_NAME="custom_config";
    //add for Dubai PWS setting
    public static void setContext(Context context) {
        mContext = context;
    }
    //end for Dubai PWS setting
    /**
     * Returns a styled CharSequence containing the message date/time and alert details.
     * @param context a Context for resource string access
     * @return a CharSequence for display in the broadcast alert dialog
     */
    public static CharSequence getMessageDetails(Context context, CellBroadcastMessage cbm) {
        SpannableStringBuilder buf = new SpannableStringBuilder();

        // Alert date/time
        int start = buf.length();
        buf.append(context.getString(R.string.delivery_time_heading));
        int end = buf.length();
        buf.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        buf.append(" ");
        buf.append(cbm.getDateString(context));

        if (cbm.isCmasMessage()) {
            // CMAS category, response type, severity, urgency, certainty
            appendCmasAlertDetails(context, buf, cbm.getCmasWarningInfo());
        }

        return buf;
    }

    private static void appendCmasAlertDetails(Context context, SpannableStringBuilder buf,
            SmsCbCmasInfo cmasInfo) {
        // CMAS category
        int categoryId = getCmasCategoryResId(cmasInfo);
        if (categoryId != 0) {
            appendMessageDetail(context, buf, R.string.cmas_category_heading, categoryId);
        }

        // CMAS response type
        int responseId = getCmasResponseResId(cmasInfo);
        if (responseId != 0) {
            appendMessageDetail(context, buf, R.string.cmas_response_heading, responseId);
        }

        // CMAS severity
        int severityId = getCmasSeverityResId(cmasInfo);
        if (severityId != 0) {
            appendMessageDetail(context, buf, R.string.cmas_severity_heading, severityId);
        }

        // CMAS urgency
        int urgencyId = getCmasUrgencyResId(cmasInfo);
        if (urgencyId != 0) {
            appendMessageDetail(context, buf, R.string.cmas_urgency_heading, urgencyId);
        }

        // CMAS certainty
        int certaintyId = getCmasCertaintyResId(cmasInfo);
        if (certaintyId != 0) {
            appendMessageDetail(context, buf, R.string.cmas_certainty_heading, certaintyId);
        }
    }

    private static void appendMessageDetail(Context context, SpannableStringBuilder buf,
            int typeId, int valueId) {
        if (buf.length() != 0) {
            buf.append("\n");
        }
        int start = buf.length();
        buf.append(context.getString(typeId));
        int end = buf.length();
        buf.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        buf.append(" ");
        buf.append(context.getString(valueId));
    }

    /**
     * Returns the string resource ID for the CMAS category.
     * @return a string resource ID, or 0 if the CMAS category is unknown or not present
     */
    private static int getCmasCategoryResId(SmsCbCmasInfo cmasInfo) {
        switch (cmasInfo.getCategory()) {
            case SmsCbCmasInfo.CMAS_CATEGORY_GEO:
                return R.string.cmas_category_geo;

            case SmsCbCmasInfo.CMAS_CATEGORY_MET:
                return R.string.cmas_category_met;

            case SmsCbCmasInfo.CMAS_CATEGORY_SAFETY:
                return R.string.cmas_category_safety;

            case SmsCbCmasInfo.CMAS_CATEGORY_SECURITY:
                return R.string.cmas_category_security;

            case SmsCbCmasInfo.CMAS_CATEGORY_RESCUE:
                return R.string.cmas_category_rescue;

            case SmsCbCmasInfo.CMAS_CATEGORY_FIRE:
                return R.string.cmas_category_fire;

            case SmsCbCmasInfo.CMAS_CATEGORY_HEALTH:
                return R.string.cmas_category_health;

            case SmsCbCmasInfo.CMAS_CATEGORY_ENV:
                return R.string.cmas_category_env;

            case SmsCbCmasInfo.CMAS_CATEGORY_TRANSPORT:
                return R.string.cmas_category_transport;

            case SmsCbCmasInfo.CMAS_CATEGORY_INFRA:
                return R.string.cmas_category_infra;

            case SmsCbCmasInfo.CMAS_CATEGORY_CBRNE:
                return R.string.cmas_category_cbrne;

            case SmsCbCmasInfo.CMAS_CATEGORY_OTHER:
                return R.string.cmas_category_other;

            default:
                return 0;
        }
    }

    /**
     * Returns the string resource ID for the CMAS response type.
     * @return a string resource ID, or 0 if the CMAS response type is unknown or not present
     */
    private static int getCmasResponseResId(SmsCbCmasInfo cmasInfo) {
        switch (cmasInfo.getResponseType()) {
            case SmsCbCmasInfo.CMAS_RESPONSE_TYPE_SHELTER:
                return R.string.cmas_response_shelter;

            case SmsCbCmasInfo.CMAS_RESPONSE_TYPE_EVACUATE:
                return R.string.cmas_response_evacuate;

            case SmsCbCmasInfo.CMAS_RESPONSE_TYPE_PREPARE:
                return R.string.cmas_response_prepare;

            case SmsCbCmasInfo.CMAS_RESPONSE_TYPE_EXECUTE:
                return R.string.cmas_response_execute;

            case SmsCbCmasInfo.CMAS_RESPONSE_TYPE_MONITOR:
                return R.string.cmas_response_monitor;

            case SmsCbCmasInfo.CMAS_RESPONSE_TYPE_AVOID:
                return R.string.cmas_response_avoid;

            case SmsCbCmasInfo.CMAS_RESPONSE_TYPE_ASSESS:
                return R.string.cmas_response_assess;

            case SmsCbCmasInfo.CMAS_RESPONSE_TYPE_NONE:
                return R.string.cmas_response_none;

            default:
                return 0;
        }
    }

    /**
     * Returns the string resource ID for the CMAS severity.
     * @return a string resource ID, or 0 if the CMAS severity is unknown or not present
     */
    private static int getCmasSeverityResId(SmsCbCmasInfo cmasInfo) {
        switch (cmasInfo.getSeverity()) {
            case SmsCbCmasInfo.CMAS_SEVERITY_EXTREME:
                return R.string.cmas_severity_extreme;

            case SmsCbCmasInfo.CMAS_SEVERITY_SEVERE:
                return R.string.cmas_severity_severe;

            default:
                return 0;
        }
    }

    /**
     * Returns the string resource ID for the CMAS urgency.
     * @return a string resource ID, or 0 if the CMAS urgency is unknown or not present
     */
    private static int getCmasUrgencyResId(SmsCbCmasInfo cmasInfo) {
        switch (cmasInfo.getUrgency()) {
            case SmsCbCmasInfo.CMAS_URGENCY_IMMEDIATE:
                return R.string.cmas_urgency_immediate;

            case SmsCbCmasInfo.CMAS_URGENCY_EXPECTED:
                return R.string.cmas_urgency_expected;

            default:
                return 0;
        }
    }

    /**
     * Returns the string resource ID for the CMAS certainty.
     * @return a string resource ID, or 0 if the CMAS certainty is unknown or not present
     */
    private static int getCmasCertaintyResId(SmsCbCmasInfo cmasInfo) {
        switch (cmasInfo.getCertainty()) {
            case SmsCbCmasInfo.CMAS_CERTAINTY_OBSERVED:
                return R.string.cmas_certainty_observed;

            case SmsCbCmasInfo.CMAS_CERTAINTY_LIKELY:
                return R.string.cmas_certainty_likely;

            default:
                return 0;
        }
    }

    public static int getDialogTitleResource(CellBroadcastMessage cbm, Context context) {
        // ETWS warning types
        //add bug659116 start
        if(cbm == null){
            return R.string.cb_other_message_identifiers;
        }
        if (context == null) return 0;
        Resources res = context.getResources();
        TelephonyManager tm = (TelephonyManager) context.getSystemService(context.TELEPHONY_SERVICE);
        SharedPreferences sp =context.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE);
        boolean isRequestModifyPopTitle= sp.getBoolean("isRequestModifyPopTitle", false);
        int messageId = cbm.getServiceCategory();
        int subId = cbm.getSubId();
        boolean isChileSupport = res.getBoolean(R.bool.show_chile_settings)
                && (COUNTRY_CHILE.equals(tm.getSimCountryIso(subId)) || COUNTRY_CHILE.equals(tm.getNetworkCountryIso(subId)));
        boolean isPeruSupport = res.getBoolean(R.bool.show_peru_settings)
                && (COUNTRY_PERU.equals(tm.getSimCountryIso(subId)) || COUNTRY_PERU.equals(tm.getNetworkCountryIso(subId)));
        boolean enableDubaiPwsSupport = res.getBoolean(R.bool.show_dubai_settings)
                && (COUNTRY_DUBAI.equals(tm.getSimCountryIso(subId)) || COUNTRY_DUBAI.equals(tm.getNetworkCountryIso(subId)));
        boolean enableTaiwanPwsSupport = res.getBoolean(R.bool.show_taiwan_settings)
                && (ARED_TAIWAN.equals(tm.getSimCountryIso(subId)) || ARED_TAIWAN.equals(tm.getNetworkCountryIso(subId)));
        boolean enableNewZealandPwsSupport = res.getBoolean(R.bool.show_newzealand_settings)
                && (ARED_NEWZEALAND.equals(tm.getSimCountryIso(subId)) || ARED_NEWZEALAND.equals(tm.getNetworkCountryIso(subId)));
        if (DBG){
            Log.d(TAG, "subId : =" + subId + " messageId = " + messageId + " isRequestModifyPopTitle = " + isRequestModifyPopTitle
            + " isChileSupport = " + isChileSupport + " isPeruSupport = " + isPeruSupport
            + " enableDubaiPwsSupport = " + enableDubaiPwsSupport + " enableTaiwanPwsSupport :" +enableTaiwanPwsSupport);
        }
        if(enableTaiwanPwsSupport){
            return getTaiwanDialogTitleResource(cbm,messageId);
        }

        if (isRequestModifyPopTitle){
            if (enableDubaiPwsSupport){
                //if messageId in range 4371-4378, displaying primary language.
                boolean pwsPrimaryLanguage = messageId >= 0x1113 && messageId <= 0x111A;
                //if messageId in range 4384-4391, displaying secondary language.
                boolean pwsSecondLanguage = messageId >= 0x1120 && messageId <=0x1127;
                //PWS messageId is 4370 and 4383
                if (messageId == 0x1112) {
                    return R.string.cmas_presidential_level_alert_ar;
                } else if (messageId == 0x111F) {
                    return R.string.cmas_presidential_level_alert_en;
                }
                if (pwsPrimaryLanguage) {
                    return R.string.cmas_extreme_alert_ar;
                } else if (pwsSecondLanguage) {
                    return R.string.cmas_extreme_alert_en;
                }
                if (messageId == 0x111B) {
                    return R.string.cmas_amber_alert_ar;
                } else if (messageId == 0x1128) {
                    return R.string.cmas_amber_alert_en;
                }
                if (messageId == 0x111C || messageId == 0x112E) {
                    return R.string.cmas_required_monthly_test_ar;
                } else if (messageId == 0x1129 || messageId == 0x112F) {
                    return R.string.cmas_required_monthly_test_en;
                }
                if (messageId == 0x111D) {
                    return R.string.cmas_exercise_alert_ar;
                } else if (messageId == 0x112A) {
                    return R.string.cmas_exercise_alert_en;
                }
                if (messageId == 0x112C) {
                    return R.string.cmas_public_safety_alert_ar;
                } else if (messageId == 0x112D) {
                    return R.string.cmas_public_safety_alert_en;
                }
            }
            if (isChileSupport){
                if (messageId ==4370 || messageId == 919)
                    return R.string.cmas_presidential_level_alert_4370_919_title;
            }
            if(isPeruSupport){
                if(messageId == 50 || messageId == 919 || messageId == 4370 || messageId == 4383 || (messageId >= 4396 && messageId <= 4399)){
                    return R.string.cmas_presidential_level_alert_4370_919_title;
                }
                if(messageId == 4382){
                    return R.string.cmas_information_alert_4382_title;
                }

                if(messageId == 4380 || messageId == 519){
                    return R.string.cmas_test_alert_4380_519_title;
                }

                if(messageId == 4381){
                    return R.string.cmas_test_alert_4381_title;
                }
            }
            if (enableNewZealandPwsSupport) {
                if (messageId == 4370) {
                    return R.string.cmas_extreme_alert1;
                } else if (messageId == 4371 || messageId == 4372) {
                    return R.string.nz_control_4371_4372_alert_title;
                } else if (messageId >= 4373 && messageId <= 4378) {
                    return R.string.nz_control_4373_4378_alert_title;
                }
            }
        }


        //add bug659116 end
        SmsCbEtwsInfo etwsInfo = cbm.getEtwsWarningInfo();
        if (etwsInfo != null) {
            switch (etwsInfo.getWarningType()) {
                case SmsCbEtwsInfo.ETWS_WARNING_TYPE_EARTHQUAKE:
                    return R.string.etws_earthquake_warning;

                case SmsCbEtwsInfo.ETWS_WARNING_TYPE_TSUNAMI:
                    return R.string.etws_tsunami_warning;

                case SmsCbEtwsInfo.ETWS_WARNING_TYPE_EARTHQUAKE_AND_TSUNAMI:
                    return R.string.etws_earthquake_and_tsunami_warning;

                case SmsCbEtwsInfo.ETWS_WARNING_TYPE_TEST_MESSAGE:
                    return R.string.etws_test_message;

                case SmsCbEtwsInfo.ETWS_WARNING_TYPE_OTHER_EMERGENCY:
                default:
                    return R.string.etws_other_emergency_type;
            }
        }

        // CMAS warning types
        SmsCbCmasInfo cmasInfo = cbm.getCmasWarningInfo();
        if (cmasInfo != null) {
            switch (cmasInfo.getMessageClass()) {
                case SmsCbCmasInfo.CMAS_CLASS_PRESIDENTIAL_LEVEL_ALERT:
                    if(enableDubaiPwsSupport){
                        return R.string.cmas_presidential_level_alert1;
                    }
                    return R.string.cmas_presidential_level_alert;

                case SmsCbCmasInfo.CMAS_CLASS_EXTREME_THREAT:
                    if(enableDubaiPwsSupport){
                        return R.string.cmas_extreme_alert1;
                    }
                    return R.string.cmas_extreme_alert;

                case SmsCbCmasInfo.CMAS_CLASS_SEVERE_THREAT:
                    if(enableDubaiPwsSupport){
                        return R.string.cmas_severe_alert1;
                    }
                    return R.string.cmas_severe_alert;

                case SmsCbCmasInfo.CMAS_CLASS_CHILD_ABDUCTION_EMERGENCY:
                    if(enableDubaiPwsSupport){
                        return R.string.cmas_amber_alert1;
                    }
                    return R.string.cmas_amber_alert;

                case SmsCbCmasInfo.CMAS_CLASS_REQUIRED_MONTHLY_TEST:
                    if(enableDubaiPwsSupport){
                        return R.string.cmas_required_monthly_test1;
                    }
                    return R.string.cmas_required_monthly_test;

                case SmsCbCmasInfo.CMAS_CLASS_CMAS_EXERCISE:
                    if(enableDubaiPwsSupport){
                        return R.string.cmas_exercise_alert1;
                    }
                    return R.string.cmas_exercise_alert;

                case SmsCbCmasInfo.CMAS_CLASS_OPERATOR_DEFINED_USE:
                    return R.string.cmas_operator_defined_alert;

                default:
                    return R.string.pws_other_message_identifiers;
            }
        }

        if (CellBroadcastConfigService.isEmergencyAlertMessage(cbm)) {
            return R.string.pws_other_message_identifiers;
        } else {
            return R.string.cb_other_message_identifiers;
        }
    }

    private static int getTaiwanDialogTitleResource(CellBroadcastMessage cbm, int messageId) {
        //if messageId in range 4371-4379, displaying Chinese_TW
        boolean pwsTw = messageId >= 0x1113 && messageId <= 0x111B;
        //if messageId in range 4384-4392, displaying English
        boolean pwsTwEn = messageId >= 0x1120 && messageId <=0x1128;

        //PWS messageId is 4370 and 4383
        SmsCbCmasInfo cmasInfo = cbm.getCmasWarningInfo();

        if (cmasInfo != null) {
            switch (cmasInfo.getMessageClass()) {
                case SmsCbCmasInfo.CMAS_CLASS_PRESIDENTIAL_LEVEL_ALERT:
                    //PWS messageId is 4370 and 4383
                    if (messageId == 0x1112) {
                        return R.string.cmas_presidential_level_alert_PWS_TW;
                    } else if (messageId == 0x111F) {
                        return R.string.cmas_presidential_level_alert_PWS_EN;
                    } else {
                        return R.string.cmas_presidential_level_alert;
                    }

                case SmsCbCmasInfo.CMAS_CLASS_EXTREME_THREAT:
                    if (pwsTw) {
                        return R.string.cmas_extreme_alert_PWS_TW;
                    } else if (pwsTwEn) {
                        return R.string.cmas_extreme_alert_PWS_EN;
                    } else {
                        return R.string.cmas_extreme_alert;
                    }

                case SmsCbCmasInfo.CMAS_CLASS_SEVERE_THREAT:
                    if (pwsTw) {
                        return R.string.cmas_extreme_alert_PWS_TW;
                    } else if (pwsTwEn) {
                        return R.string.cmas_extreme_alert_PWS_EN;
                    } else {
                        return R.string.cmas_severe_alert;
                    }

                case SmsCbCmasInfo.CMAS_CLASS_CHILD_ABDUCTION_EMERGENCY:
                    if (pwsTw) {
                        return R.string.cmas_extreme_alert_PWS_TW;
                    } else if (pwsTwEn) {
                        return R.string.cmas_extreme_alert_PWS_EN;
                    } else {
                        return R.string.cmas_amber_alert;
                    }

                case SmsCbCmasInfo.CMAS_CLASS_REQUIRED_MONTHLY_TEST:
                    //PWS messageId is 4393
                   if(messageId == 0x111C){
                        return R.string.cmas_required_monthly_test_PWS_TW;
                    }else  if (messageId == 0x1129) {
                        return R.string.cmas_required_monthly_test_PWS_EN;
                    }else {
                        return R.string.cmas_required_monthly_test;
                    }

                case SmsCbCmasInfo.CMAS_CLASS_CMAS_EXERCISE:
                    return R.string.cmas_exercise_alert;

                case SmsCbCmasInfo.CMAS_CLASS_OPERATOR_DEFINED_USE:
                    return R.string.cmas_operator_defined_alert;

                default:
                    if (pwsTw) {
                        return R.string.cmas_extreme_alert_PWS_TW;
                    } else if (pwsTwEn) {
                        return R.string.cmas_extreme_alert_PWS_EN;
                    } else {
                        return R.string.pws_other_message_identifiers;
                    }
            }
        }
        //PWS messageId is 911
        if (messageId == 0x038F) {
            return R.string.cmas_police_PWS_TW;
        }
        //PWS messageId is 919
        if (messageId == 0x0397) {
            return R.string.cmas_police_PWS_EN;
        }

        return R.string.pws_other_message_identifiers;
    }

    public static int getDialogButtonResource(Context context, CellBroadcastMessage cbm) {
        //add for Dubai PWS setting
        if (context == null) return 0;
        Resources res = context.getResources();
        TelephonyManager tm = (TelephonyManager) context.getSystemService(context.TELEPHONY_SERVICE);
        int messageId = cbm.getServiceCategory();
        boolean enableDubaiPwsSupport = res.getBoolean(R.bool.show_dubai_settings)
                && ("ae".equals(tm.getSimCountryIso(cbm.getSubId())) || "ae".equals(tm.getNetworkCountryIso(cbm.getSubId())));
        boolean enableChlSupport = res.getBoolean(R.bool.show_chile_settings) && ("cl".equals(tm.getSimCountryIso(cbm.getSubId())) || "cl".equals(tm.getNetworkCountryIso(cbm.getSubId())));
        boolean enablePeSupport = res.getBoolean(R.bool.show_peru_settings) && ("pe".equals(tm.getSimCountryIso(cbm.getSubId())) || "pe".equals(tm.getNetworkCountryIso(cbm.getSubId())));
        //if messageId in range 4371-4378, displaying primary language.
        boolean pwsPrimaryLanguage = messageId >= 0x1113 && messageId <= 0x111A;
        //if messageId in range 4384-4391, displaying secondary language.
        boolean pwsSecondLanguage = messageId >= 0x1120 && messageId <=0x1127;
        if(enableChlSupport){
            return R.string.button_dismiss_ok;
        }else if(enablePeSupport){
            return R.string.button_dismiss_ocultar;
        }

        // Add for dubai
        if (enableDubaiPwsSupport) {
            if (messageId == 0x112C) {
                return R.string.button_dismiss_ar;
            } else if (messageId == 0x112D) {
                return R.string.button_dismiss;
            }

            if (messageId == 0x112E) {
                return R.string.button_dismiss_ar;
            } else if (messageId == 0x112F) {
                return R.string.button_dismiss;
            }
        }

        // CMAS warning types
        SmsCbCmasInfo cmasInfo = cbm.getCmasWarningInfo();
        if (cmasInfo != null) {
            switch (cmasInfo.getMessageClass()) {
                case SmsCbCmasInfo.CMAS_CLASS_PRESIDENTIAL_LEVEL_ALERT:
                    //return R.string.cmas_presidential_level_alert;
                    //PWS messageId is 4370 and 4383
                    if (enableDubaiPwsSupport && messageId == 0x1112) {
                        return R.string.button_dismiss_ar;
                    } else if (enableDubaiPwsSupport && messageId == 0x111F) {
                        return R.string.button_dismiss;
                    } else {
                        return R.string.button_dismiss_ar;
                    }

                case SmsCbCmasInfo.CMAS_CLASS_EXTREME_THREAT:
                    //return R.string.cmas_extreme_alert;
                    if (enableDubaiPwsSupport && pwsPrimaryLanguage) {
                        return R.string.button_dismiss_ar;
                    } else if (enableDubaiPwsSupport && pwsSecondLanguage) {
                        return R.string.button_dismiss;
                    } else {
                        return R.string.button_dismiss_ar;
                    }

                case SmsCbCmasInfo.CMAS_CLASS_SEVERE_THREAT:
                    //return R.string.cmas_severe_alert;
                    if (enableDubaiPwsSupport && pwsPrimaryLanguage) {
                        return R.string.button_dismiss_ar;
                    } else if (enableDubaiPwsSupport && pwsSecondLanguage) {
                        return R.string.button_dismiss;
                    } else {
                        return R.string.button_dismiss_ar;
                    }

                case SmsCbCmasInfo.CMAS_CLASS_CHILD_ABDUCTION_EMERGENCY:
                    //return R.string.cmas_amber_alert;
                    if (enableDubaiPwsSupport && messageId == 0x111B) {
                        return R.string.button_dismiss_ar;
                    } else if (enableDubaiPwsSupport && messageId == 0x1128) {
                        return R.string.button_dismiss;
                    } else {
                        return R.string.button_dismiss_ar;
                    }

                case SmsCbCmasInfo.CMAS_CLASS_REQUIRED_MONTHLY_TEST:
                    //return R.string.cmas_required_monthly_test;
                    if (enableDubaiPwsSupport && messageId == 0x111C) {
                        return R.string.button_dismiss_ar;
                    } else if (enableDubaiPwsSupport && messageId == 0x1129) {
                        return R.string.button_dismiss;
                    } else {
                        return R.string.button_dismiss_ar;
                    }

                case SmsCbCmasInfo.CMAS_CLASS_CMAS_EXERCISE:
                    //return R.string.cmas_exercise_alert;
                    if (enableDubaiPwsSupport && messageId == 0x111D) {
                        return R.string.button_dismiss_ar;
                    } else if (enableDubaiPwsSupport && messageId == 0x112A) {
                        return R.string.button_dismiss;
                    } else {
                        return R.string.button_dismiss_ar;
                    }

                case SmsCbCmasInfo.CMAS_CLASS_OPERATOR_DEFINED_USE:
                    return R.string.button_dismiss;

                default:
                    return R.string.button_dismiss;
            }
        }
        return R.string.button_dismiss;
    }
}
