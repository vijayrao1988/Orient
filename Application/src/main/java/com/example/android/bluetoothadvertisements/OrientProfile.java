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

    /**
     * Construct the field values for a Current Time characteristic
     * from the given epoch timestamp and adjustment reason.
     */
    public static byte[] getExactTime(long timestamp, byte adjustReason) {
        Calendar time = Calendar.getInstance();
        time.setTimeInMillis(timestamp);

        byte[] field = new byte[10];

        // Year
        int year = time.get(Calendar.YEAR);
        field[0] = (byte) (year & 0xFF);
        field[1] = (byte) ((year >> 8) & 0xFF);
        // Month
        field[2] = (byte) (time.get(Calendar.MONTH) + 1);
        // Day
        field[3] = (byte) time.get(Calendar.DATE);
        // Hours
        field[4] = (byte) time.get(Calendar.HOUR_OF_DAY);
        // Minutes
        field[5] = (byte) time.get(Calendar.MINUTE);
        // Seconds
        field[6] = (byte) time.get(Calendar.SECOND);
        // Day of Week (1-7)
        field[7] = getDayOfWeekCode(time.get(Calendar.DAY_OF_WEEK));
        // Fractions256
        field[8] = (byte) (time.get(Calendar.MILLISECOND) / 256);

        field[9] = adjustReason;

        return field;
    }

    /* Time bucket constants for local time information */
    private static final int FIFTEEN_MINUTE_MILLIS = 900000;
    private static final int HALF_HOUR_MILLIS = 1800000;

    /**
     * Construct the field values for a Local Time Information characteristic
     * from the given epoch timestamp.
     */
    public static byte[] getLocalTimeInfo(long timestamp) {
        Calendar time = Calendar.getInstance();
        time.setTimeInMillis(timestamp);

        byte[] field = new byte[2];

        // Time zone
        int zoneOffset = time.get(Calendar.ZONE_OFFSET) / FIFTEEN_MINUTE_MILLIS; // 15 minute intervals
        field[0] = (byte) zoneOffset;

        // DST Offset
        int dstOffset = time.get(Calendar.DST_OFFSET) / HALF_HOUR_MILLIS; // 30 minute intervals
        field[1] = getDstOffsetCode(dstOffset);

        return field;
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

    /**
     * Convert a {@link Calendar} weekday value to the corresponding
     * Bluetooth weekday code.
     */
    private static byte getDayOfWeekCode(int dayOfWeek) {
        switch (dayOfWeek) {
            case Calendar.MONDAY:
                return DAY_MONDAY;
            case Calendar.TUESDAY:
                return DAY_TUESDAY;
            case Calendar.WEDNESDAY:
                return DAY_WEDNESDAY;
            case Calendar.THURSDAY:
                return DAY_THURSDAY;
            case Calendar.FRIDAY:
                return DAY_FRIDAY;
            case Calendar.SATURDAY:
                return DAY_SATURDAY;
            case Calendar.SUNDAY:
                return DAY_SUNDAY;
            default:
                return DAY_UNKNOWN;
        }
    }

    /* Bluetooth DST Offset Codes */
    private static final byte DST_STANDARD = 0x0;
    private static final byte DST_HALF     = 0x2;
    private static final byte DST_SINGLE   = 0x4;
    private static final byte DST_DOUBLE   = 0x8;
    private static final byte DST_UNKNOWN = (byte) 0xFF;

    /**
     * Convert a raw DST offset (in 30 minute intervals) to the
     * corresponding Bluetooth DST offset code.
     */
    private static byte getDstOffsetCode(int rawOffset) {
        switch (rawOffset) {
            case 0:
                return DST_STANDARD;
            case 1:
                return DST_HALF;
            case 2:
                return DST_SINGLE;
            case 4:
                return DST_DOUBLE;
            default:
                return DST_UNKNOWN;
        }
    }
}
