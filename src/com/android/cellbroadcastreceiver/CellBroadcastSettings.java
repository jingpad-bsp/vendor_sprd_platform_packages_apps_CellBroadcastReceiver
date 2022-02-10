/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.cellbroadcastreceiver;

import android.app.ActionBar;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.PersistableBundle;
import android.os.UserManager;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.telephony.CarrierConfigManager;
import android.telephony.ServiceState;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.TabHost;
import android.widget.TabHost.OnTabChangeListener;
import android.widget.TabHost.TabContentFactory;
import android.widget.TabHost.TabSpec;
import android.widget.TabWidget;

import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyIntents;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import android.widget.TextView;

/**
 * Settings activity for the cell broadcast receiver.
 */
public class CellBroadcastSettings extends PreferenceActivity{
    private final static String TAG = "CellBroadcastSettings";

    // Preference category for custom channel ringtone settings.
    public static final String KEY_CATEGORY_ENABLE_CELLBROADCAST = "category_enable_cellbroadcast";

    //set the notification ringtone

    // Preference key for whether to enable emergency notifications (default enabled).
    public static final String KEY_ENABLE_EMERGENCY_ALERTS = "enable_emergency_alerts";

    // Enable vibration on alert (unless master volume is silent).
    public static final String KEY_ENABLE_ALERT_VIBRATE = "enable_alert_vibrate";

    // Speak contents of alert after playing the alert sound.
    public static final String KEY_ENABLE_ALERT_SPEECH = "enable_alert_speech";
    
    public static final String KEY_ALERT_TONE_PREVIEW = "alert_tone_preview";

    // Preference category for emergency alert and CMAS settings.
    public static final String KEY_CATEGORY_ALERT_SETTINGS = "category_alert_settings";
    // Preference category for peru CMAS settings.
    public static final String KEY_CATEGORY_PERU_SETTINGS    = "category_peru_setting";
    // Preference category for new zealand CMAS settings.
    public static final String KEY_CATEGORY_NZ_SETTINGS    = "category_newzealand_setting";

    // Preference category for ETWS related settings.
    public static final String KEY_CATEGORY_ETWS_SETTINGS = "category_etws_settings";

    // Whether to display CMAS extreme threat notifications (default is enabled).
    public static final String KEY_ENABLE_CMAS_EXTREME_THREAT_ALERTS =
            "enable_cmas_extreme_threat_alerts";

    // Whether to display CMAS severe threat notifications (default is enabled).
    public static final String KEY_ENABLE_CMAS_SEVERE_THREAT_ALERTS =
            "enable_cmas_severe_threat_alerts";

    // Whether to display CMAS amber alert messages (default is enabled).
    public static final String KEY_ENABLE_CMAS_AMBER_ALERTS = "enable_cmas_amber_alerts";

    // Preference category for development settings (enabled by settings developer options toggle).
    public static final String KEY_CATEGORY_DEV_SETTINGS = "category_dev_settings";

    // Whether to display ETWS test messages (default is disabled).
    public static final String KEY_ENABLE_ETWS_TEST_ALERTS = "enable_etws_test_alerts";

    // Whether to display CMAS monthly test messages (default is disabled).
    public static final String KEY_ENABLE_CMAS_TEST_ALERTS = "enable_cmas_test_alerts";

    // Preference category for Brazil specific settings.
    public static final String KEY_CATEGORY_BRAZIL_SETTINGS = "category_brazil_settings";

    // Preference key for whether to enable channel 50 notifications
    // Enabled by default for phones sold in Brazil, otherwise this setting may be hidden.
    public static final String KEY_ENABLE_CHANNEL_50_ALERTS = "enable_channel_50_alerts";

    public static final String KEY_CATEGORY_CHANNEL_SETTINGS = "category_channel_settings";

    // Preference key for initial opt-in/opt-out dialog.
    public static final String KEY_SHOW_CMAS_OPT_OUT_DIALOG = "show_cmas_opt_out_dialog";

    // Alert reminder interval ("once" = single 2 minute reminder).
    public static final String KEY_ALERT_REMINDER_INTERVAL = "alert_reminder_interval";

    // First time use
    public static final String KEY_FIRST_TIME = "first_time";

    // Brazil country code
    private static final String COUNTRY_BRAZIL = "br";
    private static final String COUNTRY_PERU="pe";
    private static final String COUNTRY_DUBAI="ae";
    private static final String COUNTRY_NEWZEALAND = "nz";

    public static final String KEY_CATEGORY_MTC_SETTINGS = "category_mtc_settings";

    public static final String KEY_ENABLE_CHANNEL_4370_919 = "enable_channel_4370_919_alerts";
    public static final String KEY_ENABLE_CHANNEL_4383 = "enable_channel_4383_alerts";
    public static final String KEY_ENABLE_CHANNEL_4382 = "enable_channel_4382_alerts";
    public static final String KEY_ENABLE_CHANNEL_4380_519 = "enable_channel_4380_519_alerts";
    public static final String KEY_ENABLE_CHANNEL_4381_519 = "enable_channel_4381_519_alerts";
    public static final String KEY_ENABLE_CHANNEL_4396_4399 = "enable_channel_4396_4399_alerts";

    private static CheckBoxPreference mMtc4370_919CheckBox;
    private static CheckBoxPreference mMtc4383CheckBox;
    private static CheckBoxPreference mMtc4382CheckBox;
    private static CheckBoxPreference mMtc4380_519CheckBox;
    private static CheckBoxPreference mMtc4381_519CheckBox;
    private static CheckBoxPreference mMtc4396_4399CheckBox;

    //add for Dubai PWS setting
    public static final String KEY_CATEGORY_DUBAI_SETTINGS = "category_dubai_settings";
    public static final String KEY_ENABLE_CHANNEL_4370_4383 = "enable_channel_4370_4383_alerts";
    public static final String KEY_ENABLE_CHANNEL_ALL_ALERTS = "enable_channel_all_alerts";//4371~4378, 4384~4391,4381,4394,4398,4399
    public static final String KEY_ENABLE_CHANNEL_4379_4392 = "enable_channel_4379_4392_alerts";
    public static final String KEY_ENABLE_CHANNEL_4380_4393 = "enable_channel_4380_4393_alerts";
    public static final String KEY_ENABLE_CHANNEL_4396_4397 = "enable_channel_4396_4397_alerts";
    //add for Dubai PWS setting

    //add for Taiwan PWS setting, bug1017316
    public static final String KEY_CATEGORY_TAIWAN_SETTINGS = "category_taiwan_settings";
    public static final String KEY_TAIWAN_ENABLE_CHANNEL_911_919 = "enable_channel_911_919_alerts_tw";
    public static final String KEY_TAIWAN_ENABLE_CHANNEL_4370_4383 = "enable_channel_4370_4383_alerts_tw";
    public static final String KEY_TAIWAN_ENABLE_CHANNEL_4371_4379 = "enable_channel_4371_4379_alerts_tw";
    public static final String KEY_TAIWAN_ENABLE_CHANNEL_4384_4392 = "enable_channel_4384_4392_alerts_tw";
    public static final String KEY_TAIWAN_ENABLE_CHANNEL_4380_4393 = "enable_channel_4380_4393_alerts_tw";

    //add for Taiwan PWS setting, bug1017316
    private CheckBoxPreference mTaiwanPws911_919CheckBox;
    private CheckBoxPreference mTaiwanPws4370_4383CheckBox;
    private CheckBoxPreference mTaiwanPws4371_4379CheckBox;
    private CheckBoxPreference mTaiwanPws4384_4392CheckBox;
    private CheckBoxPreference mTaiwanPws4380_4393CheckBox;

    //add for New Zealand setting begin
    public static final String KEY_NZ_ENABLE_CHANNEL_4370 = "key_nz_control_4370_alert";
    public static final String KEY_NZ_ENABLE_CHANNEL_4371_4372 = "key_nz_control_4371_4372_alert";
    public static final String KEY_NZ_ENABLE_CHANNEL_4373_4378 = "key_nz_control_4373_4378_alert";
    //add for New Zealand setting end

    private CheckBoxPreference mDubaiPws4370_4383CheckBox;
    private CheckBoxPreference mAllAlertsCheckBox;
    private CheckBoxPreference mDubaiPws4379_4392CheckBox;
    private CheckBoxPreference mDubaiPws4380_4393CheckBox;
    private CheckBoxPreference mDubaiPws4396_4397CheckBox;
    private boolean enableDubaiPwsSupport = false;
    private boolean enablePeruPwsSupport =false;
    private boolean enableNzPwsSupport =false;
    private ProgressDialog pdDialog = null;
    private int iCount = 0;

