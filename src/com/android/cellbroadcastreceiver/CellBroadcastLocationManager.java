package com.android.cellbroadcastreceiver;

import android.content.Context;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.util.HashMap;

public class CellBroadcastLocationManager {
    private final static String TAG = "CellBroadcastLocationManager";

    public static final String LOCATIONS[] = {                                                                   // 1'st step
            "pe",  //peru
            "ae",  //dubai
            "tw",  //taiwan
            "cl",  //chile
            "br",  //brazil
            "ir"  //israel
    };

    public enum Locations {
        LOCATION_UNKNOWN,                                                                                       //2'nd step
        LOCATION_PERU,
        LOCATION_DUBAI,
        LOCATION_TAIWAN,
        LOCATION_CHILE,
        LOCATION_BRAZIL,
        LOCATION_ISRAEL;

        public boolean supportPeruAlerts() { return (this == LOCATION_PERU);}                                  //3'rd step
        public boolean supportDubaiAlerts() { return (this == LOCATION_DUBAI);}
        public boolean supportTaiwanAlerts() { return (this == LOCATION_TAIWAN);}
        public boolean supportChileAlerts() { return (this == LOCATION_CHILE);}
        public boolean supportBrazilAlerts() { return (this == LOCATION_BRAZIL);}
        public boolean supportIsraelAlerts() { return (this == LOCATION_ISRAEL);}
    };

    private static HashMap<Integer, CellBroadcastLocationManager> sLocMgr =
            new HashMap<Integer, CellBroadcastLocationManager>();
    private Context mContext;
    private int mSubId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    private Locations mCurrentLocation = Locations.LOCATION_UNKNOWN;

    CellBroadcastLocationManager(Context context, int subId) {
        mContext = context;
        mSubId = subId;
        mCurrentLocation = getCurrentLocation(context);
        Log.d(TAG, "mSubId = " + mSubId + ", mCurrentLocation = " + mCurrentLocation);
    }

    public static CellBroadcastLocationManager getInstance(final Context context, final int subId) {
        synchronized (sLocMgr) {
            if (sLocMgr.containsKey(subId)) {
                CellBroadcastLocationManager mgr = sLocMgr.get(subId);
                return mgr;
            }

            CellBroadcastLocationManager mgr = new CellBroadcastLocationManager(context, subId);
            sLocMgr.put(subId, mgr);
            return mgr;
        }
    }

    public Locations currentLocation() {
        return mCurrentLocation;
    }

    public int currentSubId() {
        return mSubId;
    }

    public Locations getCurrentLocation(final Context context) {
        final boolean following_network = context.getResources().getBoolean(R.bool.following_network_support);
        Locations loc = getCurrentLocation(context, following_network);
        if (loc == Locations.LOCATION_UNKNOWN && following_network) {
            // if get one null current location,  and enable to follow network, get location from sim card again
            loc = getCurrentLocation(context, false);
        }
        return loc;
    }

    private Locations getCurrentLocation(final Context context, final boolean following_network) {
        final String curLoc = getCurrenLocationString(context, following_network);
        if (curLoc == null) {
            return Locations.LOCATION_UNKNOWN;
        }
        for (int i = 0; i < LOCATIONS.length; i++) {
            if (LOCATIONS[i].equalsIgnoreCase(curLoc)) {
                return Locations.values()[i + 1];
            }
        }
        return Locations.LOCATION_UNKNOWN;
    }

    private String getCurrenLocationString(final Context context, final boolean following_network) {
        TelephonyManager tm = (TelephonyManager) context.getSystemService(
                Context.TELEPHONY_SERVICE);
        return following_network ? tm.getNetworkCountryIso(): tm.getSimCountryIso();
    }
}
