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

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Bundle;
import android.telephony.CellBroadcastMessage;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;

/**
 * Custom alert dialog with optional flashing warning icon.
 * Alert audio and text-to-speech handled by {@link CellBroadcastAlertAudio}.
 * Keyguard handling based on {@code AlarmAlert} class from DeskClock app.
 */
public class CellBroadcastAlertDialog extends CellBroadcastAlertFullScreen {

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        ((Button)findViewById(R.id.dismissButton)).setOnClickListener(
                new Button.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        dismissAlertDialog();
                    }
                });
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.cell_broadcast_alert;
    }

    @Override
    protected void showAlertMessage(CellBroadcastMessage message) {
        if (message == null) {
            return;
        }
        View view = findViewById(R.id.cellbroad_dialog_content);
        updateAlertText(view, message);
        executeAnimationHandler(view, message);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        // stop animating warning icon, stop playing alert sound, mark broadcast as read
        super.onBackPressed();
    }

}
