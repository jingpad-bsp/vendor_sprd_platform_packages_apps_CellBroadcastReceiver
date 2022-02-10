package com.sprd.cellbroadcastreceiver.data;

import java.util.ArrayList;

import com.android.cellbroadcastreceiver.R;
import com.sprd.cellbroadcastreceiver.ui.LanguageItemView;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.util.Log;

public class LanguageSettingAdapter extends BaseAdapter {

    private String TAG = "LanguageSettingAdapter";

    private Context mContext;
    private ArrayList<LanguageItemData> mLanguageList;
    private boolean mDataChanged = false;

    public ArrayList<LanguageItemData> getLanguageList() {
        return mLanguageList;
    }

    private Context getContext() {
        return mContext;
    }

    public LanguageSettingAdapter(Context context,
            ArrayList<LanguageItemData> channelList) {
        mLanguageList = channelList;
        mContext = context;
    }

    @Override
    public int getCount() {
        return getLanguageList().size();
    }

    @Override
    public Object getItem(int position) {
        return getLanguageList().get(position);
    }

    @Override
    public long getItemId(int position) {
        return (long) position;
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        LanguageItemView lView = (LanguageItemView) LayoutInflater.from(
                getContext()).inflate(R.layout.language_item, null);

        lView.setTag(getItem(position));
        lView.init();
        lView.getDeleteImg().setOnClickListener(new View.OnClickListener(){

            @Override
            public void onClick(View v) {
                // TODO Auto-generated method stub
                if (v instanceof ImageView) {
                    //Bug 946179 begin
                    if (getLanguageList().size() == 0) {
                        Log.e(TAG, "language list is empty now.");
                        return;
                    }
                    //Bug 946179 end
                    //Bug965560 begin
                    //int index = getLanguageList().get(position).getIndexOfArray();
                    LanguageItemView lv = (LanguageItemView)v.getParent();
                    final int index = getLanguageList().indexOf((LanguageItemData)lv.getTag());
                    Log.d(TAG, "pos[" + position + "] tag: " + lv.getTag() + " index is " + index);
                    if (index < 0) {
                        return;
                    }
                    LanguageMgr.getInstance().setLanguageEnable((LanguageItemData)lv.getTag(), false);
                    //getLanguageList().remove(position);
                    getLanguageList().remove(index);
                    //Bug965560 end
                    mDataChanged = true;
                }
                notifyDataSetChanged();
            }
        });
        return lView;
    }

    public boolean isChanged(){
        return mDataChanged;
    }
}
