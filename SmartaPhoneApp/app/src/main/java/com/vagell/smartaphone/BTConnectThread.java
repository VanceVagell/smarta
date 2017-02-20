/*Copyright 2015 Vance Vagell

        Licensed under the Apache License, Version 2.0 (the "License");
        you may not use this file except in compliance with the License.
        You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

        Unless required by applicable law or agreed to in writing, software
        distributed under the License is distributed on an "AS IS" BASIS,
        WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
        See the License for the specific language governing permissions and
        limitations under the License.*/

package com.vagell.smartaphone;

import android.app.Activity;
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
    private final Activity mActivity;

    public final static int MESSAGE_BT_CONNECTED = 0;

    /**
     * Shared UUID with the BT server code on tablet.
     */
    private static final UUID BT_UUID = UUID.fromString("05a9d5fe-d213-4f87-bdc2-fc5d0f1935af");

    public BTConnectThread(LemurColorRemoteApplication application, Handler connectHandler, Handler commHandler, BluetoothDevice device, BluetoothAdapter bluetoothAdapter, Activity activity) {
        mConnectHandler = connectHandler;
        mCommHandler = commHandler;
        mApplication = application;
        mBluetoothAdapter = bluetoothAdapter;
        mDevice = device;
        mActivity = activity;
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
//                mSocket = mDevice.createRfcommSocketToServiceRecord(BT_UUID);
                mSocket.connect();
            } catch (Exception e) {
                // TODO would love to restart app if bluetooth fails, but we get a couple intermittent failures even during a normal run, so can't :(
                // An improvement would be to just silently keep retrying the connection when it fails. Right now it may eventually fail permanently.
//                if (mActivity != null) {
//                    mActivity.runOnUiThread(new Runnable() {
//                        @Override
//                        public void run() {
//                            new AlertDialog.Builder(mActivity)
//                                    .setTitle("Can't connect")
//                                    .setMessage("Can't connect to tablet via Bluetooth. Make sure phone and tablet are paired, then try again.")
//                                    .setCancelable(false)
//                                    .setPositiveButton("Try again", new DialogInterface.OnClickListener() {
//                                        @Override
//                                        public void onClick(DialogInterface dialog, int which) {
//                                            // Restarts the app
//                                            Intent newActivity = new Intent(mActivity, MainActivity.class);
//                                            int mPendingIntentId = 123456;
//                                            PendingIntent mPendingIntent = PendingIntent.getActivity(mActivity, mPendingIntentId, newActivity, PendingIntent.FLAG_CANCEL_CURRENT);
//                                            AlarmManager mgr = (AlarmManager) mActivity.getSystemService(Context.ALARM_SERVICE);
//                                            mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 100, mPendingIntent);
//                                            System.exit(0);
//                                        }
//                                    })
//                                    .show();
//                        }
//                    });
//                }

                Log.d("LOG","ERROR: Bluetooth fallback failed. Could not establish BT connection.");
                e.printStackTrace();

                // Unable to connect; close the socket and get out
                try {
                    mSocket.close();
                } catch(IOException closeException) {
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
