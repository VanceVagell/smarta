package com.vagell.smartaphone;

import android.app.Application;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

public class LemurColorRemoteApplication extends Application {
    private BluetoothSocket mSocket = null;
    private Thread mBtCommThread = null;

    public synchronized void setBtSocket(BluetoothSocket socket) {
        mSocket = socket;
    };

    public synchronized BluetoothSocket getBtSocket() {
        if (mSocket == null) {
            Log.d("LOG", "Warning: Got BT socket before it was set.");
        }

        return mSocket;
    };

    public synchronized void setBtCommThread(Thread thread) {
        mBtCommThread = thread;
    };

    public synchronized Thread getBtCommThread() {
        return mBtCommThread;
    };
}
