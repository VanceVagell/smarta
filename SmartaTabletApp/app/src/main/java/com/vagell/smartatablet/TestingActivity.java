package com.vagell.smartatablet;

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

import android.graphics.Rect;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class TestingActivity extends BaseActivity {
    private int mTrialCount = 0;
    private Map<String, RGBColor> mColorMap = null;
    public String mPhase = "";
    private Runnable mTrialTimeoutRunnable = null, mTrialStartRunnable = null;
    private SessionData mSessionData = null;
    private Handler mHandler = null;
    private boolean mTrialActive = false;
    private String mRecordingFileName = "";

    public static final String PHASE1 = "phase1";
    public static final String PHASE2 = "phase2";

    private static final int TRIALS_PER_SESSION = 7;
    private static final int SEC_PER_TRIAL = 30;
    private static final int SEC_BETWEEN_TRIALS = 5;

    public static String TESTING_COLOR_MAP_EXTRA = "testing_color_map";
    public static String TESTING_PHASE_EXTRA = "testing_phase";
    public static String TESTING_RECORDING_NAME_EXTRA = "testing_recording_name";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mHandler = new Handler();

        String mapJson = getIntent().getStringExtra(TESTING_COLOR_MAP_EXTRA);
        mColorMap = new Gson().fromJson(mapJson, new TypeToken<HashMap<String, RGBColor>>() {}.getType());
        mPhase = getIntent().getStringExtra(TESTING_PHASE_EXTRA);
        mRecordingFileName = getIntent().getStringExtra(TESTING_RECORDING_NAME_EXTRA);

        // Manually handle touch events anywhere on the screen. Notice that we don't use touch
        // handlers on the 2 testing shapes themselves, see handleTouch() for details.
        View bgView = findViewById(R.id.testing_bg);
        bgView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                handleTouch(motionEvent);
                return true;
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Once activity is underway, start the testing.
        start();
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        super.surfaceCreated(surfaceHolder);

        // Once recording preview is created, start recording. Testing should already be started.
        startRecording(mRecordingFileName + ".mp4");
    }

    @Override
    SurfaceView getCameraView() {
        return (SurfaceView) findViewById(R.id.testing_camera_view);
    }

    @Override
    int getContentViewId() {
        return R.layout.activity_testing;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // One example where we get here is if the BT connection breaks in middle of a trial.
        stop();
    }

    public void start() {
        mTrialCount = -1;
        mSessionData = new SessionData();
        randomizeColorPairs();
        startNextTrial();
    }

    private void startNextTrial() {
        mTrialActive = true;
        mTrialCount++;
        if (mTrialCount == TRIALS_PER_SESSION) {
            stop();
            return;
        }

        final TrialData trialData = mSessionData.trialData.get(mTrialCount);
        trialData.setStartTime();

        // Show colors
        this.showColors(trialData.leftColor, trialData.rightColor);

        // Set a timer to automatically end trial if no tap
        mTrialTimeoutRunnable = new Runnable() {
            public void run() {
                trialData.timedOut = true;
                trialData.duration = SEC_PER_TRIAL;
                sendTrialData();
                if (mTrialCount < TRIALS_PER_SESSION - 1) {
                    waitBetweenTrials();
                } else {
                    stop();
                }
            }
        };
        mHandler.postDelayed(mTrialTimeoutRunnable, SEC_PER_TRIAL * 1000);
    }

    /**
     * We handle touch events manually, rather than having touch listeners on the shapes, because
     * Android doesn't do a great job with animals placing their entire hand / paw on the screen.
     * It may overlap multiple views, or be treated as a long press, or other undesirable
     * situations. It's not sensitive enough, so we check for intersections ourselves here.
     */
    private synchronized void handleTouch(MotionEvent touchEvent) {
        if (!mTrialActive) {
            return;
        }

        // If any finger touched one of the shapes, decide if it was a correct or incorrect touch.
        for (int i = 0; i < touchEvent.getPointerCount(); i++) {
            MotionEvent.PointerCoords coords = new MotionEvent.PointerCoords();
            touchEvent.getPointerCoords(i, coords);

            TrialData trialData = mSessionData.trialData.get(mTrialCount);

            // Did they touch the left shape?
            if (intersects(coords, findViewById(R.id.left_color_block))) {
                Log.d("LOG", "Finger " + i + " tapped left shape.");
                trialData.subjectChoseCorrectly = trialData.leftCorrect;

            // Did they touch the right shape?
            } else if (intersects(coords, findViewById(R.id.right_color_block))) {
                Log.d("LOG", "Finger " + i + " tapped right shape.");
                trialData.subjectChoseCorrectly = !trialData.leftCorrect;
            }

            // If they didn't touch a shape with this finger, check next finger.
            if (trialData.subjectChoseCorrectly == null) {
                Log.d("LOG", "Finger " + i + " did not touch either shape.");
                continue;

            // They touched a shape, wrap up this trial.
            } else {
                mTrialActive = false;
                mHandler.removeCallbacks(mTrialTimeoutRunnable);
                if (trialData.subjectChoseCorrectly) {
                    Log.d("LOG", "Correct!");

                    // Play a sound so animals know they did it right.
                    MediaPlayer player = MediaPlayer.create(this, R.raw.whistle);
                    player.setAudioStreamType(AudioManager.STREAM_MUSIC);
                    player.start();

                    ArduinoMessager.send(this, ArduinoMessager.ARDUINO_DISPENSE);
                } else {
                    Log.d("LOG", "Incorrect.");
                }
                trialData.timedOut = false;
                trialData.setEndTime();
                sendTrialData();
                if (mTrialCount < TRIALS_PER_SESSION - 1) {
                    waitBetweenTrials();
                } else {
                    stop();
                }
                return;
            }
        }
    }

    private boolean intersects(MotionEvent.PointerCoords coords, View view) {
        Rect viewRect = new Rect();
        view.getGlobalVisibleRect(viewRect);
        return viewRect.contains((int) coords.x, (int) coords.y);
    }

    private class SessionData {
        public Date sessionStartTime = null;
        public ArrayList<TrialData> trialData = null;

        public SessionData() {
            sessionStartTime = new Date();
            trialData = new ArrayList<TrialData>();

            for (int i = 0; i < TRIALS_PER_SESSION; i++) {
                trialData.add(new TrialData(i));
            }
        }
    }

    private void waitBetweenTrials() {
        hideColors();

        // Set a timer to start next trial shortly
        mTrialStartRunnable = new Runnable() {
            public void run() {
                startNextTrial();
            }
        };
        mHandler.postDelayed(mTrialStartRunnable, SEC_BETWEEN_TRIALS * 1000);
    }

    private void hideColors() {
        findViewById(R.id.left_color_block).setVisibility(View.GONE);
        findViewById(R.id.right_color_block).setVisibility(View.GONE);
    }

    private void showColors(RGBColor leftColor, RGBColor rightColor) {
        findViewById(R.id.left_color_block).setBackgroundColor(leftColor.toIntColor());
        findViewById(R.id.right_color_block).setBackgroundColor(rightColor.toIntColor());
        findViewById(R.id.left_color_block).setVisibility(View.VISIBLE);
        findViewById(R.id.right_color_block).setVisibility(View.VISIBLE);
    }

    /**
     * Assigns matching colors pairs (e.g. red2 and green2) to each trial in the upcoming
     * session. Note that non-matching colors (e.g. red1 and green6) are never paired in a trial.
     */
    private void randomizeColorPairs() {
        RGBColor grayColors[] = getGrayColors();
        RGBColor redColors[] = getRedColors();
        RGBColor greenColors[] = getGreenColors();

        // Keep track of which color pairs we have yet to pick
        ArrayList<Integer> colorSetIndices = new ArrayList<Integer>();
        for (int i = 0; i < grayColors.length; i++) {
            colorSetIndices.add(i);
        }

        for (int trial = 0; trial < TRIALS_PER_SESSION; trial++) {
            TrialData trialData = mSessionData.trialData.get(trial);
            int correctSide = randInt(0, 1);
            int colorSetIndicesIdx = randInt(0, colorSetIndices.size() - 1);
            int colorSetIdx = colorSetIndices.get(colorSetIndicesIdx);
            colorSetIndices.remove(colorSetIndicesIdx);

            RGBColor gray = grayColors[colorSetIdx];
            RGBColor green = greenColors[colorSetIdx];
            RGBColor red = redColors[colorSetIdx];
            if (correctSide == 0) {
                trialData.leftColor = red;
                trialData.rightColor = mPhase.equals(PHASE1) ? gray : green;
                trialData.leftCorrect = true;
            } else {
                trialData.leftColor =  mPhase.equals(PHASE1) ? gray : green;
                trialData.rightColor = red;
                trialData.leftCorrect = false;
            }
        }
    }

    private RGBColor[] getGrayColors() {
        return new RGBColor[] {
                mColorMap.get("Testing colors Gray 1"),
                mColorMap.get("Testing colors Gray 2"),
                mColorMap.get("Testing colors Gray 3"),
                mColorMap.get("Testing colors Gray 4"),
                mColorMap.get("Testing colors Gray 5"),
                mColorMap.get("Testing colors Gray 6"),
                mColorMap.get("Testing colors Gray 7")
        };
    }

    private RGBColor[] getRedColors() {
        return new RGBColor[] {
                mColorMap.get("Testing colors Red 1"),
                mColorMap.get("Testing colors Red 2"),
                mColorMap.get("Testing colors Red 3"),
                mColorMap.get("Testing colors Red 4"),
                mColorMap.get("Testing colors Red 5"),
                mColorMap.get("Testing colors Red 6"),
                mColorMap.get("Testing colors Red 7")
        };
    }

    private RGBColor[] getGreenColors() {
        return new RGBColor[] {
                mColorMap.get("Testing colors Green 1"),
                mColorMap.get("Testing colors Green 2"),
                mColorMap.get("Testing colors Green 3"),
                mColorMap.get("Testing colors Green 4"),
                mColorMap.get("Testing colors Green 5"),
                mColorMap.get("Testing colors Green 6"),
                mColorMap.get("Testing colors Green 7")
        };
    }

    private void sendTrialData() {
        sendBtMessage("TRIALDATA " + new Gson().toJson(mSessionData.trialData.get(mTrialCount)));
    }

    public void abort() {
        stop();
    }

    private void stop() {
        mTrialActive = false;
        Log.d("LOG", "Session stopped.");
        hideColors();
        try {
            mHandler.removeCallbacks(mTrialStartRunnable);
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            mHandler.removeCallbacks(mTrialTimeoutRunnable);
        } catch (Exception e) {
            e.printStackTrace();
        }
        stopRecording();
    }
}
