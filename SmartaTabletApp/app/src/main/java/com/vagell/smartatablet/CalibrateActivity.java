package com.vagell.smartatablet;

import android.app.Activity;
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


public class CalibrateActivity extends Activity {
    public static String CALIBRATION_COLOR_EXTRA = "calibration_color";

    private ServiceConnection mBtServiceConnection = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Hide the title bar
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_calibrate);

        // Hide system bars
        findViewById(android.R.id.content).setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);

        // Go to full screen brightness
        WindowManager.LayoutParams layoutParams = getWindow().getAttributes();
        layoutParams.screenBrightness = 1.0F;
        getWindow().setAttributes(layoutParams);

        // Use requested colors
        int color = (Integer) getIntent().getExtras().get(CALIBRATION_COLOR_EXTRA);
        findViewById(R.id.bg).setBackgroundColor(color);
    }


    @Override
    protected void onResume() {
        super.onResume();

        // Start listening for BT messages
        final Activity activity = this;
        Intent intent = new Intent(this, BTService.class);
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
