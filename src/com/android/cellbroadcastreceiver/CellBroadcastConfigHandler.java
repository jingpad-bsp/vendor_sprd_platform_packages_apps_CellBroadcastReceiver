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

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemProperties;
import android.telephony.SubscriptionManager;
import android.util.Log;
import com.android.internal.telephony.SubscriptionController;
import com.android.cellbroadcastreceiver.CellBroadcastContentProvider;
import com.sprd.cellbroadcastreceiver.provider.CommonSettingTableDefine;
import com.sprd.cellbroadcastreceiver.provider.ChannelTableDefine;
import com.sprd.cellbroadcastreceiver.provider.CreateChannelViewDefine;
import com.sprd.cellbroadcastreceiver.provider.LangMapTableDefine;
import com.sprd.cellbroadcastreceiver.provider.PreChannelTableDefine;

public class CellBroadcastConfigHandler extends Handler {
    private static final String TAG = "CellBroadcastConfigHandler";

    private static final String VIEW_CHANNEL_NAME     = CreateChannelViewDefine.VIEW_CHANNEL_NAME;
    private static final String VIEW_SAVE             = CreateChannelViewDefine.SAVE;
    private static final String VIEW_EDITABLE         = CreateChannelViewDefine.EDITABLE;
    private static final String VIEW_ENABLE           = CreateChannelViewDefine.ENABLE;
    private static final String VIEW_VIBRATE          = CreateChannelViewDefine.VIBRATE;
    private static final String VIEW_SOUND_URI        = CreateChannelViewDefine.SOUND_URI;
    private static final String VIEW_CHANNEL_ID       = CreateChannelViewDefine.CHANNEL_ID;
    private static final String VIEW_PRE_CHANNEL_NAME = CreateChannelViewDefine.CHANNEL_NAME;
    private static final String VIEW_NOTIFICATION     = CreateChannelViewDefine.NOTIFICATION;

    private static final String MCC              = ChannelTableDefine.MCC;
    private static final String MNC              = ChannelTableDefine.MNC;
    private static final String SAVE             = ChannelTableDefine.SAVE;
    private static final String EDITABLE         = ChannelTableDefine.EDITABLE;
    private static final String ENABLE           = ChannelTableDefine.ENABLE;
    private static final String SUB_ID           = ChannelTableDefine.SUB_ID;
    private static final String VIBRATE          = ChannelTableDefine.VIBRATE;
    private static final String SOUND_URI        = ChannelTableDefine.SOUND_URI;
    private static final String CHANNEL_ID       = ChannelTableDefine.CHANNEL_ID;
    private static final String CHANNEL_NAME     = ChannelTableDefine.CHANNEL_NAME;
    private static final String NOTIFICATION     = ChannelTableDefine.NOTIFICATION;

    private static final int CUSTOM_PRE_MCC_PRE_MNC  = 1;
    private static final int CUSTOM_PRE_MCC_NO_MNC   = 2;
    private static final int CUSTOM_NO_MCC_NO_MNC    = -1;

    public static final String INDEX_PRE_SUB_ID = PreChannelTableDefine.SUB_ID;
    private static final String PREFERENCE_NAME = "custom_config";
    private Context mContext;

    public CellBroadcastConfigHandler(final Context context, Looper looper) {
        super(looper);
        mContext = context;
    }

    @Override
    public synchronized void handleMessage(Message msg) {
        Bundle bundle = msg.getData();
        final int subId = bundle.getInt("subId");
        final int localMcc = bundle.getInt("mcc");
        final int localMnc = bundle.getInt("mnc");
        Log.d(TAG, "handleMessage: subId = " + subId + " localMcc = "
                + localMcc + " localMnc = " + localMnc);
        enableEmergencyALerts(mContext, subId, localMcc, localMnc);
        initLangMapTable(mContext, subId);
        clearCustomizeParams();
        parseConfigParmsFromXml(mContext, localMcc, localMnc, R.xml.config_mnc_mcc);
        preCustomChannels(mContext, subId, localMcc, localMnc);
        getLooper().quit();
    }

