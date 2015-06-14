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
