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

import android.app.Activity;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Renders a live timer into a TextView when start() is called, counting up from 00:00 until
 * stop() is called when it resets to 00:00.
 */
public class RenderedTimer {
    private Timer mTimer = null;
    private Date mStartTime = null;
    private Activity mActivity = null;
    private TextView mTextView;
    public static final String TIME_FORMAT_STRING = "yyyy-MM-dd HH:mm:ss zzz";
    private SimpleDateFormat START_TIME_FORMAT = new SimpleDateFormat(TIME_FORMAT_STRING);
    private int mSecondsToCountDown = 0;
    private boolean mCountDown = false;

    public RenderedTimer(Activity activity, TextView textView) {
        mActivity = activity;
        mTextView = textView;
    }

    public void start() {
        mTimer = new java.util.Timer();
        mStartTime = new Date();
        mTimer.scheduleAtFixedRate(new TimerTask() {

            @Override
            public void run() {
                mActivity.runOnUiThread(new Runnable() {
                    public void run() {
                        update();
                    }
                });
            }
        }, 0 /* delay */, 1000 /* period */);
    }

    public void startCountDown(int secondsToCountDown) {
        mSecondsToCountDown = secondsToCountDown;
        mCountDown = true;
        start();
    }

    private void update() {
        if (mStartTime == null) {
            return;
        }

        mTextView.setText(getElapsedTimeString());
    }

    /**
     * Returns the time this timer was started.
     */
    public String getStartTimeString() {
        return START_TIME_FORMAT.format(mStartTime);
    }

    public String getElapsedTimeString() {
        // Calculate elapsed time since training started
        Date currentTime = new Date();
        long msSinceStart = currentTime.getTime() - mStartTime.getTime();
        int totalSecSinceStart = (int) (msSinceStart / 1000);

        if (mCountDown) {
            int minRemain = 0, secRemain = 0;
            if (totalSecSinceStart < mSecondsToCountDown) {
                minRemain = (mSecondsToCountDown - totalSecSinceStart) / 60;
                secRemain = (mSecondsToCountDown - totalSecSinceStart) % 60;
            }
            return String.format("%02d:%02d", minRemain, secRemain);
        } else {
            int minSinceStart = totalSecSinceStart / 60;
            int secSinceStart = totalSecSinceStart % 60;
            return String.format("%02d:%02d", minSinceStart, secSinceStart);
        }
    }

    public void stop() {
        if (mTimer != null) {
            mTimer.cancel();
            mTimer.purge();
            mTimer = null;
        }
        mStartTime = null;
        mTextView.setText("00:00");
    }
}