    private void initLangMapTable(final Context context, final int subId) {
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(LangMapTableDefine.URI,
                    new String[]{LangMapTableDefine.LANG_ID},
                    LangMapTableDefine.SUBID + "=?",
                    new String[]{Integer.toString(subId)}, null, null);
            if (cursor == null || cursor.getCount() == 0) {  // new sim card
                if (cursor != null) cursor.close();
                cursor = context.getContentResolver().query(LangMapTableDefine.URI,
                        LangMapTableDefine.QUERY_COLUMNS,
                        LangMapTableDefine.SUBID + "=?",
                        new String[]{"-1"}, null, null);
                if (cursor != null && cursor.getCount() > 0) {
                    cursor.moveToFirst();
                    do {
                        ContentValues cv = new ContentValues(5);
                        cv.put(LangMapTableDefine.LANG_ID, cursor.getInt(cursor
                                .getColumnIndex(LangMapTableDefine.LANG_ID)));
                        cv.put(LangMapTableDefine.MNC_MCC_ID, cursor.getInt(cursor
                                .getColumnIndex(LangMapTableDefine.MNC_MCC_ID)));
                        cv.put(LangMapTableDefine.SHOW, cursor.getInt(cursor
                                .getColumnIndex(LangMapTableDefine.SHOW)));
                        cv.put(LangMapTableDefine.ENABLE, cursor.getInt(cursor
                                .getColumnIndex(LangMapTableDefine.ENABLE)));
                        cv.put(LangMapTableDefine.SUBID, subId);
                        context.getContentResolver().insert(LangMapTableDefine.URI, cv);
                    } while (cursor.moveToNext());
                    context.getContentResolver().notifyChange(LangMapTableDefine.URI, null);
                }
            }
        } catch (SQLiteException e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private void preCustomChannels(final Context context, final int subId, final int localMcc, final int localMnc) {
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(CreateChannelViewDefine.URI,
                    CreateChannelViewDefine.QUERY_COLUMNS,
                    INDEX_PRE_SUB_ID + "=?",
                    new String[] { Integer.toString(subId) }, null, null);
            Log.d(TAG, "preCustomChannels: query new sim card cursor = "
                    + cursor);
            if (cursor == null || cursor.getCount() == 0) { // new sim card
                int[] stMcc = null;
                int[] stMnc = null;
                if (cursor != null) cursor.close();
                // insert
                Log.d(TAG, "preCustomChannels:  new sim card insert ");
                cursor = context.getContentResolver().query(PreChannelTableDefine.URI,
                        PreChannelTableDefine.QUERY_COLUMNS,
                        INDEX_PRE_SUB_ID + "=?",
                        new String[]{"-1"}, null, null);
                if (cursor != null && cursor.getCount() > 0) {
                    stMcc = new int[cursor.getCount()];
                    stMnc = new int[cursor.getCount()];
                    cursor.moveToFirst();
                    for (int index = 0; index < cursor.getCount(); index++) {
                        stMcc[index] = cursor.getInt(cursor
                                .getColumnIndex(PreChannelTableDefine.MCC));
                        stMnc[index] = cursor.getInt(cursor
                                .getColumnIndex(PreChannelTableDefine.MNC));
                        Log.d(TAG, "preCustomChannels:  " + " pre_mcc[" + index
                                + "] = " + stMcc[index] + " pre_mnc["
                                + index + "] = " + stMnc[index]);
                        cursor.moveToNext();
                    }
                }
                if (cursor != null) cursor.close();
                if (stMcc == null || stMcc.length == 0) {
                    return;
                }
                cursor = context.getContentResolver().query(
                        CreateChannelViewDefine.URI,
                        CreateChannelViewDefine.QUERY_COLUMNS,
                        INDEX_PRE_SUB_ID + "=?",
                        new String[]{Integer.toString(-1)}, null, null);
                if (cursor != null && cursor.getCount() > 0) {
                    int[] stChannelId = new int[cursor.getCount()];
                    int channelIndex = 0;
                    cursor.moveToNext();
                    int preStatus = 0;
                    do {
                        final int channelId = cursor
                                .getInt(cursor
                                        .getColumnIndex(PreChannelTableDefine.CHANNEL_ID));
                        if (!channelIdAleadyExist(stChannelId, channelId)) {
                            preStatus = searchPreMccMnc(
                                    stMcc[channelIndex],
                                    stMnc[channelIndex]);
                            switch (preStatus) {
                                case CUSTOM_NO_MCC_NO_MNC: {
                                    stChannelId[channelIndex] = channelId;
                                    insertChannel(context,
                                            cursor, subId, localMcc, localMnc);
                                }
                                break;
                                case CUSTOM_PRE_MCC_PRE_MNC: {
                                    if ((stMcc[channelIndex] == localMcc)
                                            && (stMnc[channelIndex] == localMnc)) {
                                        stChannelId[channelIndex] = channelId;
                                        insertChannel(context,
                                                cursor, subId, stMcc[channelIndex], stMnc[channelIndex]);
                                    } else {
                                        Log.d(TAG,
                                                "preCustomChannels: custom preMcc preMnc not the save as local mcc and local mnc, not insert, out!!!!");
                                    }
                                }
                                break;
                                case CUSTOM_PRE_MCC_NO_MNC: {
                                    if ((stMcc[channelIndex] == localMcc)
                                            && (stMnc[channelIndex] != localMnc)) {
                                        stChannelId[channelIndex] = channelId;
                                        insertChannel(context,
                                                cursor, subId, localMcc, localMnc);
                                    } else {
                                        Log.d(TAG,
                                                "preCustomChannels:  custom preMcc not the save as local mcc, not insert, out!!!!");
                                    }
                                }
                                break;
                                default:
                            }
                        } else {
                            Log.d(TAG, "preCustomChannels: the save channel id , not insert, out!!!!");
                        }
                        //stChannelId[channelIndex] = channelId;
                        Log.d(TAG, "preCustomChannels:  stChannelId[" + channelIndex + "] = " + channelId);
                        channelIndex++;
                    } while (cursor.moveToNext());
                }
            }
        } catch (SQLiteException e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    private int searchPreMccMnc(int preMcc, int preMnc) {
        int ret = CUSTOM_NO_MCC_NO_MNC;
        if ((preMcc != -1) && (preMnc != -1)) {
            ret = CUSTOM_PRE_MCC_PRE_MNC;
        } else if ((preMcc != -1) && (preMnc == -1)) {
            ret = CUSTOM_PRE_MCC_NO_MNC;
        }
        Log.d(TAG, "searchPreMccMnc: ret = " + ret);
        return ret;
    }

    private boolean channelIdAleadyExist(int[] channelIds, int preCustomChannelsId) {
        for (int index = 0; index < channelIds.length; index++) {
            if (channelIds[index] == preCustomChannelsId) {
                Log.d(TAG, "channelIdAleadyExist: the same channelId = " + preCustomChannelsId);
                return true;
            }
        }
        return false;
    }

    private void insertChannel(Context context, Cursor cursor, int subId,
                                           int preMcc, int preMnc) {
        final int channelId = cursor.getInt(cursor
                .getColumnIndex(VIEW_CHANNEL_ID));
        final int editable = cursor.getInt(cursor
                .getColumnIndex(VIEW_EDITABLE));
        final int enable = cursor.getInt(cursor
                .getColumnIndex(VIEW_ENABLE));
        final String channelName = cursor
                .getString(cursor
                        .getColumnIndex(VIEW_PRE_CHANNEL_NAME));
        final int save = cursor.getInt(cursor
                .getColumnIndex(VIEW_SAVE));
        final int vibrate = cursor.getInt(cursor
                .getColumnIndex(VIEW_VIBRATE));
        final String soundUri = cursor.getString(cursor
                .getColumnIndex(VIEW_SOUND_URI));
        final int notification = cursor
                .getInt(cursor
                        .getColumnIndex(VIEW_NOTIFICATION));
        final ContentValues value = new ContentValues(10);
        value.put(CHANNEL_ID, channelId);
        value.put(CHANNEL_NAME, channelName);
        value.put(SUB_ID, subId);
        value.put(EDITABLE, editable);
        value.put(ENABLE, enable);
        value.put(SAVE, save);
        value.put(MCC, preMcc);
        value.put(MNC, preMnc);

        // the feilds bellow is used when the ringtone is set by channel
        value.put(VIBRATE, vibrate);
        value.put(SOUND_URI, soundUri);
        value.put(NOTIFICATION, notification);
        if(channelId > 0){
            context.getContentResolver().insert(CellBroadcastContentProvider.CHANNEL_URI, value);
            Log.d(TAG, "insertChannel: "
                    + " ChannelTableDefine.INDEX_CHANNEL_ID = " + channelId
                    + " ChannelTableDefine.CHANNEL_NAME = " + channelName
                    + " ChannelTableDefine.INDEX_ENABLE = " + enable
                    + "subId = " + subId);
            context.getContentResolver().notifyChange(CellBroadcastContentProvider.CHANNEL_URI, null);
        }
    }

    //used subid
    public static boolean USE_SUBID             = isUseSubId();//SystemProperties.get("use_subid", true);

    private static boolean isUseSubId(){
        if (SystemProperties.get("ro.cb_config") == null || SystemProperties.get("ro.cb_config").length()<1) {
            return true;//true
        } else {
            return (Integer.parseInt(SystemProperties.get("ro.cb_config")) & 0x01)==0;
        }
    }
    // add for prechannel end

    public static int tanslateSubIdToPhoneId(Context context, int subId){
        return SubscriptionController.getInstance().getPhoneId(subId);
    }

    private static void enableEmergencyALerts(final Context context, final int subId, final int localMcc, final int localMnc) {
        Cursor cursor = null;
        try {
            Log.d(TAG, "enableEmergencyALerts: query new sim card ");
            String[] select_column = { CellBroadcastContentProvider.ENABLED_CELLBROADCAST };
            cursor = context.getContentResolver().query(CommonSettingTableDefine.COMMON_SETTING_URI,
                    select_column,
                    SUB_ID + "=?",
                    new String[]{Integer.toString(subId)},
                    null);
            Log.d(TAG, "enableEmergencyALerts: query new sim card cursor = "
                    + cursor);
            if (cursor == null || cursor.getCount() == 0) { // new sim card
                // insert
                Log.d(TAG, "enableEmergencyALerts: new sim card insert ");
                final boolean enable = enableEmergencyAlertsForMccMnc(context, localMcc, localMnc, R.xml.pre_mnc_mcc);
                if(!enable) {
                    SubscriptionManager
                            .setSubscriptionProperty(subId,
                                    SubscriptionManager.CB_EMERGENCY_ALERT,
                                    "0");
                    final ContentValues value = new ContentValues(2);

                    value.put(SUB_ID, subId);
                    value.put(ENABLE, 0);
                    context.getContentResolver().insert(CommonSettingTableDefine.COMMON_SETTING_URI, value);
                    context.getContentResolver().notifyChange(CommonSettingTableDefine.COMMON_SETTING_URI, null);
                }
            }
        } catch (final SQLiteException e) {
            Log.e(TAG, "enableEmergencyALerts: query failure: " + e, e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private static boolean enableEmergencyAlertsForMccMnc(Context context,int localmcc, int localmnc, int resourceId){
        Resources res = context.getResources();
        XmlResourceParser xmlp = res.getXml(resourceId);
        try {
            while (xmlp.getEventType() != XmlResourceParser.END_DOCUMENT) {
                if (xmlp.getEventType() == XmlResourceParser.START_TAG) {
                    String tagName = xmlp.getName();
                    if (tagName.endsWith("mncmcc")) {
                        Log.d(TAG, "tagName.endsWith(mncmcc)");
                        int mnc = Integer.parseInt(xmlp.getAttributeValue(null,
                                "mnc"));
                        int mcc = Integer.parseInt(xmlp.getAttributeValue(null,
                                "mcc"));
                        if(mnc == localmnc && mcc == localmcc){
                            return false;
                        }
                    }
                }
                xmlp.next();
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            xmlp.close();
        }
        return true;
    }

    private boolean parseConfigParmsFromXml(Context context,int localmcc, int localmnc, int resourceId){
        Log.d(TAG, "parseConfigParmsFromXml: resourceId = " + resourceId + "\n");
        Resources res = context.getResources();
        XmlResourceParser xmlp = res.getXml(resourceId);
        try {
            while (xmlp.getEventType() != XmlResourceParser.END_DOCUMENT) {
                if (xmlp.getEventType() == XmlResourceParser.START_TAG) {
                    String tagName = xmlp.getName();
                    Log.d(TAG, "tagName = " + tagName);
                    if (tagName.endsWith("mncmcc")) {
                        //Log.d(TAG, "tagName.endsWith(mncmcc)");
                        int mnc = Integer.parseInt(xmlp.getAttributeValue(null,
                                "mnc"));
                        int mcc = Integer.parseInt(xmlp.getAttributeValue(null,
                                "mcc"));
                        int mnc_mcc_id = Integer.parseInt(xmlp.getAttributeValue(null,
                                "mncmcc_id"));
                        int duration = Integer.parseInt(xmlp.getAttributeValue(null,
                                "duration"));
                        int VsOnSilent = Integer.parseInt(xmlp.getAttributeValue(null,
                                "VsOnSilent"));
                        int VsOnDndSilent = Integer.parseInt(xmlp.getAttributeValue(null,
                                "VsOnDndSilent"));
                        int Tts= Integer.parseInt(xmlp.getAttributeValue(null,
                                "Tts"));
                        int NotifyBySms = Integer.parseInt(xmlp.getAttributeValue(null,
                                "NotifyBySms"));
                        int PopPrompt = Integer.parseInt(xmlp.getAttributeValue(null,
                                "PopPrompt"));
                        int fixTimeShow = Integer.parseInt(xmlp.getAttributeValue(null,
                                "fixTimeShow"));
                        int popCotentDigit = Integer.parseInt(xmlp.getAttributeValue(null,
                                "popCotentDigit"));
                        int modifyPopTitle = Integer.parseInt(xmlp.getAttributeValue(null,
                                "modifyPopTitle"));
                        int DisplayPreChannel = Integer.parseInt(xmlp.getAttributeValue(null,
                                "DisplayPreChannel"));
                        int prohibitAllKeyEvent = Integer.parseInt(xmlp.getAttributeValue(null,
                                "prohibitAllKeyEvent"));
                        int useLinkfy = Integer.parseInt(xmlp.getAttributeValue(null,
                                "useLinkfy"));
                        Log.d(TAG, "parseConfigParmsFromXml: mnc = " + mnc + " mcc = " + mcc
                                + " localmnc = " + localmnc + " localmcc = " + localmcc);
                        if((mnc != -1 && mnc == localmnc && mcc != -1 && mcc == localmcc)||(mnc == -1 && mcc != -1 && mcc == localmcc)){
                            Log.d(TAG, "parseConfigParmsFromXml: seachMncMcc mnc = " + mnc + " mcc = "
                                    + mcc + "duration = " + duration + " VsOnSilent = " + VsOnSilent
                                    + " VsOnDndSilent = " + VsOnDndSilent + " Tts = " + Tts
                                    + " NotifyBySms = " + NotifyBySms
                                    + " PopPrompt = " + PopPrompt + " fixTimeShow = " + fixTimeShow
                                    + " popCotentDigit = " + popCotentDigit
                                    + " modifyPopTitle = "+ modifyPopTitle
                                    + " DisplayPreChannel = " + DisplayPreChannel
                                    + " prohibitAllKeyEvent = " + prohibitAllKeyEvent
                                    + " useLinkfy = " + useLinkfy);
                            setCustomizeParams(mcc, duration, VsOnSilent, VsOnDndSilent, Tts, NotifyBySms,
                                    PopPrompt, fixTimeShow, popCotentDigit, modifyPopTitle,
                                    DisplayPreChannel, prohibitAllKeyEvent, useLinkfy);
                            return true;
                        }
                    }
                }
                xmlp.next();
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            xmlp.close();
        }
        return false;
    }

    private void setCustomizeParams(int mcc, int duration, int VsOnSilent, int VsOnDndSilent, int Tts, int NotifyBySms, int PopPrompt, int fixTimeShow, int popCotentDigit,
                                int modifyPopTitle, int DisplayPreChannel, int prohibitAllKeyEvent, int useLinkfy) {
        boolean mIsMatchedMccmnc=true;
        int mMcc = mcc;
        int mDuration = duration;
        boolean mEnableVsOnSilent = VsOnSilent == 1;
        boolean mEnableVsOnDndSilent = VsOnDndSilent == 1;
        boolean mEnableTts = Tts == 1;
        boolean mNotifyBySms = NotifyBySms == 1;
        boolean mEnablePopPrompt = PopPrompt == 1;
        boolean isRequestFixedTimeshow = fixTimeShow == 1;
        boolean isRequestPopContentdigit = popCotentDigit == 1;
        boolean isRequestModifyPopTitle = modifyPopTitle == 1;
        boolean mEnableDisplayPreChannel = DisplayPreChannel == 1;
        boolean mProhibitAllKeyEvent = prohibitAllKeyEvent == 1;
        boolean mUseLinkfy = useLinkfy == 1;
        SharedPreferences prfs = mContext.getSharedPreferences(PREFERENCE_NAME,Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prfs.edit();
        editor.putInt("mMcc", mMcc);
        editor.putInt("mDuration",mDuration);
        editor.putBoolean("mIsMatchedMccmnc", mIsMatchedMccmnc);
        editor.putBoolean("mEnableVsOnSilent", mEnableVsOnSilent);
        editor.putBoolean("mEnableVsOnDndSilent", mEnableVsOnDndSilent);
        editor.putBoolean("mEnableTts", mEnableTts);
        editor.putBoolean("mNotifyBySms", mNotifyBySms);
        editor.putBoolean("mEnablePopPrompt", mEnablePopPrompt);
        editor.putBoolean("isRequestFixedTimeshow", isRequestFixedTimeshow);
        editor.putBoolean("isRequestPopContentdigit", isRequestPopContentdigit);
        editor.putBoolean("isRequestModifyPopTitle", isRequestModifyPopTitle);
        editor.putBoolean("mEnableDisplayPreChannel", mEnableDisplayPreChannel);
        editor.putBoolean("mProhibitAllKeyEvent", mProhibitAllKeyEvent);
        editor.putBoolean("mUseLinkfy", mUseLinkfy);
        editor.commit();
        Log.d("andy", "setConfigParams:  mcc = " + mMcc + "duration = " + mDuration + " mEnableVsOnSilent = " + mEnableVsOnSilent + " mEnableVsOnDndSilent = " + mEnableVsOnDndSilent
                + " mEnableTts = " + mEnableTts + " mNotifyBySms = " + mNotifyBySms + " mEnablePopPrompt = " + mEnablePopPrompt
                + " isRequestFixedTimeshow = " + isRequestFixedTimeshow + " isRequestPopContentdigit = " + isRequestPopContentdigit
                + " isRequestModifyPopTitle = " + isRequestModifyPopTitle + " mEnableDisplayPreChannel = " + mEnableDisplayPreChannel
                + " mProhibitAllKeyEvent = " + mProhibitAllKeyEvent
                + " mUseLinkfy = " + mUseLinkfy + "mIsMatchedMccmnc = " + mIsMatchedMccmnc);
    }
    
    private void clearCustomizeParams() {
        SharedPreferences prfs = mContext.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prfs.edit();
        editor.clear();
        editor.commit();
    }
}