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

package com.vagell.smartatablet;

import android.app.Activity;
import android.content.Context;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.util.Log;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.io.IOException;
import java.util.List;

public class ArduinoMessager {
    public static final String ARDUINO_DISPENSE = "1";
    public static final String ARDUINO_BACK = "2";
    public static final String ARDUINO_BACKFAR = "3";

    /**
     * Opens serial connection, sends message, closes connection.
     */
    public static void send(Activity activity, String message) {
        // Connect to Arduino via USB
        Log.d("LOG", "Setting up USB port...");

        // Find all available drivers from attached USB devices.
        UsbManager manager = (UsbManager) activity.getSystemService(Context.USB_SERVICE);
        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager);
        if (availableDrivers.isEmpty()) {
            Log.d("LOG", "No USB drivers available. Not plugged in to Arduino?");
            return;
        }

        // Open a connection to the first available driver.
        UsbSerialDriver driver = availableDrivers.get(0);
        UsbDeviceConnection usbConnection = manager.openDevice(driver.getDevice());
        if (usbConnection == null) {
            Log.d("LOG", "Unable to open USB connection. Not plugged in to Arduino?");
            return;
        }

        // Open the first available port.
        List<UsbSerialPort> portList = driver.getPorts();
        UsbSerialPort usbSerialPort = portList.get(0);
        try {
            usbSerialPort.open(usbConnection);
            usbSerialPort.setParameters(9600 /* baud rate */, UsbSerialPort.DATABITS_8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
        } catch (IOException e) {
            e.printStackTrace();
        }

        Log.d("LOG", "Finished setting up USB port: " + usbSerialPort);

        if (usbSerialPort == null) {
            Log.d("LOG", "No USB usbSerialPort available, cannot communicate with Arduino.");
            return;
        }

        Log.d("LOG", "Sending message on USB.");
        try {
            usbSerialPort.write(message.getBytes() /* byte[] src */, 1000 /* timeoutMillis */);
        } catch (IOException e) {
            e.printStackTrace();
        }
        Log.d("LOG", "Done sending.");

        try {
            if (usbSerialPort != null) {
                usbSerialPort.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
