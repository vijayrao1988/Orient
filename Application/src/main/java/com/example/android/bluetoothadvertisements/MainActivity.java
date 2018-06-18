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
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.os.Bundle;
import android.os.Environment;
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

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Random;
import java.util.Scanner;
import java.util.Set;

import static android.graphics.Color.BLUE;
import static android.graphics.Color.GREEN;
import static android.graphics.Color.RED;
import static android.graphics.Color.green;
import static java.lang.Boolean.TRUE;
import static java.lang.Boolean.valueOf;
import static java.lang.System.currentTimeMillis;

/**
 * Setup display fragments and ensure the device supports Bluetooth.
 */
public class MainActivity extends FragmentActivity {
    private final static String TAG = MainActivity.class.getSimpleName();

    private BluetoothAdapter mBluetoothAdapter;

    private BluetoothManager mBluetoothManager;

    private BluetoothGattServer mBluetoothGattServer;

    private static BluetoothLeService mBluetoothLeService;

    private BluetoothGattService serverOrientService;

    private BluetoothGattCharacteristic serverOrientParameters;

    private BluetoothGattCharacteristic serverSensorData;

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
    private TextView viewDisplaySensorDataCount;
    private TextView viewDisplayNodeId;
    private TextView viewDisplaySensorDataProduced;
    private TextView viewDisplaySensorDataRecorded;

    private TextView viewLabelConnectedAddress;
    private TextView viewLabelConnectedOSI;
    private TextView viewLabelConnectedTSC;
    private TextView viewLabelConnectedTSD;
    private TextView viewValueConnectedAddress;
    private TextView viewValueConnectedOSI;
    private TextView viewValueConnectedTSC;
    private TextView viewValueConnectedTSD;
    private Switch viewAdvertiseSwitch;


    private Set<BluetoothDevice> mRegisteredDevices = new HashSet<>();

    private byte[] serverBuffer = new byte[11];
    private byte[] sensorDataChar = {0,0,0,0,0,0,0,0,0,0,0};

    public static int iTSC = 120;
    public static int iTSD = 60;
    public static int iOSI = 255;
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

    public static byte bTSC;
    public static byte bTSD;
    public static byte bOSI;
    public static byte bStatus;

    private int orientTasksProgress = 0;

    private int orientInterval = 5000; //(scan + update values + transfer data) within this time
    private Handler orientTasksHandler;
    private Handler restartScanningHandler;
    private Handler connectAndCommunicateHandler;

    private ScanResultAdapter mAdapter;

    public static boolean mConnected = false;

    public static boolean sink = false;

    static private ArrayList<ScanResult> scanResultsList;
    static private int numberOfDevicesFound;
    static private int scanResultIndex;

    static public ArrayList<SensorData> mSensorDataList = new ArrayList<SensorData>();

    static private int sensorDataRate = 10; //ONCE IN THIS MANY SECONDS
    static private int sensorDataTimer = 0; //Incremented every second using runnable orientTasks
    private int sensorDataProduced = 0; //The total number of sensor data units produced by this node
    private int sensorDataRecorded = 0; //The total number of sensor data units recorded to a file by this sink node

    Random rand = new Random();
    private int mNodeId = rand.nextInt(100);

    private File file;
    private String fileName;
    private String fileContents;
    private FileOutputStream outputStream;

    private static boolean isSink = false;

    private boolean scanningStartFlag = false;

    private int watchDogTimer = 0;

    private boolean communicationRunning = false;



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

        viewDisplayNodeId = (TextView) findViewById(R.id.viewNodeId);
        viewCheckboxSink = (CheckBox) findViewById(R.id.checkboxSink);
        viewDisplaySensorDataCount = (TextView) findViewById(R.id.viewSensorDataCount);
        viewDisplaySensorDataProduced = (TextView) findViewById(R.id.viewDataProduced);
        viewDisplaySensorDataRecorded = (TextView) findViewById(R.id.viewDataRecorded);

        viewDisplayStatus.setBackgroundColor(RED);
        viewDisplayOSI.setBackgroundColor(RED);
        viewDisplayTSC.setBackgroundColor(RED);
        viewDisplayTSD.setBackgroundColor(RED);
        viewLabelStatus.setBackgroundColor(RED);
        viewLabelOSI.setBackgroundColor(RED);
        viewLabelTSC.setBackgroundColor(RED);
        viewLabelTSD.setBackgroundColor(RED);



        viewDisplayNodeId.setText("Node ID: " + String.valueOf(mNodeId));

