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

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.w3c.dom.Text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;

import static android.graphics.Color.GREEN;
import static android.graphics.Color.RED;
import static android.graphics.Color.green;
import static java.lang.Boolean.TRUE;

/**
 * Setup display fragments and ensure the device supports Bluetooth.
 */
public class MainActivity extends FragmentActivity {
    private final static String TAG = MainActivity.class.getSimpleName();

    private BluetoothAdapter mBluetoothAdapter;

    private BluetoothManager mBluetoothManager;

    private BluetoothGattServer mBluetoothGattServer;

    private static BluetoothLeService mBluetoothLeService;

    private boolean permissions_granted = false;

    private static final int REQUEST_LOCATION = 0;

    private static final int REQUEST_BLUETOOTH = 0;

    private static final int REQUEST_BLUETOOTH_ADMIN = 0;

    private String mDeviceAddress;

    static private TextView mLocalConsole;
    private TextView viewDisplayOSI;
    private TextView viewDisplayTSC;
    private TextView viewDisplayTSD;
    private TextView viewDisplayStatus;
    private TextView viewLabelOSI;
    private TextView viewLabelTSC;
    private TextView viewLabelTSD;
    private TextView viewLabelStatus;
    private CheckBox viewCheckboxSink;
    private ProgressBar viewStatusProgressBar;

    private Set<BluetoothDevice> mRegisteredDevices = new HashSet<>();

    private byte[] serverBuffer = new byte[2];

    public static int iTSC = 65530;
    public static int iTSD = 65530;
    public static int iOSI = 65530;
    public static int iStatus = 0;
    //0 = sensor, 1 = sink, 2 = relay
    public static int iSubstatus = 0;
    //for iStatus = 0 (Sensor)
    //                  iSubstatus = 0 : Advertise
    //for iStatus = 1 (Sink)
    //                  iSubstatus = 0 : Scan
    //                  iSubstatus = 1 : OrientAndCollect
    //                  iSubstatus = 2 : ForwardAndStore
    //for iStatus = 2 (Relay)
    //                  iSubstatus = 0 : Scan
    //                  iSubstatus = 1 : OrientAndCollect
    //                  iSubstatus = 2 : Forward

    public static int iTSCMSB;
    public static int iTSCLSB;
    public static int iTSDMSB;
    public static int iTSDLSB;
    public static int iOSIMSB;
    public static int iOSILSB;
    public static byte bTSCMSB;
    public static byte bTSCLSB;
    public static byte bTSDMSB;
    public static byte bTSDLSB;
    public static byte bOSIMSB;
    public static byte bOSILSB;
    public static byte bStatus;

    private int orientTasksProgress = 0;

    private int orientInterval = 5000; //(scan + update values + transfer data) within this time
    private Handler orientTasksHandler;

    private ScanResultAdapter mAdapter;

    private boolean mConnected = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //setTitle(R.string.activity_main_title);

        mLocalConsole = (TextView) findViewById(R.id.text_time);
        viewDisplayOSI = (TextView) findViewById(R.id.displayOSI);
        viewDisplayTSC = (TextView) findViewById(R.id.displayTSC);
        viewDisplayTSD = (TextView) findViewById(R.id.displayTSD);
        viewDisplayStatus = (TextView) findViewById(R.id.displayStatus);

        viewLabelOSI = (TextView) findViewById(R.id.labelOSI);
        viewLabelTSC = (TextView) findViewById(R.id.labelTSC);
        viewLabelTSD = (TextView) findViewById(R.id.labelTSD);
        viewLabelStatus = (TextView) findViewById(R.id.labelStatus);

        viewCheckboxSink = (CheckBox) findViewById(R.id.checkboxSink);

        viewStatusProgressBar = (ProgressBar) findViewById(R.id.progressBar);

        viewDisplayStatus.setBackgroundColor(RED);
        viewDisplayOSI.setBackgroundColor(RED);
        viewDisplayTSC.setBackgroundColor(RED);
        viewDisplayTSD.setBackgroundColor(RED);
        viewLabelStatus.setBackgroundColor(RED);
        viewLabelOSI.setBackgroundColor(RED);
        viewLabelTSC.setBackgroundColor(RED);
        viewLabelTSD.setBackgroundColor(RED);

