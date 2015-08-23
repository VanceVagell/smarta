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

package com.vagell.smartatablet;

import android.animation.ObjectAnimator;
import android.graphics.Color;
import android.os.Bundle;
import android.view.SurfaceView;
import android.view.View;

public class TrainingActivity extends BaseActivity {
    public static String TRAINING_RED_COLOR_EXTRA = "training_red_color";
    public static String TRAINING_GRAY_COLOR_EXTRA = "training_gray_color";

    private int mRedColor = -1;
    private int mGrayColor = -1;
    private boolean mAnimating = false;
    private ObjectAnimator mAnim = null;
    View redObj = null;
    View nonRedObj = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Use requested colors
        mGrayColor = (Integer) getIntent().getExtras().get(TRAINING_RED_COLOR_EXTRA);
        mRedColor = (Integer) getIntent().getExtras().get(TRAINING_GRAY_COLOR_EXTRA);
        setObjVisible(true); // In case we were toggled off previously.
    }

    protected SurfaceView getCameraView() {
        return (SurfaceView) findViewById(R.id.training_camera_view);
    }

    protected int getContentViewId() {
        return R.layout.activity_training;
    }

    public void setObjVisible(boolean visible) {
        // Note that we don't actually hide the View, because we need the SurfaceView that
        // the media recorder uses to be on the screen. Otherwise video recording fails.
        int grayColor = visible ? mGrayColor : Color.BLACK;
        int redColor = visible ? mRedColor : Color.BLACK;

        if (visible) {
            boolean leftIsRed = randInt(0, 1) == 1;
            if (leftIsRed) {
                redObj = findViewById(R.id.training_left_color_block);
                nonRedObj = findViewById(R.id.training_right_color_block);
            } else {
                redObj = findViewById(R.id.training_right_color_block);
                nonRedObj = findViewById(R.id.training_left_color_block);
            }

            // Create animator, for when we want to move the object around
            mAnim = ObjectAnimator.ofFloat(redObj, "rotation", 0, 180);
            mAnim.setDuration(600);
            mAnim.setRepeatCount(ObjectAnimator.INFINITE);

            setAnimating(mAnimating);
        } else {
            mAnim.cancel();
        }

        nonRedObj.setBackgroundColor(grayColor);
        redObj.setBackgroundColor(redColor);
    }

    /**
     * Sets red color in model, but won't be applied until next time setObjVisible(true) is called.
     * TODO remove this side effect (it's because setObjVisible sets to black to hide)
     */
    public void setGrayColor(int objColor) {
        mGrayColor = objColor;
    }

    /**
     * Sets gray color in model, but won't be applied until next time setObjVisible(true) is called.
     * TODO remove this side effect (it's because setObjVisible sets to black to hide)
     */
    public void setRedColor(int bgColor) {
        mRedColor = bgColor;
    }

    public void setAnimating(boolean animating) {
        mAnimating = animating;
        if (mAnimating) {
            mAnim.start();
            nonRedObj.setRotation(0);
        } else {
            mAnim.cancel();
            redObj.setRotation(0);
            nonRedObj.setRotation(0);
        }
    }
}
