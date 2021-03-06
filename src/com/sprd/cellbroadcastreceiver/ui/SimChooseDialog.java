package com.sprd.cellbroadcastreceiver.ui;

import java.util.ArrayList;
import java.util.List;

import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;

import android.telephony.SmsManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

//import com.android.cellbroadcastreceiver.CellBroadcastConfigService;
import com.android.cellbroadcastreceiver.R;
//import com.android.cellbroadcastreceiver.CellBroadcastSettings;
import com.sprd.cellbroadcastreceiver.util.Utils;
//add for bug 682634 start
import android.content.IntentFilter;
import android.content.BroadcastReceiver;

import com.android.internal.telephony.TelephonyIntents;

import android.content.Intent;

import com.android.internal.telephony.IccCardConstants;
//add for bug 682634 end
import android.os.Handler;

public class SimChooseDialog extends ListActivity {

    private String TAG = "SimChooseDialog";

    private ListView mSimList;
    private String mSettingType;
    private boolean mIsAirplaneMode = false;

    private List<SubscriptionInfo> mSubList;
    private SimListAdapter adapter = null;
    private static String mPrevSimStatus = IccCardConstants.INTENT_VALUE_ICC_ABSENT;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.sim_choose_dialog);
        mIsAirplaneMode = (Settings.Global.getInt(getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON, 0) != 0);
        try {
            mSettingType = getIntent().getStringExtra(Utils.SETTING_TYPE);
        } catch (Exception ex) {
            Log.d(TAG, "SimChooseDialog onCreate exception");
        }

        //initActiveSim();
        mSubList = SubscriptionManager.from(this).getActiveSubscriptionInfoList();//SmsManager.getDefault().getActiveSubInfoList();
        //modify for bug 535332 begin
        if (checkAirMode()) {
            SimChooseDialog.this.finish();
            return;
        }
        //Modify for bug 860530 start
        if (mSubList != null && mSubList.size() > 1) {
            initActiveSim();
            if (savedInstanceState != null) {
                final Handler mHandler = new Handler();
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (!(SimChooseDialog.this.isFinishing() || SimChooseDialog.this.isDestroyed())) {
                            mSubList = SubscriptionManager.from(SimChooseDialog.this).getActiveSubscriptionInfoList();
                            if (mSubList != null && mSubList.size() > 1) {
                                initActiveSim();
                            }
                        }
                    }
                }, 600);
            }
        } else if (mSubList != null && mSubList.size() == 1) {
            showSettingActivity(((SubscriptionInfo) mSubList.get(0)).getSubscriptionId());
        } else {
            SimChooseDialog.this.finish();
        }
        //Modify for bug 860530 end
    }

    private void initActiveSim() {
        //mSubList = SmsManager.getDefault().getActiveSubInfoList();

        if (mSubList == null) {
            mSubList = new ArrayList<SubscriptionInfo>();
        }
        log("--initActiveSim--airplane mode:" + mIsAirplaneMode + " and active sim's count is:" + mSubList.size());

        adapter = new SimListAdapter(this, mSubList, R.layout.sim_choose_item);
        ListView listView = getListView();
        listView.setAdapter(adapter);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                break;
            default:
                return true;
        }
        return false;
    }

    //@Override
   /* protected void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);

        int sub_Id = ((SubscriptionInfo) adapter.getItem(position))
                .getSubscriptionId();
        //the sub_id will be translated in the queryEnabledCheckBox(), so no need translated twice*/
    private void showSettingActivity(int sub_Id) {
        //the sub_id will be translated in the queryEnabledCheckBox(), so no need translated twice
        int subId = sub_Id;
        boolean enable = SubscriptionManager.getBooleanSubscriptionProperty(sub_Id,
                SubscriptionManager.CB_EMERGENCY_ALERT, true, getApplicationContext());
        if (!enable) {
            Log.d(TAG, "check state--Enable CheckBox is false.");
            final Toast toast = Toast.makeText(getApplicationContext(),
                    R.string.cellbroadcast_need_enable, Toast.LENGTH_LONG);
            toast.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 20);
            toast.show();
            SimChooseDialog.this.finish();
            return;
        }
        if (!Utils.USE_SUBID) {
            subId = Utils.tanslateSubIdToPhoneId(SimChooseDialog.this, subId);
            log("translate subId:" + sub_Id + " to phoneId: " + subId);
        }

        Intent intent = null;
        // Add by sprd for Bug600518 begin
        if (mSettingType == null)
            mSettingType = Utils.CHANNEL_SETTING;
        // Add by sprd for Bug600518 end
        if (mSettingType.equalsIgnoreCase(Utils.CHANNEL_SETTING)) {
            intent = new Intent(this, ChannelSettingActivity.class);
            intent.putExtra("sub_id", subId);
            intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        } else if (mSettingType.equalsIgnoreCase(Utils.LANGUAGE_SETTING)) {
            intent = new Intent(this, LanguageSettingActivity.class);
            intent.putExtra("sub_id", subId);
            intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        }
        SimChooseDialog.this.finish();
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);
        int sub_Id = ((SubscriptionInfo) adapter.getItem(position))
                .getSubscriptionId();
        showSettingActivity(sub_Id);
    }

    public class SimListAdapter extends BaseAdapter {
        public final class ViewHolder {
            public ImageView color;
            public TextView name;
            public TextView number;
        }

        private LayoutInflater mInflater;
        private List<SubscriptionInfo> mSimInfoList;
        private int mLayoutId;

        public SimListAdapter(Context context, List<SubscriptionInfo> data, int layoutId) {
            this.mInflater = LayoutInflater.from(context);
            this.mSimInfoList = data;
            this.mLayoutId = layoutId;
        }

        public int getCount() {
            return mSimInfoList.size();
        }

        public SubscriptionInfo getItem(int position) {
            return mSimInfoList.get(position);
        }

        public long getItemId(int position) {
            return position;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder = null;
            if (convertView == null) {
                holder = new ViewHolder();
                convertView = mInflater.inflate(mLayoutId, null);
                holder.color = (ImageView) convertView
                        .findViewById(R.id.sim_color);
                holder.name = (TextView) convertView
                        .findViewById(R.id.sim_name);
                holder.number = (TextView) convertView
                        .findViewById(R.id.sim_number);
                holder.name.setTextColor(Color.BLACK);
                holder.number.setTextColor(Color.BLACK);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }
            holder.color.setImageBitmap(mSimInfoList.get(position)
                    .createIconBitmap(getApplicationContext()));

            holder.name.setText(TextUtils.isEmpty(mSimInfoList.get(position)
                    .getDisplayName()) ? "SIM" + (position + 1) : mSimInfoList.get(
                    position).getDisplayName());
            holder.number.setVisibility(View.GONE);

            return convertView;
        }
    }

    private void log(String string) {
        Log.d(TAG, string);
    }

    private boolean checkAirMode() {
        mIsAirplaneMode = (Settings.Global.getInt(getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON, 0) != 0);

        log("--onListItemClick--mIsAirplaneMode:" + mIsAirplaneMode);

        if (mIsAirplaneMode) {
            final Toast toast = Toast.makeText(getApplicationContext(),
                    R.string.toast_airplane_mode, Toast.LENGTH_LONG);
            toast.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 20);
            toast.show();
            return true;
        }
        return false;
    }

    //add for bug 682634 start
    private final BroadcastReceiver sReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            String simStatus = intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE);
            if (action.equals(TelephonyIntents.ACTION_SIM_STATE_CHANGED)) {
                if (IccCardConstants.INTENT_VALUE_ICC_ABSENT.equals(mPrevSimStatus)
                        && IccCardConstants.INTENT_VALUE_ICC_ABSENT.equals(simStatus)) {
                    return;
                } else if (!IccCardConstants.INTENT_VALUE_ICC_ABSENT.equals(mPrevSimStatus) &&
                        IccCardConstants.INTENT_VALUE_ICC_ABSENT.equals(simStatus)) {
                    mPrevSimStatus = simStatus;
                    SimChooseDialog.this.finish();
                }
                mPrevSimStatus = simStatus;
            }
        }
    };

    @Override
    public void onStart() {
        super.onStart();
        IntentFilter intentFilter = new IntentFilter(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        getApplicationContext().registerReceiver(sReceiver, intentFilter);
    }

    @Override
    public void onStop() {
        super.onStop();
        getApplicationContext().unregisterReceiver(sReceiver);
    }
    //add for bug 682634 end
}
