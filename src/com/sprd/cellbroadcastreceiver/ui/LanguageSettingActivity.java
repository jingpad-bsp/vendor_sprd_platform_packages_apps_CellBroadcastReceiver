package com.sprd.cellbroadcastreceiver.ui;

import java.util.ArrayList;

import com.sprd.cellbroadcastreceiver.data.LanguageItemData;
import com.sprd.cellbroadcastreceiver.data.LanguageMgr;
import com.sprd.cellbroadcastreceiver.data.LanguageSettingAdapter;
import com.sprd.cellbroadcastreceiver.provider.ChannelTableDefine;
import com.sprd.cellbroadcastreceiver.util.OsUtil;
import com.sprd.cellbroadcastreceiver.util.Utils;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.IntentFilter;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SubscriptionManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;

import com.android.cellbroadcastreceiver.R;
//add for bug757250 start
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.PhoneConstants;
//add for bug757250 end



public class LanguageSettingActivity extends Activity {

    private String TAG = "LanguageSettingActivity";

    private int mSubId;
    private int mSlotId = -1;
    private ListView mLanguageLV;
    private LanguageSettingAdapter mLanguageAdapter;
    private ArrayList<LanguageItemData> mLanguageList;

    public static final int REQUEST_LANGUAGE = 1;
    //add for bug 532474
    public static final int RESULT_CHANGED   = 2;
    private boolean hasChanged = false;
    private boolean isUnexpectedClose = false;

    private int getSubId() {
        return mSubId;
    }

    private LanguageSettingAdapter getAdapter() {
        return mLanguageAdapter;
    }

