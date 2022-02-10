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

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.telephony.CellBroadcastMessage;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.StyleSpan;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.view.accessibility.AccessibilityEvent;
import android.widget.RelativeLayout;
import android.widget.TextView;


/**
 * This class manages the list item view for a single alert.
 */
public class CellBroadcastListItem extends RelativeLayout {

    private CellBroadcastMessage mCbMessage;

    private TextView mChannelView;
    private TextView mMessageView;
    private TextView mDateView;
    private Context mContext;

    public CellBroadcastListItem(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
    }

    CellBroadcastMessage getMessage() {
        return mCbMessage;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mChannelView = (TextView) findViewById(R.id.channel);
        mDateView = (TextView) findViewById(R.id.date);
        mMessageView = (TextView) findViewById(R.id.message);
    }

    /**
     * Only used for header binding.
     * @param message the message contents to bind
     */
    public void bind(CellBroadcastMessage message) {
        mCbMessage = message;

        //add for bug 609438 start
        final boolean isReaded = message.isRead();
        int titleId = CellBroadcastResources.getDialogTitleResource(message, mContext);
        CharSequence channelName = mContext.getText(titleId);
        String szChannelName = getResources().getString(R.string.cb_other_message_identifiers);
        if(szChannelName.equals(channelName.toString())){
            channelName = message.getServiceCategory() + "";
            mChannelView.setText(formatMessage(channelName, isReaded));
        }else {
            mChannelView.setText(formatMessage(getResources().getString(CellBroadcastResources.getDialogTitleResource(message,mContext)), isReaded));
        }
        //add for bug 609438 end
        mDateView.setText(formatMessage(message.getDateString(getContext()), isReaded));
        mMessageView.setText(formatMessage(message.getMessageBody(), isReaded));
    }

    private CharSequence formatMessage(final CharSequence message, final boolean readed) {
        SpannableStringBuilder buf = new SpannableStringBuilder(message);
        // Unread messages are shown in bold
        if (!readed) {
            buf.setSpan(new StyleSpan(Typeface.BOLD), 0, buf.length(),
                    Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
            buf.setSpan(new ForegroundColorSpan(getResources()
                            .getColor(R.drawable.text_color_black)), 0, buf.length(),
                    Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
        }
        return buf;
    }

    @Override
    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        // Speak the date first, then channel name, then message body
        event.getText().add(mCbMessage.getSpokenDateString(getContext()));
        mChannelView.dispatchPopulateAccessibilityEvent(event);
        mMessageView.dispatchPopulateAccessibilityEvent(event);
        return true;
    }
}
