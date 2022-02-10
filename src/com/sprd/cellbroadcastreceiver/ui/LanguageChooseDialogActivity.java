package com.sprd.cellbroadcastreceiver.ui;

import java.util.ArrayList;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.telephony.SubscriptionManager;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import com.android.cellbroadcastreceiver.R;
import com.sprd.cellbroadcastreceiver.data.LanguageMgr;
import com.sprd.cellbroadcastreceiver.ui.LanguageSettingActivity;
import com.sprd.cellbroadcastreceiver.util.LanguageIds;
import com.sprd.cellbroadcastreceiver.util.OsUtil;
import com.sprd.cellbroadcastreceiver.util.Utils;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
//add for bug757250 start
import com.android.internal.telephony.TelephonyIntents;
import android.content.Intent;
import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.PhoneConstants;
import com.sprd.cellbroadcastreceiver.provider.ChannelTableDefine;
//add for bug757250 end

public class LanguageChooseDialogActivity extends Activity {

    private String TAG = "LanguageChooseDialogActivity";

    private ListView mLangToChooseLV;
    private ArrayList<Integer> mLangIndexList;
    private String[] mLangString;

    private ArrayList<Integer> getLangBitList() {
        return mLangIndexList;
    }
    private int mSubId;
    private int mSlotId = -1;
    private Context mContext;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.language_choosing);

        mLangToChooseLV = (ListView) findViewById(R.id.language_list);
        mLangIndexList = new ArrayList<Integer>();
        getShowLangList();

        mLangToChooseLV.setOnItemClickListener(clickListner);
        mLangToChooseLV.setAdapter(new ArrayAdapter<String>(this,
                R.layout.language_choosing_item, mLangString));

        //add for bug757250 start
        Intent intent = getIntent();
        try{
            mSubId = intent.getIntExtra(ChannelTableDefine.SUB_ID, 0);
        }catch(Exception ex){
            Log.d(TAG,"LanguageSettingActivity onCreate Exception");
        }
        mSlotId = Utils.getSimInfor(intent);
        IntentFilter intentFilter = new IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        mContext=getApplicationContext();
        intentFilter.addAction(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        mContext.registerReceiver(sReceiver, intentFilter);
        //add for bug757250 end
    }

    private boolean getShowLangList() {
        mLangIndexList = LanguageMgr.getInstance().filterToLangChooseAdapter(
                getLangBitList());
        int size = mLangIndexList.size();
        mLangString = new String[size];
        for (int i = 0; i < size; i++) {
            final int index = LanguageIds.findIndex(LanguageMgr.getInstance().get(mLangIndexList.get(i)).getLanguageBit());
            mLangString[i] = getString(LanguageIds.LangMap[index]);
        }
        return true;
    }

    private AdapterView.OnItemClickListener clickListner = new AdapterView.OnItemClickListener() {
        public void onItemClick(AdapterView<?> parent, View view, int position,
                long id) {
            if (view != null) {
                int index = mLangIndexList.get(position);
                if (LanguageMgr.getInstance().get(index).getEnabled() == 1) {
                    // this language had been added
                    final Toast toast = Toast
                            .makeText(getApplicationContext(),
                                    R.string.language_had_been_added,
                                    Toast.LENGTH_LONG);
                    toast.setGravity(
                            Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 20);
                    toast.show();
                    //LanguageChooseDialogActivity.this.finish();
                } else {
                    LanguageMgr.getInstance().get(index).setEnabled(1);
                    //add for bug 532474
                    setResult(LanguageSettingActivity.RESULT_CHANGED,
                            getIntent());
                    LanguageChooseDialogActivity.this.finish();
                }
            }
        }
    };
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

    //Bug965651 begin
    @Override
    protected void onSaveInstanceState(Bundle outState){
        super.onSaveInstanceState(outState);
        Log.d(TAG, "---onSaveInstance---");
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState){
        super.onRestoreInstanceState(savedInstanceState);
        LanguageChooseDialogActivity.this.finish();
    }
    //Bug965651 end

    @Override
    public void onStart() {
        super.onStart();
    }

    //Bug 961072 begin
    @Override
    protected void onResume() {
        super.onResume();
        if (!OsUtil.hasRequiredPermissions(this)) {
            LanguageChooseDialogActivity.this.finish();
        }
    }
    //Bug 961072 end

    @Override
    public void onStop() {
        super.onStop();
    }
  //add for bug 744806 end
  //add for776171 start
  @Override
  protected void onDestroy() {
      mContext.unregisterReceiver(sReceiver);
      Log.d(TAG, " onDestroy ");
      super.onDestroy();
  }
    //add for776171 end
}
