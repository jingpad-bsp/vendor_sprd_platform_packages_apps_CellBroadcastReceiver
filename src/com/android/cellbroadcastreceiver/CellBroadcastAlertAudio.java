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

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.AudioSystem;
import android.media.MediaPlayer;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.media.SoundPool;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.util.Locale;
import java.util.MissingResourceException;
import android.app.NotificationManager;
import android.provider.Settings.Global;

import static com.android.cellbroadcastreceiver.CellBroadcastReceiver.DBG;

/**
 * Manages alert audio and vibration and text-to-speech. Runs as a service so that
 * it can continue to play if another activity overrides the CellBroadcastListActivity.
 */
public class CellBroadcastAlertAudio extends Service implements TextToSpeech.OnInitListener,
        TextToSpeech.OnUtteranceCompletedListener {
    private static final String TAG = "CellBroadcastAlertAudio";

    /** Action to start playing alert audio/vibration/speech. */
    static final String ACTION_START_ALERT_AUDIO = "ACTION_START_ALERT_AUDIO";

    /** Extra for alert audio duration (from settings). */
    public static final String ALERT_AUDIO_DURATION_EXTRA =
            "com.android.cellbroadcastreceiver.ALERT_AUDIO_DURATION";
    //Movistar feature start
    public static final String MESSAGE_ID="com.android.cellbroadcastreceiver.MESSAGE_ID";
    //Movistar feature end
    /** Extra for message body to speak (if speech enabled in settings). */
    public static final String ALERT_AUDIO_MESSAGE_BODY =
            "com.android.cellbroadcastreceiver.ALERT_AUDIO_MESSAGE_BODY";

    /** Extra for text-to-speech preferred language (if speech enabled in settings). */
    public static final String ALERT_AUDIO_MESSAGE_PREFERRED_LANGUAGE =
            "com.android.cellbroadcastreceiver.ALERT_AUDIO_MESSAGE_PREFERRED_LANGUAGE";

    /** Extra for text-to-speech default language when preferred language is
        not available (if speech enabled in settings). */
    public static final String ALERT_AUDIO_MESSAGE_DEFAULT_LANGUAGE =
            "com.android.cellbroadcastreceiver.ALERT_AUDIO_MESSAGE_DEFAULT_LANGUAGE";

    /** Extra for alert audio vibration enabled (from settings). */
    public static final String ALERT_AUDIO_VIBRATE_EXTRA =
            "com.android.cellbroadcastreceiver.ALERT_AUDIO_VIBRATE";

    /** Extra for alert audio ETWS behavior (always vibrate, even in silent mode). */
    public static final String ALERT_AUDIO_ETWS_VIBRATE_EXTRA =
            "com.android.cellbroadcastreceiver.ALERT_AUDIO_ETWS_VIBRATE";

    private static final String TTS_UTTERANCE_ID = "com.android.cellbroadcastreceiver.UTTERANCE_ID";

    /** Pause duration between alert sound and alert speech. */
    private static final int PAUSE_DURATION_BEFORE_SPEAKING_MSEC = 1000;

    /** Duration of a CMAS alert. */
    private static final int CMAS_DURATION_MSEC = 5500;

    /** Vibration uses the same on/off pattern as the CMAS alert tone */
    private static final long[] sVibratePattern = {0, 2000, 500, 1000, 500, 1000, 560};

    private static final int STATE_IDLE = 0;
    private static final int STATE_ALERTING = 1;
    private static final int STATE_PAUSING = 2;
    private static final int STATE_SPEAKING = 3;

    private int mState = STATE_IDLE;

    private TextToSpeech mTts;
    private boolean mTtsEngineReady;

    private String mMessageBody;
    private String mMessagePreferredLanguage;
    private String mMessageDefaultLanguage;
    private boolean mTtsLanguageSupported;
    private boolean mEnableVibrate;
    private boolean mEnableAudio;
    private int mMcc;

    private Vibrator mVibrator;
    private AudioManager mAudioManager;
    private TelephonyManager mTelephonyManager;
    private int mInitialCallState;

    //Movistar feature start
    private static int messageIda;
    private static int messageIdb;
    boolean hasChangedZenMode = false;
    //Movistar feature end
    private static final String PREFERENCE_NAME="custom_config";
    private boolean mEnableTts;
    private boolean mEnableVsOnSilent;
    private boolean mEnableVsOnDndSilent;
    private int mCurrentRingerMode = 0;
    private int mCurrentVolume = 0;
    private int mAudioStreamType = -1;
    private int mCurrentForceUse = -1;

    private static final int SOUNDPOOL_STREAMS = 4;
    private static final int DEFAULT_INVALID_ID = -1;
    private static final int PRIORITY = 16;
    private static final int SOUND_TIMESPAN = 5500;
    private static final float MAX_VOLUME = 1.0f;
    private SoundPool mSoundPool;
    private int mSoundId = -1;
    private int mStreamID = -1;
    private boolean mForceStop = false;
    private float mPlaySpeedRate = 1.0f;
    private int mDuration;

    // Internal messages
    private static final int ALERT_SOUND_FINISHED = 1000;
    private static final int ALERT_PAUSE_FINISHED = 1001;
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case ALERT_SOUND_FINISHED:
                    if (DBG) log("ALERT_SOUND_FINISHED");
                    stop();     // stop alert sound
                    // if we can speak the message text
                    if (mMessageBody != null && mTtsEngineReady && mTtsLanguageSupported) {
                        if(!mEnableTts){
                            if (DBG) log("ALERT_SOUND_FINISHED movistar feature does not speak!!! ");
                        }else{
                        mHandler.sendMessageDelayed(mHandler.obtainMessage(ALERT_PAUSE_FINISHED),
                                PAUSE_DURATION_BEFORE_SPEAKING_MSEC);
                        }
                        mState = STATE_PAUSING;
                    } else {
                        if (DBG) log("MessageEmpty = " + (mMessageBody == null) +
                                ", mTtsEngineReady = " + mTtsEngineReady +
                                ", mTtsLanguageSupported = " + mTtsLanguageSupported);
                        stopSelf();
                        mState = STATE_IDLE;
                    }
                    break;

                case ALERT_PAUSE_FINISHED:
                    if (DBG) log("ALERT_PAUSE_FINISHED");
                    int res = TextToSpeech.ERROR;
                    if (mMessageBody != null && mTtsEngineReady && mTtsLanguageSupported) {
                        if (DBG) log("Speaking broadcast text: " + mMessageBody);

                        Bundle params = new Bundle();
                        // Play TTS in notification stream.
                        params.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM,
                                AudioManager.STREAM_NOTIFICATION);
                        // Use the non-public parameter 2 --> TextToSpeech.QUEUE_DESTROY for TTS.
                        // The entire playback queue is purged. This is different from QUEUE_FLUSH
                        // in that all entries are purged, not just entries from a given caller.
                        // This is for emergency so we want to kill all other TTS sessions.
                        res = mTts.speak(mMessageBody, 2, params, TTS_UTTERANCE_ID);
                        mState = STATE_SPEAKING;
                    }
                    if (res != TextToSpeech.SUCCESS) {
                        loge("TTS engine not ready or language not supported or speak() failed");
                        stopSelf();
                        mState = STATE_IDLE;
                    }
                    break;

                default:
                    loge("Handler received unknown message, what=" + msg.what);
            }
        }
    };

    private final PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
        @Override
        public void onCallStateChanged(int state, String ignored) {
            // Stop the alert sound and speech if the call state changes.
            if (state != TelephonyManager.CALL_STATE_IDLE
                    && state != mInitialCallState) {
                stopSelf();
            }
        }
    };

    /**
     * Callback from TTS engine after initialization.
     * @param status {@link TextToSpeech#SUCCESS} or {@link TextToSpeech#ERROR}.
     */
    @Override
    public void onInit(int status) {
        if (DBG) log("onInit() TTS engine status: " + status);
        if (status == TextToSpeech.SUCCESS) {
            mTtsEngineReady = true;
            mTts.setOnUtteranceCompletedListener(this);
            // try to set the TTS language to match the broadcast
            setTtsLanguage();
        } else {
            mTtsEngineReady = false;
            mTts = null;
            loge("onInit() TTS engine error: " + status);
        }
    }

    /**
     * Try to set the TTS engine language to the preferred language. If failed, set
     * it to the default language. mTtsLanguageSupported will be updated based on the response.
     */
    private void setTtsLanguage() {
        String language = mMessagePreferredLanguage;
        if (language == null || language.isEmpty() ||
                TextToSpeech.LANG_AVAILABLE != mTts.isLanguageAvailable(new Locale(language))) {
            language = mMessageDefaultLanguage;
            if (language == null || language.isEmpty() ||
                    TextToSpeech.LANG_AVAILABLE != mTts.isLanguageAvailable(new Locale(language))) {
                mTtsLanguageSupported = false;
                return;
            }
            if (DBG) log("Language '" + mMessagePreferredLanguage + "' is not available, using" +
                    "the default language '" + mMessageDefaultLanguage + "'");
        }

        if (DBG) log("Setting TTS language to '" + language + '\'');

        try {
            int result = mTts.setLanguage(new Locale(language));
            if (DBG) log("TTS setLanguage() returned: " + result);
            mTtsLanguageSupported = (result == TextToSpeech.LANG_AVAILABLE);
        }
        catch (MissingResourceException e) {
            mTtsLanguageSupported = false;
            loge("Language '" + language + "' is not available.");
        }
    }

    /**
     * Callback from TTS engine.
     * @param utteranceId the identifier of the utterance.
     */
    @Override
    public void onUtteranceCompleted(String utteranceId) {
        if (utteranceId.equals(TTS_UTTERANCE_ID)) {
            // When we reach here, it could be TTS completed or TTS was cut due to another
            // new alert started playing. We don't want to stop the service in the later case.
            if (mState == STATE_SPEAKING) {
                stopSelf();
            }
        }
    }

    @Override
    public void onCreate() {
        Log.d(TAG,"onCreate");
        mVibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        // Listen for incoming calls to kill the alarm.
        mTelephonyManager =
                (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        mTelephonyManager.listen(
                mPhoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
        initCustomParms();
        initCurrentRingerMode();
    }

    private void initCustomParms() {
        SharedPreferences sp =this.getSharedPreferences(PREFERENCE_NAME, MODE_PRIVATE);
        mEnableTts = sp.getBoolean("mEnableTts",false);
        mEnableVsOnSilent= sp.getBoolean("mEnableVsOnSilent", false);
        mEnableVsOnDndSilent = sp.getBoolean("mEnableVsOnDndSilent",false);
        mMcc = sp.getInt("mMcc", 001);
        if (DBG){
            Log.d(TAG, "initCustomParms  : mEnableTts = " + mEnableTts + " mEnableVsOnSilent = " + mEnableVsOnSilent +
                    " mEnableVsOnDndSilent = " + mEnableVsOnDndSilent + " mMcc = " + mMcc);
        }
    }

    private void initCurrentRingerMode(){
        mCurrentRingerMode = mAudioManager.getRingerModeInternal();
    }

    @Override
    public void onDestroy() {
        // stop audio, vibration and TTS
        Log.d(TAG," onDestroy");
        mForceStop = true;
        stop();
        // Stop listening for incoming calls.
        mTelephonyManager.listen(mPhoneStateListener, 0);
        // shutdown TTS engine
        if (mTts != null) {
            try {
                mTts.shutdown();
            } catch (IllegalStateException e) {
                // catch "Unable to retrieve AudioTrack pointer for stop()" exception
                loge("exception trying to shutdown text-to-speech");
            }
        }
        if (mEnableAudio) {
            // Release the audio focus so other audio (e.g. music) can resume.
            // Do not do this in stop() because stop() is also called when we stop the tone (before
            // TTS is playing). We only want to release the focus when tone and TTS are played.
            mAudioManager.abandonAudioFocus(null);
        }
        // release the screen bright wakelock acquired by CellBroadcastAlertService
//        CellBroadcastAlertWakeLock.releaseCpuLock();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // No intent, tell the system not to restart us.
        if (intent == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        // This extra should always be provided by CellBroadcastAlertService,
        // but default to 10.5 seconds just to be safe (CMAS requirement).
        int duration = intent.getIntExtra(ALERT_AUDIO_DURATION_EXTRA, CMAS_DURATION_MSEC);
        Log.d(TAG, "onStartCommand: duration = " + duration);

        int messageId = intent.getIntExtra(MESSAGE_ID, 0);
        Log.d(TAG, "onStartCommand: messageId = " + messageId);
        if (DBG) log("Duration: " + duration);
        // Get text to speak (if enabled by user)
        mMessageBody = intent.getStringExtra(ALERT_AUDIO_MESSAGE_BODY);
        mMessagePreferredLanguage = intent.getStringExtra(ALERT_AUDIO_MESSAGE_PREFERRED_LANGUAGE);
        mMessageDefaultLanguage = intent.getStringExtra(ALERT_AUDIO_MESSAGE_DEFAULT_LANGUAGE);

        mEnableVibrate = intent.getBooleanExtra(ALERT_AUDIO_VIBRATE_EXTRA, true);
        if (intent.getBooleanExtra(ALERT_AUDIO_ETWS_VIBRATE_EXTRA, false)) {
            mEnableVibrate = true;  // force enable vibration for ETWS alerts
        }
        resetEnableVibrate();
        if(mEnableVsOnDndSilent){
            Log.d(TAG, "onStartCommand: mEnableVsOnDndSilent true");
            mEnableAudio = true;
            mEnableVibrate = true;
        }

        if (mMessageBody != null && mEnableAudio) {
            if (mTts == null) {
                mTts = new TextToSpeech(this, this);
            } else if (mTtsEngineReady) {
                setTtsLanguage();
            }
        }
        if (mEnableAudio || mEnableVibrate) {
            play(duration, messageId);     // in milliseconds
        } else {
            stopSelf();
            return START_NOT_STICKY;
        }

        // Record the initial call state here so that the new alarm has the
        // newest state.
        mInitialCallState = mTelephonyManager.getCallState();

        return START_STICKY;
    }

    private void resetEnableVibrate() {
        switch (mCurrentRingerMode) {
            case AudioManager.RINGER_MODE_SILENT:
                Log.d(TAG,"Ringer mode: silent");
                mEnableAudio = false;
                mEnableVibrate = false;
                break;

            case AudioManager.RINGER_MODE_VIBRATE:
                Log.d(TAG,"Ringer mode: vibrate");
                mEnableAudio = false;
                break;
            case AudioManager.RINGER_MODE_NORMAL:
            default:
                Log.d(TAG,"Ringer mode: normal");
                mEnableAudio = true;
                break;
        }
    }

    // Volume suggested by media team for in-call alarms.
    private static final float IN_CALL_VOLUME = 0.125f;

    /**
     * Start playing the alert sound, and send delayed message when it's time to stop.
     * @param duration the alert sound duration in milliseconds
     */
    private void play(int duration, int messageId) {
        // stop() checks to see if we are already playing.
        stop();

        boolean mSmsTone = false;
        if (mMcc == 424 && (messageId == 4380 || messageId == 4393 || messageId == 4396
                || messageId == 4397 || messageId == 4398 || messageId == 4399)) {
            mSmsTone = true;
        }

        Log.d(TAG,"play()  mCurrentRingerMode :" + mAudioManager.getRingerModeInternal());
        if (OsUtil.isAtAndroidO() || OsUtil.isAtAndroidO_MR1()) {
            adjusZenMode();
        }
        if (mEnableAudio) {
            mDuration = duration;
            if(mEnableVsOnDndSilent){
                mAudioStreamType = AudioManager.STREAM_SYSTEM_ENFORCED;
            }
            Log.d(TAG,"mState " + mState + " mCurrentVolume :" +mCurrentVolume + " mAudioStreamType : " + mAudioStreamType);
            if (OsUtil.isAtLeastQ() && mState != STATE_ALERTING && mAudioStreamType != -1) {
                mCurrentVolume = mAudioManager.getStreamVolume(mAudioStreamType);
                final int maxVolume = mAudioManager.getStreamMaxVolume(mAudioStreamType);
                mAudioManager.setStreamVolume(mAudioStreamType, maxVolume, 0);
                mAudioManager.setRingerModeInternal(AudioManager.RINGER_MODE_NORMAL);
                Log.d(TAG," mAudioManager.setStreamVolume to maxVolume :" + maxVolume  +" mCurrentRingerMode : " + mAudioManager.getRingerModeInternal());
            }
            createSoundPoolIfNeeded();
            // mSoundId is invalid ,load from res raw for once before mSoundPool is released
            if (mSoundId == DEFAULT_INVALID_ID) {
                if (mSmsTone) {
                    mSoundId = mSoundPool.load(getApplicationContext(), R.raw.info, 1); // load Sms audio raw resource
                } else {
                    mSoundId = mSoundPool.load(getApplicationContext(), R.raw.attention_signal_5d5s, 1); // load Alert audio raw resource
                }
            } else {
                // reuse the loaded res
                if (mStreamID != DEFAULT_INVALID_ID) {
                    mSoundPool.resume(mStreamID);
                }
            }

            try {
                if(OsUtil.isAtLeastQ() && mEnableVsOnSilent) {
                    mCurrentForceUse = AudioSystem.getForceUse(AudioSystem.FOR_SYSTEM);
                    Log.d(TAG," mCurrentForceUse :" +mCurrentForceUse);
                    AudioSystem.setForceUse(AudioSystem.FOR_SYSTEM, AudioSystem.FORCE_SYSTEM_ENFORCED);
                }
            } catch (Exception ex) {
                loge("Failed to play alert sound: " + ex);
            }
        } else {  //if don't play notification sound, do vibroator only
            doVibrator();
            mState = STATE_ALERTING;
            forceDelayStop(duration);
        }
    }

    private static void setDataSourceFromResource(Resources resources,
            MediaPlayer player, int res) throws java.io.IOException {
        AssetFileDescriptor afd = resources.openRawResourceFd(res);
        if (afd != null) {
            player.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(),
                    afd.getLength());
            afd.close();
        }
    }

    private void playAlertReminderSound() {
        Uri notificationUri = RingtoneManager.getDefaultUri(
                RingtoneManager.TYPE_NOTIFICATION | RingtoneManager.TYPE_ALARM);
        if (notificationUri == null) {
            loge("Can't get URI for alert reminder sound");
            return;
        }
        Ringtone r = RingtoneManager.getRingtone(this, notificationUri);
        if (r != null) {
            log("playing alert reminder sound");
            r.play();
        } else {
            loge("can't get Ringtone for alert reminder sound");
        }
    }

    /**
     * Stops alert audio and speech.
     */
    public void stop() {
        if (DBG) Log.d(TAG,"stop()" + " mCurrentForceUse :"+mCurrentForceUse + " mState :" +mState);
        if(OsUtil.isAtLeastQ() && mEnableVsOnSilent && mCurrentForceUse != -1) {
            AudioSystem.setForceUse(AudioSystem.FOR_SYSTEM, mCurrentForceUse);
            mCurrentForceUse = -1;
        }
        mHandler.removeMessages(ALERT_SOUND_FINISHED);
        mHandler.removeMessages(ALERT_PAUSE_FINISHED);
        resumeZenMode();
        if (mState == STATE_ALERTING) {
            // Stop audio playing
            if (mSoundPool != null) {
                mSoundPool.stop(mStreamID);
                mStreamID = DEFAULT_INVALID_ID;
                mSoundId = DEFAULT_INVALID_ID;
            }
            if (OsUtil.isAtLeastQ() && mEnableAudio && mAudioManager != null  && mAudioStreamType != -1) {
                mAudioManager.setStreamVolume(mAudioStreamType, mCurrentVolume, 0);
                mAudioManager.setRingerModeInternal(mCurrentRingerMode);
                Log.d(TAG," mAudioManager.setRingerModeInternal :" + mCurrentRingerMode );
                mAudioStreamType = -1;
            }
            // Stop vibrator
            mVibrator.cancel();
        } else if (mState == STATE_SPEAKING && mTts != null) {
            try {
                mTts.stop();
            } catch (IllegalStateException e) {
                // catch "Unable to retrieve AudioTrack pointer for stop()" exception
                loge("exception trying to stop text-to-speech");
            }
        }
        mState = STATE_IDLE;
    }

    private void createSoundPoolIfNeeded() {
        if (mSoundPool == null) {
            int streamType = AudioManager.STREAM_RING;
            if (mEnableVsOnDndSilent) {
                streamType = AudioManager.STREAM_SYSTEM_ENFORCED;
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                AudioAttributes.Builder audioAttributesBuilder = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC);
                audioAttributesBuilder.setLegacyStreamType(streamType);
                AudioAttributes audioAttributes = audioAttributesBuilder.build();
                mSoundPool = new SoundPool.Builder()
                        .setMaxStreams(SOUNDPOOL_STREAMS)
                        .setAudioAttributes(audioAttributes)
                        .build();
            } else {
                mSoundPool = new SoundPool(SOUNDPOOL_STREAMS, streamType, 0);  // create SoundPool
            }

            mSoundPool.setOnLoadCompleteListener(loadCompleteListener);
        }
    }

    SoundPool.OnLoadCompleteListener loadCompleteListener = new SoundPool.OnLoadCompleteListener() {
        @Override
        public void onLoadComplete(SoundPool soundPool, int sampleId, int status) {
            if (mForceStop) {
                mForceStop = false;
                return;
            }
            if (mSoundPool != null) {
                // Check if we are in a call. If we are, play the alert
                // sound at a low volume to not disrupt the call.
                float volume = MAX_VOLUME;
                if (mTelephonyManager.getCallState()
                        != TelephonyManager.CALL_STATE_IDLE) {
                    log("in call: reducing volume");
                    volume = IN_CALL_VOLUME;
                }
                if (mStreamID == DEFAULT_INVALID_ID) {
                    final int cnt = (int) (10 * mDuration / SOUND_TIMESPAN);
                    mStreamID = mSoundPool.play(mSoundId, volume, volume, PRIORITY, (cnt % 10 == 0 ? cnt / 10 : (int) cnt / 10 + 1) - 1, mPlaySpeedRate);
                    doVibrator();
                    mState = STATE_ALERTING;
                    forceDelayStop(mDuration);
                }
            }
        }
    };

    private void doVibrator() {
        if (mEnableVibrate) {
            if(mDuration != CMAS_DURATION_MSEC){
                mVibrator.vibrate(sVibratePattern, 0, new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .build());
            }else{
                mVibrator.vibrate(sVibratePattern, -1, new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .build());
            }
        }
    }

    private void forceDelayStop(final int duration) {
        // stop alert after the specified duration, unless we are playing the full 10.5s file once
        // in which case we'll use the end of playback callback rather than a delayed message.
        // This is to avoid the CMAS alert potentially being truncated due to audio playback lag.
        if (duration != CMAS_DURATION_MSEC) {
            mHandler.sendMessageDelayed(mHandler.obtainMessage(ALERT_SOUND_FINISHED), duration);
        }
    }

    //Bug 1082990 begin
    private int mCurrentZenMode = 0;
    void adjusZenMode() {
        mCurrentZenMode = getZenMode();
        Log.d(TAG,"adjusZenMode mCurrentZenMode = " + mCurrentZenMode);
        if(mCurrentZenMode != Global.ZEN_MODE_NO_INTERRUPTIONS) {
            NotificationManager.from(this).setZenMode(Global.ZEN_MODE_NO_INTERRUPTIONS, null, TAG);
            hasChangedZenMode = true;
        }
    }

    void resumeZenMode() {
        if(hasChangedZenMode) {
            hasChangedZenMode = false;
            Log.d(TAG,"resumeZenMode mCurrentZenMode = " + mCurrentZenMode);
            NotificationManager.from(this).setZenMode(mCurrentZenMode, null, TAG);
        }
    }

    protected int getZenMode() {
        return Global.getInt(this.getContentResolver(), Global.ZEN_MODE, Global.ZEN_MODE_OFF);
    }
    //Bug 1082990 end

    private static void log(String msg) {
        Log.d(TAG, msg);
    }

    private static void loge(String msg) {
        Log.e(TAG, msg);
    }
}
