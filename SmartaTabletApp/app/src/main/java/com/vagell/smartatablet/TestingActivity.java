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

import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
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
    private static Random mRandom = new Random();
    private boolean mTrialActive = false;

    public static final String PHASE1 = "phase1";
    public static final String PHASE2 = "phase2";

    private static final int TRIALS_PER_SESSION = 7;
    private static final int SEC_PER_TRIAL = 30;
    private static final int SEC_BETWEEN_TRIALS = 5;

    public static String TESTING_COLOR_MAP_EXTRA = "testing_color_map";
    public static String TESTING_PHASE_EXTRA = "testing_phase";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mHandler = new Handler();

        String mapJson = getIntent().getStringExtra(TESTING_COLOR_MAP_EXTRA);
        mColorMap = new Gson().fromJson(mapJson, new TypeToken<HashMap<String, RGBColor>>() {}.getType());
        mPhase = (String) getIntent().getStringExtra(TESTING_PHASE_EXTRA);

        // TODO fix bug where putting your whole hand on screen doesn't trigger a touch
        // event. Might need to listen to highest-level view and pass event to appropriate
        // child manually (assuming touch event data is accurate).
        View leftColor = findViewById(R.id.left_color_block);
        leftColor.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                handleLeftColorTapped(view);
                return true;
            }
        });

        View rightColor = findViewById(R.id.right_color_block);
        rightColor.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                handleRightColorTapped(view);
                return true;
            }
        });
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

    public void handleLeftColorTapped(View v) {
        if (!mTrialActive) { // debounce touch events
            return;
        }
        mTrialActive = false;
        mHandler.removeCallbacks(mTrialTimeoutRunnable);
        TrialData trialData = mSessionData.trialData.get(mTrialCount);
        trialData.subjectChoseCorrectly = trialData.leftCorrect;
        if (trialData.subjectChoseCorrectly) {
            Log.d("LOG", "Correct!");
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
    }

    public void handleRightColorTapped(View v) {
        if (!mTrialActive) { // debounce touch events
            return;
        }
        mTrialActive = false;
        mHandler.removeCallbacks(mTrialTimeoutRunnable);
        TrialData trialData = mSessionData.trialData.get(mTrialCount);
        trialData.subjectChoseCorrectly = !trialData.leftCorrect;
        if (trialData.subjectChoseCorrectly) {
            Log.d("LOG", "Correct!");
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

    public static int randInt(int min, int max) {
        return mRandom.nextInt((max - min) + 1) + min;
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
