package com.vagell.lemurcolor;

import android.app.Activity;
import android.bluetooth.BluetoothSocket;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.hardware.Camera;
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

/**
 * Training and Testing are so similar that we've extracted common functionality into this class.
 */
public abstract class BaseActivity extends Activity implements SurfaceHolder.Callback {
    private ServiceConnection mBtServiceConnection = null;
    private BTService.BTBinder mBtServiceBinder = null;
    private MediaRecorder mRecorder = null;
    private SurfaceHolder mSurfaceHolder = null;
    private boolean mRecording = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Hide the title bar
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(getContentViewId());

        // Hide system bars
        findViewById(android.R.id.content).setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);

        // Go to full screen brightness
        WindowManager.LayoutParams layoutParams = getWindow().getAttributes();
        layoutParams.screenBrightness = 1.0F;
        getWindow().setAttributes(layoutParams);

        // Setup video camera
        SurfaceView cameraView = getCameraView();
        mSurfaceHolder = cameraView.getHolder();
        mSurfaceHolder.addCallback(this);
        mSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }

    abstract SurfaceView getCameraView();
    abstract int getContentViewId();

    @Override
    protected void onResume() {
        super.onResume();

        // Start listening for BT messages
        final Activity activity = this;
        Intent intent = new Intent(this, BTService.class);
        mBtServiceConnection = new ServiceConnection() {
            public BTService.BTBinder btServiceBinder = null;

            @Override
            public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
                // Once we're bound to the service, give it a new message handler.
                mBtServiceBinder = ((BTService.BTBinder) iBinder);
                mBtServiceBinder.setHandler(new BTMessageHandler(Looper.getMainLooper(), activity));
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

    private File mVideoFile = null;
    private Camera mCamera = null;

    public void startRecording(String filename) {
        if (mRecording) {
            return;
        }

        mRecording = true;

        // Create the recorder
        mRecorder = new MediaRecorder();

        // Stage 1 config
        mCamera = Camera.open(1 /* front facing */);
        mCamera.unlock();
        mRecorder.setCamera(mCamera);
        mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        mRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);

        // Stage 2 config
        mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mRecorder.setVideoEncodingBitRate(3000000);
        mRecorder.setAudioSamplingRate(48000);
        mRecorder.setAudioEncodingBitRate(48000);
        mRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mVideoFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), filename);
        mRecorder.setOutputFile(mVideoFile.getAbsolutePath());
        mRecorder.setVideoSize(640, 480);
        mRecorder.setVideoFrameRate(30);
        mRecorder.setPreviewDisplay(mSurfaceHolder.getSurface());
        mRecorder.setOrientationHint(180);
        try {
            mRecorder.prepare();
        } catch (IllegalStateException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        mRecorder.start();

        Log.d("LOG", "Started video recording of file: " + mVideoFile.getAbsolutePath());
    }

    private void releaseMediaDevices() {
        if (mRecorder != null) {
            try {
                mRecorder.stop();
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                mRecorder.reset();
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                mRecorder.release();
            } catch (Exception e) {
                e.printStackTrace();
            }
            mRecorder = null;
        }

        if (mCamera != null) {
            try {
                mCamera.release();
            } catch (Exception e) {
                e.printStackTrace();
            }
            mCamera = null;
        }
    }

    public void stopRecording() {
        if (!mRecording) {
            return;
        }

        mRecording = false;
        releaseMediaDevices();

        Log.d("LOG", "Stopped video recording.");

        // Force Android to recognize the new video file on the filesystem.
        MediaScannerConnection.scanFile(this, new String[]{mVideoFile.getAbsolutePath()},
                null, new MediaScannerConnection.OnScanCompletedListener() {
                    public void onScanCompleted(String path, Uri uri) {
                    }
                });
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        Log.d("LOG", "Recording preview surface created.");
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i2, int i3) { }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        releaseMediaDevices();
    }

    protected void sendBtMessage(String message) {
        mBtServiceBinder.write(message);
    }
}
