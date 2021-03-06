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

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import java.lang.ref.WeakReference;

/**
 * Container activity for CMAS opt-in/opt-out alert dialog.
 */
public class CellBroadcastOptOutActivity extends Activity {
    private static final String TAG = "CellBroadcastOptOutActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "created activity");
        showOptOutDialog(new WeakReference<>(this));
    }

    /**
     * Show the opt-out dialog. Uses the CellBroadcastAlertDialog activity unless the device is
     * in restricted keyguard mode, in which case we create a new CellBroadcastOptOutActivity
     * so that the dialog appears underneath the lock screen. The user must unlock the device
     * to configure the settings, so we don't want to show the opt-in dialog before then.
     */
    //modify for activity leak
    static void showOptOutDialog(WeakReference<Activity> reference) {
        Activity activity = reference.get();
        if(null ==activity)
            return;
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setMessage(R.string.cmas_opt_out_dialog_text)
                .setPositiveButton(R.string.cmas_opt_out_button_yes,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                Log.d(TAG, "User clicked Yes");
                                //modify for 1071746 , the message should be removed when activity was finished.
                                if(activity instanceof CellBroadcastAlertFullScreen){
                                    ((CellBroadcastAlertFullScreen) activity).removeLatestMessage();
                                }
                                activity.finish();
                            }
                        })
                .setNegativeButton(R.string.cmas_opt_out_button_no,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                Log.d(TAG, "User clicked No");
                                Intent intent = new Intent(activity, CellBroadcastSettings.class);
                                activity.startActivity(intent);
                                if(activity instanceof CellBroadcastAlertFullScreen){
                                    ((CellBroadcastAlertFullScreen) activity).removeLatestMessage();
                                }
                                activity.finish();
                            }
                        })
                .setOnCancelListener(
                        new DialogInterface.OnCancelListener() {
                            @Override
                            public void onCancel(DialogInterface dialog) {
                                Log.d(TAG, "User cancelled");
                                // If the user presses the back button, make sure we dismiss the
                                // emergency alert's dialog as well.
                                if(activity instanceof CellBroadcastAlertFullScreen){
                                    ((CellBroadcastAlertFullScreen) activity).removeLatestMessage();
                                }
                                activity.finish();
                            }
                        })
                .create().show();
    }
}
