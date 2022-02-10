package com.sprd.cellbroadcastreceiver.provider;

public interface ChannelTableDefine {
    public static final String TABLE_NAME       = "channel";
		
	public static final String _ID              = "_id";
	public static final String MCC              = "mcc";
    public static final String MNC              = "mnc";
    public static final String SAVE             = "save";
    public static final String EDITABLE         = "editable";
    public static final String ENABLE           = "enable";
    public static final String SUB_ID           = "sub_id";
    public static final String VIBRATE          = "vibrate";
    public static final String SOUND_URI       = "sound_uri";
    public static final String CHANNEL_ID       = "channel_id";
    public static final String CHANNEL_NAME     = "channel_name";
    public static final String NOTIFICATION     = "notification";

    public static final int INDEX_ID            = 0;
    public static final int INDEX_CHANNEL_ID    = 1;
    public static final int INDEX_CHANNEL_NAME  = 2;
    public static final int INDEX_EDITABLE      = 3;
    public static final int INDEX_ENABLE        = 4;
    public static final int INDEX_SUB_ID        = 5;
    public static final int INDEX_SAVE          = 6;
    public static final int INDEX_MCC           = 7;
    public static final int INDEX_MNC           = 8;
    public static final int INDEX_VIBRATE       = 9;
    public static final int INDEX_SOUND_URI     = 10;
    public static final int INDEX_NOTIFICATION  = 11;

    public static final String[] QUERY_COLUMNS = { _ID, CHANNEL_ID, CHANNEL_NAME, EDITABLE,
            ENABLE, SUB_ID, SAVE, MCC, MNC, VIBRATE, SOUND_URI, NOTIFICATION };
    
    public static final String CREATE_CHANNEL_TABLE = "CREATE TABLE " + TABLE_NAME + " ("
            + _ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
            + CHANNEL_ID + " INTEGER,"
            + CHANNEL_NAME + " TEXT,"
            + EDITABLE + " INTEGER  DEFAULT 1,"
            + ENABLE + " INTEGER  DEFAULT 1,"
            + SUB_ID + " INTEGER,"
            + SAVE + " INTEGER  DEFAULT 1,"
            + MCC + " INTEGER,"
            + MNC + " INTEGER,"
            + VIBRATE + " INTEGER,"
             + SOUND_URI + " TEXT,"
            + NOTIFICATION + " INTEGER"
            + ");";
}
