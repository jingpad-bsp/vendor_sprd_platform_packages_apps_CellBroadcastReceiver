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
import android.content.res.Resources;
import com.android.cellbroadcastreceiver.R;

import android.content.res.XmlResourceParser;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.Telephony;
import android.telephony.SmsCbCmasInfo;
import android.telephony.SmsCbEtwsInfo;
import android.telephony.SmsCbMessage;
import android.util.Log;

import com.android.internal.telephony.gsm.SmsCbConstants;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import com.sprd.cellbroadcastreceiver.provider.CellbroadcastDefine;
import com.sprd.cellbroadcastreceiver.provider.ChannelTableDefine;
import com.sprd.cellbroadcastreceiver.provider.CreateLangViewDefine;
import com.sprd.cellbroadcastreceiver.provider.LangMapTableDefine;
import com.sprd.cellbroadcastreceiver.provider.LangNameTableDefine;
import com.sprd.cellbroadcastreceiver.provider.MncMccTableDefine;
import com.sprd.cellbroadcastreceiver.provider.CommonSettingTableDefine;
//import com.sprd.cellbroadcastreceiver.provider.IcbDbHelperUtils;
import com.sprd.cellbroadcastreceiver.provider.PreChannelTableDefine;
import com.sprd.cellbroadcastreceiver.provider.CreateChannelViewDefine;
import com.sprd.cellbroadcastreceiver.util.LanguageIds;

import android.content.Context;
import android.util.Xml;

import java.io.IOException;

/**
 * Open, create, and upgrade the cell broadcast SQLite database. Previously an inner class of
 * {@code CellBroadcastDatabase}, this is now a top-level class. The column definitions in
 * {@code CellBroadcastDatabase} have been moved to {@link Telephony.CellBroadcasts} in the
 * framework, to simplify access to this database from third-party apps.
 */
public class CellBroadcastDatabaseHelper extends SQLiteOpenHelper{

    private static final String TAG = "CellBroadcastDatabaseHelper";

    static final String DATABASE_NAME = "cell_broadcasts.db";
    static final String TABLE_NAME = "broadcasts";

    /** Temporary table for upgrading the database version. */
    static final String TEMP_TABLE_NAME = "old_broadcasts";
    private Context mContext;

    /**
     * Database version 1: initial version
     * Database version 2-9: (reserved for OEM database customization)
     * Database version 10: adds ETWS and CMAS columns and CDMA support
     * Database version 11: adds delivery time index
     * Database version 14: add sub_id in broadcast table
     */
    static final int DATABASE_VERSION = 14;

    CellBroadcastDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        mContext = context;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_NAME + " ("
                + Telephony.CellBroadcasts._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + Telephony.CellBroadcasts.GEOGRAPHICAL_SCOPE + " INTEGER,"
                + Telephony.CellBroadcasts.PLMN + " TEXT,"
                + Telephony.CellBroadcasts.LAC + " INTEGER,"
                + Telephony.CellBroadcasts.CID + " INTEGER,"
                + Telephony.CellBroadcasts.SERIAL_NUMBER + " INTEGER,"
                + Telephony.CellBroadcasts.SERVICE_CATEGORY + " INTEGER,"
                + Telephony.CellBroadcasts.LANGUAGE_CODE + " TEXT,"
                + Telephony.CellBroadcasts.MESSAGE_BODY + " TEXT,"
                + Telephony.CellBroadcasts.DELIVERY_TIME + " INTEGER,"
                + Telephony.CellBroadcasts.MESSAGE_READ + " INTEGER,"
                + Telephony.CellBroadcasts.MESSAGE_FORMAT + " INTEGER,"
                + Telephony.CellBroadcasts.MESSAGE_PRIORITY + " INTEGER,"
                + Telephony.CellBroadcasts.ETWS_WARNING_TYPE + " INTEGER,"
                + Telephony.CellBroadcasts.CMAS_MESSAGE_CLASS + " INTEGER,"
                + Telephony.CellBroadcasts.CMAS_CATEGORY + " INTEGER,"
                + Telephony.CellBroadcasts.CMAS_RESPONSE_TYPE + " INTEGER,"
                + Telephony.CellBroadcasts.CMAS_SEVERITY + " INTEGER,"
                + Telephony.CellBroadcasts.CMAS_URGENCY + " INTEGER,"
                + Telephony.CellBroadcasts.CMAS_CERTAINTY + " INTEGER);");

        createDeliveryTimeIndex(db);
        Log.d(TAG, "onCreate CellBroadcastDatabaseHelper start!!!!!" + DATABASE_VERSION);
        Log.d(TAG, "CellBroadcastDatabaseHelper  start !!!!");
        createOptionalTables(db);
    }

    private void createDeliveryTimeIndex(SQLiteDatabase db) {
        db.execSQL("CREATE INDEX IF NOT EXISTS deliveryTimeIndex ON " + TABLE_NAME
                + " (" + Telephony.CellBroadcasts.DELIVERY_TIME + ");");
    }

    /** Columns to copy on database upgrade. */
    private static final String[] COLUMNS_V1 = {
            Telephony.CellBroadcasts.GEOGRAPHICAL_SCOPE,
            Telephony.CellBroadcasts.SERIAL_NUMBER,
            Telephony.CellBroadcasts.V1_MESSAGE_CODE,
            Telephony.CellBroadcasts.V1_MESSAGE_IDENTIFIER,
            Telephony.CellBroadcasts.LANGUAGE_CODE,
            Telephony.CellBroadcasts.MESSAGE_BODY,
            Telephony.CellBroadcasts.DELIVERY_TIME,
            Telephony.CellBroadcasts.MESSAGE_READ,
    };

    private static final int COLUMN_V1_GEOGRAPHICAL_SCOPE   = 0;
    private static final int COLUMN_V1_SERIAL_NUMBER        = 1;
    private static final int COLUMN_V1_MESSAGE_CODE         = 2;
    private static final int COLUMN_V1_MESSAGE_IDENTIFIER   = 3;
    private static final int COLUMN_V1_LANGUAGE_CODE        = 4;
    private static final int COLUMN_V1_MESSAGE_BODY         = 5;
    private static final int COLUMN_V1_DELIVERY_TIME        = 6;
    private static final int COLUMN_V1_MESSAGE_READ         = 7;

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion == newVersion) {
            return;
        }
        // always log database upgrade
        Log.d(TAG, "Upgrading DB from version " + oldVersion + " to " + newVersion);

        switch (oldVersion) {
            // Upgrade from V1 to V10
            case 1:
                if (newVersion <= 1) {
                    return;
                }
                db.beginTransaction();
                try {
                    // Step 1: rename original table
                    db.execSQL("DROP TABLE IF EXISTS " + TEMP_TABLE_NAME);
                    db.execSQL("ALTER TABLE " + TABLE_NAME + " RENAME TO " + TEMP_TABLE_NAME);

                    // Step 2: create new table and indices
                    onCreate(db);

                    // Step 3: copy each message into the new table
                    Cursor cursor = null;
                    try {
                        cursor = db.query(TEMP_TABLE_NAME, COLUMNS_V1, null, null, null, null,
                                null);

                        while (cursor.moveToNext()) {
                            upgradeMessageV1ToV2(db, cursor);
                        }
                    } catch (SQLiteException e) {
                        e.printStackTrace();
                    } finally {
                        if (cursor != null) {
                            cursor.close();
                        }
                    }
                    // Step 4: drop the original table and commit transaction
                    db.execSQL("DROP TABLE " + TEMP_TABLE_NAME);
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
            case 10:
                if (newVersion <= 10) {
                    return;
                }
                createDeliveryTimeIndex(db);
            case 11:
                if (newVersion <=11) {
                    return;
                }
                createOptionalTables(db);
            case 14:
                if (newVersion <= 14) {
                    return;
                }
                break;
            default:
                break;
        }
    }

    /**
     * Upgrades a single broadcast message from version 1 to version 2.
     */
    private static void upgradeMessageV1ToV2(SQLiteDatabase db, Cursor cursor) {
        int geographicalScope = cursor.getInt(COLUMN_V1_GEOGRAPHICAL_SCOPE);
        int updateNumber = cursor.getInt(COLUMN_V1_SERIAL_NUMBER);
        int messageCode = cursor.getInt(COLUMN_V1_MESSAGE_CODE);
        int messageId = cursor.getInt(COLUMN_V1_MESSAGE_IDENTIFIER);
        String languageCode = cursor.getString(COLUMN_V1_LANGUAGE_CODE);
        String messageBody = cursor.getString(COLUMN_V1_MESSAGE_BODY);
        long deliveryTime = cursor.getLong(COLUMN_V1_DELIVERY_TIME);
        boolean isRead = (cursor.getInt(COLUMN_V1_MESSAGE_READ) != 0);

        int serialNumber = ((geographicalScope & 0x03) << 14)
                | ((messageCode & 0x3ff) << 4) | (updateNumber & 0x0f);

        ContentValues cv = new ContentValues(16);
        cv.put(Telephony.CellBroadcasts.GEOGRAPHICAL_SCOPE, geographicalScope);
        cv.put(Telephony.CellBroadcasts.SERIAL_NUMBER, serialNumber);
        cv.put(Telephony.CellBroadcasts.SERVICE_CATEGORY, messageId);
        cv.put(Telephony.CellBroadcasts.LANGUAGE_CODE, languageCode);
        cv.put(Telephony.CellBroadcasts.MESSAGE_BODY, messageBody);
        cv.put(Telephony.CellBroadcasts.DELIVERY_TIME, deliveryTime);
        cv.put(Telephony.CellBroadcasts.MESSAGE_READ, isRead);
        cv.put(Telephony.CellBroadcasts.MESSAGE_FORMAT, SmsCbMessage.MESSAGE_FORMAT_3GPP);

        int etwsWarningType = SmsCbEtwsInfo.ETWS_WARNING_TYPE_UNKNOWN;
        int cmasMessageClass = SmsCbCmasInfo.CMAS_CLASS_UNKNOWN;
        int cmasSeverity = SmsCbCmasInfo.CMAS_SEVERITY_UNKNOWN;
        int cmasUrgency = SmsCbCmasInfo.CMAS_URGENCY_UNKNOWN;
        int cmasCertainty = SmsCbCmasInfo.CMAS_CERTAINTY_UNKNOWN;
        switch (messageId) {
            case SmsCbConstants.MESSAGE_ID_ETWS_EARTHQUAKE_WARNING:
                etwsWarningType = SmsCbEtwsInfo.ETWS_WARNING_TYPE_EARTHQUAKE;
                break;

            case SmsCbConstants.MESSAGE_ID_ETWS_TSUNAMI_WARNING:
                etwsWarningType = SmsCbEtwsInfo.ETWS_WARNING_TYPE_TSUNAMI;
                break;

            case SmsCbConstants.MESSAGE_ID_ETWS_EARTHQUAKE_AND_TSUNAMI_WARNING:
                etwsWarningType = SmsCbEtwsInfo.ETWS_WARNING_TYPE_EARTHQUAKE_AND_TSUNAMI;
                break;

            case SmsCbConstants.MESSAGE_ID_ETWS_TEST_MESSAGE:
                etwsWarningType = SmsCbEtwsInfo.ETWS_WARNING_TYPE_TEST_MESSAGE;
                break;

            case SmsCbConstants.MESSAGE_ID_ETWS_OTHER_EMERGENCY_TYPE:
                etwsWarningType = SmsCbEtwsInfo.ETWS_WARNING_TYPE_OTHER_EMERGENCY;
                break;

            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL:
                cmasMessageClass = SmsCbCmasInfo.CMAS_CLASS_PRESIDENTIAL_LEVEL_ALERT;
                break;

            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_OBSERVED:
                cmasMessageClass = SmsCbCmasInfo.CMAS_CLASS_EXTREME_THREAT;
                cmasSeverity = SmsCbCmasInfo.CMAS_SEVERITY_EXTREME;
                cmasUrgency = SmsCbCmasInfo.CMAS_URGENCY_IMMEDIATE;
                cmasCertainty = SmsCbCmasInfo.CMAS_CERTAINTY_OBSERVED;
                break;

            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_LIKELY:
                cmasMessageClass = SmsCbCmasInfo.CMAS_CLASS_EXTREME_THREAT;
                cmasSeverity = SmsCbCmasInfo.CMAS_SEVERITY_EXTREME;
                cmasUrgency = SmsCbCmasInfo.CMAS_URGENCY_IMMEDIATE;
                cmasCertainty = SmsCbCmasInfo.CMAS_CERTAINTY_LIKELY;
                break;

            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_EXPECTED_OBSERVED:
                cmasMessageClass = SmsCbCmasInfo.CMAS_CLASS_SEVERE_THREAT;
                cmasSeverity = SmsCbCmasInfo.CMAS_SEVERITY_EXTREME;
                cmasUrgency = SmsCbCmasInfo.CMAS_URGENCY_EXPECTED;
                cmasCertainty = SmsCbCmasInfo.CMAS_CERTAINTY_OBSERVED;
                break;

            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_EXPECTED_LIKELY:
                cmasMessageClass = SmsCbCmasInfo.CMAS_CLASS_SEVERE_THREAT;
                cmasSeverity = SmsCbCmasInfo.CMAS_SEVERITY_EXTREME;
                cmasUrgency = SmsCbCmasInfo.CMAS_URGENCY_EXPECTED;
                cmasCertainty = SmsCbCmasInfo.CMAS_CERTAINTY_LIKELY;
                break;

            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_IMMEDIATE_OBSERVED:
                cmasMessageClass = SmsCbCmasInfo.CMAS_CLASS_SEVERE_THREAT;
                cmasSeverity = SmsCbCmasInfo.CMAS_SEVERITY_SEVERE;
                cmasUrgency = SmsCbCmasInfo.CMAS_URGENCY_IMMEDIATE;
                cmasCertainty = SmsCbCmasInfo.CMAS_CERTAINTY_OBSERVED;
                break;

            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_IMMEDIATE_LIKELY:
                cmasMessageClass = SmsCbCmasInfo.CMAS_CLASS_SEVERE_THREAT;
                cmasSeverity = SmsCbCmasInfo.CMAS_SEVERITY_SEVERE;
                cmasUrgency = SmsCbCmasInfo.CMAS_URGENCY_IMMEDIATE;
                cmasCertainty = SmsCbCmasInfo.CMAS_CERTAINTY_LIKELY;
                break;

            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_EXPECTED_OBSERVED:
                cmasMessageClass = SmsCbCmasInfo.CMAS_CLASS_SEVERE_THREAT;
                cmasSeverity = SmsCbCmasInfo.CMAS_SEVERITY_SEVERE;
                cmasUrgency = SmsCbCmasInfo.CMAS_URGENCY_EXPECTED;
                cmasCertainty = SmsCbCmasInfo.CMAS_CERTAINTY_OBSERVED;
                break;

            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_EXPECTED_LIKELY:
                cmasMessageClass = SmsCbCmasInfo.CMAS_CLASS_SEVERE_THREAT;
                cmasSeverity = SmsCbCmasInfo.CMAS_SEVERITY_SEVERE;
                cmasUrgency = SmsCbCmasInfo.CMAS_URGENCY_EXPECTED;
                cmasCertainty = SmsCbCmasInfo.CMAS_CERTAINTY_LIKELY;
                break;

            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY:
                cmasMessageClass = SmsCbCmasInfo.CMAS_CLASS_CHILD_ABDUCTION_EMERGENCY;
                break;

            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_REQUIRED_MONTHLY_TEST:
                cmasMessageClass = SmsCbCmasInfo.CMAS_CLASS_REQUIRED_MONTHLY_TEST;
                break;

            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXERCISE:
                cmasMessageClass = SmsCbCmasInfo.CMAS_CLASS_CMAS_EXERCISE;
                break;

            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_OPERATOR_DEFINED_USE:
                cmasMessageClass = SmsCbCmasInfo.CMAS_CLASS_OPERATOR_DEFINED_USE;
                break;
        }

        if (etwsWarningType != SmsCbEtwsInfo.ETWS_WARNING_TYPE_UNKNOWN
                || cmasMessageClass != SmsCbCmasInfo.CMAS_CLASS_UNKNOWN) {
            cv.put(Telephony.CellBroadcasts.MESSAGE_PRIORITY,
                    SmsCbMessage.MESSAGE_PRIORITY_EMERGENCY);
        } else {
            cv.put(Telephony.CellBroadcasts.MESSAGE_PRIORITY,
                    SmsCbMessage.MESSAGE_PRIORITY_NORMAL);
        }

        if (etwsWarningType != SmsCbEtwsInfo.ETWS_WARNING_TYPE_UNKNOWN) {
            cv.put(Telephony.CellBroadcasts.ETWS_WARNING_TYPE, etwsWarningType);
        }

        if (cmasMessageClass != SmsCbCmasInfo.CMAS_CLASS_UNKNOWN) {
            cv.put(Telephony.CellBroadcasts.CMAS_MESSAGE_CLASS, cmasMessageClass);
        }

        if (cmasSeverity != SmsCbCmasInfo.CMAS_SEVERITY_UNKNOWN) {
            cv.put(Telephony.CellBroadcasts.CMAS_SEVERITY, cmasSeverity);
        }

        if (cmasUrgency != SmsCbCmasInfo.CMAS_URGENCY_UNKNOWN) {
            cv.put(Telephony.CellBroadcasts.CMAS_URGENCY, cmasUrgency);
        }

        if (cmasCertainty != SmsCbCmasInfo.CMAS_CERTAINTY_UNKNOWN) {
            cv.put(Telephony.CellBroadcasts.CMAS_CERTAINTY, cmasCertainty);
        }

        db.insert(TABLE_NAME, null, cv);
    }

    /****************************************************************************************
     * @param db
     ****************************************************************************************/
    private void createOptionalTables(SQLiteDatabase db) {
        db.execSQL("ALTER TABLE " + TABLE_NAME
                + " ADD sub_id INTEGER DEFAULT 0");
        Log.d(TAG, "change the orignal cellbroadcast table and add table.");
        db.execSQL(CommonSettingTableDefine.CREATE_COMMOMSETTING_TABLE);
        db.execSQL(ChannelTableDefine.CREATE_CHANNEL_TABLE);
        Log.d(TAG, "create database table lang_map:"+ LangMapTableDefine.LANG_MAP_TABLE_NAME);
        db.execSQL(LangMapTableDefine.CREATE_LANG_MAP_TABLE);
        db.execSQL(LangNameTableDefine.CREATE_LANG_NAME_TABLE);
        db.execSQL(MncMccTableDefine.CREATE_MNC_MCC_TABLE);
        db.execSQL(PreChannelTableDefine.CREATE_PRE_CHANNEL_TABLE);
        db.execSQL(CreateLangViewDefine.CREATE_LANG_VIEW);
        initLangNameData(db);
        db.execSQL(CreateChannelViewDefine.CREATE_CHANNEL_VIEW);
        preCustomConfigsFromXmlPath(db, R.xml.pre_channel);
    }

    private void initLangNameData(SQLiteDatabase db){
        if(LanguageIds.LangMap == null || LanguageIds.LangMap.length <= 0){
            Log.d(TAG, "LanguageIds.LangMap data is null !!! ");
            return;
        }
        ContentValues value = new ContentValues();
        Log.d(TAG, "LanguageIds.LangMap data is not null !!! ");
        for(int i = 0; i < LanguageIds.LangMap.length; i++){
            String description = mContext.getResources().getString(LanguageIds.LangMap[i]);
            final String englist_string = mContext.getResources().getString(R.string.lang_english);
            int show = 1;
            int enable = 0;
            if(englist_string.equalsIgnoreCase(description)){
                enable = 1;
            }
            value.put(LangMapTableDefine.LANG_ID, LanguageIds.LANGUAGE_ID[i]);
            value.put(LangMapTableDefine.MNC_MCC_ID, 1);
            value.put(LangMapTableDefine.ENABLE, enable);
            value.put(LangMapTableDefine.SHOW, show);
            value.put(LangMapTableDefine.SUBID, -1);
            db.insert(LangMapTableDefine.LANG_MAP_TABLE_NAME, "",
                    value);
            value.clear();
            value.put(LangNameTableDefine.LANG_NAME_ID, LanguageIds.LANGUAGE_ID[i]);
            value.put(LangNameTableDefine.DESCRIPTION, description);
            db.insert(LangNameTableDefine.LANG_NAME_TABLE_NAME, "",
                    value);
            value.clear();
        }
        //value.clear();
        value.put(MncMccTableDefine.MNC, 1);
        value.put(MncMccTableDefine.MCC, 460);
        value.put(MncMccTableDefine.MNCMCC_ID, 1);
        db.insert(MncMccTableDefine.MNC_MCC_TABLE_NAME, "", value);
    }

    private void preCustomConfigsFromXmlPath(final SQLiteDatabase db, final int resId){
        Log.d(TAG, "preCustomConfigsFromXmlPath start  xmlPath = " + resId + "\n");
        try {
            XmlResourceParser parser = mContext.getResources().getXml(resId);

            db.beginTransaction();
            while (parser.getEventType() != XmlPullParser.END_DOCUMENT) {
                if (parser.getEventType() == XmlPullParser.START_TAG) {
                    String tagName = parser.getName();
                    Log.d(TAG, "tagName = " + tagName);
                    if (tagName.endsWith("mncmcc")) {
                        Log.d(TAG, "tagName.endsWith(mncmcc)");
                        int mnc = Integer.parseInt(parser.getAttributeValue(null,
                                "mnc"));
                        int mcc = Integer.parseInt(parser.getAttributeValue(null,
                                "mcc"));
                        int mnc_mcc_id = Integer.parseInt(parser.getAttributeValue(null,
                                "mncmcc_id"));
                        Log.d(TAG, "out: mnc = " + mnc
                                + " \n  mcc = " + mcc + "");
                        ContentValues value = new ContentValues();

                        value.put(MncMccTableDefine.MNC, mnc);
                        value.put(MncMccTableDefine.MCC, mcc);
                        value.put(MncMccTableDefine.MNCMCC_ID, mnc_mcc_id);
                        db.insert(MncMccTableDefine.MNC_MCC_TABLE_NAME, "", value);

                    } else if (tagName.endsWith("langname")) {
                        Log.d(TAG, "tagName.endsWith(langname)");
                        String description = parser.getAttributeValue(null, "description");
                        int lang_name_id = Integer.parseInt(parser.getAttributeValue(null,
                                "_id"));

                        Log.d(TAG, "out : description = " + description + "");
                        ContentValues value = new ContentValues();

                        value.put(LangNameTableDefine.DESCRIPTION, description);
                        value.put(LangNameTableDefine.LANG_NAME_ID, lang_name_id);
                        db.insert(LangNameTableDefine.LANG_NAME_TABLE_NAME, "",
                                value);

                    } else if (tagName.endsWith("langmap")) {
                        Log.d(TAG, "tagName.endsWith(langmap)");

                        int lang_id = Integer.parseInt(parser.getAttributeValue(
                                null, "lang_id"));
                        int mnc_mcc_id = Integer.parseInt(parser
                                .getAttributeValue(null, "mnc_mcc_id"));
                        int show = Integer.parseInt(parser.getAttributeValue(
                                null, "show"));
                        int enable = Integer.parseInt(parser.getAttributeValue(
                                null, "enable"));
                        Log.d(TAG, "out : lang_id = " + lang_id
                                + " \n  mnc_mcc_id = " + mnc_mcc_id + "");
                        ContentValues value = new ContentValues();

                        value.put(LangMapTableDefine.LANG_ID, lang_id);
                        value.put(LangMapTableDefine.MNC_MCC_ID, mnc_mcc_id);
                        value.put(LangMapTableDefine.SHOW, show);
                        value.put(LangMapTableDefine.ENABLE, enable);
                        value.put(LangMapTableDefine.SUBID, -1);
                        db.insert(LangMapTableDefine.LANG_MAP_TABLE_NAME, "",
                                value);

                    } else if (tagName.endsWith("prechannel")) {
                        Log.d(TAG, "tagName.endsWith(prechannel)");

                        final int channel_id = Integer.parseInt(parser
                                .getAttributeValue(null, "channel_id"));
                        final String channel_name = parser.getAttributeValue(null,
                                "channel_name");
                        final int editable = Integer.parseInt(parser.getAttributeValue(
                                null, "editable"));
                        final int enable = Integer.parseInt(parser.getAttributeValue(
                                null, "enable"));
                        final int save = Integer.parseInt(parser.getAttributeValue(
                                null, "save"));
                        final int mcc = Integer.parseInt(parser.getAttributeValue(null,
                                "mcc"));
                        int mnc = Integer.parseInt(parser.getAttributeValue(null,
                                "mnc"));
                        // int sub_id = Integer.parseInt(parser
                        // .getAttributeValue(null, "sub_id"));
                        final int vibrate = Integer.parseInt(parser.getAttributeValue(
                                null, "vibrate"));
                        final String sound_uri = parser.getAttributeValue(null,
                                "sound_uri");
                        final int notification = Integer.parseInt(parser
                                .getAttributeValue(null, "notification"));
                        Log.d(TAG, "out : pre_channel_id = " + channel_id
                                + " \n  pre_editable = " + editable + "");
                        ContentValues value = new ContentValues();

                        value.put(PreChannelTableDefine.CHANNEL_ID, channel_id);
                        value.put(PreChannelTableDefine.CHANNEL_NAME,
                                channel_name);
                        value.put(PreChannelTableDefine.EDITABLE, editable);
                        value.put(PreChannelTableDefine.ENABLE, enable);
                        value.put(PreChannelTableDefine.SAVE, save);
                        value.put(PreChannelTableDefine.MCC, mcc);
                        value.put(PreChannelTableDefine.MNC, mnc);
                        value.put(PreChannelTableDefine.SUB_ID, -1);
                        value.put(PreChannelTableDefine.VIBRATE, vibrate);
                        value.put(PreChannelTableDefine.SOUND_URI, sound_uri);
                        value.put(PreChannelTableDefine.NOTIFICATION,
                                notification);
                        db.insert(PreChannelTableDefine.TABLE_NAME, "", value);
                        Log.d(TAG, "insert pre_channel sucessfully !!!");
                    }
                }
                parser.next();
            }
            db.setTransactionSuccessful();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Resources.NotFoundException e) {
            e.printStackTrace();
        } catch (XmlPullParserException e) {
            e.printStackTrace();
        } finally {
            db.endTransaction();
        }
    }
}
