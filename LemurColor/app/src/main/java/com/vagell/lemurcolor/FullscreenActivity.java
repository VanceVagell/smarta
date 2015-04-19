package com.vagell.lemurcolor;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import com.hoho.android.usbserial.driver.UsbSerialPort;


public class FullscreenActivity extends Activity {
    /**
     * Custom code for our request to enable Bluetooth. Value doesn't matter, we get it back
     * in the callback.
     */
    private static final int REQUEST_ENABLE_BT_CODE = 9484;

    /**
     * The Bluetooth adapter we'll use to communicate with the phone app.
     */
    private final BluetoothAdapter mBluetoothAdapter;

    /**
     * Connection (if any) to the long-lived BT service.
     */
    private ServiceConnection mBtServiceConnection = null;

    public FullscreenActivity() {
        super();
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Hide the title bar
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_fullscreen);

        // Hide system bars
        findViewById(android.R.id.content).setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);

        // Initialize Bluetooth
        if (mBluetoothAdapter == null) {
            // Device does not support Bluetooth
            throw new RuntimeException("Device does not support Bluetooth.");
        }

        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT_CODE);
        }

        // Go to full screen brightness
        WindowManager.LayoutParams layoutParams = getWindow().getAttributes();
        layoutParams.screenBrightness = 1.0F;
        getWindow().setAttributes(layoutParams);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Show "Waiting..."
        findViewById(R.id.waiting_message).setVisibility(View.VISIBLE);

        Intent intent = new Intent(this, BTService.class);
        startService(intent); // Keeps BT service running regardless of if any activity bound to it.
        final Activity activity = this;
        mBtServiceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
                // Once we're bound to the service, give it a new message handler.
                ((BTService.BTBinder) iBinder).setHandler(new BTMessageHandler(Looper.getMainLooper(), activity));
            }

            @Override
            public void onServiceDisconnected(ComponentName componentName) {
                Log.d("LOG", "Warning: BT service unexpectedly disconnected.");
            }
        };
        bindService(intent, mBtServiceConnection, 0 /* flags */);
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (mBtServiceConnection != null) {
            unbindService(mBtServiceConnection);
            mBtServiceConnection = null;
        }
    }
}
