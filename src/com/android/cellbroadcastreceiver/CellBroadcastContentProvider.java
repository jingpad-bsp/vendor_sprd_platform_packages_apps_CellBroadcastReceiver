/*
 * Copyright (C) 2012 The Android Open Source Project
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

import android.annotation.NonNull;
import android.app.AppOpsManager;
import android.content.ContentProvider;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.Telephony;
import android.telephony.CellBroadcastMessage;
import android.text.TextUtils;
import android.util.Log;
import android.database.sqlite.SQLiteFullException;
import android.database.sqlite.SQLiteException;

import com.sprd.cellbroadcastreceiver.provider.CreateChannelViewDefine;
import com.sprd.cellbroadcastreceiver.provider.PreChannelTableDefine;


/**
 * ContentProvider for the database of received cell broadcasts.
 */
public class CellBroadcastContentProvider extends ContentProvider {
    private static final String TAG = "CellBroadcastContentProvider";

    /** URI matcher for ContentProvider queries. */
    private static final UriMatcher sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);

    /** Authority string for content URIs. */
    static final String CB_AUTHORITY = "cellbroadcasts";

    /** Content URI for notifying observers. */
    static final Uri CONTENT_URI = Uri.parse("content://cellbroadcasts/");

    public static Uri CHANNEL_URI = Uri.parse("content://cellbroadcasts/channel");

    /** URI matcher type to get all cell broadcasts. */
    private static final int CB_ALL = 0;

    /** URI matcher type to get a cell broadcast by ID. */
    private static final int CB_ALL_ID              = 1;
    private static final int CB_CHANNEL             = 2;
    private static final int CB_VIEW_LANG           = 3;
    private static final int CB_COMMON_SETTING      = 4;
    private static final int CB_LANG_MAP            = 5;
    private static final int CB_CHANNEL_BULK        = 6;
    private static final int CB_LANG_MAP_BULK       = 7;
    private static final int CB_VIEW_CHANNEL        = 8;
    private static final int CB_PRE_CHANNEL         = 9;
    /** MIME type for the list of all cell broadcasts. */
    private static final String CB_LIST_TYPE = "vnd.android.cursor.dir/cellbroadcast";

    /** MIME type for an individual cell broadcast. */
    private static final String CB_TYPE = "vnd.android.cursor.item/cellbroadcast";
    //sprd cbcustomsetting start
    public static final String CHANNEL_TABLE_NAME       = "channel";
    public static final String CHANNEL_SAVE             = "save";
    public static final String SUB_ID           = "sub_id";
    public static final String CHANNEL_ID       = "channel_id";
    public static final String LANG_MAP_TABLE_NAME  = "lang_map";
    public static final String COMMEN_SETTING_TABLE_NAME               = "common_setting";
    public static final String BROADCASTS_TABLE_NAME = "broadcasts";
    public static final String SERVICE_CATEGORY = "service_category";
    public static final String VIEW_LANG_NAME = "view_lang";
    public static final String ENABLED_CELLBROADCAST    = "enable";
    //sprd cbcustomsetting end

    static {
        sUriMatcher.addURI(CB_AUTHORITY, null, CB_ALL);
        sUriMatcher.addURI(CB_AUTHORITY, "#", CB_ALL_ID);
        sUriMatcher.addURI(CB_AUTHORITY, "channel", CB_CHANNEL);
        sUriMatcher.addURI(CB_AUTHORITY, "lang_map", CB_LANG_MAP);
        sUriMatcher.addURI(CB_AUTHORITY, "view_lang", CB_VIEW_LANG);
        sUriMatcher.addURI(CB_AUTHORITY, "common_setting", CB_COMMON_SETTING);
        sUriMatcher.addURI(CB_AUTHORITY, "channel_bulk", CB_CHANNEL_BULK);
        sUriMatcher.addURI(CB_AUTHORITY, "lang_map_bulk", CB_LANG_MAP_BULK);
        sUriMatcher.addURI(CB_AUTHORITY, "view_channel", CB_VIEW_CHANNEL);
        sUriMatcher.addURI(CB_AUTHORITY, "pre_channel", CB_PRE_CHANNEL);
    }

    /** The database for this content provider. */
    private SQLiteOpenHelper mOpenHelper;
    private SQLiteDatabase mSQLiteWDB;

    /**
     * Initialize content provider.
     * @return true if the provider was successfully loaded, false otherwise
     */
    @Override
    public boolean onCreate() {
        mOpenHelper = new CellBroadcastDatabaseHelper(getContext());
        try{
            mSQLiteWDB = mOpenHelper.getWritableDatabase();
        }catch (SQLiteException e){
            Log.d(TAG,"error:" + e.toString());
           return false;
        }
        setAppOps(AppOpsManager.OP_READ_CELL_BROADCASTS, AppOpsManager.OP_NONE);
        return true;
    }

    private SQLiteDatabase getSQLiteDB() {
        return mSQLiteWDB;
    }

    /**
     * Return a cursor for the cell broadcast table.
     * @param uri the URI to query.
     * @param projection the list of columns to put into the cursor, or null.
     * @param selection the selection criteria to apply when filtering rows, or null.
     * @param selectionArgs values to replace ?s in selection string.
     * @param sortOrder how the rows in the cursor should be sorted, or null to sort from most
     *  recently received to least recently received.
     * @return a Cursor or null.
     */
    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        /* SPRD: modify for bug#274468
         * orig : @{ */
         qb.setTables(CellBroadcastDatabaseHelper.TABLE_NAME);  
      //  qb.setTables(BROADCASTS_TABLE_NAME + " LEFT JOIN " + CHANNEL_TABLE_NAME
       //       + " ON broadcasts.service_category = channel.channel_id and broadcasts.sub_id = channel.sub_id");
       

        SQLiteDatabase db = mOpenHelper.getReadableDatabase();
        int match = sUriMatcher.match(uri);
        switch (match) {
            case CB_ALL:
                // get all broadcasts
                break;
            case CB_CHANNEL:
                // query the channel
                Log.d(TAG, "--Query--query the channel table.");
                qb.setTables(CHANNEL_TABLE_NAME);
                return qb.query(db, projection, selection, selectionArgs, null, null, sortOrder);

            case CB_ALL_ID:
                // get broadcast by ID
                qb.appendWhere("(_id=" + uri.getPathSegments().get(0) + ')');
                break;
            case CB_LANG_MAP:
                qb.setTables(LANG_MAP_TABLE_NAME);
                return qb.query(db, projection, selection, selectionArgs, null, null, sortOrder);
            case CB_VIEW_LANG:
                qb.setTables(VIEW_LANG_NAME);
                return qb.query(db, projection, selection, selectionArgs, null, null, sortOrder);

            case CB_COMMON_SETTING:
                qb.setTables(COMMEN_SETTING_TABLE_NAME);
                return qb.query(db, projection, selection, selectionArgs, null, null, sortOrder);
            case CB_VIEW_CHANNEL:
                qb.setTables(CreateChannelViewDefine.VIEW_CHANNEL_NAME);
                return qb.query(db, projection, selection, selectionArgs, null, null, sortOrder);
            case CB_PRE_CHANNEL:
                qb.setTables(PreChannelTableDefine.TABLE_NAME);
                return qb.query(db, projection, selection, selectionArgs, null, null, sortOrder);
            default:
                Log.e(TAG, "Invalid query: " + uri);
                throw new IllegalArgumentException("Unknown URI: " + uri);
        }

        String orderBy;
        if (!TextUtils.isEmpty(sortOrder)) {
            orderBy = sortOrder;
        } else {
            orderBy = Telephony.CellBroadcasts.DEFAULT_SORT_ORDER;
        }

