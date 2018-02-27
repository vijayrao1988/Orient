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
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
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
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
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
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static android.bluetooth.BluetoothProfile.STATE_CONNECTED;
import static android.bluetooth.BluetoothProfile.STATE_CONNECTING;
import static android.bluetooth.BluetoothProfile.STATE_DISCONNECTED;
import static android.graphics.Color.BLUE;
import static android.graphics.Color.GREEN;
import static android.graphics.Color.RED;
import static android.graphics.Color.green;
import static com.example.android.bluetoothadvertisements.BluetoothLeService.ACTION_GATT_CONNECTED;
import static com.example.android.bluetoothadvertisements.BluetoothLeService.ACTION_GATT_DISCONNECTED;
import static com.example.android.bluetoothadvertisements.BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED;
import static java.lang.Boolean.TRUE;

/**
 * Setup display fragments and ensure the device supports Bluetooth.
 */
public class MainActivity extends FragmentActivity {
    private final static String TAG = MainActivity.class.getSimpleName();

    private static final long SCAN_PERIOD = 5000;

    private BluetoothAdapter mBluetoothAdapter;

    private BluetoothManager mBluetoothManager;

    private BluetoothGattServer mBluetoothGattServer;

    private static BluetoothLeService mBluetoothLeService;

    private boolean permissions_granted = false;

    private static final int REQUEST_LOCATION = 0;

    private static final int REQUEST_BLUETOOTH = 0;

    private static final int REQUEST_BLUETOOTH_ADMIN = 0;

    private String mDeviceAddress;

    private String mBluetoothDeviceAddress;

    private BluetoothGatt mBluetoothGatt;

    private int mConnectionState = STATE_DISCONNECTED;

    private ScanCallback mScanCallback;

    private BluetoothLeScanner mBluetoothLeScanner;

    private ScanResultAdapter mAdapter;

    private Handler mHandler;

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
    private TextView viewLabelRemoteAddress;
    private TextView viewLabelRemoteOSI;
    private TextView viewLabelRemoteTSC;
    private TextView viewLabelRemoteTSD;
    private TextView viewValueRemoteAddress;
    private TextView viewValueRemoteOSI;
    private TextView viewValueRemoteTSC;
    private TextView viewValueRemoteTSD;

    private Set<BluetoothDevice> mRegisteredDevices = new HashSet<>();

    private byte[] serverBuffer = new byte[2];

    public static int iTSC = 120;
    public static int iTSD = 120;
    public static int iOSI = 120;
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

    private boolean mConnected = false;

    private boolean mScanning = false;


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
        viewLabelRemoteAddress = (TextView) findViewById(R.id.labelRemoteAddress);
        viewLabelRemoteOSI = (TextView) findViewById(R.id.labelRemoteOSI);
        viewLabelRemoteTSC = (TextView) findViewById(R.id.labelRemoteTSC);
        viewLabelRemoteTSD = (TextView) findViewById(R.id.labelRemoteTSD);
        viewValueRemoteAddress = (TextView) findViewById(R.id.valueRemoteAddress);
        viewValueRemoteOSI = (TextView) findViewById(R.id.valueRemoteOSI);
        viewValueRemoteTSC = (TextView) findViewById(R.id.valueRemoteTSC);
        viewValueRemoteTSD = (TextView) findViewById(R.id.valueRemoteTSD);


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

        mAdapter = new ScanResultAdapter(this.getApplicationContext(), LayoutInflater.from(this));



        mHandler = new Handler();

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

            mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();

