package com.example.android.bluetoothadvertisements;

/**
 * Created by blitz on 03/06/18.
 */

public class SensorData extends Object {
    int nodeId;
    int timeStamp;
    byte data[] = {0x01,0x02,0x03,0x04,0x05};

    SensorData(int sensorNodeId, int sensorDataTimeStamp) {
        nodeId = sensorNodeId;
        timeStamp = sensorDataTimeStamp;
    };

}