    private ArrayList<LanguageItemData> getDataSource() {
        return mLanguageList;
    }
    private AlertDialog mDialog;
    private Context mContext;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.language_setting);
        //SPRD:Bug#527751
        getActionBar().setDisplayUseLogoEnabled(false);
        getActionBar().setDisplayShowHomeEnabled(false);
        getActionBar().setDisplayOptions(
                ActionBar.DISPLAY_SHOW_TITLE | ActionBar.DISPLAY_HOME_AS_UP);

        Intent intent = getIntent();
        try{
            mSubId = intent.getIntExtra(ChannelTableDefine.SUB_ID, 0);
        }catch(Exception ex){
            Log.d(TAG,"LanguageSettingActivity onCreate Exception");
        }
        mSlotId = Utils.getSimInfor(intent);
        //add for bug757250 start
        IntentFilter intentFilter = new IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        mContext=getApplicationContext();
        intentFilter.addAction(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        mContext.registerReceiver(sReceiver, intentFilter);
        //add for bug757250 end

        log("the sub_id send in is:" + getSubId());

        mLanguageLV = (ListView) findViewById(R.id.lstLanguage);
        LayoutInflater inflater = getLayoutInflater();
        View header = inflater.inflate(R.layout.language_set_header, null);
        mLanguageLV.addHeaderView(header, null, false);
        // mLanguageList.setOnItemClickListener(listItemClickListener);
        //modify for bug 542754
       // reflashLanguageList();
    }

    private void reflashLanguageList() {
        if (mLanguageAdapter == null) {
            //modify for bug 532105
            //modify for bug 543161 begin
            LanguageMgr.getInstance().init(getApplicationContext(), getSubId(), isUnexpectedClose);
            //modify for bug 543161 end
            mLanguageList = new ArrayList<LanguageItemData>();
            LanguageMgr.getInstance().filterToLangAdapter(mLanguageList);

            log("--reflashLanguageList--first time init. And the size of adapter is:"
                            + mLanguageList.size());

            mLanguageAdapter = new LanguageSettingAdapter(
                    LanguageSettingActivity.this, mLanguageList);
            mLanguageLV.setAdapter(mLanguageAdapter);
        } else {
            LanguageMgr.getInstance().filterToLangAdapter(mLanguageList);
            log("--reflashLanguageList--update datasource. The new size is:"
                            + mLanguageList.size());
            mLanguageAdapter.notifyDataSetChanged();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        //Bug 961072 begin
        if (!OsUtil.hasRequiredPermissions(this)) {
            OsUtil.requestMissingPermission(this);
        } else {
            reflashLanguageList();
        }
        //Bug 961072 end
    }

    //Bug965651 begin
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        for (int i : grantResults) {
            if (android.content.pm.PackageManager.PERMISSION_DENIED == i) {
                finish();
                return;
            }
        }
        mLanguageAdapter = null;
        reflashLanguageList();
    }
    //Bug965651 end

    @Override
    public void onBackPressed() {
        //add for bug 532474
        if (mLanguageAdapter.isChanged() || hasChanged) {
            AlertDialog.Builder dialog = new AlertDialog.Builder(this);
            mDialog = dialog.setTitle(R.string.alert)
                    .setIconAttribute(android.R.attr.alertDialogIcon)
                    .setCancelable(true)
                    .setMessage(R.string.remind_save)
                    .setPositiveButton(android.R.string.ok, clearChangeListener)
                    .setNegativeButton(R.string.no, null).create();
            mDialog.show();
        } else {
            //modify for bug 542754
            // LanguageMgr.releaseInstance();
            this.finish();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.channel_setting_menu, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (getDataSource() == null || getDataSource().size() == 0) {
            menu.findItem(R.id.del_all).setEnabled(false);
        } else {
            menu.findItem(R.id.del_all).setEnabled(true);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        log("--onOptionsItemSelected--");
        switch (item.getItemId()) {
        case R.id.add:
            log("--onOptionsItemSelected--option ADD.");
            Intent intent = new Intent(this, LanguageChooseDialogActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            intent.putExtra(ChannelTableDefine.SUB_ID, getSubId());
            startActivityForResult(intent, REQUEST_LANGUAGE);
            return true;
        case R.id.del_all:
            log("--onOptionsItemSelected--option DELTE ALL.");
            //modify for bug 542754
            for (int i = 0; i < getAdapter().getCount(); i++) {
                int index = ((LanguageItemData) getAdapter().getItem(i))
                        .getIndexOfArray();
                LanguageMgr.getInstance().get(index).setEnabled(0);
            }
            getDataSource().clear();
            getAdapter().notifyDataSetChanged();
            //add for bug 532474
            hasChanged = true;

            return true;
        case R.id.save:
            log("--onOptionsItemSelected--option APPLY: sync DB and send AT command.");
            Thread t1 = new Thread(new Runnable() {
                public void run() {
                    LanguageMgr.getInstance().SaveDataToDB();
                }
            }, "SaveDataToDB");
            t1.start();
            LanguageSettingActivity.this.finish();
            return true;
        // SPRD:Bug#527751
        case android.R.id.home:
            //SPRD:Bug#531838
            //add for bug 532474
            if (mLanguageAdapter.isChanged() || hasChanged) {
                AlertDialog.Builder dialog = new AlertDialog.Builder(this);
                mDialog = dialog.setTitle(R.string.alert)
                        .setIconAttribute(android.R.attr.alertDialogIcon)
                        .setCancelable(true)
                        .setMessage(R.string.remind_save)
                        .setPositiveButton(android.R.string.ok, clearChangeListener)
                        .setNegativeButton(R.string.no, null).create();
                mDialog.show();
            } else {
                //modify for bug 542754
                //LanguageMgr.releaseInstance();
                finish();
            }
            break;
        }
        return false;
    }

    private void log(String string){
        Log.d(TAG, string);
    }

    //add for bug 530800
    @Override
    protected void onDestroy() {
        //add for bug 770766 start
        Log.d(TAG, "onDestroy !!!");
        mContext.unregisterReceiver(sReceiver);
        //add for bug 770766 end
        //modify for bug 542754
        //LanguageMgr.releaseInstance();
        super.onDestroy();
    }
    //add for bug 532474

    //add for bug 543161
    @Override
    protected void onSaveInstanceState(Bundle outState){
        super.onSaveInstanceState(outState);
        Log.d(TAG, "---onSaveInstance---");
        outState.putBoolean("hasChange", hasChanged);
        outState.putBoolean("unexpected_close", true);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState){
        super.onRestoreInstanceState(savedInstanceState);
        Log.d(TAG, "---onRestoreInstance---");
        hasChanged = savedInstanceState.getBoolean("hasChange", false);
        isUnexpectedClose = savedInstanceState.getBoolean("unexpected_close", false);
    }
    //add for bug 543161 end

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (resultCode) {
        case RESULT_CHANGED:
            hasChanged = true;
            break;

        default:
            break;
        }
    }

    private OnClickListener clearChangeListener = new OnClickListener() {
        public void onClick(DialogInterface dialog, int which) {
            //modify for bug 542754
         //   LanguageMgr.releaseInstance();
            LanguageSettingActivity.this.finish();
        }
    };

    @Override
    public void onPause() {
        Log.d(TAG, "onPause !!!");
        //if (isInMultiWindowMode()){
        //}
        super.onPause();
    }
    //add for bug 744806 start
    private final BroadcastReceiver sReceiver = new  BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            //add for bug757250 start
            String simStatus = intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE);
            int subIdEx = intent.getIntExtra(PhoneConstants.SUBSCRIPTION_KEY, -1);
            Log.d(TAG, " 1 The simStatus"+ simStatus + "  subIdEx = " + subIdEx);
            if (action.equals(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)) {
                String reason = intent.getStringExtra("reason");
                if (reason != null && (reason.equals("homekey"))){ //|| reason.equals("recentapps"))) {
                    Log.d(TAG, "homekey action = Intent.ACTION_CLOSE_SYSTEM_DIALOGS " + reason);
                    finish();
                }
            } else if (intent.getAction() == TelephonyIntents.ACTION_SIM_STATE_CHANGED
                    && IccCardConstants.INTENT_VALUE_ICC_ABSENT.equals(simStatus)) {
                if (SubscriptionManager.isValidSubscriptionId(subIdEx)) {
                    if (subIdEx == mSubId) {
                        finish();
                    }
                    return;
                }
                int slotId = intent.getIntExtra("slot", -1);
                Log.d(TAG, "SIM ABSENT, slotId = " + slotId);
                if (mSlotId == slotId) {
                    finish();
                }
            }
            //add for bug757250 end
        }
     };

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onStop() {
        Log.d(TAG, "onStop !!!");
        if (isInMultiWindowMode()){
            if(mDialog != null){
                if(mDialog.isShowing()){
                    mDialog.dismiss();
                }
            }
        }
       //add for bug 744806 end
        super.onStop();
    }
}
