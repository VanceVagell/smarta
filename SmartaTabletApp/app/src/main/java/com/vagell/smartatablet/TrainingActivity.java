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
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

public class TrainingActivity extends BaseActivity {
    public static String TRAINING_RED_COLOR_EXTRA = "training_red_color";
    public static String TRAINING_GRAY_COLOR_EXTRA = "training_gray_color";
    public static String TRAINING_MODE_EXTRA = "training_mode";

    private int mRedColor = -1;
    private int mGrayColor = -1;
    private boolean mAnimating = false;
    private ObjectAnimator mAnim = null;
    private String mTrainingMode = null;
    private View mRedObj = null;
    private View mNonRedObj = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Get passed-in info from the intent, including requested colors
        mGrayColor = (Integer) getIntent().getExtras().get(TRAINING_GRAY_COLOR_EXTRA);
        mRedColor = (Integer) getIntent().getExtras().get(TRAINING_RED_COLOR_EXTRA);
        mTrainingMode = (String) getIntent().getExtras().get(TRAINING_MODE_EXTRA);
        if (mTrainingMode != null) {
            setObjVisible(true);
        }
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
        if (visible) {
            // TODO remove fragile constants shared with client, should share versioned enum class or something
            if (mTrainingMode.equals("Blank")) {
                // Do nothing :) Just a black screen. Still counts as active training mode.
            } else if (mTrainingMode.equals("All red")) {
                mRedObj = null;
                mNonRedObj = null;
                View obj = findViewById(R.id.training_mode_all_red);
                obj.setBackgroundColor(mRedColor);
                obj.setVisibility(View.VISIBLE);
                findViewById(R.id.training_mode_large_red_box).setVisibility(View.INVISIBLE);
                findViewById(R.id.training_mode_small_red_box).setVisibility(View.INVISIBLE);
                findViewById(R.id.training_mode_red_and_gray_boxes).setVisibility(View.INVISIBLE);
            } else if (mTrainingMode.equals("Large red box")) {
                View obj = findViewById(R.id.training_mode_large_red_box);
                mRedObj = obj;
                obj.setBackgroundColor(mRedColor);
                obj.setVisibility(View.VISIBLE);
                findViewById(R.id.training_mode_all_red).setVisibility(View.INVISIBLE);
                findViewById(R.id.training_mode_small_red_box).setVisibility(View.INVISIBLE);
                findViewById(R.id.training_mode_red_and_gray_boxes).setVisibility(View.INVISIBLE);
            } else if (mTrainingMode.equals("Small red box")) {
                View obj = findViewById(R.id.training_mode_small_red_box);
                mRedObj = obj;
                obj.setBackgroundColor(mRedColor);
                obj.setVisibility(View.VISIBLE);
                findViewById(R.id.training_mode_all_red).setVisibility(View.INVISIBLE);
                findViewById(R.id.training_mode_large_red_box).setVisibility(View.INVISIBLE);
                findViewById(R.id.training_mode_red_and_gray_boxes).setVisibility(View.INVISIBLE);
            } else if (mTrainingMode.equals("Red and gray boxes")) {
                View leftObj = findViewById(R.id.training_left_color_block);
                View rightObj = findViewById(R.id.training_right_color_block);
                boolean leftIsRed = randInt(0, 1) == 1;
                if (leftIsRed) {
                    mRedObj = leftObj;
                    mNonRedObj = rightObj;
                } else {
                    mRedObj = rightObj;
                    mNonRedObj = leftObj;
                }

                mNonRedObj.setBackgroundColor(mGrayColor);
                mRedObj.setBackgroundColor(mRedColor);

                findViewById(R.id.training_mode_red_and_gray_boxes).setVisibility(View.VISIBLE);
                findViewById(R.id.training_mode_all_red).setVisibility(View.INVISIBLE);
                findViewById(R.id.training_mode_large_red_box).setVisibility(View.INVISIBLE);
                findViewById(R.id.training_mode_small_red_box).setVisibility(View.INVISIBLE);
            } else {
                Log.d("LOG", "ERROR: Unexpected training mode value: " + mTrainingMode);
                return;
            }

            // Create animator, for when we want to move the object around
            if (mRedObj != null) {
                mAnim = ObjectAnimator.ofFloat(mRedObj, "rotation", 0, 180);
                mAnim.setDuration(600);
                mAnim.setRepeatCount(ObjectAnimator.INFINITE);
            }

            setAnimating(mAnimating);
        } else { // !visible
            if (mAnim != null) {
                mAnim.cancel();
            }
            findViewById(R.id.training_mode_all_red).setVisibility(View.INVISIBLE);
            findViewById(R.id.training_mode_large_red_box).setVisibility(View.INVISIBLE);
            findViewById(R.id.training_mode_small_red_box).setVisibility(View.INVISIBLE);
            findViewById(R.id.training_mode_red_and_gray_boxes).setVisibility(View.INVISIBLE);
        }
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

    public void setTrainingMode(String mode) {
        mTrainingMode = mode;
    }

    public void setAnimating(boolean animating) {
        mAnimating = animating;
        if (mAnimating) {
            if (mAnim != null) {
                mAnim.start();
            }
            if (mNonRedObj != null) {
                mNonRedObj.setRotation(0);
            }
        } else {
            if (mAnim != null) {
                mAnim.cancel();
            }
            if (mRedObj != null) {
                mRedObj.setRotation(0);
            }
            if (mNonRedObj != null) {
                mNonRedObj.setRotation(0);
            }
        }
    }
}
