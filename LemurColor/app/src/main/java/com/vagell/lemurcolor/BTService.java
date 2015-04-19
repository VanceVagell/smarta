package com.vagell.lemurcolor;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Scanner;
import java.util.UUID;

public class BTService extends Service {
    private BluetoothAdapter mBluetoothAdapter = null;
    private BTAcceptThread mBtAcceptThread = null;
    private BTCommThread mBtCommThread = null;
    private Handler mHandler = null;

    private BtState mBtState = BtState.NOT_CONNECTED;

    private enum BtState {
        NOT_CONNECTED,
        CONNECTED
    }

    @Override
    public void onCreate() {
        // Called once
        super.onCreate();
        Log.d("LOG", "BTService.onCreate()");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Called every time the app tries to start the service
        int result = super.onStartCommand(intent, flags, startId);

        Log.d("LOG", "BTService.onStartCommand()");

        if (mBtState == BtState.NOT_CONNECTED) {
            restartBtConnection();
        }

        return result;
    }

    private void restartBtConnection() {
        if (mBtAcceptThread != null) {
            mBtAcceptThread.cancel();
        }
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mBtAcceptThread = new BTAcceptThread(mBluetoothAdapter);
        mBtAcceptThread.start();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d("LOG", "BTService.onDestroy()");
        // TODO disconnect
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d("LOG", "BTService.onBind()");
        return new BTBinder();
    }

    /**
     * Activities that bind to this service get an instance of this. They can use it to interact
     * directly with the BT service.
     */
    public class BTBinder extends Binder {
        // Only the most recent handler is notified of incoming messages.
        public void setHandler(Handler handler) {
            mHandler = handler;
        }

        public synchronized void write(String message) {
            writeToCommThread(message);
        }
    }

    private class BTAcceptThread extends Thread {
        private final BluetoothServerSocket mServerSocket;
        private final UUID BT_UUID = UUID.fromString("05a9d5fe-d213-4f87-bdc2-fc5d0f1935af");

        public BTAcceptThread(BluetoothAdapter bluetoothAdapter) {
            BluetoothServerSocket tempSocket = null;
            try {
                tempSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord("Lemur Vision Display", BT_UUID);
            } catch (IOException e) {
                e.printStackTrace();
            }
            mServerSocket = tempSocket;
        }

        public void run() {
            BluetoothSocket socket = null;

            // Listen until socket is returned
            while (true) {
                try {
                    Log.d("LOG", "Waiting for bluetooth connection...");
                    socket = mServerSocket.accept();

                    if (socket != null) {
                        Log.d("LOG", "Bluetooth connected.");
                        mBtState = BtState.CONNECTED;
                        mBtCommThread = new BTCommThread(socket);
                        mBtCommThread.start();
                        mHandler.obtainMessage(BTMessageHandler.MESSAGE_BT_CONNECTED).sendToTarget();
                        break;
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    break;
                }
            }
        }

        /**
         * Cancel BT connection listening and exit thread.
         */
        public void cancel() {
            try {
                mServerSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private class BTCommThread extends Thread {
        private final BluetoothSocket mSocket;
        private final InputStream mInStream;
        private final OutputStream mOutStream;
        public static final String BT_MESSAGE_DIVIDER = "***";
        private static final String DELIMITER_REGEX = "\\*\\*\\*";

        public BTCommThread(BluetoothSocket socket) {
            mSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }

            mInStream = tmpIn;
            mOutStream = tmpOut;
        }

        public void run() {
            Log.d("LOG", "Waiting for BT messages.");
            Scanner scanner = new Scanner(new BufferedInputStream(mInStream)).useDelimiter(DELIMITER_REGEX);
            while (true) {
                try {
                    String btMsg = scanner.next();
                    Log.d("LOG", "BTService received message: '" + btMsg + "'");

                    mHandler.obtainMessage(BTMessageHandler.MESSAGE_BT_READ, btMsg).sendToTarget();
                } catch (Exception e) {
                    Log.d("LOG", "Lost BT connection.");
                    mHandler.obtainMessage(BTMessageHandler.MESSAGE_BT_RESET).sendToTarget();
                    try {
                        mSocket.close();
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                    restartBtConnection();
                    return;
                }
            }
        }

        /* Call to send data to the remote device */
        public void write(byte[] bytes) {
            try {
                mOutStream.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void writeToCommThread(String message) {
        Log.d("LOG", "Sending BT message: '" + message + "'");
        if (mBtCommThread != null) {
            try {
                mBtCommThread.write((message + BTCommThread.BT_MESSAGE_DIVIDER).getBytes());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}