        orientTasksHandler = new Handler();
        orientTasks.run(); //This call starts running ORIENT tasks continually with auto callbacks


        mAdapter = new ScanResultAdapter(this.getApplicationContext(),
                LayoutInflater.from(this.getApplicationContext()));

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissions_granted = false;
            requestLocationPermission();
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
            permissions_granted = false;
            requestBluetoothPermission();
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
            permissions_granted = false;
            requestBluetoothAdminPermission();
        }

        if (savedInstanceState == null) {

            mBluetoothAdapter = ((BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE))
                    .getAdapter();

            // Is Bluetooth supported on this device?
            if (mBluetoothAdapter != null) {

                // Is Bluetooth turned on?
                if (mBluetoothAdapter.isEnabled()) {

                    // Are Bluetooth Advertisements supported on this device?
                    if (mBluetoothAdapter.isMultipleAdvertisementSupported()) {

                        // Everything is supported and enabled, load the fragments.
                        setupFragments();
                        startServer(); //Start the GATT server


                    } else {

                        // Bluetooth Advertisements are not supported.
                        showErrorText(R.string.bt_ads_not_supported);
                    }
                } else {

                    // Prompt user to turn on Bluetooth (logic continues in onActivityResult()).
                    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableBtIntent, Constants.REQUEST_ENABLE_BT);
                }
            } else {

                // Bluetooth is not supported.
                showErrorText(R.string.bt_not_supported);
            }
        }
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
    }

    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    // ACTION_CHARACTERISTIC_WRITTEN: BLE characteristic write was successful. This broadcast has the
    // characteristic as well.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                showMsg("Connected to BLE Device");

            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Get all the supported services and characteristics on the user interface and write Orient Parameters
                if(mBluetoothLeService.OrientProcedure() == false) {
                    showMsg("OSI Write Failed");
                } else {
                    showMsg("OSI Write Succeeded");
                    mBluetoothLeService.readOrientSensorData();
                }
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                byte[] dataBuffer = new byte[3];
                dataBuffer = intent.getByteArrayExtra(BluetoothLeService.EXTRA_DATA);
                showMsg("Data received : " + dataBuffer[0] + ":" + dataBuffer[1] + ":" + dataBuffer[2]);
                mBluetoothLeService.disconnect();
            } else if (BluetoothLeService.ACTION_CHARACTERISTIC_WRITTEN.equals(action)) {
                if(action.toString().equalsIgnoreCase("01234567-0001-1000-8000-0123456789ab")) {
                    showMsg("OSI written");
                }
                else if(action.toString().equalsIgnoreCase("01234567-0002-1000-8000-0123456789ab")) {
                    showMsg("TSC written");
                }
                else if(action.toString().equalsIgnoreCase("01234567-0003-1000-8000-0123456789ab")) {
                    showMsg("TSD written");
                }
            }
        }
    };

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case Constants.REQUEST_ENABLE_BT:

                if (resultCode == RESULT_OK) {

                    // Bluetooth is now Enabled, are Bluetooth Advertisements supported on
                    // this device?
                    if (mBluetoothAdapter.isMultipleAdvertisementSupported()) {

                        // Everything is supported and enabled, load the fragments.
                        setupFragments();

                    } else {

                        // Bluetooth Advertisements are not supported.
                        showErrorText(R.string.bt_ads_not_supported);
                    }
                } else {

                    // User declined to enable Bluetooth, exit the app.
                    Toast.makeText(this, R.string.bt_not_enabled_leaving,
                            Toast.LENGTH_SHORT).show();
                    finish();
                }

            default:
                super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void setupFragments() {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();

        ScannerFragment scannerFragment = new ScannerFragment();
        // Fragments can't access system services directly, so pass it the BluetoothAdapter
        scannerFragment.setBluetoothAdapter(mBluetoothAdapter);
        transaction.replace(R.id.scanner_fragment_container, scannerFragment);

        AdvertiserFragment advertiserFragment = new AdvertiserFragment();
        transaction.replace(R.id.advertiser_fragment_container, advertiserFragment);

        transaction.commit();
    }

    private void showErrorText(int messageId) {

        TextView view = (TextView) findViewById(R.id.error_textview);
        view.setText(getString(messageId));
    }

    private void requestLocationPermission() {
        Log.i(Constants.TAG, "Location permission has NOT yet been granted. Requesting permission.");
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_COARSE_LOCATION)) {
            Log.i(Constants.TAG, "Displaying location permission rationale to provide additional context.");
            final AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Permision Required");
            builder.setMessage("Please grant Location access so this application can perform Bluetooth scanning");
            builder.setPositiveButton(android.R.string.ok, null);
            builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                public void onDismiss(DialogInterface dialog) {
                    Log.d(Constants.TAG, "Requesting permissions after explanation");
                    ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_LOCATION);
                }
            });
            builder.show();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_LOCATION);
        }
    }

    private void requestBluetoothPermission() {
        Log.i(Constants.TAG, "Bluetooth permission has NOT yet been granted. Requesting permission.");
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.BLUETOOTH)) {
            Log.i(Constants.TAG, "Displaying location permission rationale to provide additional context.");
            final AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Permision Required");
            builder.setMessage("Please grant Bluetooth permission so this application can perform Bluetooth activities");
            builder.setPositiveButton(android.R.string.ok, null);
            builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                public void onDismiss(DialogInterface dialog) {
                    Log.d(Constants.TAG, "Requesting permissions after explanation");
                    ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.BLUETOOTH}, REQUEST_BLUETOOTH);
                }
            });
            builder.show();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH}, REQUEST_BLUETOOTH);
        }
    }

    private void requestBluetoothAdminPermission() {
        Log.i(Constants.TAG, "Bluetooth permission has NOT yet been granted. Requesting permission.");
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.BLUETOOTH_ADMIN)) {
            Log.i(Constants.TAG, "Displaying location permission rationale to provide additional context.");
            final AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Permision Required");
            builder.setMessage("Please grant Bluetooth permission so this application can perform Bluetooth activities");
            builder.setPositiveButton(android.R.string.ok, null);
            builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                public void onDismiss(DialogInterface dialog) {
                    Log.d(Constants.TAG, "Requesting permissions after explanation");
                    ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.BLUETOOTH_ADMIN}, REQUEST_BLUETOOTH_ADMIN);
                }
            });
            builder.show();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_ADMIN}, REQUEST_BLUETOOTH_ADMIN);
        }
    }

    /**
     * Initialize the GATT server instance with the services/characteristics
     * from the Time Profile.
     */
    private void startServer() {
        mBluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        mBluetoothGattServer = mBluetoothManager.openGattServer(this, mGattServerCallback);
        if (mBluetoothGattServer == null) {
            Log.w(Constants.TAG, "Unable to create GATT server");
            return;
        }

        //mBluetoothGattServer.addService(TimeProfile.createTimeService());
        mBluetoothGattServer.addService(OrientProfile.createOrientService());

        // Initialize the local UI
        updateLocalUi();

    }

    /**
     * Shut down the GATT server.
     */
    private void stopServer() {
        if (mBluetoothGattServer == null) return;

        mBluetoothGattServer.close();
    }

    /**
     * Callback to handle incoming requests to the GATT server.
     * All read/write requests for characteristics and descriptors are handled here.
     */
    private BluetoothGattServerCallback mGattServerCallback = new BluetoothGattServerCallback() {

        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(Constants.TAG, "BluetoothDevice CONNECTED: " + device);
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(Constants.TAG, "BluetoothDevice DISCONNECTED: " + device);
                //Remove device from any active subscriptions
                mRegisteredDevices.remove(device);
            }
        }

        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset,
                                                BluetoothGattCharacteristic characteristic) {
            long now = System.currentTimeMillis();

            iOSIMSB = iOSI / 256;
            iOSILSB = iOSI % 256;
            bOSIMSB = (byte) (iOSIMSB - 128);
            bOSILSB = (byte) (iOSILSB - 128);
            iTSCMSB = iTSC / 256;
            iTSCLSB = iTSC % 256;
            bTSCMSB = (byte) (iTSCMSB - 128);
            bTSCLSB = (byte) (iTSCLSB - 128);
            iTSDMSB = iTSD / 256;
            iTSDLSB = iTSD % 256;
            bTSDMSB = (byte) (iTSDMSB - 128);
            bTSDLSB = (byte) (iTSDLSB - 128);

            if (OrientProfile.OSI_CHARACTERISTIC.equals(characteristic.getUuid())) {
                Log.i(Constants.TAG, "Read CurrentTime");
                serverBuffer[0] = bOSIMSB;
                serverBuffer[1] = bOSILSB;
                mBluetoothGattServer.sendResponse(device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        0,
                        serverBuffer
                        );
            } else if (OrientProfile.SENSOR_DATA_CHARACTERISTIC.equals(characteristic.getUuid())) {
                Log.i(Constants.TAG, "Read Sensor Data Characteristic");
                serverBuffer[0] = 11;
                serverBuffer[1] = 51;
                serverBuffer[2] = 99;
                mBluetoothGattServer.sendResponse(device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        0,
                        serverBuffer);
            } else {
                Log.w(Constants.TAG, "Invalid Characteristic Read: " + characteristic.getUuid());
                mBluetoothGattServer.sendResponse(device,
                        requestId,
                        BluetoothGatt.GATT_FAILURE,
                        0,
                        null);
            }
        }

        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId,
                                                 BluetoothGattCharacteristic characteristic,
                                                 boolean preparedWrite,
                                                 boolean responseNeeded,
                                                 int offset, byte[] data) {
            if (characteristic.getUuid().equals(OrientProfile.OSI_CHARACTERISTIC)) {
                iOSI = (data[0]+128);
                iTSC = (data[1]+128);
                iTSD = ((data[0]+128) * 256) + data[1] + 128;
                showMsg("OSI written - callback " + Integer.toString(data[0]+128) + " "  + Integer.toString(data[1]+128));

            }

            updateLocalUi();

            mBluetoothGattServer.sendResponse(device,
                    requestId,
                    BluetoothGatt.GATT_SUCCESS,
                    0,
                    data
            );

        }

        @Override
        public void onDescriptorReadRequest(BluetoothDevice device, int requestId, int offset,
                                            BluetoothGattDescriptor descriptor) {
            if (TimeProfile.CLIENT_CONFIG.equals(descriptor.getUuid())) {
                Log.d(Constants.TAG, "Config descriptor read");
                byte[] returnValue;
                if (mRegisteredDevices.contains(device)) {
                    returnValue = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE;
                } else {
                    returnValue = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE;
                }
                mBluetoothGattServer.sendResponse(device,
                        requestId,
                        BluetoothGatt.GATT_FAILURE,
                        0,
                        returnValue);
            } else {
                Log.w(Constants.TAG, "Unknown descriptor read request");
                mBluetoothGattServer.sendResponse(device,
                        requestId,
                        BluetoothGatt.GATT_FAILURE,
                        0,
                        null);
            }
        }

        @Override
        public void onDescriptorWriteRequest(BluetoothDevice device, int requestId,
                                             BluetoothGattDescriptor descriptor,
                                             boolean preparedWrite, boolean responseNeeded,
                                             int offset, byte[] value) {
            if (TimeProfile.CLIENT_CONFIG.equals(descriptor.getUuid())) {
                if (Arrays.equals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE, value)) {
                    Log.d(Constants.TAG, "Subscribe device to notifications: " + device);
                    mRegisteredDevices.add(device);
                } else if (Arrays.equals(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE, value)) {
                    Log.d(Constants.TAG, "Unsubscribe device from notifications: " + device);
                    mRegisteredDevices.remove(device);
                }

                if (responseNeeded) {
                    mBluetoothGattServer.sendResponse(device,
                            requestId,
                            BluetoothGatt.GATT_SUCCESS,
                            0,
                            null);
                }
            } else {
                Log.w(Constants.TAG, "Unknown descriptor write request");
                if (responseNeeded) {
                    mBluetoothGattServer.sendResponse(device,
                            requestId,
                            BluetoothGatt.GATT_FAILURE,
                            0,
                            null);
                }
            }
        }
    };

    /**
     * Update graphical UI on devices that support it with the current time.
     */
    private void updateLocalUi() {
        String orientParameters = String.valueOf(iOSI) + "," + String.valueOf(iTSC) + ", " + String.valueOf(iTSD);
        //mLocalTimeView.setText(orientParameters);
        //mLocalTimeView.refreshDrawableState();
        switch(iStatus)
        {
            case 0:
                viewDisplayStatus.setText("Sensor");
                break;

            case 1:
                viewDisplayStatus.setText("Sink");
                break;

            case 2:
                viewDisplayStatus.setText("Relay");
                break;

            default:
                viewDisplayStatus.setText("Error Mode");

        }

        viewDisplayOSI.setText(String.valueOf(iOSI));
        viewDisplayTSC.setText(String.valueOf(iTSC));
        viewDisplayTSD.setText(String.valueOf(iTSD));
    }

    public void changeStatus(View view) {
        ScannerFragment scannerFragment = (ScannerFragment) getSupportFragmentManager().findFragmentById(R.id.scanner_fragment_container);

        if(viewCheckboxSink.isChecked()) {
            iOSI = 0;
            iTSC = 0;
            iTSD = 0;
            iStatus = 1;

            viewDisplayStatus.setBackgroundColor(GREEN);
            viewDisplayOSI.setBackgroundColor(GREEN);
            viewDisplayTSC.setBackgroundColor(GREEN);
            viewDisplayTSD.setBackgroundColor(GREEN);
            viewLabelStatus.setBackgroundColor(GREEN);
            viewLabelOSI.setBackgroundColor(GREEN);
            viewLabelTSC.setBackgroundColor(GREEN);
            viewLabelTSD.setBackgroundColor(GREEN);
            viewStatusProgressBar.setVisibility(View.VISIBLE);
            updateLocalUi();

        }
        else {
            stopScanning();

            iOSI = 65530;
            iTSC = 65530;
            iTSD = 65530;
            iStatus = 0;
            orientTasksProgress = 0;

            viewDisplayStatus.setBackgroundColor(RED);
            viewDisplayOSI.setBackgroundColor(RED);
            viewDisplayTSC.setBackgroundColor(RED);
            viewDisplayTSD.setBackgroundColor(RED);
            viewLabelStatus.setBackgroundColor(RED);
            viewLabelOSI.setBackgroundColor(RED);
            viewLabelTSC.setBackgroundColor(RED);
            viewLabelTSD.setBackgroundColor(RED);
            viewStatusProgressBar.setVisibility(View.INVISIBLE);
            updateLocalUi();


            scannerFragment.clearScanResult();
        }
    }

    private void startScanning() {
        ScannerFragment scannerFragment = (ScannerFragment) getSupportFragmentManager().findFragmentById(R.id.scanner_fragment_container);
        scannerFragment.startScanning();
    }

    private void stopScanning() {
        ScannerFragment scannerFragment = (ScannerFragment) getSupportFragmentManager().findFragmentById(R.id.scanner_fragment_container);
        scannerFragment.stopScanning();
    }

    public void onDestroy() {
        super.onDestroy();
        stopScanning();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    Runnable orientTasks = new Runnable() {
        public void run() {
            switch (iStatus) {
                case 0: //sensor
                    updateLocalUi();
                    break;


                case 1: //sink
                    ScannerFragment scannerFragment = (ScannerFragment) getSupportFragmentManager().findFragmentById(R.id.scanner_fragment_container);
                    showMsg("Devices Found: " + String.valueOf(mAdapter.getCount()));
                    switch (orientTasksProgress) {
                        case 1:
                            showMsg("Scanning started");
                            scannerFragment.clearScanResult();
                            startScanning();
                            break;

                        case 6:
                            //analyse list of ble devices
                            //connect with these devices
                            //write to the gatt server of these devices
                            showMsg("Scanning stopped");
                            break;

                        case 10:
                            viewStatusProgressBar.setProgress(orientTasksProgress);
                            orientTasksProgress = 0;
                            break;

                        default:
                            break;


                    }
                    viewStatusProgressBar.setProgress(orientTasksProgress);
                    orientTasksProgress++;
                    break;

                case 2: //relay

                    break;

                default:
                    break;

            }

            orientTasksHandler.postDelayed(orientTasks, 1000);
        }
    };

    public static void showMsg(String text) {
        mLocalConsole.setText(text);
    }

    public static void initializeOrientTransfer (String deviceAddress) {
        showMsg("ORIENT Transfer Initialized : " + deviceAddress);
        mBluetoothLeService.connect(deviceAddress);
    }



    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

}