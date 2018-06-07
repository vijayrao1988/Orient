/*
 * Copyright 2017, The Android Open Source Project
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

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;

import java.util.Calendar;
import java.util.UUID;

import static com.example.android.bluetoothadvertisements.TimeProfile.CLIENT_CONFIG;
import static com.example.android.bluetoothadvertisements.TimeProfile.CURRENT_TIME;

/**
 * Implementation of the Bluetooth GATT Time Profile.
 * https://www.bluetooth.com/specifications/adopted-specifications
 */
public class OrientProfile {
    private static final String TAG = OrientProfile.class.getSimpleName();

    /* Custom GUIDs generated for the orient protocol
    d69faef0-a1c5-4862-93ba-f14744d9a3f7
    8f0048be-a048-40c8-9454-588e5d1e7423
    be1f34bc-8f4d-45eb-82f1-ee145591c4cb
    44c4b881-6fe7-4de3-9be7-7d63120e3795
     */

    // ORIENT Profile Service UUID
    public static UUID ORIENT_MESH_SERVICE = UUID.fromString("01234567-0000-1000-8000-0123456789ab");

    // OSI Characteristic UUID
    public static UUID ORIENT_PARAMETERS_CHARACTERISTIC = UUID.fromString("01234567-0001-1000-8000-0123456789ab");

    // Sensor Data Characteristic UUID
    public static UUID SENSOR_DATA_CHARACTERISTIC = UUID.fromString("01234567-0002-1000-8000-0123456789ab");

    // TSC Characteristic UUID
//    public static UUID TSC_CHARACTERISTIC = UUID.fromString("01234567-0002-1000-8000-0123456789ab");

    // TSD Characteristic UUID
//    public static UUID TSD_CHARACTERISTIC = UUID.fromString("01234567-0003-1000-8000-0123456789ab");

    //Orient Status Characteristic UUID
//    public static UUID STATUS_CHARACTERISTIC = UUID.fromString("01234567-0004-1000-8000-0123456789ab");


    // Adjustment Flags
    public static final byte ADJUST_NONE     = 0x0;
    public static final byte ADJUST_MANUAL   = 0x1;
    public static final byte ADJUST_EXTERNAL = 0x2;
    public static final byte ADJUST_TIMEZONE = 0x4;
    public static final byte ADJUST_DST      = 0x8;

    /**
     * Return a configured {@link BluetoothGattService} instance for the
     * Current Time Service.
     */
    public static BluetoothGattService createOrientService() {
        BluetoothGattService service = new BluetoothGattService(ORIENT_MESH_SERVICE,
                BluetoothGattService.SERVICE_TYPE_PRIMARY);

        // Orientation Stream Index characteristic
        BluetoothGattCharacteristic orientParameters = new BluetoothGattCharacteristic(ORIENT_PARAMETERS_CHARACTERISTIC,
                //Read-only characteristic, supports notifications
                BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_READ | BluetoothGattCharacteristic.PERMISSION_WRITE);

        // Time Since Contact characteristic
        BluetoothGattCharacteristic sensorData = new BluetoothGattCharacteristic(SENSOR_DATA_CHARACTERISTIC,
                //Read-only characteristic
                BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_READ | BluetoothGattCharacteristic.PERMISSION_WRITE);

        service.addCharacteristic(orientParameters);
        service.addCharacteristic(sensorData);

        return service;
    }



    /* Bluetooth Weekday Codes */
    private static final byte DAY_UNKNOWN = 0;
    private static final byte DAY_MONDAY = 1;
    private static final byte DAY_TUESDAY = 2;
    private static final byte DAY_WEDNESDAY = 3;
    private static final byte DAY_THURSDAY = 4;
    private static final byte DAY_FRIDAY = 5;
    private static final byte DAY_SATURDAY = 6;
    private static final byte DAY_SUNDAY = 7;


}
