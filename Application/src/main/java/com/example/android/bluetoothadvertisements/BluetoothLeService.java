/*
 * Copyright (C) 2013 The Android Open Source Project
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

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import java.util.List;
import java.util.UUID;

import static com.example.android.bluetoothadvertisements.MainActivity.iOSI;
import static com.example.android.bluetoothadvertisements.MainActivity.iTSC;
import static com.example.android.bluetoothadvertisements.MainActivity.showMsg;
import static java.lang.Boolean.TRUE;

/**
 * Service for managing connection and data communication with a GATT server hosted on a
 * given Bluetooth LE device.
 */
public class BluetoothLeService extends Service {
    private final static String TAG = BluetoothLeService.class.getSimpleName();

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private String mBluetoothDeviceAddress;
    private BluetoothGatt mBluetoothGatt;
    private int mConnectionState = STATE_DISCONNECTED;
    private BluetoothGattService mBluetoothGattService;

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

    public final static String ACTION_GATT_CONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "com.example.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE =
            "com.example.bluetooth.le.ACTION_DATA_AVAILABLE";
    public final static String ACTION_ORIENT_DATA_AVAILABLE =
            "com.example.bluetooth.le.ACTION_ORIENT_DATA_AVAILABLE";
    public final static String ACTION_SENSOR_DATA_AVAILABLE =
            "com.example.bluetooth.le.ACTION_SENSOR_DATA_AVAILABLE";
    public final static String EXTRA_DATA =
            "com.example.bluetooth.le.EXTRA_DATA";
    public final static String ACTION_CHARACTERISTIC_WRITTEN =
            "com.example.bluetooth.le.ACTION_CHARACTERISTIC_WRITTEN";

    public final static UUID UUID_HEART_RATE_MEASUREMENT =
            UUID.fromString(SampleGattAttributes.HEART_RATE_MEASUREMENT);

