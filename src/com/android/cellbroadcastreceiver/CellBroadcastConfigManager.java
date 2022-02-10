package com.android.cellbroadcastreceiver;

import java.util.HashMap;
import java.util.ArrayList;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.telephony.SmsManager;

import com.android.internal.telephony.SubscriptionController;
import com.android.internal.telephony.ISmsEx;
import com.android.internal.telephony.ISub;

import android.telephony.TelephonyManager;
import android.util.Log;

public class CellBroadcastConfigManager {
    private static String TAG = "CellBroadcastConfigManager";

    public static int SET_CHANNEL = 5;
    public static int SET_LANGUAGE = 6;
    public static int PADDING = -1;  //0xffff -> .
    private static int ENABLE_CHANNEL_LANG = 1;  // only for P and lowest version

    public static final Uri LANG_URI = Uri.parse("content://cellbroadcasts/lang_map");
    public static final Uri CHANNEL_URI = Uri.parse("content://cellbroadcasts/channel");

    public static final String SUB_ID = "sub_id";
    public static final String CHANNEL_ID = "channel_id";
    public static final String ENABLE = "enable";
    public static final String LANG_ID = "lang_id";
    //database columns end

    private static HashMap<Integer, CellBroadcastConfigManager> sCbcMgr =
            new HashMap<Integer, CellBroadcastConfigManager>();

    private int mSubId;
    private IntRangeManager mGsmChannelBlackList;
    private IntRangeManager mGsmLanguageBlackList;

    public static CellBroadcastConfigManager getInstance(final int subId) {
        synchronized (sCbcMgr) {
            if (sCbcMgr.containsKey(subId)) {
                CellBroadcastConfigManager mgr = sCbcMgr.get(subId);
                return mgr;
            }

            CellBroadcastConfigManager mgr = new CellBroadcastConfigManager(subId);
            sCbcMgr.put(subId, mgr);
            return mgr;
        }
    }

    private CellBroadcastConfigManager(final int id) {
        mSubId = id;
    }

    public boolean setGsmCellBroadcastChannelList(final boolean enable, final ArrayList<IntRange> irs) {
        if (irs == null || irs.size() == 0) {
            Log.e(TAG, "setGsmCellBroadcastChannelList > channels is empty!!");
            return true;
        }
        boolean ret = true;
        for (int i = 0; i < irs.size(); i++) {
            IntRange ir = irs.get(i);
            boolean r = setGsmCellBroadcastChannelRange(enable, ir.getStartId(), ir.getEndId());
            if (!r) {
                Log.e(TAG, "setGsmCellBroadcastChannelList > " + (enable ? "enable " : "disable ") + ir.toString() + " failed!!");
            }
            ret &= r;
        }
        return ret;
    }

    public boolean setGsmCellBroadcastLanguage(final boolean enable, final int id) {
        return setGsmCellBroadcastLanguageRange(enable, id, id);
    }

    public boolean setGsmCellBroadcastChannel(final boolean enable, final int id) {
        return setGsmCellBroadcastChannelRange(enable, id, id);
    }

    public boolean setGsmCellBroadcastLanguageRange(
            final boolean enable, final int start, final int end) {
        if (mGsmLanguageBlackList == null) {
            mGsmLanguageBlackList = new IntRangeManager();
        }
        return setGsmCellBroadcastRange(mGsmLanguageBlackList, enable, start, end, SET_LANGUAGE);
    }

    public boolean setGsmCellBroadcastChannelRange(
            final boolean enable, final int start, final int end) {
        if (mGsmChannelBlackList == null) {
            mGsmChannelBlackList = new IntRangeManager();
        }
        return setGsmCellBroadcastRange(mGsmChannelBlackList, enable, start, end, SET_CHANNEL);
    }

    public void setAllCustomConfigs(final Context context, final boolean action) {
        Log.d(TAG, "[" + mSubId + "] setAllCustomConfigs: action = " + action);
        enableGsmCustomConfigs(context, 1, action ? 1 : 0, SET_CHANNEL);
        if (action) {  // only enalbe language id, don't disable them
            enableGsmCustomConfigs(context, 1, 1, SET_LANGUAGE);
        }
    }

