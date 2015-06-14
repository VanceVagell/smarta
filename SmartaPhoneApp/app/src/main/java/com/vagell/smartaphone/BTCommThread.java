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

import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Scanner;

public class BTCommThread extends Thread {
    private final BluetoothSocket mSocket;
    private final InputStream mInStream;
    private final OutputStream mOutStream;
    private final Handler mCommHandler;

    private static final String DELIMITER_REGEX = "\\*\\*\\*";

    public BTCommThread(BluetoothSocket socket, Handler commHandler) {
        mSocket = socket;
        mCommHandler = commHandler;
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

                // TODO avoid using awkward inner activity class as handler, extract is (like in LemurRemote)
                mCommHandler.obtainMessage(MainActivity.BTMessageHandler.MESSAGE_BT_READ, btMsg).sendToTarget();
            } catch (Exception e) {
                Log.d("LOG", "Lost BT connection.");
                // TODO restart connection
//                    mHandler.obtainMessage(BTMessageHandler.MESSAGE_BT_RESET).sendToTarget();
//                    try {
//                        mSocket.close();
//                    } catch (IOException e1) {
//                        e1.printStackTrace();
//                    }
//                    restartBtConnection();
                return;
            }
        }
    }

}
