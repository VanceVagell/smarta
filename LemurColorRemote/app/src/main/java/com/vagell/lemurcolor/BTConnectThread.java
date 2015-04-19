package com.vagell.lemurcolor;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.util.Log;

import java.io.IOException;
import java.util.UUID;

public class BTConnectThread extends Thread {
    private BluetoothSocket mSocket;
    private final BluetoothDevice mDevice;
    private final BluetoothAdapter mBluetoothAdapter;
    private final LemurColorRemoteApplication mApplication;
    private final Handler mConnectHandler;
    private final Handler mCommHandler;

    public final static int MESSAGE_BT_CONNECTED = 0;

    /**
     * Shared UUID with the BT server code on tablet.
     */
    private static final UUID BT_UUID = UUID.fromString("05a9d5fe-d213-4f87-bdc2-fc5d0f1935af");

    public BTConnectThread(LemurColorRemoteApplication application, Handler connectHandler, Handler commHandler, BluetoothDevice device, BluetoothAdapter bluetoothAdapter) {
        mConnectHandler = connectHandler;
        mCommHandler = commHandler;
        mApplication = application;
        mBluetoothAdapter = bluetoothAdapter;
        mDevice = device;
        BluetoothSocket tmp = null;

        try {
            tmp = device.createRfcommSocketToServiceRecord(BT_UUID);
        } catch (Exception e) {
            e.printStackTrace();
        }
        mSocket = tmp;
    }

    public void run() {
        Log.d("LOG", "Starting bluetooth connection...");

        // Cancel discovery, just assume the 2 devices have paired before.
        mBluetoothAdapter.cancelDiscovery();

        try {
            // Connect, which blocks until it succeed.
            mSocket.connect();
        } catch (IOException connectException) {
            Log.d("LOG", "Warning: Bluetooth connection failed. Attempting fallback.");
            try {
                // Workaround Android BT stack changes: http://stackoverflow.com/questions/18657427/ioexception-read-failed-socket-might-closed-bluetooth-on-android-4-3/18786701#18786701
                mSocket = (BluetoothSocket) mDevice.getClass().getMethod("createRfcommSocket", new Class[]{int.class}).invoke(mDevice, 1);
                mSocket.connect();
            } catch (Exception e) {
                Log.d("LOG", "ERROR: Bluetooth fallback failed. Could not establish BT connection.");
                e.printStackTrace();

                // Unable to connect; close the socket and get out
                try {
                    mSocket.close();
                } catch (IOException closeException) {
                    closeException.printStackTrace();
                }
                return;
            }
        }

        Log.d("LOG", "Bluetooth connected to tablet.");
        mApplication.setBtSocket(mSocket);
        BTCommThread thread = new BTCommThread(mSocket, mCommHandler);
        thread.start();
        mApplication.setBtCommThread(thread);
        mConnectHandler.obtainMessage(MESSAGE_BT_CONNECTED).sendToTarget();
    }

    /** Will cancel an in-progress connection, and close the socket */
    public void cancel() {
        try {
            mSocket.close();
        } catch (IOException e) { }
    }
}
