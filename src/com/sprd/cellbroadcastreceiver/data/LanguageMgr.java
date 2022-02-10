package com.sprd.cellbroadcastreceiver.data;

import java.util.ArrayList;

import com.android.cellbroadcastreceiver.CellBroadcastConfigManager;
import com.sprd.cellbroadcastreceiver.provider.CreateLangViewDefine;
import com.sprd.cellbroadcastreceiver.provider.LangMapTableDefine;
import com.sprd.cellbroadcastreceiver.provider.MncMccTableDefine;
import com.sprd.cellbroadcastreceiver.util.LanguageIds;
import com.sprd.cellbroadcastreceiver.util.Utils;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteFullException;
import android.net.Uri;
import android.util.Log;
import android.telephony.SubscriptionManager;
import android.telephony.SubscriptionInfo;

public class LanguageMgr extends ArrayList<LanguageItemData> {

    private static final long serialVersionUID = 1L;

    private final String TAG = "LanguageMgr";
    private boolean mNoRecordBySub = false;
    private int mSubId;
    private Context mContext;
    private SubscriptionInfo mSubscriptionInfo;
    private static LanguageMgr mIns;
    private static boolean mForceGetDataFromDB = false; //Bug965651
    private boolean mSavingToDB = false;  //Bug953562

    private LanguageMgr() {

    }

    private Context getContext() {
        return mContext;
    }

    private int getSubId() {
        return mSubId;
    }

    private SubscriptionInfo getSubscriptionInfo() {
        return mSubscriptionInfo;
    }

    private int getMcc() {
        return 460;
        //return getSubscriptionInfo().getMcc();
    }

    private int getMnc() {
        return 1;
        //return getSubscriptionInfo().getMnc();
    }

    public static LanguageMgr getInstance() {
        if (mIns == null) {
            mForceGetDataFromDB = true;  //Bug965651
            mIns = new LanguageMgr();
        }
        return mIns;
    }

    public static void releaseInstance() {
        synchronized (LanguageMgr.getInstance()) {
            mIns = null;
        }
    }

    public boolean init(Context context, int sub_id, boolean unexpected) {

        mContext = context;
        mSubId = sub_id;
        mSubscriptionInfo = SubscriptionManager.from(context).getActiveSubscriptionInfo(sub_id);
        return loadFromDb(sub_id, unexpected);
    }