        orientTasksHandler = new Handler();
        restartScanningHandler = new Handler();
        connectAndCommunicateHandler = new Handler();


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
            watchDogTimer = 0;
            Log.i("MainActivity",action);
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                //Callback1
                Log.i("MainActivity","Connected to BLE Device.");
                if(mBluetoothLeService.discoverServices())
                    Log.i("MainActivity","discoverServices() called successfully.");
                else
                    Log.i("MainActivity","Failed to call discoverServices().");
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                Log.i("MainActivity","Disconnected from BLE Device");
                mBluetoothLeService.close();
                if(communicationRunning == true) { //If the client has received a ACTION_GATT_DISCONNECTED without the communicationRunning flag reset, there has been an error and the remote node has called for a disconnect. So the client should react to the error and disconnect and call for a connectAndCommunicate() again.
                    communicationRunning = false;
                    Log.i("MainActivity","Posting a 3s delayed call to connectAndCommunicate.");
                    connectAndCommunicateHandler.postDelayed(connectAndCommunicateRunnable, 3000);
                }

            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Get all the supported services and characteristics on the user interface and write Orient Parameters
                Log.i("MainActivity","Services discovered successfully");
                if(mBluetoothLeService.readOrientParameters())
                    Log.i("MainActivity","Call to read ORIENT parameters sent successfully.");
                else
                    Log.i("MainActivity","Call to read ORIENT parameters failed.");
            } else if (BluetoothLeService.ACTION_ORIENT_DATA_AVAILABLE.equals(action)) {
                Log.i("MainActivity","Responding to ORIENT parameters.");
                byte dataBuffer[];
                dataBuffer = intent.getByteArrayExtra(BluetoothLeService.EXTRA_DATA);
                int iOSIremote = byteToInt(dataBuffer[0]);
                int iTSCremote = byteToInt(dataBuffer[1]);
                int iTSDremote = byteToInt(dataBuffer[2]);
                Log.i("MainActivity","OSI = " + iOSIremote + ", TSC = " + iTSCremote + ", TSD = " + iTSDremote);
                if ((iOSIremote > iOSI) || ((iOSIremote==iOSI) && (iTSDremote > iTSC)) ) {
                    if(mBluetoothLeService.readSensorData())
                        Log.i("MainActivity","Call to read sensor data sent successfully.");
                    else
                        Log.i("MainActivity","Call to read sensor data failed.");
                } else {
                    Log.i("MainActivity","OSI found to be unsuitable. Disconnecting from remote device.");
                    communicationRunning = false;
                    mBluetoothLeService.disconnect();
                    Log.i("MainActivity","Posting a 3s delayed call to connectAndCommunicate.");
                    connectAndCommunicateHandler.postDelayed(connectAndCommunicateRunnable, 3000); //Provide an extended time of 3 seconds for the BLE Service to disconnect from an erroneous link
                }
            } else if (BluetoothLeService.ACTION_SENSOR_DATA_AVAILABLE.equals(action)) {
                Log.i("MainActivity","Responding to sensor data.");
                byte sensorDataBuffer[];
                sensorDataBuffer = intent.getByteArrayExtra(BluetoothLeService.EXTRA_DATA);

                Log.i("MainActivity","Received data from the remote node in sensorDataBuffer = " + sensorDataBuffer.toString());

                int rxSensorDataNodeId = byteToInt(sensorDataBuffer[0]);
                int rxSensorDataTimeStamp = (16777216*byteToInt(sensorDataBuffer[1])) + (65536*byteToInt(sensorDataBuffer[2])) + (256*byteToInt(sensorDataBuffer[3])) + byteToInt(sensorDataBuffer[4]);
                int rxSensorDataNumberOfHops = byteToInt(sensorDataBuffer[5]) + 1;
                byte rxSensorData[] = {1, 2, 3, 4, 5};

                rxSensorData[0] = sensorDataBuffer[6];
                rxSensorData[1] = sensorDataBuffer[7];
                rxSensorData[2] = sensorDataBuffer[8];
                rxSensorData[3] = sensorDataBuffer[9];
                rxSensorData[4] = sensorDataBuffer[10];
                SensorData sd = new SensorData(rxSensorDataNodeId, rxSensorDataTimeStamp, rxSensorDataNumberOfHops, rxSensorData);

                Log.i("MainActivity","Sensor Data Node Id = " + String.valueOf(rxSensorDataNodeId));
                Log.i("MainActivity","Sensor Data Time Stamp = " + String.valueOf(rxSensorDataTimeStamp));
                Log.i("MainActivity","Sensor Data Number Of Hops = " + String.valueOf(rxSensorDataNumberOfHops));
                Log.i("MainActivity","Sensor Data = " + String.valueOf(byteToInt(rxSensorData[0])) + "," + String.valueOf(byteToInt(rxSensorData[1])) + "," + String.valueOf(byteToInt(rxSensorData[2])) + "," + String.valueOf(byteToInt(rxSensorData[3])) + "," + String.valueOf(byteToInt(rxSensorData[4])));

                //if sensorDataTimeStamp is non zero, this sensorDataItem must be added to the sink/relay list
                //and the next item in the list must be read.
                //else the client must move to write orient parameters to the server and further to close the connection


                /* OLD IMPLEMENTATION. This implementation gathers all sensor data from the remote node until zeros are received.
                After a zero sensor data is received, if this node is a sink, it will write all the sensor buffer to the file.
                if (rxSensorDataTimeStamp!=0) {
                    mSensorDataList.add(sd);
                    updateLocalUi();
                    if(mBluetoothLeService.readSensorData())
                        Log.i("MainActivity","Call to read sensor data sent successfully.");
                    else
                        Log.i("MainActivity","Call to read sensor data failed.");

                } else {
                    //When the sensorDataTimeStamp is zero, the end of the sensor data in the remote node has been reached,
                    //a sink node must record all the data from the sensor data list in a file now before ORIENTing the remote node
                    //We check if there is data recorded or if the remote node had no data. If this node is a sink, it must record the data to a file.
                    if((iStatus==1)&&(getSensorDataCount()>0)) {
                        int lastItem = getSensorDataCount() - 1;

                        //Using a local sensorData object to buffer the last item in the sensor data list

                        try {
                            file.createNewFile();

                            int index = 0;
                            outputStream = new FileOutputStream(new File(file.getAbsoluteFile().toString()), true);
                            //Write the present time to the file so as to maintain a record of when the data was received at the sink
                            fileContents = "\r\nReceived at the sink at " + String.valueOf(((int)(currentTimeMillis()/1000))) + "\r\n";
                            outputStream.write(fileContents.getBytes());
                            Log.i("MainActivity", "Writing to file : " + fileContents);

                            for (index=lastItem; index>=0; index--) { //Loop from last item in the list to the zeroth item
                                SensorData sensorData = getItem(index); //Capture the information of this data into a local object
                                //Prepare this information to write into a file
                                fileContents = (String.valueOf(sensorData.nodeId) + " : " + String.valueOf(sensorData.timeStamp) + " : " + String.valueOf(byteToInt(sensorData.data[0])) + "," + String.valueOf(byteToInt(sensorData.data[1])) + "," + String.valueOf(byteToInt(sensorData.data[2])) + "," + String.valueOf(byteToInt(sensorData.data[3])) + "," + String.valueOf(byteToInt(sensorData.data[4])) + "\r\n");
                                outputStream.write(fileContents.getBytes());
                                Log.i("MainActivity", "Writing to file : " + fileContents);
                                //Remove this data item from the data buffer after it has been written to file
                                mSensorDataList.remove(index);
                                Log.i("MainActivity", "Removing item " + index + "from local buffer.");
                                //Increment the number of data units recorded to display on the UI
                                sensorDataRecorded++;
                                updateLocalUi();
                            }
                            outputStream.close();
                        } catch (Exception e) {
                            e.printStackTrace();
                            Log.i("MainActivity","Error in writing to file.");
                        }
                    }

                    //After the data has been recorded, the node must proceed to write orient parameters to the remote node
                    if(mBluetoothLeService.writeOrientParameters(iOSI, iTSC, 0))
                        Log.i("MainActivity","Call to write ORIENT parameters sent successfully.");
                    else {
                        Log.i("MainActivity","Call to write ORIENT parameters failed.");
                        mBluetoothLeService.disconnect();
                    }
                }*/

                //NEW IMPLEMENTATION. This implementation decides whether to add sensor data to the list or to write it to a file,
                //depending on whether this node is a relay or a sink.
                //After a zero sensor data is received, this node sends a request to write orient paramaters to the remote node as per the
                //ORIENT procedure.
                if(sd.timeStamp != 0) {
                    if (iStatus==1) {
                        try {
                            file.createNewFile();

                            outputStream = new FileOutputStream(new File(file.getAbsoluteFile().toString()), true);
                            Log.i("MainActivity", "Writing to file : " + fileContents);

                            fileContents = String.valueOf(((int) (currentTimeMillis() / 1000))) + " : " + (String.valueOf(sd.nodeId) + " : " + String.valueOf(sd.timeStamp) + " : " + String.valueOf(sd.numberOfHops) + " : " + String.valueOf(byteToInt(sd.data[0])) + "," + String.valueOf(byteToInt(sd.data[1])) + "," + String.valueOf(byteToInt(sd.data[2])) + "," + String.valueOf(byteToInt(sd.data[3])) + "," + String.valueOf(byteToInt(sd.data[4])) + "\r\n");
                            outputStream.write(fileContents.getBytes());
                            Log.i("MainActivity", "Writing to file : " + fileContents);
                            sensorDataRecorded++;
                            updateLocalUi();
                            outputStream.close();
                        } catch (Exception e) {
                            e.printStackTrace();
                            Log.i("MainActivity", "Error in writing to file.");
                        }
                    } else {
                        mSensorDataList.add(sd);
                        updateLocalUi();
                    }

                    if(mBluetoothLeService.readSensorData())
                        Log.i("MainActivity","Call to read sensor data sent successfully.");
                    else
                        Log.i("MainActivity","Call to read sensor data failed.");
                } else {
                    if(mBluetoothLeService.writeOrientParameters(iOSI, iTSC, 0))
                        Log.i("MainActivity","Call to write ORIENT parameters sent successfully.");
                    else {
                        Log.i("MainActivity","Call to write ORIENT parameters failed.");
                        mBluetoothLeService.disconnect();
                    }
                }

            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                byte dataBuffer[];
                dataBuffer = intent.getByteArrayExtra(BluetoothLeService.EXTRA_DATA);
                Log.i("MainActivity","Data received : " + byteToInt(dataBuffer[0]) + "," + byteToInt(dataBuffer[1]) + ":" + byteToInt(dataBuffer[2]));

            } else if (BluetoothLeService.ACTION_CHARACTERISTIC_WRITTEN.equals(action)) {
                Log.i("Server","ORIENT Parameters written.");
                //Client is actively closing the connection as procedure is over
                mBluetoothLeService.disconnect();
                communicationRunning = false;
                Log.i("MainActivity","Posting a 3s delayed call to connectAndCommunicate.");
                connectAndCommunicateHandler.postDelayed(connectAndCommunicateRunnable, 3000); //Provide an extended time of 3 seconds for the BLE Service to disconnect from an erroneous link
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
                        setupFragments();
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

        serverOrientService = mBluetoothGattServer.getService(OrientProfile.ORIENT_MESH_SERVICE);
        serverOrientParameters = serverOrientService.getCharacteristic(OrientProfile.ORIENT_PARAMETERS_CHARACTERISTIC);
        serverSensorData = serverOrientService.getCharacteristic(OrientProfile.SENSOR_DATA_CHARACTERISTIC);

        orientTasks.run(); //This call starts running ORIENT tasks continually with auto callbacks

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
                mConnected = true;
                Log.i("GattServer","Connection opened. mConnected = true;");
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(Constants.TAG, "BluetoothDevice DISCONNECTED: " + device);
                //Remove device from any active subscriptions
                mRegisteredDevices.remove(device);
                mConnected = false;
                Log.i("GattServer","Connection closed. mConnected = false");
            }
        }

        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset,
                                                BluetoothGattCharacteristic characteristic) {
            long now = currentTimeMillis();

            bOSI = (byte) iOSI;
            bTSC = (byte) iTSC;
            bTSD = (byte) iTSD;

            if (OrientProfile.ORIENT_PARAMETERS_CHARACTERISTIC.equals(characteristic.getUuid())) {
                Log.i(Constants.TAG, "Received request to read ORIENT parameters.");
                serverBuffer[0] = bOSI;
                serverBuffer[1] = bTSC;
                serverBuffer[2] = bTSD;
                mBluetoothGattServer.sendResponse(device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        0,
                        serverBuffer
                        );
            } else if (OrientProfile.SENSOR_DATA_CHARACTERISTIC.equals(characteristic.getUuid())) {
                Log.i("GattServer", "Read Sensor Data Characteristic");

                //Initialize the sensorDataChar to zero so that if there is no data, zeros gets transmitted thereby indicating absence of data.
                sensorDataChar[0] = 0;
                sensorDataChar[1] = 0;
                sensorDataChar[2] = 0;
                sensorDataChar[3] = 0;
                sensorDataChar[4] = 0;
                sensorDataChar[5] = 0;
                sensorDataChar[6] = 0;
                sensorDataChar[7] = 0;
                sensorDataChar[8] = 0;
                sensorDataChar[9] = 0;
                sensorDataChar[10] = 0;


                //If the length of the list of sensor data items is zero, the server must respond with zeros.
                //therefore the below code of retrieving sensor data and responding with it, is performed only if sensordatalength > 0
                if(getSensorDataCount()>0) {
                    int lastItem = getSensorDataCount() - 1;

                    //Using a local byte array to pick up data from the last item in the sensor data list and provide to the BLE characteristic
                    SensorData SDPacket = getItem(lastItem);

                    //If the length of the list is non-zero, the server must respond with the last item in the list.

                    //NodeID: Byte0 of the sensor data characteristic
                    sensorDataChar[0] = (byte) SDPacket.nodeId;
                    //TimeStamp : Byte1 - Byte4 of the sensor data characteristic
                    sensorDataChar[1] = (byte) (SDPacket.timeStamp / 16777216);
                    sensorDataChar[2] = (byte) ((SDPacket.timeStamp % 16777216) / 65536);
                    sensorDataChar[3] = (byte) ((SDPacket.timeStamp % 65536) / 256);
                    sensorDataChar[4] = (byte) (SDPacket.timeStamp % 256);
                    //NumberOfHops : Byte5 of the sensor data characteristic
                    sensorDataChar[5] = (byte) (SDPacket.numberOfHops);
                    //SensorData: Byte6 - Byte10 of the sensor data characteristic
                    sensorDataChar[6] = SDPacket.data[0];
                    sensorDataChar[7] = SDPacket.data[1];
                    sensorDataChar[8] = SDPacket.data[2];
                    sensorDataChar[9] = SDPacket.data[3];
                    sensorDataChar[10] = SDPacket.data[4];

                    Log.i("GattServer","Responding with sensor data buffer = " + sensorDataChar.toString());
                    Log.i("GattServer", SDPacket.nodeId + " : " + SDPacket.timeStamp + " : " + String.valueOf(SDPacket.data[0])+","+String.valueOf(SDPacket.data[1])+","+String.valueOf(SDPacket.data[2])+","+String.valueOf(SDPacket.data[3])+","+String.valueOf(SDPacket.data[4]));
                    //Remove this sensor Data object from the sensor data list
                    mSensorDataList.remove(lastItem);
                }

                serverSensorData.setValue(sensorDataChar);
                mBluetoothGattServer.sendResponse(device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        0,
                        sensorDataChar);
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

            Log.i("MainActivity","Characteristic write request received " + characteristic.getUuid().toString());
            if (characteristic.getUuid().equals(OrientProfile.ORIENT_PARAMETERS_CHARACTERISTIC)) {
                //set the OSI to the value received from the sink/relay. The sink/relay increments its own OSI value and sends it.
                iOSI = byteToInt(data[0]);
                //set the TSC  to the value received from the sink/relay. The sink/relay replicates its own TSC value onto the next node.
                iTSC = byteToInt(data[1]);
                //set the TSD to zero as a connection has just been established.
                iTSD = 0;
                iStatus = 2;    //Move status of the device to RELAY.
                Log.i("MainActivity","OSI and TSC written " + Integer.toString(data[0]) + " "  + Integer.toString(data[1]));
                scanningStartFlag = true;
            }

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
        viewDisplaySensorDataCount.setText("Sensor Data Count : " + String.valueOf(getSensorDataCount()));
        viewDisplaySensorDataProduced.setText("Sensor Data Produced : " + String.valueOf(sensorDataProduced));
        viewDisplaySensorDataRecorded.setText("Sensor Data Recorded : " + String.valueOf(sensorDataRecorded));
        switch(iStatus)
        {
            case 0:
                viewDisplayStatus.setText("Sensor");
                viewDisplayStatus.setBackgroundColor(RED);
                viewDisplayOSI.setBackgroundColor(RED);
                viewDisplayTSC.setBackgroundColor(RED);
                viewDisplayTSD.setBackgroundColor(RED);
                viewLabelStatus.setBackgroundColor(RED);
                viewLabelOSI.setBackgroundColor(RED);
                viewLabelTSC.setBackgroundColor(RED);
                viewLabelTSD.setBackgroundColor(RED);
                viewDisplaySensorDataRecorded.setVisibility(View.INVISIBLE);
                break;

            case 1:
                viewDisplayStatus.setText("Sink");
                viewDisplayStatus.setBackgroundColor(GREEN);
                viewDisplayOSI.setBackgroundColor(GREEN);
                viewDisplayTSC.setBackgroundColor(GREEN);
                viewDisplayTSD.setBackgroundColor(GREEN);
                viewLabelStatus.setBackgroundColor(GREEN);
                viewLabelOSI.setBackgroundColor(GREEN);
                viewLabelTSC.setBackgroundColor(GREEN);
                viewLabelTSD.setBackgroundColor(GREEN);
                viewDisplaySensorDataRecorded.setVisibility(View.VISIBLE);
                break;

            case 2:
                viewDisplayStatus.setText("Relay");
                viewDisplayStatus.setBackgroundColor(GREEN);
                viewDisplayOSI.setBackgroundColor(GREEN);
                viewDisplayTSC.setBackgroundColor(GREEN);
                viewDisplayTSD.setBackgroundColor(GREEN);
                viewLabelStatus.setBackgroundColor(GREEN);
                viewLabelOSI.setBackgroundColor(GREEN);
                viewLabelTSC.setBackgroundColor(GREEN);
                viewLabelTSD.setBackgroundColor(GREEN);
                viewDisplaySensorDataRecorded.setVisibility(View.INVISIBLE);
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
            sink = true;
            sensorDataRecorded = 0;

            if(isExternalStorageWritable()) {
                Log.i("MainActivity","External Storage Writable.");
            } else {
                Log.i("MainActivity","External Storage Not Writable.");
            }

            fileName = String.valueOf(((int)(currentTimeMillis()/1000)));
            fileName = fileName + ".txt";
            file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), fileName);
            Log.i("MainActivity","Sink File Name : " + fileName + "\r\n");

            try {
                file.createNewFile();

                outputStream = new FileOutputStream (new File(file.getAbsoluteFile().toString()), true);
                fileContents = "RecordingTime : NodeID : UnixTime(Epoch) : NumberOfHops : Data\r\n";
                outputStream.write(fileContents.getBytes());
                Log.i("MainActivity","Writing to file : " + fileContents);
                outputStream.close();
            } catch (Exception e) {
                e.printStackTrace();
                Log.i("MainActivity","Error in writing to file.");
            }

            updateLocalUi();
            startScanning();

        }
        else {

            iOSI = 255;
            iTSC = 120;
            iTSD = 60;
            iStatus = 0;
            orientTasksProgress = 0;
            sink = false;


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
        //stopScanning();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    Runnable orientTasks = new Runnable() {
        public void run() {
            ScannerFragment scannerFragment = (ScannerFragment) getSupportFragmentManager().findFragmentById(R.id.scanner_fragment_container);
            byte parameters[] = {(byte) iOSI, (byte) iTSC, (byte) iTSD};
            byte sensorDataChar[] = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};

            if (scanningStartFlag) {
                startScanning();
                scanningStartFlag = false;
            }

            if (communicationRunning) {
                watchDogTimer++;
                Log.i("MainActivity","WatchDogTimer = " + String.valueOf(watchDogTimer));

                if (watchDogTimer > 15) {
                    Log.i("MainActivity","WatchDogTimer overflow. Process will continue with next iteration of the communication loop after disconnect.");
                    watchDogTimer = 0;
                    //Client is actively closing the connection as the watchdogtimer has overflowed
                    mBluetoothLeService.disconnect();
                    communicationRunning = false;
                    Log.i("MainActivity","Posting a 3s delayed call to connectAndCommunicate.");
                    connectAndCommunicateHandler.postDelayed(connectAndCommunicateRunnable, 3000); //Provide an extended time of 5 seconds for the BLE Service to disconnect from an erroneous link
                }
            }

            orientTasksHandler.postDelayed(orientTasks, 1000);
            switch (iStatus) {
                case 0: //sensor
                    sensorDataTimer++;
                    if((AdvertiserFragment.advertising)&&(sensorDataTimer >= sensorDataRate)) {
                        sensorDataTimer = 0;
                        byte sensorData[] = {0,0,0,0,0};
                        sensorData[0] = (byte) rand.nextInt(100);
                        sensorData[1] = (byte) rand.nextInt(100);
                        sensorData[2] = (byte) rand.nextInt(100);
                        sensorData[3] = (byte) rand.nextInt(100);
                        sensorData[4] = (byte) rand.nextInt(100);
                        SensorData SDPacket = new SensorData(mNodeId, (int)(currentTimeMillis()/1000), 0, sensorData);


                        //NodeID: Byte0 of the sensor data characteristic
                        sensorDataChar[0] = (byte) SDPacket.nodeId;
                        //TimeStamp : Byte1 - Byte4 of the sensor data characteristic
                        sensorDataChar[1] = (byte) (SDPacket.timeStamp / 16777216);
                        sensorDataChar[2] = (byte) ((SDPacket.timeStamp % 16777216) / 65536);
                        sensorDataChar[3] = (byte) ((SDPacket.timeStamp % 65536) / 256);
                        sensorDataChar[4] = (byte) (SDPacket.timeStamp % 256);
                        //SensorData: Byte5 - Byte9 of the sensor data characteristic
                        sensorDataChar[5] = sensorData[0];
                        sensorDataChar[6] = sensorData[1];
                        sensorDataChar[7] = sensorData[2];
                        sensorDataChar[8] = sensorData[3];
                        sensorDataChar[9] = sensorData[4];

                        mSensorDataList.add(SDPacket);
                        sensorDataProduced++;
                        serverSensorData.setValue(sensorDataChar);

                        Log.i("MainActivity","Sensor Data Length = " + getSensorDataCount());
                        int i;
                        for(i=0;i<getSensorDataCount();i++) {
                            SensorData mSD = mSensorDataList.get(i);
                            Log.i("MainActivity", mSD.nodeId + " : " + mSD.timeStamp + " : " + String.valueOf(mSD.data[0])+","+String.valueOf(mSD.data[1])+","+String.valueOf(mSD.data[2])+","+String.valueOf(mSD.data[3])+","+String.valueOf(mSD.data[4]));
                        }
                    }
                    if(iTSD<60) {
                        iTSD++;
                        iTSC++;
                    }
                    serverOrientParameters.setValue(parameters);
                    updateLocalUi();
                    break;


                case 1: //sink
                    updateLocalUi();
                    switch (orientTasksProgress) {
                        case 0:
                            //showMsg("Scanning");
                            //scannerFragment.clearScanResult();
                            //startScanning();
                            break;

                        case 5:
                            //analyse list of ble devices
                            //connect with these devices
                            //write to the gatt server of these devices
                            //showMsg("Scanning stopped");
                            break;

                        case 10:
                            break;

                        default:
                            break;


                    }
                    if(orientTasksProgress < 10) {
                        orientTasksProgress++;
                    }
                    else {
                        orientTasksProgress = 0;
                    }
                    break;

                case 2: //relay
                    sensorDataTimer++;
                    if((AdvertiserFragment.advertising)&&(sensorDataTimer >= sensorDataRate)) {
                        sensorDataTimer = 0;
                        byte sensorData[] = {0,0,0,0,0};
                        sensorData[0] = (byte) rand.nextInt(100);
                        sensorData[1] = (byte) rand.nextInt(100);
                        sensorData[2] = (byte) rand.nextInt(100);
                        sensorData[3] = (byte) rand.nextInt(100);
                        sensorData[4] = (byte) rand.nextInt(100);
                        SensorData SDPacket = new SensorData(mNodeId, (int)(currentTimeMillis()/1000), 0, sensorData);


                        //NodeID: Byte0 of the sensor data characteristic
                        sensorDataChar[0] = (byte) SDPacket.nodeId;
                        //TimeStamp : Byte1 - Byte4 of the sensor data characteristic
                        sensorDataChar[1] = (byte) (SDPacket.timeStamp / 16777216);
                        sensorDataChar[2] = (byte) ((SDPacket.timeStamp % 16777216) / 65536);
                        sensorDataChar[3] = (byte) ((SDPacket.timeStamp % 65536) / 256);
                        sensorDataChar[4] = (byte) (SDPacket.timeStamp % 256);
                        //SensorData: Byte5 - Byte9 of the sensor data characteristic
                        sensorDataChar[5] = sensorData[0];
                        sensorDataChar[6] = sensorData[1];
                        sensorDataChar[7] = sensorData[2];
                        sensorDataChar[8] = sensorData[3];
                        sensorDataChar[9] = sensorData[4];

                        mSensorDataList.add(SDPacket);
                        sensorDataProduced++;
                        serverSensorData.setValue(sensorDataChar);

                        Log.i("MainActivity","Sensor Data Length = " + getSensorDataCount());
                        int i;
                        for(i=0;i<getSensorDataCount();i++) {
                            SensorData mSD = mSensorDataList.get(i);
                            Log.i("MainActivity", mSD.nodeId + " : " + mSD.timeStamp + " : " + String.valueOf(mSD.data[0])+","+String.valueOf(mSD.data[1])+","+String.valueOf(mSD.data[2])+","+String.valueOf(mSD.data[3])+","+String.valueOf(mSD.data[4]));
                        }
                    }
                    switch (orientTasksProgress) {
                        case 0:
                            //showMsg("Scanning");
                            //scannerFragment.clearScanResult();
                            //startScanning();
                            break;

                        case 5:
                            //analyse list of ble devices
                            //connect with these devices
                            //write to the gatt server of these devices
                            //showMsg("Scanning stopped");
                            break;

                        case 10:
                            break;

                        default:
                            break;


                    }
                    if(orientTasksProgress < 10) {
                        orientTasksProgress++;
                    }
                    else {
                        orientTasksProgress = 0;
                    }

                    if(iTSC<60)
                    {
                        iTSC++;
                    }
                    else
                    {
                        iStatus = 0;
                        iTSD = 0;
                        iOSI = 255;
                    }
                    serverOrientParameters.setValue(parameters);
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
        intentFilter.addAction(BluetoothLeService.ACTION_CHARACTERISTIC_WRITTEN);
        intentFilter.addAction(BluetoothLeService.ACTION_ORIENT_DATA_AVAILABLE);
        intentFilter.addAction(BluetoothLeService.ACTION_SENSOR_DATA_AVAILABLE);
        return intentFilter;
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

    public void postScanProcess(ScanResultAdapter mAdapter) {
        scanResultsList = mAdapter.mArrayList;
        numberOfDevicesFound = mAdapter.getCount();
        scanResultIndex = 0;
        Log.i("MainActivity","Found " + numberOfDevicesFound + " devices.");
        if(numberOfDevicesFound > 0) {
            Log.i("MainActivity","Posting a 1s delayed call to connectAndCommunicate.");
            connectAndCommunicateHandler.postDelayed(connectAndCommunicateRunnable, 1000);
        } else {
            if(iStatus>0) {
                ScannerFragment scannerFragment = (ScannerFragment) getSupportFragmentManager().findFragmentById(R.id.scanner_fragment_container);
                scannerFragment.clearScanResult();
                Log.i("MainActivity","Scanning started again.");
                restartScanningHandler.postDelayed(delayedCallToStartScanning, 5000);
            }
        }
    }

    public void connectAndCommunicate() {
        Log.i("MainActivity","ConnectAndCommunicate " + scanResultIndex);
        numberOfDevicesFound = mAdapter.getCount();
        if((numberOfDevicesFound>0)&&(scanResultIndex<numberOfDevicesFound)) {
            Log.i("MainActivity","Connecting to list item " + scanResultIndex);
            communicationRunning = true;
            mBluetoothLeService.connect(scanResultsList.get(scanResultIndex).getDevice().getAddress());
            scanResultIndex++;
        } else {
            Log.i("MainActivity","Reached end of list of devices.");
            scanResultIndex = 0;
            if(iStatus > 0) {//iStatus = 0 for sensor. iStatus = 1 for sink. iStatus = 2 for relay.
                ScannerFragment scannerFragment = (ScannerFragment) getSupportFragmentManager().findFragmentById(R.id.scanner_fragment_container);
                scannerFragment.clearScanResult();

                Log.i("MainActivity","Scanning shall start again after 5 seconds.");
                restartScanningHandler.postDelayed(delayedCallToStartScanning, 5000);
            }
        }
    }

    public int getSensorDataCount() {
        return mSensorDataList.size();
    }

    public SensorData getItem (int position) {
        return mSensorDataList.get(position);
    }


    /**
     * Clear out the sensor data buffer.
     */
    public void clear() {
        mSensorDataList.clear();
    }

    /**
     * Add a SensorData item to the buffer with current system time as timestamp
     */
    public void add(SensorData sensorData) {
         mSensorDataList.add(sensorData);
    }


    /* Checks if external storage is available for read and write */
    public boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }

    //This is the handler for the escape process that closes a stuck communication loop and restarts scanning
    Runnable delayedCallToStartScanning = new Runnable() {
        public void run() {
            startScanning();
        }
    };

    //This is the runnable for the connectAndCommunicate process so that connectAndCommunicate can be called after a small differentiating time gap
    Runnable connectAndCommunicateRunnable = new Runnable() {
        public void run() {
            connectAndCommunicate();
        }
    };
}