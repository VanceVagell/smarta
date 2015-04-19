package com.vagell.lemurcolor;

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
    private SimpleDateFormat START_TIME_FORMAT = new SimpleDateFormat("EEE MMM dd yyyy HH:mm:ss zzz");
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
        mTimer.cancel();
        mTimer.purge();
        mTimer = null;
        mStartTime = null;
        mTextView.setText("00:00");
    }
}