    private TelephonyManager mTelephonyManager;
    private SubscriptionInfo mSir;
    private TabHost mTabHost;
    private TabWidget mTabWidget;
    private List<SubscriptionInfo> mSelectableSubInfos;
        private CheckBoxPreference mExtremeCheckBox;
        private CheckBoxPreference mSevereCheckBox;
        private CheckBoxPreference mAmberCheckBox;
        private CheckBoxPreference mEmergencyCheckBox;
        private ListPreference mReminderInterval;
        private CheckBoxPreference mVibrateCheckBox;
        private CheckBoxPreference mSpeechCheckBox;
        private CheckBoxPreference mEtwsTestCheckBox;
        private CheckBoxPreference mChannel50CheckBox;
        private CheckBoxPreference mCmasTestCheckBox;
        private CheckBoxPreference mOptOutCheckBox;
        private PreferenceCategory mAlertCategory;
        private PreferenceCategory mETWSSettingCategory;
        private PreferenceCategory mDubaiSettingsCategory;
        private PreferenceCategory mPeruSettingsCategory;
        private PreferenceCategory mNewZealandSettingsCategory;
    private Preference mTonePreview;
    //add for 616618 start
    private static final String SMS_CB_SUBSCRIPTIONINFO_EXTRA = "subscriptionInfo";
    private String mTabId = "";
    private static final String TAB_TAG_ID_EXTRA = "tabid";
    //add for 616618 end

    public static final String ENABLED_CELLBROADCAST    = "enable";
    public static final String SUBID                    = "sub_id";
    public static final String SUB_ID           = "sub_id";
    public static final String CHANNEL_ID       = "channel_id";
    public static final String ENABLE           = "enable";
    public static final String LANG_ID              = "lang_id";

    public static int DISABLE_CHANNEL           = 1;
    public static int OPERATION_ADD             = 2;
    public static int OPERATION_EDIT            = 3;
    public static int OPERATION_DEL             = 4;
    public static int SET_CHANNEL               = 5;
    public static int SET_LANGUAGE              = 6;
    public static int PADDING                   = -1;  //0xffff -> .

    //bug 903820 begin
    private static final int OPT_MSG = 1;
    private static final int CB_ALERT_VIBRATE_MSG = 2;
    private static final int CB_EMERGENCY_ALERT_MSG = 3;
    private static final int CB_ALERT_SPEECH_MSG = 4;
    private static final int CB_CHANNEL_50_ALERT_MSG = 5;
    private static final int CB_ETWS_TEST_ALERT_MSG = 6;
    private static final int CB_EXTREME_THREAT_ALERT_MSG = 7;
    private static final int CB_SEVERE_THREAT_ALERT_MSG = 8;
    private static final int CB_AMBER_ALERT_MSG = 9;
    private static final int CB_CMAS_TEST_ALERT_MSG = 10;