    // Implements callback methods for GATT events that the app cares about.  For example,
    // connection change and services discovered.
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String intentAction;
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                intentAction = ACTION_GATT_CONNECTED;
                mConnectionState = STATE_CONNECTED;
                broadcastUpdate(intentAction);


            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                intentAction = ACTION_GATT_DISCONNECTED;
                mConnectionState = STATE_DISCONNECTED;
                broadcastUpdate(intentAction);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (OrientProfile.ORIENT_PARAMETERS_CHARACTERISTIC.equals(characteristic.getUuid())) {
                    Log.i("BLEService","ORIENT parameters characteristic read.");
                    broadcastUpdate(ACTION_ORIENT_DATA_AVAILABLE, characteristic);
                } else if (OrientProfile.SENSOR_DATA_CHARACTERISTIC.equals(characteristic.getUuid())) {
                    Log.i("BLEService","Sensor data characteristic read.");
                    broadcastUpdate(ACTION_SENSOR_DATA_AVAILABLE, characteristic);
                } else {
                    broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
                }
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            broadcastUpdate(ACTION_CHARACTERISTIC_WRITTEN, characteristic);
            Log.i("BLEService","Characteristic written.");
        }
    };

    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    private void broadcastUpdate(final String action,
                                 final BluetoothGattCharacteristic characteristic) {
        final Intent intent = new Intent(action);
        Log.i("BleService","broadcastUpdate for " + characteristic.getUuid().toString());
        if (OrientProfile.ORIENT_PARAMETERS_CHARACTERISTIC.equals(characteristic.getUuid())) {
            Log.i("BleService","Received ORIENT parameters.");
            final byte[] data = characteristic.getValue();
            intent.putExtra(EXTRA_DATA, data);
        } else if (OrientProfile.SENSOR_DATA_CHARACTERISTIC.equals(characteristic.getUuid())) {
            Log.i("BleService","Received sensor data.");
            final byte[] data = characteristic.getValue();
            intent.putExtra(EXTRA_DATA, data);
        } else {
            // For all other profiles, writes the data formatted in HEX.
            final byte[] data = characteristic.getValue();
            if (data != null && data.length > 0) {
                final StringBuilder stringBuilder = new StringBuilder(data.length);
                for(byte byteChar : data)
                    stringBuilder.append(String.format("%02X ", byteChar));
                intent.putExtra(EXTRA_DATA, new String(data) + "\n" + stringBuilder.toString());
            }
        }

        sendBroadcast(intent);
    }

    public class LocalBinder extends Binder {
        BluetoothLeService getService() {
            return BluetoothLeService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // After using a given device, you should make sure that BluetoothGatt.close() is called
        // such that resources are cleaned up properly.  In this particular example, close() is
        // invoked when the UI is disconnected from the Service.
        close();
        return super.onUnbind(intent);
    }

    private final IBinder mBinder = new LocalBinder();

    /**
     * Initializes a reference to the local Bluetooth adapter.
     *
     * @return Return true if the initialization is successful.
     */
    public boolean initialize() {
        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        return true;
    }

    /**
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @param address The device address of the destination device.
     *
     * @return Return true if the connection is initiated successfully. The connection result
     *         is reported asynchronously through the
     *         {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     *         callback.
     */
    public boolean connect(final String address) {
        if (mBluetoothAdapter == null || address == null) {
            if(mBluetoothAdapter == null)
                Log.w(TAG, "BluetoothAdapter not initialized.");
            else
                Log.w(TAG, "Unspecified address.");

            return false;
        }

        // Previously connected device.  Try to reconnect.
        if (mBluetoothDeviceAddress != null && address.equals(mBluetoothDeviceAddress)
                && mBluetoothGatt != null) {
            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
            if (mBluetoothGatt.connect()) {
                mConnectionState = STATE_CONNECTING;
                return true;
            } else {
                return false;
            }
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.");
            return false;
        }
        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
        Log.d(TAG, "Trying to create a new connection.");
        mBluetoothDeviceAddress = address;
        mConnectionState = STATE_CONNECTING;
        return true;
    }

    /**
     * Disconnects an existing connection or cancel a pending connection. The disconnection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public void disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.disconnect();
        Log.i("BLEService","Disconnect called");
    }

    /**
     * After using a given BLE device, the app must call this method to ensure resources are
     * released properly.
     */
    public void close() {
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
        Log.i("BLEService","Close called");
    }

    /**
     * Request a read on a given {@code BluetoothGattCharacteristic}. The read result is reported
     * asynchronously through the {@code BluetoothGattCallback#onCharacteristicRead(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)}
     * callback.
     *
     * @param characteristic The characteristic to read from.
     */
    public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.readCharacteristic(characteristic);
    }

    public boolean OrientProcedure() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return(false);
        }
        /*check if orient service is available on the device*/
        BluetoothGattService mOrientService = mBluetoothGatt.getService(OrientProfile.ORIENT_MESH_SERVICE);
        if(mOrientService == null){
            Log.w(TAG, "Custom BLE Service not found");
            return(false);
        }

        byte[] writeBuffer = new byte[4];

        //First prepare and write OSI
        int nextOrientationIndex = iOSI + 1;

        byte bNOSI = (byte) nextOrientationIndex;
        byte bMyTSC = (byte) iTSC;
        writeBuffer[0] = bNOSI;
        writeBuffer[1] = bMyTSC;

        /* OSI Characteristic UUID
        public static UUID OSI_CHARACTERISTIC = UUID.fromString("8f0048be-a048-40c8-9454-588e5d1e7423");*/

        /*get the read characteristic from the service*/
        BluetoothGattCharacteristic mWriteCharacteristic = mOrientService.getCharacteristic(OrientProfile.ORIENT_PARAMETERS_CHARACTERISTIC);
        mWriteCharacteristic.setValue(writeBuffer);
        if(mBluetoothGatt.writeCharacteristic(mWriteCharacteristic) == false){
            Log.w(TAG, "Failed to write characteristic");
            return(false);
        }
        return(true);
    }

    public boolean readOrientSensorData() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return(false);
        }
        /*check if orient service is available on the device*/
        BluetoothGattService mCustomService = mBluetoothGatt.getService(OrientProfile.ORIENT_MESH_SERVICE);
        if(mCustomService == null){
            Log.w(TAG, "Custom BLE Service not found");
            return(false);
        }

        byte[] readBuffer = new byte[3];

        /* OSI Characteristic UUID
        public static UUID OSI_CHARACTERISTIC = UUID.fromString("8f0048be-a048-40c8-9454-588e5d1e7423");*/

        /*get the read characteristic from the service*/
        BluetoothGattCharacteristic mReadCharacteristic = mCustomService.getCharacteristic(OrientProfile.SENSOR_DATA_CHARACTERISTIC);
        readCharacteristic(mCustomService.getCharacteristic(OrientProfile.SENSOR_DATA_CHARACTERISTIC));

        if(mBluetoothGatt.readCharacteristic(mCustomService.getCharacteristic(OrientProfile.SENSOR_DATA_CHARACTERISTIC)) == false) {
            Log.w(TAG, "Failed to read characteristic");
            return(false);
        }
        Log.w(TAG, "readCharacteristic called successfully.");
        return(true);

    }

    /**
     * Enables or disables notification on a give characteristic.
     *
     * @param characteristic Characteristic to act on.
     * @param enabled If true, enable notification.  False otherwise.
     */
    public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic,
                                              boolean enabled) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);

        // This is specific to Heart Rate Measurement.
        if (UUID_HEART_RATE_MEASUREMENT.equals(characteristic.getUuid())) {
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(
                    UUID.fromString(SampleGattAttributes.CLIENT_CHARACTERISTIC_CONFIG));
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            mBluetoothGatt.writeDescriptor(descriptor);
        }
    }

    /**
     * Retrieves a list of supported GATT services on the connected device. This should be
     * invoked only after {@code BluetoothGatt#discoverServices()} completes successfully.
     *
     * @return A {@code List} of supported services.
     */
    public List<BluetoothGattService> getSupportedGattServices() {
        if (mBluetoothGatt == null) return null;

        return mBluetoothGatt.getServices();
    }

    public boolean readOrientParameters() {
        if (mBluetoothGatt == null) return false;

        /*get  services available on the device*/
        mBluetoothGattService  = mBluetoothGatt.getService(OrientProfile.ORIENT_MESH_SERVICE);

        //if orient services are avilable on the device, read orient parameters
        if(mBluetoothGattService == null){
            Log.w(TAG, "ORIENT BLE Service not found on the remote device.");
            return(false);
        }

        mBluetoothGatt.readCharacteristic(mBluetoothGattService.getCharacteristic(OrientProfile.ORIENT_PARAMETERS_CHARACTERISTIC));

        return true;
    }

    public boolean readSensorData() {
        if (mBluetoothGatt == null) return false;

        //if orient services are available on the device, read sensor data
        if(mBluetoothGattService == null){
            Log.w(TAG, "ORIENT BLE Service not found on the remote device.");
            return(false);
        }

        mBluetoothGatt.readCharacteristic(mBluetoothGattService.getCharacteristic(OrientProfile.SENSOR_DATA_CHARACTERISTIC));

        return true;
    }

    public boolean writeOrientParameters(int OSI, int TSC, int TSD) {
        if (mBluetoothGatt == null) return false;

        /*get  services available on the device*/
        mBluetoothGattService  = mBluetoothGatt.getService(OrientProfile.ORIENT_MESH_SERVICE);

        //if orient services are avilable on the device, read orient parameters
        if(mBluetoothGattService == null){
            Log.w(TAG, "ORIENT BLE Service not found on the remote device.");
            return(false);
        }

        byte[] writeBuffer = new byte[4];

        //First prepare and write OSI
        int nextOrientationIndex = OSI + 1;

        byte bNOSI = (byte) nextOrientationIndex;
        byte bMyTSC = (byte) TSC;
        writeBuffer[0] = bNOSI;
        writeBuffer[1] = bMyTSC;

        BluetoothGattCharacteristic mWriteCharacteristic = mBluetoothGattService.getCharacteristic(OrientProfile.ORIENT_PARAMETERS_CHARACTERISTIC);
        mWriteCharacteristic.setValue(writeBuffer);
        if(mBluetoothGatt.writeCharacteristic(mWriteCharacteristic) == false){
            Log.w(TAG, "Failed to write characteristic");
            return(false);
        }
        return(true);
    }

    public boolean discoverServices () {
        if (mBluetoothGatt == null) return false;

        if(mBluetoothGatt.discoverServices()) return true;
        else return false;
    }

}
