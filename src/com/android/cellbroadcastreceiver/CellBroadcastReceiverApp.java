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

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.graphics.Color;
import android.telephony.CellBroadcastMessage;
import android.util.Log;
import android.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The application class loads the default preferences at first start,
 * and remembers the time of the most recently received broadcast.
 */
public class CellBroadcastReceiverApp extends Application {
    private static final String TAG = "CellBroadcastReceiverApp";
    public static final String CHANNAL_NAME = "cb_notification_channel_name";
    public static final String CB_CHANNAL_ID = "cb_notification_channel_id";
    @Override
    public void onCreate() {
        super.onCreate();
        // TODO: fix strict mode violation from the following method call during app creation
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        //Bug950322 begin
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            final NotificationManager notificationManager=(NotificationManager)this.getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel simNch = notificationManager.getNotificationChannel(CB_CHANNAL_ID);
            if (simNch == null) {

                NotificationChannel cn = new NotificationChannel(CB_CHANNAL_ID,CHANNAL_NAME, NotificationManager.IMPORTANCE_HIGH);
                cn.enableLights(true);
                cn.setLightColor(Color.BLUE);
                notificationManager.createNotificationChannel(cn);
            }
        }
        //Bug950322 end
    }

    /** List of unread non-emergency alerts to show when user selects the notification. */
    private static final ArrayList<CellBroadcastMessage> sNewMessageList =
            new ArrayList<CellBroadcastMessage>(4);

    /** Latest area info cell broadcast received. */
    private static CellBroadcastMessage sLatestAreaInfo;

    /** Adds a new unread non-emergency message and returns the current list. */
    static ArrayList<CellBroadcastMessage> addNewMessageToList(CellBroadcastMessage message) {
        sNewMessageList.add(message);
        return sNewMessageList;
    }

    /** Clears the list of unread non-emergency messages. */
    static void clearNewMessageList() {
        sNewMessageList.clear();
    }

    /** Saves the latest area info broadcast received. */
    static void setLatestAreaInfo(CellBroadcastMessage areaInfo) {
        sLatestAreaInfo = areaInfo;
    }

    /** Returns the latest area info broadcast received. */
    static CellBroadcastMessage getLatestAreaInfo() {
        return sLatestAreaInfo;
    }

    // add for bug606395 start
    /** Remove a new unread non-emergency message and returns the current list. */
    static ArrayList<CellBroadcastMessage> removeNewMessageFromList(
            CellBroadcastMessage message) {


        if (sNewMessageList != null && sNewMessageList.size() > 0) {
            for (int i = sNewMessageList.size()-1; i >=0; i--) {
                if (sNewMessageList.get(i).getMessageBody()
                        .equals(message.getMessageBody())
                        && sNewMessageList.get(i).getDeliveryTime() == message
                                .getDeliveryTime()) {
                    Log.d(TAG, " removeNewMessageFromList "
                            + sNewMessageList.get(i).getMessageBody());
                    sNewMessageList.remove(i);
                    Log.d(TAG, " removeNewMessageFromList " + sNewMessageList.size());
                    return sNewMessageList;
                }
            }
        }
        return sNewMessageList;
    }
    // add for bug606395 end
}