    private CellBroadcastConfigManager mCbcMgr;
	private CellBroadcastLocationManager mLocMgr;
    private Context mContext;
    private HandlerThread mWorkThread;
    private Handler mWorkHandler;
    private Handler mHandler = new Handler()  {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case OPT_MSG: {
                    final boolean isChecked = (boolean)msg.obj;
                    //Log.d("weicn", "OPT_MSG > isChecked = " + isChecked);
                    if (mOptOutCheckBox != null) {
                        mOptOutCheckBox.setChecked(isChecked);
                    }
                    break;
                }
                case CB_ALERT_VIBRATE_MSG: {
                    final boolean isChecked = (boolean)msg.obj;
                    //Log.d("weicn", "CB_ALERT_VIBRATE_MSG > isChecked = " + isChecked);
                    if (mVibrateCheckBox != null) {
                        mVibrateCheckBox.setChecked(isChecked);
                    }
                    break;
                }
                case CB_EMERGENCY_ALERT_MSG: {
                    final boolean isChecked = (boolean)msg.obj;
                    //Log.d("weicn", "CB_EMERGENCY_ALERT_MSG > isChecked = " + isChecked);
                    if (mEmergencyCheckBox != null) {
                        mEmergencyCheckBox.setChecked(isChecked);
                    }
                    break;
                }
                case CB_ALERT_SPEECH_MSG: {
                    final boolean isChecked = (boolean)msg.obj;
                    //Log.d("weicn", "CB_ALERT_SPEECH_MSG > isChecked = " + isChecked);
                    if (mSpeechCheckBox != null) {
                        mSpeechCheckBox.setChecked(isChecked);
                    }
                    break;
                }
                case CB_CHANNEL_50_ALERT_MSG: {
                    final boolean isChecked = (boolean)msg.obj;
                    //Log.d("weicn", "CB_CHANNEL_50_ALERT_MSG > isChecked = " + isChecked);
                    if (mChannel50CheckBox != null) {
                        mChannel50CheckBox.setChecked(isChecked);
                    }
                    break;
                }
                case CB_ETWS_TEST_ALERT_MSG: {
                    final boolean isChecked = (boolean)msg.obj;
                    //Log.d("weicn", "CB_ETWS_TEST_ALERT_MSG > isChecked = " + isChecked);
                    if (mEtwsTestCheckBox != null) {
                        mEtwsTestCheckBox.setChecked(isChecked);
                    }
                    break;
                }
                case CB_EXTREME_THREAT_ALERT_MSG: {
                    final boolean isChecked = (boolean)msg.obj;
                    //Log.d("weicn", "CB_EXTREME_THREAT_ALERT_MSG > isChecked = " + isChecked);
                    if (mExtremeCheckBox != null) {
                        mExtremeCheckBox.setChecked(isChecked);
                    }

                    if (mSevereCheckBox != null) {
                        mSevereCheckBox.setEnabled(isChecked);
                    }
                    break;
                }
                case CB_SEVERE_THREAT_ALERT_MSG: {
                    final boolean isChecked = (boolean)msg.obj;
                    //Log.d("weicn", "CB_SEVERE_THREAT_ALERT_MSG > isChecked = " + isChecked);
                    if (mSevereCheckBox != null) {
                        mSevereCheckBox.setChecked(isChecked);
                    }
                    break;
                }
                case CB_AMBER_ALERT_MSG: {
                    final boolean isChecked = (boolean)msg.obj;
                    //Log.d("weicn", "CB_AMBER_ALERT_MSG > isChecked = " + isChecked);
                    if (mAmberCheckBox != null) {
                        mAmberCheckBox.setChecked(isChecked);
                    }
                    break;
                }
                case CB_CMAS_TEST_ALERT_MSG: {
                    final boolean isChecked = (boolean)msg.obj;
                    //Log.d("weicn", "CB_CMAS_TEST_ALERT_MSG > isChecked = " + isChecked);
                    if (mCmasTestCheckBox != null) {
                        mCmasTestCheckBox.setChecked(isChecked);
                    }
                    break;
                }
                default: {
                    break;
                }
            }
        }
    };
    //bug 903820 end

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //SPRD:Bug#527751
        getActionBar().setDisplayUseLogoEnabled(false);
        getActionBar().setDisplayShowHomeEnabled(false);
        getActionBar().setDisplayOptions(
                ActionBar.DISPLAY_SHOW_TITLE | ActionBar.DISPLAY_HOME_AS_UP);

        UserManager userManager = (UserManager) getSystemService(Context.USER_SERVICE);
        if (userManager.hasUserRestriction(UserManager.DISALLOW_CONFIG_CELL_BROADCASTS)) {
            setContentView(R.layout.cell_broadcast_disallowed_preference_screen);
            return;
        }
        IntentFilter intentFilter = new IntentFilter(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        intentFilter.addAction(TelephonyIntents.ACTION_SERVICE_STATE_CHANGED);   //Bug 1060637
        mContext = getApplicationContext();
        mContext.registerReceiver(sReceiver, intentFilter);
        mTelephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        updateSelectableSubInfos();
        addPreferencesFromResource(R.xml.preferences);
        //add for 616618 start
        if (savedInstanceState != null) {
            mSir = savedInstanceState.getParcelable(SMS_CB_SUBSCRIPTIONINFO_EXTRA);
            Log.d(TAG, "onCreate getting mSir from saved instance state");
        } else {
            mSir = mSelectableSubInfos.size() > 0 ? mSelectableSubInfos.get(0) : null;
        }
        if (mSir != null) {
            mCbcMgr = CellBroadcastConfigManager.getInstance(mSir.getSubscriptionId());
            mLocMgr = CellBroadcastLocationManager.getInstance(this, mSir.getSubscriptionId());
        }
        //bug 903820 begin
        mWorkThread = new HandlerThread("work_thread");
        mWorkThread.start();
        mWorkHandler = new Handler(mWorkThread.getLooper());
        //bug 903820 end
        //add for 616618 end
        updateTabView(savedInstanceState);
        updatePreferences();
        updateEnabledCategory();
    }

    private void updateTabView(Bundle savedInstanceState) {
        if (mSelectableSubInfos.size() > 1) {
            setContentView(com.android.internal.R.layout.common_tab_settings);

            mTabHost = (TabHost) findViewById(android.R.id.tabhost);
            mTabHost.setup();
            mTabHost.setOnTabChangedListener(mTabListener);
            mTabHost.clearAllTabs();

            for (int i = 0; i < mSelectableSubInfos.size(); i++) {
                mTabHost.addTab(buildTabSpec(String.valueOf(i),
                        String.valueOf(mSelectableSubInfos.get(i).getDisplayName())));
                mTabWidget = mTabHost.getTabWidget();
                for (int j = 0; j < mTabWidget.getChildCount(); j++) {
                    TextView tv = (TextView) mTabWidget.getChildAt(j).findViewById(
                            android.R.id.title);
                    tv.setTransformationMethod(null);
                }
            }
            //add for 616618 start
            if (!TextUtils.isEmpty(mTabId)) {
                mTabHost.setCurrentTabByTag(mTabId);
                Log.d(TAG, "onCreate getting mTabId from saved instance state mTabId = " + mTabId);
            }
            //add for 616618 end
        }
    }
    private void updateSelectableSubInfos() {
        mSelectableSubInfos = new ArrayList<SubscriptionInfo>();
        for (int i = 0; i < mTelephonyManager.getSimCount(); i++) {
            final SubscriptionInfo sir =
                    findRecordBySlotId(getApplicationContext(), i);
            if (sir != null) {
                mSelectableSubInfos.add(sir);
            }
        }
    }

    private void setLocationAlertsPreference() {
        switch(mLocMgr.currentLocation()) {
            case LOCATION_PERU:
                break;
            default:
                break;
        }
    }

    private MediaPlayer mMediaPlayer;
    // Volume suggested by media team for in-call alarms.
    private static final float IN_CALL_VOLUME = 0.125f;
    /** Duration of a CMAS alert. */
    private static void setDataSourceFromResource(Resources resources,
            MediaPlayer player, int res) throws java.io.IOException {
        AssetFileDescriptor afd = resources.openRawResourceFd(res);
        if (afd != null) {
            player.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(),
                    afd.getLength());
            afd.close();
        }
    }

    // Do the common stuff when starting the alarm.
    private void startAlarm(MediaPlayer player, boolean looping)
            throws java.io.IOException, IllegalArgumentException, IllegalStateException {
        //Movistar feature start
        player.setAudioStreamType(AudioManager.STREAM_ALARM);
        player.setLooping(false);
        player.prepare();
        player.start();
        Log.d(TAG, " startAlarm looping = " + looping + System.currentTimeMillis());
    }

    private void playAlertTonePreview() {
        Log.d(TAG, "playAlertTonePreview start");
        // future optimization: reuse media player object
        mMediaPlayer = new MediaPlayer();

        try {
            // Check if we are in a call. If we are, play the alert
            // sound at a low volume to not disrupt the call.
            if (mTelephonyManager.getCallState()
                    != TelephonyManager.CALL_STATE_IDLE) {
                //Log.d("in call: reducing volume");
                mMediaPlayer.setVolume(IN_CALL_VOLUME, IN_CALL_VOLUME);
            }

            // start playing alert audio (unless master volume is vibrate only or silent).
            setDataSourceFromResource(getResources(), mMediaPlayer,
                    R.raw.attention_signal);
            startAlarm(mMediaPlayer, false);
            //Movistar feature start
            /**
             if(getResources().getBoolean(R.bool.Movistar_feature_control)){
             mAudioManager.requestAudioFocus(null, AudioManager.STREAM_ALARM,
             AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
             }else{
             mAudioManager.requestAudioFocus(null, AudioManager.STREAM_NOTIFICATION,
             AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
             }
             */
            //Movistar feature end
            // if the duration isn't equal to one play of the full 10.5s file then play
            // with looping enabled.
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private void updatePreferences() {

        PreferenceScreen preferenceScreen = getPreferenceScreen();

        if (preferenceScreen != null) {
            preferenceScreen.removeAll();
            // Load the preferences from an XML resource
            addPreferencesFromResource(R.xml.preferences);

            mExtremeCheckBox = (CheckBoxPreference)
                    findPreference(KEY_ENABLE_CMAS_EXTREME_THREAT_ALERTS);
            mSevereCheckBox = (CheckBoxPreference)
                    findPreference(KEY_ENABLE_CMAS_SEVERE_THREAT_ALERTS);
            mAmberCheckBox = (CheckBoxPreference)
                    findPreference(KEY_ENABLE_CMAS_AMBER_ALERTS);
            mEmergencyCheckBox = (CheckBoxPreference)
                    findPreference(KEY_ENABLE_EMERGENCY_ALERTS);
            mReminderInterval = (ListPreference)
                    findPreference(KEY_ALERT_REMINDER_INTERVAL);
            mVibrateCheckBox = (CheckBoxPreference)
                    findPreference(KEY_ENABLE_ALERT_VIBRATE);
            mSpeechCheckBox = (CheckBoxPreference)
                    findPreference(KEY_ENABLE_ALERT_SPEECH);
            mEtwsTestCheckBox = (CheckBoxPreference)
                    findPreference(KEY_ENABLE_ETWS_TEST_ALERTS);
            mChannel50CheckBox = (CheckBoxPreference)
                    findPreference(KEY_ENABLE_CHANNEL_50_ALERTS);
            mCmasTestCheckBox = (CheckBoxPreference)
                    findPreference(KEY_ENABLE_CMAS_TEST_ALERTS);
            mOptOutCheckBox = (CheckBoxPreference)
                    findPreference(KEY_SHOW_CMAS_OPT_OUT_DIALOG);
            mAlertCategory = (PreferenceCategory)
                    findPreference(KEY_CATEGORY_ALERT_SETTINGS);
            mETWSSettingCategory = (PreferenceCategory)
                    findPreference(KEY_CATEGORY_ETWS_SETTINGS);
            mDubaiSettingsCategory = (PreferenceCategory)
                    findPreference(KEY_CATEGORY_DUBAI_SETTINGS);
            mPeruSettingsCategory = (PreferenceCategory)
                    findPreference(KEY_CATEGORY_PERU_SETTINGS);
            mNewZealandSettingsCategory = (PreferenceCategory)
                    findPreference(KEY_CATEGORY_NZ_SETTINGS);
			mTonePreview = (Preference)findPreference(KEY_ALERT_TONE_PREVIEW);

          //add for MTC setting
            mMtc4370_919CheckBox = (CheckBoxPreference) findPreference(KEY_ENABLE_CHANNEL_4370_919);
            mMtc4383CheckBox = (CheckBoxPreference) findPreference(KEY_ENABLE_CHANNEL_4383);
            mMtc4382CheckBox = (CheckBoxPreference) findPreference(KEY_ENABLE_CHANNEL_4382);
            mMtc4380_519CheckBox = (CheckBoxPreference) findPreference(KEY_ENABLE_CHANNEL_4380_519);
            mMtc4381_519CheckBox = (CheckBoxPreference) findPreference(KEY_ENABLE_CHANNEL_4381_519);
            mMtc4396_4399CheckBox = (CheckBoxPreference) findPreference(KEY_ENABLE_CHANNEL_4396_4399);
            //add for Dubai PWS setting
            mDubaiPws4370_4383CheckBox = (CheckBoxPreference)
                    findPreference(KEY_ENABLE_CHANNEL_4370_4383);
            mAllAlertsCheckBox = (CheckBoxPreference)
                    findPreference(KEY_ENABLE_CHANNEL_ALL_ALERTS);
            mDubaiPws4379_4392CheckBox = (CheckBoxPreference)
                    findPreference(KEY_ENABLE_CHANNEL_4379_4392);
            mDubaiPws4380_4393CheckBox = (CheckBoxPreference)
                    findPreference(KEY_ENABLE_CHANNEL_4380_4393);
            mDubaiPws4396_4397CheckBox = (CheckBoxPreference)
                    findPreference(KEY_ENABLE_CHANNEL_4396_4397);

            //end for Dubai PWS setting
            updateTaiwanPreferences(preferenceScreen);
            // used control different areas
            if (mSir != null) {
                Resources res = getResources();
                enableDubaiPwsSupport = res.getBoolean(R.bool.show_dubai_settings) &&
                        (COUNTRY_DUBAI.equals(mTelephonyManager.getSimCountryIso(mSir.getSubscriptionId()))
                                || COUNTRY_DUBAI.equals(mTelephonyManager.getNetworkCountryIso(mSir.getSubscriptionId())));
                enablePeruPwsSupport = res.getBoolean(R.bool.show_peru_settings) &&
                        (COUNTRY_PERU.equals(mTelephonyManager.getSimCountryIso(mSir.getSubscriptionId()))
                                || COUNTRY_PERU.equals(mTelephonyManager.getNetworkCountryIso(mSir.getSubscriptionId())));
                enableNzPwsSupport = res.getBoolean(R.bool.show_newzealand_settings) &&
                        (COUNTRY_NEWZEALAND.equals(mTelephonyManager.getSimCountryIso(mSir.getSubscriptionId()))
                                || COUNTRY_NEWZEALAND.equals(mTelephonyManager.getNetworkCountryIso(mSir.getSubscriptionId())));
            }

            if(mSir == null) {
                mExtremeCheckBox.setEnabled(false);
                mSevereCheckBox.setEnabled(false);
                mAmberCheckBox.setEnabled(false);
                mEmergencyCheckBox.setEnabled(false);
                mReminderInterval.setEnabled(false);
                mVibrateCheckBox.setEnabled(false);
                mTonePreview.setEnabled(false);
                mSpeechCheckBox.setEnabled(false);
                mEtwsTestCheckBox.setEnabled(false);
                mChannel50CheckBox.setEnabled(false);
                mCmasTestCheckBox.setEnabled(false);
                mOptOutCheckBox.setEnabled(false);
                if (preferenceScreen.findPreference(KEY_CATEGORY_DUBAI_SETTINGS) != null) {
                    preferenceScreen.removePreference(findPreference(KEY_CATEGORY_DUBAI_SETTINGS));
                }
                if (preferenceScreen.findPreference(KEY_CATEGORY_PERU_SETTINGS) != null) {
                    preferenceScreen.removePreference(findPreference(KEY_CATEGORY_PERU_SETTINGS));
                }
                if (mNewZealandSettingsCategory != null) {
                    preferenceScreen.removePreference(mNewZealandSettingsCategory);
                }
                return;
            }

            // Handler for settings that require us to reconfigure enabled channels in radio
            Preference.OnPreferenceChangeListener startConfigServiceListener =
                    new Preference.OnPreferenceChangeListener() {
                        @Override
                        public boolean onPreferenceChange(Preference pref, Object newValue) {
                            int newVal = (((Boolean) newValue).booleanValue()) ? 1 : 0;

                            switch (pref.getKey()) {
                                case KEY_ENABLE_EMERGENCY_ALERTS:
                                    enabledStateChanged(((Boolean) newValue).booleanValue());
                                    SubscriptionManager
                                            .setSubscriptionProperty(mSir.getSubscriptionId(),
                                                    SubscriptionManager.CB_EMERGENCY_ALERT,
                                                    newVal + "");
                                    break;
                                case KEY_ENABLE_CHANNEL_50_ALERTS:
                                    SubscriptionManager
                                            .setSubscriptionProperty(mSir.getSubscriptionId(),
                                                    SubscriptionManager.CB_CHANNEL_50_ALERT,
                                                    newVal + "");
                                    break;
                                case KEY_ENABLE_ETWS_TEST_ALERTS:
                                    SubscriptionManager
                                            .setSubscriptionProperty(mSir.getSubscriptionId(),
                                                    SubscriptionManager.CB_ETWS_TEST_ALERT,
                                                    newVal + "");
                                    break;
                                case KEY_ENABLE_CMAS_EXTREME_THREAT_ALERTS:
                                    SubscriptionManager
                                            .setSubscriptionProperty(mSir.getSubscriptionId(),
                                                    SubscriptionManager.CB_EXTREME_THREAT_ALERT,
                                                    newVal + "");
                                    SubscriptionManager
                                            .setSubscriptionProperty(mSir.getSubscriptionId(),
                                                    SubscriptionManager.CB_SEVERE_THREAT_ALERT,
                                                    "0");

                                    boolean isExtremeAlertChecked =
                                            ((Boolean) newValue).booleanValue();

                                    if (mSevereCheckBox != null) {
                                        mSevereCheckBox.setEnabled(isExtremeAlertChecked);
                                        mSevereCheckBox.setChecked(false);
                                    }
                                    break;
                                case KEY_ENABLE_CMAS_SEVERE_THREAT_ALERTS:
                                    SubscriptionManager
                                            .setSubscriptionProperty(mSir.getSubscriptionId(),
                                                    SubscriptionManager.CB_SEVERE_THREAT_ALERT,
                                                    newVal + "");
                                    break;
                                case KEY_ENABLE_CMAS_AMBER_ALERTS:
                                    SubscriptionManager
                                            .setSubscriptionProperty(mSir.getSubscriptionId(),
                                                    SubscriptionManager.CB_AMBER_ALERT,
                                                    newVal + "");
                                    break;
                                case KEY_ENABLE_CMAS_TEST_ALERTS:
                                    SubscriptionManager
                                            .setSubscriptionProperty(mSir.getSubscriptionId(),
                                                    SubscriptionManager.CB_CMAS_TEST_ALERT,
                                                    newVal + "");
                                    break;

                                default:
                                    Log.d(TAG, "Invalid preference changed");

                            }

                            CellBroadcastReceiver.startConfigService(pref.getContext(),
                                    mSir.getSubscriptionId());
                            return true;
                        }
                    };

            // Show extra settings when developer options is enabled in settings.
            boolean enableDevSettings = Settings.Global.getInt(getContentResolver(),
                    Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) != 0;

            boolean showEtwsSettings = SubscriptionManager.getResourcesForSubId(
                    getApplicationContext(), mSir.getSubscriptionId())
                    .getBoolean(R.bool.show_etws_settings);

            boolean forceDisableEtwsCmasTest =
                    isEtwsCmasTestMessageForcedDisabled(this, mSir.getSubscriptionId());

            boolean emergencyAlertOnOffOptionEnabled =
                    isEmergencyAlertOnOffOptionEnabled(getApplicationContext(), mSir.getSubscriptionId());

            // enable/disable all alerts except CMAS presidential alerts.
            if (mEmergencyCheckBox != null) {
                //bug 903820 begin
                mWorkHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        final boolean isChecked = SubscriptionManager.getBooleanSubscriptionProperty(mSir.getSubscriptionId(),
                                SubscriptionManager.CB_EMERGENCY_ALERT, true, mContext);
                        Message msg = Message.obtain(mHandler, CB_EMERGENCY_ALERT_MSG);
                        msg.obj = isChecked;
                        msg.sendToTarget();
                    }
                }, 1);
                //bug 903820 end
                mEmergencyCheckBox.setOnPreferenceChangeListener(startConfigServiceListener);
            }

            // Show alert settings and ETWS categories for ETWS builds and developer mode.
            if (enableDevSettings || showEtwsSettings) {
                if (forceDisableEtwsCmasTest) {
                    // Remove ETWS test preference.
                    preferenceScreen.removePreference(mETWSSettingCategory);

                    PreferenceCategory devSettingCategory =
                            (PreferenceCategory) findPreference(KEY_CATEGORY_DEV_SETTINGS);

                    // Remove CMAS test preference.
                    if (devSettingCategory != null) {
                        devSettingCategory.removePreference(mCmasTestCheckBox);
                    }
                }
            } else {
                // Remove ETWS test preference category.
                preferenceScreen.removePreference(mETWSSettingCategory);
            }

            if (!SubscriptionManager.getResourcesForSubId(getApplicationContext(),
                    mSir.getSubscriptionId()).getBoolean(R.bool.show_cmas_settings)) {
                // Remove CMAS preference items in emergency alert category.
                mAlertCategory.removePreference(mExtremeCheckBox);
                mAlertCategory.removePreference(mSevereCheckBox);
                mAlertCategory.removePreference(mAmberCheckBox);
            }

            if (enablePeruPwsSupport) {
                if (preferenceScreen.findPreference(KEY_CATEGORY_ALERT_SETTINGS) != null) {
                    preferenceScreen.removePreference(findPreference(KEY_CATEGORY_ALERT_SETTINGS));
                }
                if (preferenceScreen.findPreference(KEY_CATEGORY_DUBAI_SETTINGS) != null) {
                    preferenceScreen.removePreference(findPreference(KEY_CATEGORY_DUBAI_SETTINGS));
                }
                if (preferenceScreen.findPreference(KEY_CATEGORY_ENABLE_CELLBROADCAST) != null) {
                    preferenceScreen.removePreference(findPreference(KEY_CATEGORY_ENABLE_CELLBROADCAST));
                }
                if (preferenceScreen.findPreference(KEY_CATEGORY_ETWS_SETTINGS) != null) {
                    preferenceScreen.removePreference(findPreference(KEY_CATEGORY_ETWS_SETTINGS));
                }
            } else {
                if (preferenceScreen.findPreference(KEY_CATEGORY_PERU_SETTINGS) != null) {
                    preferenceScreen.removePreference(findPreference(KEY_CATEGORY_PERU_SETTINGS));
                }
            }

            if (mNewZealandSettingsCategory != null) {
                if (enableNzPwsSupport) {
                    if (preferenceScreen.findPreference(KEY_CATEGORY_ALERT_SETTINGS) != null) {
                        preferenceScreen.removePreference(findPreference(KEY_CATEGORY_ALERT_SETTINGS));
                    }
                    if (preferenceScreen.findPreference(KEY_CATEGORY_ENABLE_CELLBROADCAST) != null) {
                        preferenceScreen.removePreference(findPreference(KEY_CATEGORY_ENABLE_CELLBROADCAST));
                    }
                    mReminderInterval = (ListPreference) mNewZealandSettingsCategory.findPreference(KEY_ALERT_REMINDER_INTERVAL);
                    CheckBoxPreference nzEnable4370 = (CheckBoxPreference) mNewZealandSettingsCategory.findPreference(KEY_NZ_ENABLE_CHANNEL_4370);
                    if (nzEnable4370 != null) {
                        nzEnable4370.setOnPreferenceChangeListener(
                                new Preference.OnPreferenceChangeListener() {
                                    @Override
                                    public boolean onPreferenceChange(Preference pref, Object newValue) {
                                        final boolean newVal = (((Boolean) newValue).booleanValue());
                                        nzEnable4370.setChecked(newVal);
                                        CellBroadcastReceiver.startConfigService(getPreferenceScreen().getContext(),
                                                mSir.getSubscriptionId());
                                        return true;
                                    }
                                });
                    }

                    CheckBoxPreference nzEnable4371_4372 = (CheckBoxPreference) mNewZealandSettingsCategory.findPreference(KEY_NZ_ENABLE_CHANNEL_4371_4372);
                    if (nzEnable4371_4372 != null) {
                        nzEnable4371_4372.setOnPreferenceChangeListener(
                                new Preference.OnPreferenceChangeListener() {
                                    @Override
                                    public boolean onPreferenceChange(Preference pref, Object newValue) {
                                        final boolean newVal = (((Boolean) newValue).booleanValue());
                                        nzEnable4371_4372.setChecked(newVal);
                                        CellBroadcastReceiver.startConfigService(getPreferenceScreen().getContext(),
                                                mSir.getSubscriptionId());
                                        return true;
                                    }
                                });
                    }

                    CheckBoxPreference nzEnableOtherAlert = (CheckBoxPreference) mNewZealandSettingsCategory.findPreference(KEY_NZ_ENABLE_CHANNEL_4373_4378);
                    if (nzEnableOtherAlert != null) {
                        nzEnableOtherAlert.setOnPreferenceChangeListener(
                                new Preference.OnPreferenceChangeListener() {
                                    @Override
                                    public boolean onPreferenceChange(Preference pref, Object newValue) {
                                        final boolean newVal = (((Boolean) newValue).booleanValue());
                                        nzEnableOtherAlert.setChecked(newVal);
                                        CellBroadcastReceiver.startConfigService(getPreferenceScreen().getContext(),
                                                mSir.getSubscriptionId());
                                        return true;
                                    }
                                });
                    }
                } else {
                    preferenceScreen.removePreference(mNewZealandSettingsCategory);
                }
            }
            // alert reminder interval
            if (mReminderInterval != null) {
                final String queryReturnVal = SubscriptionManager.getIntegerSubscriptionProperty(
                        mSir.getSubscriptionId(), SubscriptionManager.CB_ALERT_REMINDER_INTERVAL,
                        0, this) + "";

                mReminderInterval.setValue(queryReturnVal);
                mReminderInterval.setSummary(mReminderInterval
                        .getEntries()[mReminderInterval.findIndexOfValue(queryReturnVal)]);

                mReminderInterval.setOnPreferenceChangeListener(
                        new Preference.OnPreferenceChangeListener() {
                            @Override
                            public boolean onPreferenceChange(Preference pref, Object newValue) {
                                final ListPreference listPref = (ListPreference) pref;
                                final int idx = listPref.findIndexOfValue((String) newValue);
                                listPref.setSummary(listPref.getEntries()[idx]);
                                SubscriptionManager.setSubscriptionProperty(mSir.getSubscriptionId(),
                                        SubscriptionManager.CB_ALERT_REMINDER_INTERVAL,
                                        String.valueOf((String) newValue));
                                return true;
                            }
                        });
            }
            boolean enableChannel50Support = SubscriptionManager.getResourcesForSubId(
                    getApplicationContext(), mSir.getSubscriptionId()).getBoolean(
                    R.bool.show_brazil_settings) ||
                    COUNTRY_BRAZIL.equals(mTelephonyManager.getSimCountryIso(mSir.getSubscriptionId()));

            if (!enableChannel50Support) {
                preferenceScreen.removePreference(findPreference(KEY_CATEGORY_BRAZIL_SETTINGS));
            }
            if (!enableDevSettings) {
                preferenceScreen.removePreference(findPreference(KEY_CATEGORY_DEV_SETTINGS));
            }
            //add for Dubai PWS setting, bug655113
            if(!enableDubaiPwsSupport) {
                if (preferenceScreen.findPreference(KEY_CATEGORY_DUBAI_SETTINGS) != null) {
                    preferenceScreen.removePreference(findPreference(KEY_CATEGORY_DUBAI_SETTINGS));
                }
            } else {
                if (mEmergencyCheckBox != null) {
                    mEmergencyCheckBox.setEnabled(false);
                }
                if (preferenceScreen.findPreference(KEY_CATEGORY_ALERT_SETTINGS) != null) {
                    preferenceScreen.removePreference(findPreference(KEY_CATEGORY_ALERT_SETTINGS));
                }
                if (preferenceScreen.findPreference(KEY_CATEGORY_ETWS_SETTINGS) != null) {
                    preferenceScreen.removePreference(findPreference(KEY_CATEGORY_ETWS_SETTINGS));
                }
                if (preferenceScreen.findPreference(KEY_CATEGORY_ENABLE_CELLBROADCAST) != null) {
                    preferenceScreen.removePreference(findPreference(KEY_CATEGORY_ENABLE_CELLBROADCAST));
                }
                if (preferenceScreen.findPreference(KEY_CATEGORY_PERU_SETTINGS) != null) {
                    preferenceScreen.removePreference(findPreference(KEY_CATEGORY_PERU_SETTINGS));
                }
                mDubaiSettingsCategory.removePreference(mAllAlertsCheckBox);
                mDubaiSettingsCategory.removePreference(mDubaiPws4370_4383CheckBox);
                mDubaiSettingsCategory.removePreference(mTonePreview);
            }
            if (mDubaiPws4379_4392CheckBox != null) {
                mDubaiPws4379_4392CheckBox.setOnPreferenceChangeListener(
                        new Preference.OnPreferenceChangeListener() {
                            @Override
                            public boolean onPreferenceChange(Preference pref, Object newValue) {
                                final boolean newVal = (((Boolean) newValue).booleanValue());
                                mDubaiPws4379_4392CheckBox.setChecked(newVal);
                                CellBroadcastReceiver.startConfigService(getPreferenceScreen().getContext(),
                                        mSir.getSubscriptionId());
                                return true;
                            }
                        });
            }
            if (mDubaiPws4380_4393CheckBox != null) {
                mDubaiPws4380_4393CheckBox.setOnPreferenceChangeListener(
                        new Preference.OnPreferenceChangeListener() {
                            @Override
                            public boolean onPreferenceChange(Preference pref, Object newValue) {
                                final boolean newVal = (((Boolean) newValue).booleanValue());
                                mDubaiPws4380_4393CheckBox.setChecked(newVal);
                                CellBroadcastReceiver.startConfigService(getPreferenceScreen().getContext(),
                                        mSir.getSubscriptionId());
                                return true;
                            }
                        });
            }
            if (mDubaiPws4396_4397CheckBox != null) {
                mDubaiPws4396_4397CheckBox.setOnPreferenceChangeListener(
                        new Preference.OnPreferenceChangeListener() {
                            @Override
                            public boolean onPreferenceChange(Preference pref, Object newValue) {
                                final boolean newVal = (((Boolean) newValue).booleanValue());
                                mDubaiPws4396_4397CheckBox.setChecked(newVal);
                                CellBroadcastReceiver.startConfigService(getPreferenceScreen().getContext(),
                                        mSir.getSubscriptionId());
                                return true;
                            }
                        });
            }
            //end for Dubai PWS setting
            if (mEmergencyCheckBox != null) {
                boolean isEnable = mEmergencyCheckBox.isChecked();
                    if (preferenceScreen.findPreference(KEY_CATEGORY_BRAZIL_SETTINGS) != null) {
                        preferenceScreen.findPreference(KEY_CATEGORY_BRAZIL_SETTINGS).setEnabled(isEnable);
                    }
                    if (preferenceScreen.findPreference(KEY_CATEGORY_DEV_SETTINGS) != null) {
                        preferenceScreen.findPreference(KEY_CATEGORY_DEV_SETTINGS).setEnabled(isEnable);
                    }
                    if (preferenceScreen.findPreference(KEY_CATEGORY_ALERT_SETTINGS) != null) {
                        preferenceScreen.findPreference(KEY_CATEGORY_ALERT_SETTINGS).setEnabled(isEnable);
                    }
                    if (preferenceScreen.findPreference(KEY_CATEGORY_ETWS_SETTINGS) != null) {
                        preferenceScreen.findPreference(KEY_CATEGORY_ETWS_SETTINGS).setEnabled(isEnable);
                    }
                    if(preferenceScreen.findPreference(KEY_CATEGORY_TAIWAN_SETTINGS) != null){
                        preferenceScreen.findPreference(KEY_CATEGORY_TAIWAN_SETTINGS).setEnabled(isEnable);
                    }
            }
            if (mSpeechCheckBox != null) {
                //bug 903820 begin
                mWorkHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        final boolean isChecked = SubscriptionManager.getBooleanSubscriptionProperty(mSir.getSubscriptionId(),
                                SubscriptionManager.CB_ALERT_SPEECH, true, mContext);
                        Message msg = Message.obtain(mHandler, CB_ALERT_SPEECH_MSG);
                        msg.obj = isChecked;
                        msg.sendToTarget();
                    }
                }, 1);
                //bug 903820 end
                mSpeechCheckBox.setOnPreferenceChangeListener(
                        new Preference.OnPreferenceChangeListener() {
                            @Override
                            public boolean onPreferenceChange(Preference pref, Object newValue) {
                                int newVal = (((Boolean) newValue).booleanValue()) ? 1 : 0;
                                SubscriptionManager.setSubscriptionProperty(
                                        mSir.getSubscriptionId(),
                                        SubscriptionManager.CB_ALERT_SPEECH, newVal + "");
                                return true;
                            }
                        });
            }
            if (mVibrateCheckBox != null) {
                //bug 903820 begin
                mWorkHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        final boolean isChecked = SubscriptionManager.getBooleanSubscriptionProperty(mSir.getSubscriptionId(),
                                SubscriptionManager.CB_ALERT_VIBRATE, true, mContext);
                        Message msg = Message.obtain(mHandler, CB_ALERT_VIBRATE_MSG);
                        msg.obj = isChecked;
                        msg.sendToTarget();
                    }
                }, 1);
                //bug 903820 end
                mVibrateCheckBox.setOnPreferenceChangeListener(
                        new Preference.OnPreferenceChangeListener() {
                            @Override
                            public boolean onPreferenceChange(Preference pref, Object newValue) {
                                int newVal = (((Boolean) newValue).booleanValue()) ? 1 : 0;
                                SubscriptionManager.setSubscriptionProperty(
                                        mSir.getSubscriptionId(),
                                        SubscriptionManager.CB_ALERT_VIBRATE, newVal + "");
                                return true;
                            }
                        });
            }
            if (mTonePreview != null) {

                mTonePreview.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        iCount = 0;

                        pdDialog = new ProgressDialog(CellBroadcastSettings.this);

                        pdDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);

                        pdDialog.setTitle("Alert tone preview");

                        pdDialog.setProgress(0);

                        pdDialog.setMax(100);

                        pdDialog.setIndeterminate(false);

                        pdDialog.setCancelable(false);

                        pdDialog.setCanceledOnTouchOutside(false);

                        pdDialog.setButton(DialogInterface.BUTTON_POSITIVE, "OK", new DialogInterface.OnClickListener() {

                            @Override

                            public void onClick(DialogInterface dialog, int which) {

                                //Toast.makeText(MainActivity.this, "OK", Toast.LENGTH_SHORT).show();
                                if (mMediaPlayer != null) {
                                    try {
                                        mMediaPlayer.stop();
                                        mMediaPlayer.release();
                                    } catch (IllegalStateException e) {
                                        // catch "Unable to retrieve AudioTrack pointer for stop()" exception
                                        e.printStackTrace();
                                    }
                                    mMediaPlayer = null;
                                }
                            }

                        });

                        pdDialog.show();

                        new Thread() {
                            public void run() {
                                try {
                                    playAlertTonePreview();
                                    while (iCount <= 100) {

                                        pdDialog.setProgress(iCount++);
                                        Thread.sleep(110);
                                    }
                                } catch (InterruptedException e) {
                                    pdDialog.cancel();
                                }
                            }

                        }.start();
                        //finish(); 
                        return false;
                    }
                });
            }
            if (mOptOutCheckBox != null) {
                //bug 903820 begin
                mWorkHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        final boolean isChecked = SubscriptionManager.getBooleanSubscriptionProperty(mSir.getSubscriptionId(),
                                SubscriptionManager.CB_OPT_OUT_DIALOG, true, mContext);
                        Message msg = Message.obtain(mHandler, OPT_MSG);
                        msg.obj = isChecked;
                        msg.sendToTarget();
                    }
                }, 1);
                //bug 903820 end
                mOptOutCheckBox.setOnPreferenceChangeListener(
                        new Preference.OnPreferenceChangeListener() {
                            @Override
                            public boolean onPreferenceChange(Preference pref, Object newValue) {
                                int newVal = (((Boolean) newValue).booleanValue()) ? 1 : 0;
                                SubscriptionManager.setSubscriptionProperty(
                                        mSir.getSubscriptionId(),
                                        SubscriptionManager.CB_OPT_OUT_DIALOG, newVal + "");
                                return true;
                            }
                        });
            }

            if (mChannel50CheckBox != null) {
                //bug 903820 begin
                mWorkHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        final boolean isChecked = SubscriptionManager.getBooleanSubscriptionProperty(mSir.getSubscriptionId(),
                                SubscriptionManager.CB_CHANNEL_50_ALERT, true, mContext);
                        Message msg = Message.obtain(mHandler, CB_CHANNEL_50_ALERT_MSG);
                        msg.obj = isChecked;
                        msg.sendToTarget();
                    }
                }, 1);
                //bug 903820 end
                mChannel50CheckBox.setOnPreferenceChangeListener(startConfigServiceListener);
            }
            if (mEtwsTestCheckBox != null) {
                //bug 903820 begin
                if (!forceDisableEtwsCmasTest) {
                    mWorkHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            final boolean isChecked = SubscriptionManager.getBooleanSubscriptionProperty(mSir.getSubscriptionId(),
                                    SubscriptionManager.CB_ETWS_TEST_ALERT, true, mContext);
                            Message msg = Message.obtain(mHandler, CB_ETWS_TEST_ALERT_MSG);
                            msg.obj = isChecked;
                            msg.sendToTarget();
                        }
                    }, 1);
                } else {
                    mEtwsTestCheckBox.setChecked(false);
                }
                //bug 903820 end
                mEtwsTestCheckBox.setOnPreferenceChangeListener(startConfigServiceListener);
            }
            if (mExtremeCheckBox != null) {
                //bug 903820 begin
                mWorkHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        final boolean isChecked = SubscriptionManager.getBooleanSubscriptionProperty(mSir.getSubscriptionId(),
                                SubscriptionManager.CB_EXTREME_THREAT_ALERT, true, mContext);
                        Message msg = Message.obtain(mHandler, CB_EXTREME_THREAT_ALERT_MSG);
                        msg.obj = isChecked;
                        msg.sendToTarget();
                    }
                }, 1);
                //bug 903820 end
                mExtremeCheckBox.setOnPreferenceChangeListener(startConfigServiceListener);
            }
            if (mSevereCheckBox != null) {
                //bug 903820 begin
                mWorkHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        final boolean isChecked = SubscriptionManager.getBooleanSubscriptionProperty(mSir.getSubscriptionId(),
                                SubscriptionManager.CB_SEVERE_THREAT_ALERT, true, mContext);
                        Message msg = Message.obtain(mHandler, CB_SEVERE_THREAT_ALERT_MSG);
                        msg.obj = isChecked;
                        msg.sendToTarget();
                    }
                }, 1);
                //bug 903820 end
                mSevereCheckBox.setOnPreferenceChangeListener(startConfigServiceListener);
                if (mExtremeCheckBox != null) {
                    boolean isExtremeAlertChecked =
                            ((CheckBoxPreference) mExtremeCheckBox).isChecked();
                    mSevereCheckBox.setEnabled(mExtremeCheckBox.isChecked());
                }
            }
            if (mAmberCheckBox != null) {
                //bug 903820 begin
                mWorkHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        final boolean isChecked = SubscriptionManager.getBooleanSubscriptionProperty(mSir.getSubscriptionId(),
                                SubscriptionManager.CB_AMBER_ALERT, true, mContext);
                        Message msg = Message.obtain(mHandler, CB_AMBER_ALERT_MSG);
                        msg.obj = isChecked;
                        msg.sendToTarget();
                    }
                }, 1);
                //bug 903820 end
                mAmberCheckBox.setOnPreferenceChangeListener(startConfigServiceListener);
            }
            if (mCmasTestCheckBox != null) {
                //bug 903820 begin
                if (!forceDisableEtwsCmasTest) {
                    mWorkHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            final boolean isChecked = SubscriptionManager.getBooleanSubscriptionProperty(mSir.getSubscriptionId(),
                                    SubscriptionManager.CB_CMAS_TEST_ALERT, true, mContext);
                            Message msg = Message.obtain(mHandler, CB_CMAS_TEST_ALERT_MSG);
                            msg.obj = isChecked;
                            msg.sendToTarget();
                        }
                    }, 1);
                } else {
                    mCmasTestCheckBox.setChecked(false);
                }
                //bug 903820 end
                mCmasTestCheckBox.setOnPreferenceChangeListener(startConfigServiceListener);
            }

            if (mMtc4370_919CheckBox != null) {
                mMtc4370_919CheckBox.setOnPreferenceChangeListener(
                        new Preference.OnPreferenceChangeListener() {
                            @Override
                            public boolean onPreferenceChange(Preference pref, Object newValue) {
                                final boolean newVal = (((Boolean) newValue).booleanValue());
                                mMtc4370_919CheckBox.setChecked(newVal);
                                CellBroadcastReceiver.startConfigService(pref.getContext(), mSir.getSubscriptionId());
                                return true;
                            }
                        });
            }

            if (mMtc4383CheckBox != null) {
                mMtc4383CheckBox.setOnPreferenceChangeListener(
                        new Preference.OnPreferenceChangeListener() {
                            @Override
                            public boolean onPreferenceChange(Preference pref, Object newValue) {
                                final boolean newVal = (((Boolean) newValue).booleanValue());
                                mMtc4383CheckBox.setChecked(newVal);
                                CellBroadcastReceiver.startConfigService(pref.getContext(), mSir.getSubscriptionId());
                                return true;
                            }
                        });
            }

            if (mMtc4382CheckBox != null) {
                mMtc4382CheckBox.setOnPreferenceChangeListener(
                        new Preference.OnPreferenceChangeListener() {
                            @Override
                            public boolean onPreferenceChange(Preference pref, Object newValue) {
                                final boolean newVal = (((Boolean) newValue).booleanValue());
                                mMtc4382CheckBox.setChecked(newVal);
                                CellBroadcastReceiver.startConfigService(pref.getContext(), mSir.getSubscriptionId());
                                return true;
                            }
                        });
            }

            if (mMtc4380_519CheckBox != null) {
                mMtc4380_519CheckBox.setOnPreferenceChangeListener(
                        new Preference.OnPreferenceChangeListener() {
                            @Override
                            public boolean onPreferenceChange(Preference pref, Object newValue) {
                                final boolean newVal = (((Boolean) newValue).booleanValue());
                                mMtc4380_519CheckBox.setChecked(newVal);
                                CellBroadcastReceiver.startConfigService(pref.getContext(), mSir.getSubscriptionId());
                                return true;
                            }
                        });
            }
            if (mMtc4381_519CheckBox != null) {
                mMtc4381_519CheckBox.setOnPreferenceChangeListener(
                        new Preference.OnPreferenceChangeListener() {
                            @Override
                            public boolean onPreferenceChange(Preference pref, Object newValue) {
                                final boolean newVal = (((Boolean) newValue).booleanValue());
                                mMtc4381_519CheckBox.setChecked(newVal);
                                CellBroadcastReceiver.startConfigService(pref.getContext(), mSir.getSubscriptionId());
                                return true;
                            }
                        });
            }

            if (mMtc4396_4399CheckBox != null) {
                mMtc4396_4399CheckBox.setOnPreferenceChangeListener(
                        new Preference.OnPreferenceChangeListener() {
                            @Override
                            public boolean onPreferenceChange(Preference pref, Object newValue) {
                                final boolean newVal = (((Boolean) newValue).booleanValue());
                                mMtc4396_4399CheckBox.setChecked(newVal);
                                CellBroadcastReceiver.startConfigService(pref.getContext(), mSir.getSubscriptionId());
                                return true;
                            }
                        });
            }
        }
    }

	private void setAlertsSwitchListener() {
        final PreferenceCategory channelSettingsCategory = (PreferenceCategory) findPreference(KEY_CATEGORY_CHANNEL_SETTINGS);
        final int cnt = (channelSettingsCategory != null ? channelSettingsCategory.getPreferenceCount() : 0);
        for (int i = 0; i < cnt ; i++) {
            Preference p = channelSettingsCategory.getPreference(i);
            if (p instanceof AlertCheckBoxPreference) {
                p.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        if (preference instanceof AlertCheckBoxPreference) {
                            AlertCheckBoxPreference cc = (AlertCheckBoxPreference)preference;
                            Log.d(TAG, "channelSwitchChangeListener > channels = " + cc.toString() + ", newValue = " + newValue);
                            mCbcMgr.setGsmCellBroadcastChannelList((boolean) newValue, cc.getChannels());
                        }
                        return true;
                    }
                });
            }
        }
    }

    //add for Taiwan PWS setting, bug1017316
    private void updateTaiwanPreferences(PreferenceScreen preferenceScreen){

        boolean supportTw = getResources().getBoolean(R.bool.show_taiwan_settings) && ("tw".equals(mTelephonyManager.getSimCountryIso()) || "tw".equals(mTelephonyManager.getNetworkCountryIso()));
        if(!supportTw) {
            preferenceScreen.removePreference(findPreference(KEY_CATEGORY_TAIWAN_SETTINGS));
            return;
        }else{
            mAlertCategory.removePreference(findPreference(KEY_ENABLE_CMAS_EXTREME_THREAT_ALERTS));
            mAlertCategory.removePreference(findPreference(KEY_ENABLE_CMAS_SEVERE_THREAT_ALERTS));
            mAlertCategory.removePreference(findPreference(KEY_ENABLE_CMAS_AMBER_ALERTS));
        }
        mTaiwanPws911_919CheckBox = (CheckBoxPreference)
                findPreference(KEY_TAIWAN_ENABLE_CHANNEL_911_919);
        mTaiwanPws4370_4383CheckBox = (CheckBoxPreference)
                findPreference(KEY_TAIWAN_ENABLE_CHANNEL_4370_4383);
        mTaiwanPws4371_4379CheckBox = (CheckBoxPreference)
                findPreference(KEY_TAIWAN_ENABLE_CHANNEL_4371_4379);
        mTaiwanPws4384_4392CheckBox = (CheckBoxPreference)
                findPreference(KEY_TAIWAN_ENABLE_CHANNEL_4384_4392);
        mTaiwanPws4380_4393CheckBox = (CheckBoxPreference)
                findPreference(KEY_TAIWAN_ENABLE_CHANNEL_4380_4393);

        Preference.OnPreferenceChangeListener taiwaiPreferenceChangeListener = new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                CellBroadcastReceiver.startConfigService(getPreferenceScreen().getContext(),
                        mSir.getSubscriptionId());
                return true;
            }
        };

        mTaiwanPws911_919CheckBox.setOnPreferenceChangeListener(taiwaiPreferenceChangeListener);

        mTaiwanPws4370_4383CheckBox.setOnPreferenceChangeListener(taiwaiPreferenceChangeListener);

        mTaiwanPws4371_4379CheckBox.setOnPreferenceChangeListener(taiwaiPreferenceChangeListener);

        mTaiwanPws4384_4392CheckBox.setOnPreferenceChangeListener(taiwaiPreferenceChangeListener);

        mTaiwanPws4380_4393CheckBox.setOnPreferenceChangeListener(taiwaiPreferenceChangeListener);
        //end for Taiwan PWS setting, bug1017316


    }
    //end for Taiwan PWS setting, bug1017316
    // Check if ETWS/CMAS test message is forced disabled on the device.
    public static boolean isEtwsCmasTestMessageForcedDisabled(Context context, int subId) {

        if (context == null) {
            return false;
        }

//        int subId = SubscriptionManager.getDefaultSmsSubscriptionId();
//        if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
//            subId = SubscriptionManager.getDefaultSubscriptionId();
//            if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID)
//                return false;
//        }

        CarrierConfigManager configManager =
                (CarrierConfigManager) context.getSystemService(Context.CARRIER_CONFIG_SERVICE);

        if (configManager != null) {
            PersistableBundle carrierConfig =
                    configManager.getConfigForSubId(subId);

            if (carrierConfig != null) {
                return carrierConfig.getBoolean(
                        CarrierConfigManager.KEY_CARRIER_FORCE_DISABLE_ETWS_CMAS_TEST_BOOL);
            }
        }

        return false;
    }

    private OnTabChangeListener mTabListener = new OnTabChangeListener() {
        @Override
        public void onTabChanged(String tabId) {
            //add for 616618 start
            mTabId = tabId;
            //add for 616618 end
            final int slotId = Integer.parseInt(tabId);
            mSir = mSelectableSubInfos.get(slotId);
            if (mSir != null) {
                mCbcMgr = CellBroadcastConfigManager.getInstance(mSir.getSubscriptionId());
            }
            updatePreferences();

            //final SharedPreferences prefs = getPreferenceScreen().getSharedPreferences();
            //String ringtoneString = getRingtoneUri();
            //updateSoundSummary(prefs, ringtoneString);
            updateEnabledCategory();
        }
    };

    private TabContentFactory mEmptyTabContent = new TabContentFactory() {
        @Override
        public View createTabContent(String tag) {
            return new View(mTabHost.getContext());
        }
    };

    private TabSpec buildTabSpec(String tag, String title) {
        return mTabHost.newTabSpec(tag).setIndicator(title).setContent(
                mEmptyTabContent);

    }

    public SubscriptionInfo findRecordBySlotId(Context context, final int slotId) {
        SubscriptionManager subManager = SubscriptionManager.from(context);
        final List<SubscriptionInfo> subInfoList =
                subManager.getActiveSubscriptionInfoList();
        if (subInfoList != null) {
            final int subInfoLength = subInfoList.size();

            for (int i = 0; i < subInfoLength; ++i) {
                final SubscriptionInfo sir = subInfoList.get(i);
                if (sir.getSimSlotIndex() == slotId) {
                    return sir;
                }
            }
        }

        return null;
    }

    // Check if "Turn on Notifications" option should be always displayed regardless of developer
    // options turned on or not.
    public static boolean isEmergencyAlertOnOffOptionEnabled(Context context, int subId) {


        if (context == null) {
            return false;
        }

        CarrierConfigManager configManager =
                (CarrierConfigManager) context.getSystemService(Context.CARRIER_CONFIG_SERVICE);

        if (configManager != null) {
            PersistableBundle carrierConfig =
                    configManager.getConfigForSubId(subId);

            if (carrierConfig != null) {
                return carrierConfig.getBoolean(
                    CarrierConfigManager.KEY_ALWAYS_SHOW_EMERGENCY_ALERT_ONOFF_BOOL);
            }
        }
        return false;
    }

    private int getSubIdOrPhoneId() {
        if (mSir == null) {
            return -1;
        }
        Log.d(TAG, "---getSubIdOrPhoneId---the subId is:"+mSir.getSubscriptionId()
                +" and slotId is:"+mSir.getSimSlotIndex());
        if (USE_SUBID) {
            return mSir.getSubscriptionId();
        } else {
            return mSir.getSimSlotIndex();
        }
    }
    private void updateEnabledCategory(){
        // modify for bug 914646 start
        if (!OsUtil.hasRequiredPermissions(this)) {
            OsUtil.requestMissingPermission(this);
            return;
        }
        // modify for bug 914646 end
        if (mSir == null) {
            return;
        }
        final boolean enable = SubscriptionManager.getBooleanSubscriptionProperty(
                getSubIdOrPhoneId(), SubscriptionManager.CB_EMERGENCY_ALERT, true, this);
        mEmergencyCheckBox.setChecked(enable);
        enabledStateChanged(enable);
    }

    private void enabledStateChanged(boolean newVal){
        Log.d(TAG, "Enable Cellbroadcast Preference Changed to:"+newVal);

        if (findPreference(KEY_CATEGORY_BRAZIL_SETTINGS) != null) {
            findPreference(KEY_CATEGORY_BRAZIL_SETTINGS).setEnabled(newVal);
        }
        if (findPreference(KEY_CATEGORY_DEV_SETTINGS) != null) {
            findPreference(KEY_CATEGORY_DEV_SETTINGS).setEnabled(newVal);
        }
        if (findPreference(KEY_CATEGORY_ALERT_SETTINGS) != null) {
            findPreference(KEY_CATEGORY_ALERT_SETTINGS).setEnabled(newVal);
        }
        if(findPreference(KEY_CATEGORY_TAIWAN_SETTINGS) != null){
            findPreference(KEY_CATEGORY_TAIWAN_SETTINGS).setEnabled(newVal);
        }
        if (findPreference(KEY_CATEGORY_ETWS_SETTINGS) != null) {
            findPreference(KEY_CATEGORY_ETWS_SETTINGS).setEnabled(newVal);
        }
    }

    public static boolean USE_SUBID             = isUseSubId();//SystemProperties.get("use_subid", true);

    private static boolean isUseSubId(){
        if (SystemProperties.get("ro.cb_config") == null || SystemProperties.get("ro.cb_config").length()<1) {
            return true;//true
        } else {
            return (Integer.parseInt(SystemProperties.get("ro.cb_config")) & 0x01)==0;
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        super.onOptionsItemSelected(item);
        switch (item.getItemId()) {
        //SPRD:Bug#527751
        case android.R.id.home:
            finish();
            break;
        }
        return false;
    }

    //add for 616618 start
    /**
     * Save the list of mSir so the state can be restored later.
     * @param outState Bundle in which to place the saved state.
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(SMS_CB_SUBSCRIPTIONINFO_EXTRA, mSir);
        outState.putString(TAB_TAG_ID_EXTRA, mTabId);
        Log.d(TAG, "onSaveInstanceState saved mSir to bundle");
    }
    //add for 616618 end

    @Override
    protected void onRestoreInstanceState(Bundle state) {
        super.onRestoreInstanceState(state);
        if(null!=state){
            mSir = state.getParcelable(SMS_CB_SUBSCRIPTIONINFO_EXTRA);
            mTabId = state.getString(TAB_TAG_ID_EXTRA);
        }
    }

    //add for bug 676251 start
    private final BroadcastReceiver sReceiver = new  BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            String simStatus = intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE);
            Log.d(TAG, "onReceive :" + action + " simstatus :" + simStatus);
            if (action.equals(TelephonyIntents.ACTION_SIM_STATE_CHANGED)) {
                //add for bug 1068038 1068054 start
                if (IccCardConstants.INTENT_VALUE_ICC_ABSENT.equals(simStatus)) {
                    if(mSelectableSubInfos.size()>1){
                        finish();
                    }
                    if(mTelephonyManager.getSimState()==TelephonyManager.SIM_STATE_ABSENT && mSelectableSubInfos.size() ==1){
                        finish();
                    }
                }else if(IccCardConstants.INTENT_VALUE_ICC_LOADED.equals(simStatus)){
                    Log.d(TAG,"start update");
                    updateSelectableSubInfos();
                    updateTabView(null);
                    mSir = mSelectableSubInfos.size() > 0 ? mSelectableSubInfos.get(0) : null;
                    updatePreferences();
                }
                //add for bug 1068038 1068054 end
            } else if (action.equals(TelephonyIntents.ACTION_SERVICE_STATE_CHANGED)) {  //Bug 1060637 begin
                final int subId = intent.getExtras().getInt(PhoneConstants.SUBSCRIPTION_KEY);
                if (!SubscriptionManager.isValidSubscriptionId(subId)) {
                    return;
                }
                ServiceState serviceState = ServiceState.newFromBundle(intent.getExtras());
                if (serviceState != null) {
                    final int newState = serviceState.getState();
                    Log.d(TAG, "Service state changed! " + newState + " Full: " + serviceState);
                    if ((newState == ServiceState.STATE_IN_SERVICE) && UserManager.get(context).isSystemUser()) {
                        final int slotId = SubscriptionManager.getSlotIndex(subId);
                        final int simCount = mSelectableSubInfos.size();
                        Log.d(TAG, "subId = " + subId + ", slotId: " + slotId + ", simCount: " + simCount);
                        if (simCount > 1) {
                            final SubscriptionManager subscriptionManager = SubscriptionManager.from(getApplicationContext());
                            if (null != subscriptionManager) {
                                final String displayName =  String.valueOf(subscriptionManager.getActiveSubscriptionInfo(subId).getDisplayName());
                                if (mTabWidget == null || slotId >= mTabWidget.getChildCount()) {
                                    return;
                                }
                                TextView tv = (TextView) mTabWidget.getChildAt(slotId).findViewById(
                                        android.R.id.title);
                                tv.setText(displayName);
                            }
                        }
                    }
                }
            }
            //Bug 1060637 end
        }
    };

    @Override
    public void onStart() {
        super.onStart();

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mContext.unregisterReceiver(sReceiver);
    }
    //add for bug 676251 end
}

/**
 * Hacky way to call the hidden SystemProperties class API
 */
class SystemProperties {
    private static Method sSystemPropertiesGetMethod = null;

    public static String get(final String name) {
        if (sSystemPropertiesGetMethod == null) {
            try {
                final Class systemPropertiesClass = Class.forName("android.os.SystemProperties");
                if (systemPropertiesClass != null) {
                    sSystemPropertiesGetMethod =
                            systemPropertiesClass.getMethod("get", String.class);
                }
            } catch (final ClassNotFoundException e) {
                // Nothing to do
            } catch (final NoSuchMethodException e) {
                // Nothing to do
            }
        }
        if (sSystemPropertiesGetMethod != null) {
            try {
                return (String) sSystemPropertiesGetMethod.invoke(null, name);
            } catch (final IllegalArgumentException e) {
                // Nothing to do
            } catch (final IllegalAccessException e) {
                // Nothing to do
            } catch (final InvocationTargetException e) {
                // Nothing to do
            }
        }
        return null;
    }
}