    //modify for bug 543161 begin
    private boolean loadFromDb(int sub_id, boolean unexpected) {
        //modify for bug 542754
        // remove ALL data
        if (!unexpected) {
            log("Activity is closed nomal.");
            clear();
        } else {
            log("Activity is closed unexpected.");
            //return true;//delete for bug762381
        }
        //modify for bug 543161 end

        Cursor cursor = null;
        //Bug1145881 begin
        try {
            cursor = getContext().getContentResolver().query(
                    Utils.mViewLangUri,
                    CreateLangViewDefine.QUERY_COLUMNS,
                    LangMapTableDefine.SUBID + "=" + sub_id + " and "
                            + MncMccTableDefine.MNC + "=" + getMnc() + " and "
                            + MncMccTableDefine.MCC + "=" + getMcc(),
                    null,
                    LangMapTableDefine.LANG_ID + " ASC");

            if (cursor == null || cursor.getCount() == 0) {
                //modify for bug 549170 begin
                if (cursor != null) {
                    cursor.close();
                }
                //modify for bug 549170 end
                log("used default values.");
                mNoRecordBySub = true;
                cursor = getContext().getContentResolver().query(
                        Utils.mViewLangUri,
                        CreateLangViewDefine.QUERY_COLUMNS,
                        MncMccTableDefine.MNC + "=" + getMnc() + " and "
                                + MncMccTableDefine.MCC + "=" + getMcc() + " and "
                                + LangMapTableDefine.SUBID + "=" + "-1",
                        null,
                        LangMapTableDefine.LANG_ID + " ASC");
            } else {//modify for bug 554672
                mNoRecordBySub = false;
            }
            log("--Lang--LoadFromDb--the subId is:" + sub_id
                    + " the count is:" + cursor.getCount());
            if (mForceGetDataFromDB || !unexpected) {//add for bug765963 //add mForceGetDataFromDB for Bug965651
                addDataToArray(cursor);
            }
        } catch (SQLiteException e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        //Bug1145881 end
        mForceGetDataFromDB = false; //Bug965651
        return true;
    }

    private boolean addDataToArray(Cursor cursor) {
        if (cursor == null || cursor.getCount() == 0) {
            return false;
        }

        try {
            cursor.moveToFirst();
            do {
                LanguageItemData itemData = new LanguageItemData(cursor,
                        this.size());
                getInstance().add(itemData);
            } while (cursor.moveToNext());
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public ArrayList<LanguageItemData> filterToLangAdapter(
            ArrayList<LanguageItemData> list) {
        if (list == null) {
            return null;
        } else {
            list.clear();
        }
        int size = this.size();
        log("the size of LanguageMgr is:"+size);
        log("the detail of LanguageMgr is:"+this.toString());
        for (int i = 0; i < size; i++) {
            if (get(i).getEnabled() == 0) {
                continue;
            } else {
                list.add(get(i));
                log("i is:"+i+" and add language:"+ get(i).getDescription()+"\n");
            }
        }
        return list;
    }

    public ArrayList<Integer> filterToLangChooseAdapter(ArrayList<Integer> list) {
        if (list == null) {
            return null;
        } else {
            list.clear();
        }
        int size = this.size();
        log("the size of LanguageMgr is:"+size);
        log("the detail of LanguageMgr is:"+this.toString());
        for (int i = 0; i < size; i++) {
            if (get(i).getShow() == 0) {
                continue;
            } else {
                list.add(get(i).getIndexOfArray());
            }
        }
        return list;
    }

    public boolean SaveDataToDB() {
        //Bug953562 begin
        if (mSavingToDB) {
            log("The action for saving data to db has been executing.");
            return false;
        }
        mSavingToDB = true;
        //Bug953562 end
        bulkUpdate(Utils.mLangUri, getSubId());
        //every language need to base a value and set ATCommand
        //Utils.sendLangAT(mContext, getLanguageId(), getSubId(), true);
        final CellBroadcastConfigManager cbcMgr = CellBroadcastConfigManager.getInstance(getSubId());
        for (int i = 0; i < getLanguageId().size(); i ++) {
            cbcMgr.setGsmCellBroadcastLanguage(true, getLanguageId().get(i));
        }
        mSavingToDB = false; //Bug953562
        return true;
    }

    //filter those bits which need to open
    private ArrayList<Integer> getLanguageId(){
//        int codeScheme = 0;
        int size = getInstance().size()<LanguageIds.MAX_LANG ? getInstance().size():LanguageIds.MAX_LANG;
        ArrayList<Integer> languageId = new ArrayList<Integer>();
        for (int i = 0; i < size; i++) {
//            codeScheme |= get(i).getEnabled() << get(i).getLanguageBit()-1;
            if (get(i).getEnabled() == 1) {
                final int bit = get(i).getLanguageBit();
                log("getLang langId by:" 
                        + getContext().getString(LanguageIds.LangMap[LanguageIds.findIndex(bit)])
                        +" and language datacoding scheme is: " + bit);
                languageId.add(bit);
            }
        }
//        log("--getLanguageId--codeScheme is:"+codeScheme);
        return languageId;
    }

    private boolean bulkUpdate(Uri uri, int sub_id) {
        ///*
        //CellBroadcastDatabaseHelper dbHelper = new CellBroadcastDatabaseHelper(
         //       getContext());
        //dbHelper.getWritableDatabase().beginTransaction();
        try {
            int size = this.size();
            for (int i = 0; i < size; i++) {
                ContentValues cv = new ContentValues();
                cv.put(LangMapTableDefine.ENABLE, get(i).getEnabled());
                if (mNoRecordBySub) {
                    log("--insertToDB--");
                    cv.put(LangMapTableDefine.LANG_ID, get(i).getLanguageBit());
                    cv.put(LangMapTableDefine.MNC_MCC_ID, get(i).getMncMccId());
                    cv.put(LangMapTableDefine.SUBID, sub_id);
                    cv.put(LangMapTableDefine.SHOW, get(i).getShow());
                    getContext().getContentResolver().insert(Utils.mLangBulkUri, cv);
                } else {
                    getContext().getContentResolver().update(
                            Utils.mLangBulkUri, cv,
                            LangMapTableDefine._ID + "=" + get(i).getId(), null);
                }

            }
           // dbHelper.getWritableDatabase().setTransactionSuccessful();
        } catch(SQLiteFullException e){
            Log.e(TAG,"bulkUpdate SQLiteFullException: "+ e,new Throwable());
        }
        finally {
            //dbHelper.getWritableDatabase().endTransaction();
        }
        //getContext().getContentResolver().notifyChange(uri, null);
       // */
        return true;
    }

    //Bug965560 begin
    public void setLanguageEnable(final LanguageItemData data, final boolean enable) {
        if (data == null) {
            return;
        }
        for (int i = 0; i < this.size(); i++) {
            if (data.getLanguageBit() == LanguageMgr.getInstance().get(i).getLanguageBit()) {
                LanguageMgr.getInstance().get(i).setEnabled(enable ? 1 : 0);
                break;
            }
        }
    }
    //Bug965560 end

    private void log(String string){
        Log.d(TAG, string);
    }

    @Override
    public String toString(){
        String s = null;
        for (int i = 0; i < LanguageMgr.getInstance().size(); i++) {
            s += "i is:"+i+" ,Description is:"+get(i).getDescription()+" and showed:"+get(i).getShow()+"\n";
        }
        return s;
    }
}
