package com.vagell.lemurcolor;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.hardware.Camera;
import android.media.AudioManager;
import android.media.MediaRecorder;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import java.io.File;
import java.io.IOException;

public class TrainingActivity extends BaseActivity {
    public static String TRAINING_OBJ_COLOR_EXTRA = "training_obj_color";
    public static String TRAINING_BG_COLOR_EXTRA = "training_bg_color";

    private int mBgColor = -1;
    private int mObjColor = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Use requested colors
        int objColor = (Integer) getIntent().getExtras().get(TRAINING_OBJ_COLOR_EXTRA);
        findViewById(R.id.training_obj).setBackgroundColor(objColor);
        mObjColor = objColor;
        int bgColor = (Integer) getIntent().getExtras().get(TRAINING_BG_COLOR_EXTRA);
        findViewById(R.id.training_bg).setBackgroundColor(bgColor);
        mBgColor = bgColor;
        setVisible(true); // In case we were toggled off previously.
    }

    protected SurfaceView getCameraView() {
        return (SurfaceView) findViewById(R.id.training_camera_view);
    }

    protected int getContentViewId() {
        return R.layout.activity_training;
    }

    public void setVisible(boolean visible) {
        // Note that we don't actually hide the View, because we need the SurfaceView that
        // the media recorder uses to be on the screen. Otherwise video recording fails.
        int objColor = visible ? mObjColor : Color.BLACK;
        int bgColor = visible ? mBgColor : Color.BLACK;
        findViewById(R.id.training_obj).setBackgroundColor(objColor);
        findViewById(R.id.training_bg).setBackgroundColor(bgColor);
    }
}
