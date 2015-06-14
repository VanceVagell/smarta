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