    /**
     * Enable/disable cell broadcast with messages id range
     *
     * @param manager SMS manager
     * @param enable  True for enabling cell broadcast with id range, otherwise for disabling.
     * @param type    GSM or CDMA
     * @param start   Cell broadcast id range start
     * @param end     Cell broadcast id range end
     */
    public boolean setCdmaCellBroadcastChannelRange(
            SmsManager manager, boolean enable, int start, int end) {
        if (enable) {
            return manager.enableCellBroadcastRange(start, end, SmsManager.CELL_BROADCAST_RAN_TYPE_CDMA);
        } else {
            if (sendATCommand(mSubId, getConfigArray(start, 1, SET_CHANNEL))) {//send AT to close modem side
                Log.d(TAG, "setCdmaCellBroadcastChannelRange disable channel start = :" + start + " channel start = : " + end);
                return manager.disableCellBroadcastRange(start, end, SmsManager.CELL_BROADCAST_RAN_TYPE_CDMA);
            }
            return false;
        }
    }

    public int[] getConfigArray(final int startId, final int endId, final int enabled, final int type) {
        int[] channelConfig = new int[5];
        if (type == SET_CHANNEL) {
            channelConfig[0] = startId;
            channelConfig[1] = endId;
            channelConfig[2] = PADDING;
            channelConfig[3] = PADDING;
            channelConfig[4] = enabled;   // 0: open channel; 1: close channel
        } else {//if type is SET_LANGUAGE, channel_id means language_id
            channelConfig[0] = PADDING;
            channelConfig[1] = PADDING;
            channelConfig[2] = startId;
            channelConfig[3] = endId;
            channelConfig[4] = enabled;
        }
        Log.d(TAG, "Send AT Command > " + (enabled == 1 ? "Disable " : "Enable ") + (type == SET_CHANNEL ? "channel " : "language ") + " [" + startId + "-" + endId + "]");
        return channelConfig;
    }

    public int[] getConfigArray(final int channel_id, final int enabled, final int type) {
        return getConfigArray(channel_id, channel_id, enabled, type);
    }

    public boolean sendATCommand(final int subId, final int[] data) {
        boolean success = false;
        try {
            ISmsEx iccISms = ISmsEx.Stub.asInterface(ServiceManager.getService("ismsEx"));
            if (iccISms != null) {
                if (OsUtil.isAtLeastQ()) {
                    success = iccISms.setCellBroadcastConfig(subId, data);
                } else {
                    success = iccISms.commonInterfaceForMessaging(ENABLE_CHANNEL_LANG,
                            subId, null, data);
                }
            }
            Log.d(TAG, " iccISms = " + iccISms + " success = " + success);
        } catch (RemoteException ex) {
            // ignore it
        }
        return success;
    }

    private boolean setGsmCellBroadcastRange(final IntRangeManager igm, final boolean enable, final int start, final int end, final int type) {
        if (enable && igm.including(start, end)) {
            return false;  //do nothing
        }
        final int enabled = enable ? 0 : 1;  //0: open channel; 1: close channel
        if (sendATCommand(mSubId, getConfigArray(start, end, enabled, type))) {
            if (enable) {
                igm.add(start, end);
            } else {
                igm.remove(start, end);
            }
            Log.d(TAG, "[" + mSubId + "] setGsmCellBroadcastRange: after " + (enable ? "enable " : "disable ") +
                    "[" + start + ", " + end + "], " + (type == SET_LANGUAGE ? "mGsmLanguageBlackList " : "mGsmChannelBlackList ") + " = " + igm.toString());
            return true;
        } else {
            return false;
        }
    }

    private void enableGsmCustomConfigs(final Context context, final int enable, final int action, final int type) {
        final String sql_query = SUB_ID + "=" + mSubId + " and " + ENABLE + "=" + enable;
        Uri uri;
        String query_projection;
        if (type == SET_CHANNEL) {
            uri = CHANNEL_URI;
            query_projection = CHANNEL_ID;
        } else if (type == SET_LANGUAGE) {
            uri = LANG_URI;
            query_projection = LANG_ID;
        } else {
            Log.e(TAG, "openCustomConfigs: unkonw type: " + type);
            return;
        }

        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(uri, new String[]{query_projection},
                    sql_query, null, null);
            if (cursor == null || cursor.getCount() == 0) {
                if (cursor != null) {
                    cursor.close();
                }
                return;
            }
            cursor.moveToFirst();
            do {
                final int id = cursor.getInt(cursor.getColumnIndex(query_projection));
                if (type == SET_CHANNEL) {
                    setGsmCellBroadcastChannel(action == 1, id);
                } else {
                    setGsmCellBroadcastLanguage(action == 1, id);
                }
            } while (cursor.moveToNext());
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }
}
