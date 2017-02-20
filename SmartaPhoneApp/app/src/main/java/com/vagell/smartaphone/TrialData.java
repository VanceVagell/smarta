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

import java.util.Date;

/**
 * MUST be kept in sync with matching Java file in tablet app. Used for serialization.
 */
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
        duration = (int) ((new Date().getTime() - startTime.getTime()));
    }
}