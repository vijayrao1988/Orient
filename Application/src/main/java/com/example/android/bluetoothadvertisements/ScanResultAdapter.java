/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.example.android.bluetoothadvertisements;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import static android.R.attr.matchOrder;
import static android.R.attr.name;

/**
 * Holds and displays {@link ScanResult}s, used by {@link ScannerFragment}.
 */
public class ScanResultAdapter extends BaseAdapter {

    static public ArrayList<ScanResult> mArrayList;

    private Context mContext;

    private LayoutInflater mInflater;

    ScanResultAdapter(Context context, LayoutInflater inflater) {
        super();
        mContext = context;
        mInflater = inflater;
        mArrayList = new ArrayList<>();
    }

    @Override
    public int getCount(){
        return mArrayList.size();
    }

    @Override
    public Object getItem(int position) {
        return mArrayList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return mArrayList.get(position).getDevice().getAddress().hashCode();
    }

    public BluetoothDevice getItemDevice(int position) {
        return mArrayList.get(position).getDevice();
    }

    @Override
    public View getView(int position, View view, ViewGroup parent) {

        // Reuse an old view if we can, otherwise create a new one.
        if (view == null) {
            view = mInflater.inflate(R.layout.listitem_scanresult, null);
        }

        TextView deviceNameView = (TextView) view.findViewById(R.id.device_name);
        TextView deviceAddressView = (TextView) view.findViewById(R.id.device_address);
        //TextView lastSeenView = (TextView) view.findViewById(R.id.last_seen);
        TextView orientOsiView = (TextView) view.findViewById(R.id.orientOsi);
        TextView orientTscView = (TextView) view.findViewById(R.id.orientTsc);
        TextView orientTsdView = (TextView) view.findViewById(R.id.orientTsd);


        ScanResult scanResult = mArrayList.get(position);
        byte[] scanRecord = scanResult.getScanRecord().getBytes();
        final StringBuilder stringBuilder = new StringBuilder(scanRecord.length);

        for(byte byteChar : scanRecord) {
            stringBuilder.append((char) byteChar);
        }

        String advData = stringBuilder.toString();

        String name = scanResult.getDevice().getName();
        if (name == null) {
            name = mContext.getResources().getString(R.string.no_name);
        }


        deviceNameView.setText(name);
        deviceAddressView.setText(scanResult.getDevice().getAddress());
        //lastSeenView.setText(getTimeSinceString(mContext, scanResult.getTimestampNanos()));

        int osi = 255;
        int tsc = 255;
        int tsd = 255;
        if(scanRecord[8]=='a'&&scanRecord[9]=='b'&&scanRecord[10]=='c') {
            int manufacturerDataLength = scanRecord[7];
            //osi, tsc and tsd are transmitted in unsigned byte arrays. these carry values from -128 to 127. so add 128 to correct the values.

            osi = byteToInt(scanRecord[11]);
            tsc = byteToInt(scanRecord[12]);
            tsd = byteToInt(scanRecord[13]);
            orientOsiView.setText(String.valueOf(osi));
            orientTscView.setText(String.valueOf(tsc));
            orientTsdView.setText(String.valueOf(tsd));
        }
        return view;
    }

    /**
     * Search the adapter for an existing device address and return it, otherwise return -1.
     */
    private int getPosition(String address) {
        int position = -1;
        for (int i = 0; i < mArrayList.size(); i++) {
            if (mArrayList.get(i).getDevice().getAddress().equals(address)) {
                position = i;
                break;
            }
        }
        return position;
    }


    /**
     * Add a ScanResult item to the adapter if a result from that device isn't already present.
     * Otherwise updates the existing position with the new ScanResult.
     */
    public void add(ScanResult scanResult) {

        int existingPosition = getPosition(scanResult.getDevice().getAddress());

        if (existingPosition >= 0) {
            // Device is already in list, update its record.
            mArrayList.set(existingPosition, scanResult);
        } else {
            // Add new Device's ScanResult to list.
            mArrayList.add(scanResult);
        }
    }

    /**
     * Clear out the adapter.
     */
    public void clear() {
        mArrayList.clear();
    }


    int byteToInt(byte bData) {
        int iData;
        if(bData < 0) {
            iData = 256 + bData;
        } else {
            iData = bData;
        }
        return iData;
    }

}
