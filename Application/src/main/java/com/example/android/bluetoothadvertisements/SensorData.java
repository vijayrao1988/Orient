package com.example.android.bluetoothadvertisements;

/**
 * Created by blitz on 03/06/18.
 */

public class SensorData extends Object {
    int nodeId;
    int timeStamp;
    int numberOfHops;
    byte data[] = {0x01,0x02,0x03,0x04,0x05};

    SensorData(int sensorNodeId, int sensorDataTimeStamp, int sensorNumberOfHops, byte sensorData[]) {
        nodeId = sensorNodeId;
        timeStamp = sensorDataTimeStamp;
        numberOfHops = sensorNumberOfHops;
        data[0] = sensorData[0];
        data[1] = sensorData[1];
        data[2] = sensorData[2];
        data[3] = sensorData[3];
        data[4] = sensorData[4];
    };

}