            // Is Bluetooth supported on this device?
            if (mBluetoothAdapter != null) {
                Log.i(TAG, "BLE Adapter is non null");
                // Is Bluetooth turned on?
                if (mBluetoothAdapter.isEnabled()) {
                Log.i(TAG, "BLE Adapter enabled");
                    // Are Bluetooth Advertisements supported on this device?
                    if (mBluetoothAdapter.isMultipleAdvertisementSupported()) {

                        // Everything is supported and enabled, load the fragments.
                        Log.i(TAG, "Multiple Advertisement Supported");
                        setupFragments();
                        startServer(); //Start the GATT server
                    } else {
                        // Bluetooth Advertisements are not supported.
                        Log.i(TAG, "Bluetooth Advertisements not supported");
                        showErrorText(R.string.bt_ads_not_supported);
                    }
                } else {

                    // Prompt user to turn on Bluetooth (logic continues in onActivityResult()).
                    Log.i(TAG, "Prompting user to turn on Bluetooth");
                    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableBtIntent, Constants.REQUEST_ENABLE_BT);
                }
            } else {

                // Bluetooth is not supported.
                Log.i(TAG, "Bluetooth is not supported on this device");
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
            // mBluetoothLeService.connect(mDeviceAddress);
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
            if (ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;

                showMsg("Connected to BLE Device");

            } else if (ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
            } else if (ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Get all the supported services and characteristics on the user interface and write Orient Parameters
                Log.i(TAG, "Received broadcast in MainActivity. Calling OrientProcedure();.");
                if(mBluetoothLeService.OrientProcedure() == false) {
                    Log.i(TAG, "OrientProcedure failed");

                } else {
                    Log.i(TAG, "OrientProcedure() succeeded. Calling disconnect();");
                    mBluetoothLeService.disconnect();
                    //mBluetoothLeService.close();
                    //mBluetoothLeService.readOrientSensorData();
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

        //ScannerFragment scannerFragment = new ScannerFragment();
        // Fragments can't access system services directly, so pass it the BluetoothAdapter
        //scannerFragment.setBluetoothAdapter(mBluetoothAdapter);
        //transaction.replace(R.id.scanner_fragment_container, scannerFragment);

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
    public void startServer() {
        mBluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);

        mBluetoothGattServer = mBluetoothManager.openGattServer(this, mGattServerCallback);
        if (mBluetoothGattServer == null) {
            Log.e(Constants.TAG, "Unable to create GATT server");
            return;
        }

        BluetoothGattService service = new BluetoothGattService(OrientProfile.ORIENT_MESH_SERVICE, BluetoothGattService.SERVICE_TYPE_PRIMARY);
        BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(OrientProfile.OSI_CHARACTERISTIC, BluetoothGattCharacteristic.PROPERTY_WRITE, BluetoothGattCharacteristic.PERMISSION_WRITE);
        service.addCharacteristic(characteristic);
        if (mBluetoothGattServer.addService(OrientProfile.createOrientService())) {
            Log.i("BluetoothGattServer","OSI Service Added");
        } else {
            Log.i("BluetoothGattServer","Error in adding OSI Service");
        }

        Log.i(TAG, "ORIENT Server started");

        // Initialize the local UI
        updateLocalUi();

    }

    /**
     * Shut down the GATT server.
     */
    public void stopServer() {
        if (mBluetoothGattServer == null) return;

        mBluetoothGattServer.close();
    }

    /**
     * Callback to handle incoming requests to the GATT server.
     * All read/write requests for characteristics and descriptors are handled here.
     */
    private BluetoothGattServerCallback mGattServerCallback = new BluetoothGattServerCallback() {
        @Override
        public void onServiceAdded(int status, BluetoothGattService service) {
            Log.i("GattServerCallback","Service Added Successfully");
        }

        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {

            Log.i(TAG, "Connection state changed of the GATT Server");
            if (newState == STATE_CONNECTED) {
                Log.i("GattServerCallback", "BluetoothDevice CONNECTED: " + device);
            } else if (newState == STATE_DISCONNECTED) {
                Log.i("GattServerCallback", "BluetoothDevice DISCONNECTED: " + device);
                //Remove device from any active subscriptions
                mRegisteredDevices.remove(device);
            } else if (newState == STATE_CONNECTING) {
                Log.i(TAG, "Client is connecting");
            } else if (newState == BluetoothProfile.STATE_DISCONNECTING) {
                Log.i(TAG, "Client is disconnecting");
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
            Log.i(TAG, "Server : Remote device wrote characteristic");
            if (characteristic.getUuid().equals(OrientProfile.OSI_CHARACTERISTIC)) {
                //set the OSI to the value received from the sink/relay. The sink/relay increments its own OSI value and sends it.
                iOSI = byteToInt(data[0]);
                //set the TSC  to the value received from the sink/relay. The sink/relay replicates its own TSC value onto the next node.
                iTSC = byteToInt(data[1]);
                //set the TSD to zero as a connection has just been established.
                iTSD = 0;
                iStatus = 2;    //Move status of the device to RELAY.
                showMsg("OSI and TSC written " + Integer.toString(data[0]+128) + " "  + Integer.toString(data[1]+128));

            }

            //update the screen with the updated values of OSI, TSC and TSD.
            updateLocalUi();

            //start advertising the updated values of OSI, TSC and TSD.


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

            iOSI = 120;
            iTSC = 120;
            iTSD = 120;
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

    public void onDestroy() {
        super.onDestroy();
        stopScanning();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    Runnable orientTasks = new Runnable() {
        public void run() {
            orientTasksHandler.postDelayed(orientTasks, 1000);
            switch (iStatus) {
                case 0: //sensor
                    if(iTSD<60) {
                        iTSD++;
                        iTSC++;
                    }
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

                        case 8:
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
                    if(iTSC<60)
                    {
                        iTSC++;
                    }
                    else
                    {
                        iStatus = 0;
                        iTSD = 0;
                    }
                    updateLocalUi();
                    break;

                default:
                    break;

            }

        }
    };

    public static void showMsg(String text) {
        mLocalConsole.setText(text);
    }

    public void initializeOrientTransfer (String deviceAddress) {
        showMsg("ORIENT Transfer Initialized : " + deviceAddress);
        if(connect(deviceAddress)) {
            Log.i(TAG, "Connecting to Bluetooth Device");
        } else {
            Log.i(TAG, "Failed to connect to Bluetooth Device");
        }
    }



    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            //final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            //Log.d(TAG, "Connect request result=" + result);
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
        intentFilter.addAction(BluetoothDevice.ACTION_FOUND);
        intentFilter.addAction(BluetoothDevice.ACTION_UUID);
        return intentFilter;
    }

    public int byteToInt(byte byteValue) {
        if((int)byteValue > 0) {
            return (int)byteValue;
        } else {
            return (256 + (int)byteValue);
        }
    }

    public boolean connect(final String address) {
        if (mBluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
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
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback, BluetoothDevice.TRANSPORT_LE);
        Log.d(TAG, "Trying to create a new connection.");
        mBluetoothDeviceAddress = address;
        mConnectionState = STATE_CONNECTING;
        return true;
    }

    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String intentAction;
            if (newState == STATE_CONNECTED) {
                intentAction = ACTION_GATT_CONNECTED;
                mConnectionState = STATE_CONNECTED;
                Log.i(TAG, "Connected to GATT server.");
                // Attempts to discover services after successful connection.
                Log.i(TAG, "Attempting to start service discovery:" +
                        mBluetoothGatt.discoverServices());

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                intentAction = ACTION_GATT_DISCONNECTED;
                mConnectionState = STATE_DISCONNECTED;
                Log.i(TAG, "Disconnected from GATT server.");
                mBluetoothGatt.close();
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Services discovered successfully");
                //broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                //broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            //broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            //broadcastUpdate(ACTION_CHARACTERISTIC_WRITTEN, characteristic);
        }
    };

    ////////////////////////////// The following part is for scanning /////////////////////////////




    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.scanner_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case R.id.refresh:
                startScanning();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Start scanning for BLE Advertisements (& set it up to stop after a set period of time).
     */
    public void startScanning() {
        if (mScanCallback == null) {
            Log.d(TAG, "Starting Scanning");

            mScanning = true;

            mHandler = new Handler();

            // Will stop the scanning after a set time.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    stopScanning();
                }
            }, SCAN_PERIOD);

            // Kick off a new scan.
            mScanCallback = new SampleScanCallback();

            mBluetoothLeScanner.startScan(buildScanFilters(), buildScanSettings(), mScanCallback);
            //mBluetoothLeScanner.startScan(mScanCallback);

            String toastText = getString(R.string.scan_start_toast) + " "
                    + TimeUnit.SECONDS.convert(SCAN_PERIOD, TimeUnit.MILLISECONDS) + " "
                    + getString(R.string.seconds);
            Toast.makeText(this, toastText, Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, R.string.already_scanning, Toast.LENGTH_SHORT);
        }
    }

    /**
     * Stop scanning for BLE Advertisements.
     */
    public void stopScanning() {
        if (mScanning == true) {
            Log.d(TAG, "Stopping Scanning");

            mScanning = false;

            // Flush results of scanning before stopping the ble scan
            mBluetoothLeScanner.flushPendingScanResults(mScanCallback);

            // Stop the scan, wipe the callback.
            mBluetoothLeScanner.stopScan(mScanCallback);
            mScanCallback = null;

            // Even if no new results, update 'last seen' times.
            mAdapter.notifyDataSetChanged();

            Log.i("MainActivityScanner","stopScanEnded");
        }
    }

    /**
     * Return a List of {@link ScanFilter} objects to filter by Service UUID.
     */
    private List<ScanFilter> buildScanFilters() {
        List<ScanFilter> scanFilters = new ArrayList<>();

        ScanFilter.Builder builder = new ScanFilter.Builder();
        // Comment out the below line to see all BLE devices around you
        builder.setServiceUuid(Constants.Service_UUID);
        scanFilters.add(builder.build());

        return scanFilters;
    }

    /**
     * Return a {@link ScanSettings} object set to use low power (to preserve battery life).
     */
    private ScanSettings buildScanSettings() {
        ScanSettings.Builder builder = new ScanSettings.Builder();
        builder.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY);
        return builder.build();
    }

    /**
     * Custom ScanCallback object - adds to adapter on success, displays error on failure.
     */
    private class SampleScanCallback extends ScanCallback {

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);

            for (ScanResult result : results) {
                mAdapter.add(result);
            }
            mAdapter.notifyDataSetChanged();
        }

        @Override
        public void onScanResult(int callbackType, final ScanResult result) {
            super.onScanResult(callbackType, result);
            //mAdapter.add(result);
            //mAdapter.notifyDataSetChanged();
            byte scanRecord[] = result.getScanRecord().getBytes();
            if(scanRecord[7]=='a'&&scanRecord[8]=='b') {
                Log.i("MainActivityScanner","Found an ORIENT device");
                stopScanning();
                viewValueRemoteAddress.setText(result.getDevice().getAddress().toString());
                viewValueRemoteOSI.setText(String.valueOf(scanRecord[9]));
                viewValueRemoteTSC.setText(String.valueOf(scanRecord[10]));
                viewValueRemoteTSD.setText(String.valueOf(scanRecord[11]));

               // Will attempt to connect with the remote device.
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if(mBluetoothLeService.connect(result.getDevice().getAddress().toString()))
                        {
                            Log.i("MainActivity","Connection succeeded");
                        } else {
                            Log.i("MainActivity" ,"Connection failed");
                        }

                    }
                }, 2000);
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            Log.i("MainActivity", "Scan failed");
        }
    }

}