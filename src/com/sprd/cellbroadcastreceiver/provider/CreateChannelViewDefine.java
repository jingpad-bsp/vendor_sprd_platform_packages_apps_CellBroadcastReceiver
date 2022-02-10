package com.sprd.cellbroadcastreceiver.provider;

import android.net.Uri;

import com.sprd.cellbroadcastreceiver.provider.PreChannelTableDefine;
import com.sprd.cellbroadcastreceiver.provider.ChannelTableDefine;

public interface CreateChannelViewDefine {
    public static final String VIEW_CHANNEL_NAME = "view_channel";
    public static final String _ID = PreChannelTableDefine._ID;
    public static final String MCC = PreChannelTableDefine.MCC;
    public static final String MNC = PreChannelTableDefine.MNC;
    public static final String SAVE = PreChannelTableDefine.SAVE;
    public static final String EDITABLE = PreChannelTableDefine.EDITABLE;
    public static final String ENABLE = PreChannelTableDefine.ENABLE;
    public static final String SUB_ID = PreChannelTableDefine.SUB_ID;
    public static final String VIBRATE = PreChannelTableDefine.VIBRATE;
    public static final String SOUND_URI = PreChannelTableDefine.SOUND_URI;
    public static final String CHANNEL_ID = PreChannelTableDefine.CHANNEL_ID;
    public static final String CHANNEL_NAME = PreChannelTableDefine.CHANNEL_NAME;
    public static final String NOTIFICATION = PreChannelTableDefine.NOTIFICATION;

    public static Uri URI = Uri.parse("content://cellbroadcasts/view_channel");

    public static final String[] QUERY_COLUMNS = {
            _ID, CHANNEL_ID, CHANNEL_NAME, EDITABLE, ENABLE,
            SUB_ID, SAVE, MCC, MNC, VIBRATE, SOUND_URI, NOTIFICATION };

    public static final String CREATE_CHANNEL_VIEW = "CREATE VIEW "
            + VIEW_CHANNEL_NAME + " AS " + "SELECT " + "* " + " FROM "
            + PreChannelTableDefine.TABLE_NAME + " UNION " + "SELECT " + "* "
            + " FROM " + ChannelTableDefine.TABLE_NAME;
}