//        SQLiteDatabase db = mOpenHelper.getReadableDatabase();
        Cursor c = qb.query(db, projection, selection, selectionArgs, null, null, orderBy);
        if (c != null) {
            c.setNotificationUri(getContext().getContentResolver(), CONTENT_URI);
        }
        return c;
    }

    /**
     * Return the MIME type of the data at the specified URI.
     * @param uri the URI to query.
     * @return a MIME type string, or null if there is no type.
     */
    @Override
    public String getType(Uri uri) {
        int match = sUriMatcher.match(uri);
        switch (match) {
            case CB_ALL:
                return CB_LIST_TYPE;

            case CB_ALL_ID:
                return CB_TYPE;

            default:
                return null;
        }
    }

    /**
     * Insert a new row. This throws an exception, as the database can only be modified by
     * calling custom methods in this class, and not via the ContentProvider interface.
     * @param uri the content:// URI of the insertion request.
     * @param values a set of column_name/value pairs to add to the database.
     * @return the URI for the newly inserted item.
     */
    @Override
    public Uri insert(Uri uri, ContentValues values) {
        int match = sUriMatcher.match(uri);
        Uri rUri = null;
        switch (match) {
            case CB_CHANNEL:
                long rowID = getSQLiteDB().insert(CHANNEL_TABLE_NAME, null, values);
                Log.d(TAG, "insert the new value.the new _id is:"+ rowID);
                rUri = Uri.parse(CONTENT_URI + CHANNEL_TABLE_NAME + "/" + rowID);
                break;
            case CB_LANG_MAP:
                    long rowID1 = getSQLiteDB().insert(LANG_MAP_TABLE_NAME, null, values);
                    Log.d(TAG, "insert the new value.the new _id is:"+ rowID1);
                    rUri = Uri.parse(CONTENT_URI + LANG_MAP_TABLE_NAME + "/" + rowID1);
                break;
            case CB_COMMON_SETTING:
                long rowID2 = getSQLiteDB().insert(COMMEN_SETTING_TABLE_NAME, null, values);
                Log.d(TAG, "insert the new value.the new _id is:"+ rowID2);
                rUri = Uri.parse(CONTENT_URI + CHANNEL_TABLE_NAME + "/" + rowID2);
                break;
            case CB_LANG_MAP_BULK:
                getSQLiteDB().beginTransaction();
                long rowID3 = -1;
                try {
                rowID3 = getSQLiteDB().insert(LANG_MAP_TABLE_NAME, null, values);
                Log.d(TAG, "insert the new value.the new _id is:"+ rowID3);
                rUri = Uri.parse(CONTENT_URI + LANG_MAP_TABLE_NAME + "/" + rowID3);
                getSQLiteDB().setTransactionSuccessful();
                } finally {
                    getSQLiteDB().endTransaction();
                }
                getContext().getContentResolver().notifyChange(uri, null);
            break;
        }
        Log.d(TAG, "the Uri is:"+ rUri);
        return rUri;
    }

    /**
     requests to insert a set of new rows, or the
     * default implementation will iterate over the values and call 
     * 
     * @param uri The content:// URI of the insertion request.
     * @param values An array of sets of column_name/value pairs to add to the database.
     *    This must not be {@code null}.
     * @return The number of values that were inserted.
     */
    @Override
    public int bulkInsert(@NonNull Uri uri, @NonNull ContentValues[] values) {
        int numOfValues =values.length;
        //modify for bug736240 start
		getSQLiteDB().beginTransaction();
        try {
            for (int i = 0; i < numOfValues; i++) {
                Log.d(TAG, "bulkInsert the new value. The value is:"+values[i].toString());
                insert(uri, values[i]);
            }
            getSQLiteDB().setTransactionSuccessful();
        }catch(SQLiteFullException e){
            Log.e(TAG,"bulkInsert SQLiteFullException: "+ e,new Throwable());
        } finally {
          if(getSQLiteDB() != null){
             getSQLiteDB().endTransaction();
             //getSQLiteDB().close();
             Log.d(TAG,"bulkInsert finally! ");
        }
             //modify for bug736240 end
        }
        getContext().getContentResolver().notifyChange(uri, null);
        return numOfValues;
    }

    /**
     * Delete one or more rows. This throws an exception, as the database can only be modified by
     * calling custom methods in this class, and not via the ContentProvider interface.
     * @param uri the full URI to query, including a row ID (if a specific record is requested).
     * @param selection an optional restriction to apply to rows when deleting.
     * @return the number of rows affected.
     */
    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        int match = sUriMatcher.match(uri);
        int count = -1;
        switch (match) {
        case CB_CHANNEL:
            count = getSQLiteDB().delete(CHANNEL_TABLE_NAME, selection, selectionArgs);   
            break;
        case CB_LANG_MAP:
            count = getSQLiteDB().delete(LANG_MAP_TABLE_NAME, selection, selectionArgs);   
            break;
        case CB_COMMON_SETTING:
            count = getSQLiteDB().delete(COMMEN_SETTING_TABLE_NAME, selection, selectionArgs);   
            break;
        case CB_CHANNEL_BULK:
            getSQLiteDB().beginTransaction();
            try {
                count = getSQLiteDB().delete(CHANNEL_TABLE_NAME, selection, selectionArgs);
                getSQLiteDB().setTransactionSuccessful();
            } finally {
                getSQLiteDB().endTransaction();
            }
            getContext().getContentResolver().notifyChange(uri, null);
            break;
        case CB_VIEW_LANG:
//            count = getSQLiteDB().delete(COMMEN_SETTING_TABLE_NAME, selection, selectionArgs);   
            break;
        }
        return count;
    }

    /**
     * Update one or more rows. This throws an exception, as the database can only be modified by
     * calling custom methods in this class, and not via the ContentProvider interface.
     * @param uri the URI to query, potentially including the row ID.
     * @param values a Bundle mapping from column names to new column values.
     * @param selection an optional filter to match rows to update.
     * @return the number of rows affected.
     */
    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        int match = sUriMatcher.match(uri);
        //SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        int effectRow = -1;
        switch (match) {
            case CB_CHANNEL:
                effectRow = getSQLiteDB().update(CHANNEL_TABLE_NAME, values,
                        selection, selectionArgs);
                break;
            case CB_LANG_MAP:
                    effectRow = getSQLiteDB().update(LANG_MAP_TABLE_NAME, values,
                            selection, selectionArgs);
                break;
            case CB_COMMON_SETTING:
                effectRow = getSQLiteDB().update(COMMEN_SETTING_TABLE_NAME, values,
                        selection, selectionArgs);
                break;
            case CB_VIEW_LANG:
    //            effectRow = getSQLiteDB().update(COMMEN_SETTING_TABLE_NAME, values, selection, selectionArgs);
                break;
            case CB_LANG_MAP_BULK:
                getSQLiteDB().beginTransaction();
                try {
                  effectRow = getSQLiteDB().update(LANG_MAP_TABLE_NAME, values,
                        selection, selectionArgs);
                  getSQLiteDB().setTransactionSuccessful();
                } finally {
                    getSQLiteDB().endTransaction();
                }
                getContext().getContentResolver().notifyChange(uri, null);
                break;
            case CB_CHANNEL_BULK:
                getSQLiteDB().beginTransaction();
                try {
                    effectRow = getSQLiteDB().update(CHANNEL_TABLE_NAME, values,
                    selection, selectionArgs);
                    getSQLiteDB().setTransactionSuccessful();
                } finally {
                    getSQLiteDB().endTransaction();
                }
                getContext().getContentResolver().notifyChange(uri, null);
                break;
        }
        return effectRow;
    }
    
    /**
     * Internal method to insert a new Cell Broadcast into the database and notify observers.
     * @param message the message to insert
     * @return true if the broadcast is new, false if it's a duplicate broadcast.
     */
    boolean insertNewBroadcast(CellBroadcastMessage message) {
        ContentValues cv = message.getContentValues();
        cv.put("sub_id", message.getSubId());

        // Note: this method previously queried the database for duplicate message IDs, but this
        // is not compatible with CMAS carrier requirements and could also cause other emergency
        // alerts, e.g. ETWS, to not display if the database is filled with old messages.
        // Use duplicate message ID detection in CellBroadcastAlertService instead of DB query.

        long rowId = getSQLiteDB().insert(BROADCASTS_TABLE_NAME, null, cv);
        if (rowId == -1) {
            Log.e(TAG, "failed to insert new broadcast into database");
            // Return true on DB write failure because we still want to notify the user.
            // The CellBroadcastMessage will be passed with the intent, so the message will be
            // displayed in the emergency alert dialog, or the dialog that is displayed when
            // the user selects the notification for a non-emergency broadcast, even if the
            // broadcast could not be written to the database.
        }
        return true;    // broadcast is not a duplicate
    }

    /**
     * Internal method to delete a cell broadcast by row ID and notify observers.
     * @param rowId the row ID of the broadcast to delete
     * @return true if the database was updated, false otherwise
     */
    boolean deleteBroadcast(long rowId) {

        int rowCount = getSQLiteDB().delete(BROADCASTS_TABLE_NAME,
                Telephony.CellBroadcasts._ID + "=?",
                new String[]{Long.toString(rowId)});
        if (rowCount != 0) {
            return true;
        } else {
            Log.e(TAG, "failed to delete broadcast at row " + rowId);
            return false;
        }
    }

    /**
     * Internal method to delete all cell broadcasts and notify observers.
     * @return true if the database was updated, false otherwise
     */
    boolean deleteAllBroadcasts() {
        int rowCount = getSQLiteDB().delete(BROADCASTS_TABLE_NAME, null, null);
        if (rowCount != 0) {
            return true;
        } else {
            Log.e(TAG, "failed to delete all broadcasts");
            return false;
        }
    }

    /**
     * Internal method to mark a broadcast as read and notify observers. The broadcast can be
     * identified by delivery time (for new alerts) or by row ID. The caller is responsible for
     * decrementing the unread non-emergency alert count, if necessary.
     *
     * @param columnName the column name to query (ID or delivery time)
     * @param columnValue the ID or delivery time of the broadcast to mark read
     * @return true if the database was updated, false otherwise
     */
    boolean markBroadcastRead(String columnName, long columnValue) {

        String whereClause = columnName + "=?";
        String[] whereArgs = new String[] { Long.toString(columnValue) };
        int rowCount = 0;

        if (isSaved(columnName, columnValue)) {
            ContentValues cv = new ContentValues(1);
            cv.put(Telephony.CellBroadcasts.MESSAGE_READ, 1);

            rowCount = getSQLiteDB().update(BROADCASTS_TABLE_NAME,
                    cv, whereClause, whereArgs);
        } else {
            rowCount = getSQLiteDB().delete(BROADCASTS_TABLE_NAME,
                    whereClause, whereArgs);
        }
        if (rowCount != 0) {
            return true;
        } else {
            Log.e(TAG, "failed to mark broadcast read: " + columnName + " = "
                    + columnValue);
            return false;
        }
    }

    private boolean isSaved(String columnName, long columnValue) {
        // String[] queryColumn = {CHANNEL_SAVE};
        String whereClause = columnName + "=?";
        String[] query_columns = { SERVICE_CATEGORY,
                SUB_ID };
        String[] whereArgs = new String[] { Long.toString(columnValue) };
        //add for 632314 start
        Cursor cursor = null;
        try {
            cursor = getSQLiteDB().query(BROADCASTS_TABLE_NAME,
                    query_columns, whereClause, whereArgs, null, null, null);
            if (cursor == null || cursor.getCount() == 0) {
                return true;
            } else {
                cursor.moveToFirst();
                int channel_id = cursor.getInt(0);
                int sub_id = cursor.getInt(1);
                Log.d(TAG, "query the subId :" + sub_id + " and channelid:"
                        + channel_id + " by the date.");
                cursor.close();
                String[] querySaved = {CHANNEL_SAVE};
                cursor = getSQLiteDB().query(
                        CHANNEL_TABLE_NAME,
                        querySaved,
                        SUB_ID + "=" + sub_id + " and "
                                + CHANNEL_ID + "=" + channel_id,
                        null, null, null, null);
                if (cursor == null || cursor.getCount() == 0) {
                    //modify for bug 549170 begin
                    if (cursor != null) {
                        cursor.close();
                    }
                    //modify for bug 549170 end
                    return true;
                } else {
                    cursor.moveToFirst();
                    Log.d(TAG, "the db has this channel, the saved is:" + cursor.getInt(0));
                    return cursor.getInt(0) == 1 ? true : false;
                }
            }
        }catch(SQLiteException e){
            Log.e(TAG, "isSaved: ", e);
        }finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return false;
        //add for 632314 end
    }

    /** Callback for users of AsyncCellBroadcastOperation. */
    interface CellBroadcastOperation {
        /**
         * Perform an operation using the specified provider.
         * @param provider the CellBroadcastContentProvider to use
         * @return true if any rows were changed, false otherwise
         */
        boolean execute(CellBroadcastContentProvider provider);
    }

    /**
     * Async task to call this content provider's internal methods on a background thread.
     * The caller supplies the CellBroadcastOperation object to call for this provider.
     */
    static class AsyncCellBroadcastTask extends AsyncTask<CellBroadcastOperation, Void, Void> {
        /** Reference to this app's content resolver. */
        private ContentResolver mContentResolver;

        AsyncCellBroadcastTask(ContentResolver contentResolver) {
            mContentResolver = contentResolver;
        }

        /**
         * Perform a generic operation on the CellBroadcastContentProvider.
         * @param params the CellBroadcastOperation object to call for this provider
         * @return void
         */
        @Override
        protected Void doInBackground(CellBroadcastOperation... params) {
            ContentProviderClient cpc = mContentResolver.acquireContentProviderClient(
                    CellBroadcastContentProvider.CB_AUTHORITY);
            CellBroadcastContentProvider provider = (CellBroadcastContentProvider)
                    cpc.getLocalContentProvider();

            if (provider != null) {
                try {
                    boolean changed = params[0].execute(provider);
                    if (changed) {
                        Log.d(TAG, "database changed: notifying observers...");
                        mContentResolver.notifyChange(CONTENT_URI, null, false);
                    }
                } finally {
                    cpc.release();
                }
            } else {
                Log.e(TAG, "getLocalContentProvider() returned null");
            }

            mContentResolver = null;    // free reference to content resolver
            return null;
        }
    }
}
