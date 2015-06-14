package com.vagell.smartaphone;

import java.util.Date;

public class TrialData {
    public int count = -1, duration = -1;
    RGBColor leftColor = null, rightColor = null;
    public Boolean timedOut = false, subjectChoseCorrectly = null, leftCorrect = null;
    public Date startTime = null;

    public TrialData(int count) {
        this.count = count;
    }

    public void setStartTime() {
        startTime = new Date();
    }

    public void setEndTime() {
        duration = (int) ((new Date().getTime() - startTime.getTime()) / 1000);
    }
}