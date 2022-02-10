package com.sprd.cellbroadcastreceiver.util;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.MediaStore;
import android.provider.Settings;
import android.provider.SettingsEx;
import android.telephony.SmsManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import com.android.cellbroadcastreceiver.R;
import com.android.internal.telephony.ISms;
import com.android.internal.telephony.ISub;
import com.android.internal.telephony.SubscriptionController;
import com.sprd.cellbroadcastreceiver.provider.ChannelTableDefine;
import com.sprd.cellbroadcastreceiver.provider.CommonSettingTableDefine;
import com.sprd.cellbroadcastreceiver.util.SystemProperties;

public class Utils {

    public final static boolean DEBUG           = true;

    public static int DISABLE_CHANNEL           = 1;
    public static int OPERATION_ADD             = 2;
    public static int OPERATION_EDIT            = 3;
    public static int OPERATION_DEL             = 4;
    public static int SET_CHANNEL               = 5;
    public static int SET_LANGUAGE              = 6;
    public static int PADDING                   = -1;  //0xffff -> .

    public static String TAG                    = "Utils";
    public static String SETTING_TYPE           = "setting_type";
    public static String CHANNEL_SETTING        = "channel_setting";
    public static String LANGUAGE_SETTING       = "language_setting";

    public static String MATCHED                = "matched";
    public static String OPERATION              = "operation";
    public static String INDEXOFARRAY           = "indexOfArray";
    public static String DEFAULT_RINGTONE       = Settings.System.DEFAULT_NOTIFICATION_URI.toString();

    public static Uri mLangUri                  = Uri.parse("content://cellbroadcasts/lang_map");
    public static Uri mChannelUri               = Uri.parse("content://cellbroadcasts/channel");
    public static Uri mViewLangUri              = Uri.parse("content://cellbroadcasts/view_lang");
    public static Uri mChannelBulkUri         = Uri.parse("content://cellbroadcasts/channel_bulk");
    public static Uri mLangBulkUri                  = Uri.parse("content://cellbroadcasts/lang_map_bulk");

    //used subid
    public static boolean USE_SUBID             = isUseSubId();//SystemProperties.get("use_subid", true);
    //the ringtone depend on channel or slot
    public static boolean DEPEND_ON_SLOT         = isDependOnSlot();//SystemProperties.get("depend_on_sim", false);

    private static boolean isUseSubId(){
        if (SystemProperties.get("ro.cb_config") == null || SystemProperties.get("ro.cb_config").length()<1) {
            return true;//true
        } else {
            return (Integer.parseInt(SystemProperties.get("ro.cb_config")) & 0x01)==0;
        }
    }

    private static boolean isDependOnSlot(){
        if (SystemProperties.get("ro.cb_config") == null || SystemProperties.get("ro.cb_config").length()<1) {
            return false;//false
        } else {
            Log.d(TAG, "ro.cb_config of SystemProperties is:"+ SystemProperties.get("ro.cb_config"));
            return (Integer.parseInt(SystemProperties.get("ro.cb_config")) & 0x02)!=0;
        }
    }

    public static boolean hasActiveSim(Context context) {
        List<SubscriptionInfo> subInfoList = SubscriptionManager.from(context).getActiveSubscriptionInfoList();
        int phoneCount = subInfoList!=null ? subInfoList.size():0;//added for coverity 107975
        Log.d(TAG, "--check the active sim, phoneCount is:"+ phoneCount);
        if (phoneCount >= 1) {
            return true;
        } else {
            return false;
        }
    }

    public static int getSimInfor(final Intent intent) {
        int slotId = -1;
        final int subId = intent.getIntExtra(ChannelTableDefine.SUB_ID, -1);
        if (SubscriptionManager.isValidSubscriptionId(subId)) {
            slotId = SubscriptionManager.getSlotIndex(subId);
        }
        Log.d(TAG, "getSimInfor, subId:" + subId + " slotId:" + slotId);
        return slotId;
    }

    public static int tanslateSubIdToPhoneId(Context context, int subId){
        return SubscriptionController.getInstance().getPhoneId(subId);
    }

    //add for bug 545650 begin
    public static String getRingtoneTitle(Context context, Uri ringtoneUri){

        String ringtoneName = null;
        // Is it a silent ringtone?
        if (ringtoneUri != null && !TextUtils.isEmpty(ringtoneUri.toString())) {
            final Ringtone tone = RingtoneManager.getRingtone(context, ringtoneUri);
            if (tone != null) {
                final String title = tone.getTitle(context);
                Log.d(TAG, "titled = " + title);
                if (!TextUtils.isEmpty(title)) {
                    ringtoneName = title;
                } else {
                    ringtoneName = context.getString(R.string.silence);
                }
            } else {
                ringtoneName = context.getString(R.string.silence);
            }
        } else {
            ringtoneName = context.getString(R.string.silence);
        }
        return ringtoneName;
    }
    //add for bug 545650 end
    //add for bug 605579 begin
    public static Uri getRingtoneUri(Context context, Uri ringtoneUri){
        // Is it a silent ringtone?
        if (ringtoneUri != null && !TextUtils.isEmpty(ringtoneUri.toString())) {
            // Fetch the ringtone title from the media provider
            Cursor cursor = null;
            try {
                cursor = context.getContentResolver().query(
                        ringtoneUri,
                        new String[] {MediaStore.Audio.Media.DATA},
                        null,
                        null,
                        null);
                if (cursor != null && cursor.getCount() > 0) {
                    if (cursor.moveToFirst()) {
                        File filePath = new File(cursor.getString(0));
                        if (!filePath.exists()) { // exist in db but the
                            // file is deleted
                            Log.d(TAG, "filePath is not exists");
                            ringtoneUri = getDefaultRingtoneUri(context, ringtoneUri);
                        }
                    }
                } else {
                    Log.d(TAG, "cursor is null");
                    ringtoneUri = getDefaultRingtoneUri(context, ringtoneUri);
                }
            } catch (SQLiteException sqle) {
                Log.d(TAG, sqle.toString());
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        } else {
            ringtoneUri = getDefaultRingtoneUri(context, ringtoneUri);
        }

        return ringtoneUri;
    }

    private static Uri getDefaultRingtoneUri(Context context, Uri ringtoneUri) {
        ringtoneUri = RingtoneManager.getActualDefaultRingtoneUri(
                context, RingtoneManager.TYPE_NOTIFICATION);
        if (ringtoneUri != null && !TextUtils.isEmpty(ringtoneUri.toString())) {
            Cursor cursor = null;
            try {
                cursor = context.getContentResolver().query(ringtoneUri, new String[]{
                        MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.DATA
                }, null, null, null);
                if (cursor != null && cursor.getCount() > 0) {
                    if (cursor.moveToFirst()) {
                        File filePath = new File(cursor.getString(1));
                        if (!filePath.exists()) { // exist in db but the
                            // file is deleted
                            Log.d(TAG, "default ringtone filePath is not exists");
                            ringtoneUri = getOriginRingtoneUri(context, ringtoneUri);
                        }
                    }
                } else {
                    Log.d(TAG, "default ringtone cursor is null");
                    ringtoneUri = getOriginRingtoneUri(context, ringtoneUri);
                }
            } catch (SQLiteException sqle) {
                Log.e(TAG, sqle.toString());
            } finally {
                if (cursor != null) {
                    cursor.close();
                    cursor = null;
                }
            }
        } else {
            Log.d(TAG, "default ringtone uri is null");
            ringtoneUri = getOriginRingtoneUri(context, ringtoneUri);
        }
        return ringtoneUri;
    }

    private static Uri getOriginRingtoneUri(Context context, Uri ringtoneUri) {
        return (DEFAULT_RINGTONE != null ? Uri.parse(DEFAULT_RINGTONE) : null);
    }
    //add for bug 605579 end
}
