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

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.View;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.HashMap;
import java.util.Map;

public class BTMessageHandler extends Handler {
    private Activity mActivity = null;

    public static final int MESSAGE_BT_CONNECTED = 0;   // BT connection established
    public static final int MESSAGE_BT_RESET = 1;       // Need to reset BT connection
    public static final int MESSAGE_BT_READ = 2;        // Read bytes from BT

    public BTMessageHandler(Looper looper, Activity activity) {
        super(looper);
        mActivity = activity;
    }

    @Override
    public void handleMessage(Message message) {
        try {
            Intent intent = null;
            switch (message.what) {
                case MESSAGE_BT_CONNECTED:
                    Log.d("LOG", "BTMessageHandler received BT connect confirmation.");
                    mActivity.findViewById(R.id.waiting_message).setVisibility(View.GONE);
                    break;
                case MESSAGE_BT_RESET:
                    Log.d("LOG", "BTMessageHandler received BT msg: Reset. Restarting BT connection.");
                    intent = new Intent(mActivity, MainActivity.class);
                    mActivity.finish();
                    mActivity.startActivity(intent);
                    break;
                case MESSAGE_BT_READ:
                    Log.d("LOG", "BTMessageHandler received BT msg: '" + message.obj + "'");

                    String messageStr = message.obj.toString();
                    // Example:
                    // GOTO Calibrate1 { json representation of RGBColor }
                    // TODO replace entire message with JSON, not just payload
                    if (messageStr.startsWith("GOTO")) {
                        if (messageStr.contains("Calibrate1")) {
                            String colorJson = messageStr.substring("GOTO Calibrate1".length());
                            int colorInt = new Gson().fromJson(colorJson, RGBColor.class).toIntColor();
                            intent = new Intent(mActivity, CalibrateActivity.class);
                            intent.putExtra(CalibrateActivity.CALIBRATION_COLOR_EXTRA, colorInt);
                            mActivity.startActivity(intent);
                        } else if (messageStr.contains("TrainingOn")) {
                            int prefixLength = "GOTO TrainingOn \"".length();
                            int endOfModeIdx = messageStr.indexOf('\"', prefixLength);
                            String mode = messageStr.substring(prefixLength, endOfModeIdx);
                            String mapJson = messageStr.substring(endOfModeIdx + 1);
                            Map<String, RGBColor> colorMap = new Gson().fromJson(mapJson, new TypeToken<HashMap<String, RGBColor>>() {}.getType());

                            // TODO don't rely on these fragile names (share enums with phone app?)
                            RGBColor redColor = colorMap.get("Training colors Red");
                            RGBColor grayColor = colorMap.get("Training colors Gray");

                            if (mActivity.getClass() == TrainingActivity.class) {
                                ((TrainingActivity) mActivity).setRedColor(redColor.toIntColor());
                                ((TrainingActivity) mActivity).setGrayColor(grayColor.toIntColor());
                                ((TrainingActivity) mActivity).setTrainingMode(mode);
                                ((TrainingActivity) mActivity).setObjVisible(true);
                            } else {
                                intent = new Intent(mActivity, TrainingActivity.class);
                                intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
                                intent.putExtra(TrainingActivity.TRAINING_RED_COLOR_EXTRA, redColor.toIntColor());
                                intent.putExtra(TrainingActivity.TRAINING_GRAY_COLOR_EXTRA, grayColor.toIntColor());
                                intent.putExtra(TrainingActivity.TRAINING_MODE_EXTRA, mode);
                                mActivity.startActivity(intent);
                            }
                        } else if (messageStr.contains("TrainingOff")) {
                            if (mActivity.getClass() == TrainingActivity.class) {
                                ((TrainingActivity) mActivity).setObjVisible(false);
                            } else {
                                intent = new Intent(mActivity, TrainingActivity.class);
                                intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
                                mActivity.startActivity(intent);
                            }
                        } else if (messageStr.contains("Testing")) {
                            String mapJson = messageStr.substring("GOTO Testing".length());

                            intent = new Intent(mActivity, TestingActivity.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION); // Important to not fade colors at all when displaying
                            intent.putExtra(TestingActivity.TESTING_COLOR_MAP_EXTRA, mapJson);
                            mActivity.startActivity(intent);
                        }
                    } else if (messageStr.startsWith("CALIBRATE")) {
                        String colorJson = messageStr.substring("CALIBRATE".length());
                        int colorInt = new Gson().fromJson(colorJson, RGBColor.class).toIntColor();

                        // TODO Extract activity-specific logic like this, into the activities themselves
                        if (mActivity.getClass() == CalibrateActivity.class) {
                            try {
                                mActivity.findViewById(R.id.bg).setBackgroundColor(colorInt);
                            } catch (Exception e) {
                                e.printStackTrace();
                                Log.d("LOG", "Expected CalibrateActivity to have bg.");
                            }
                        }
                    } else if (messageStr.startsWith("RECORD")) {
                        if (messageStr.contains("Start")) {
                            String filename = messageStr.substring("RECORD Start ".length()) + ".mp4";

                            if (mActivity.getClass() == TrainingActivity.class) {
                                ((TrainingActivity) mActivity).startRecording(filename);
                            } else if (mActivity.getClass() == TestingActivity.class) {
                                ((TestingActivity) mActivity).startRecording(filename);
                            }
                        } else if (messageStr.contains("Stop")) {
                            if (mActivity.getClass() == TrainingActivity.class) {
                                ((TrainingActivity) mActivity).stopRecording();
                            } else if (mActivity.getClass() == TestingActivity.class) {
                                ((TestingActivity) mActivity).stopRecording();
                            }
                        }
                    } else if (messageStr.contains("ANIM")) {
                        if (messageStr.contains("Start")) {
                            if (mActivity.getClass() == TrainingActivity.class) {
                                ((TrainingActivity) mActivity).setAnimating(true);
                            } else {
                                Log.d("Log", "WARNING: Tried to start animating while not in TrainingActivity.");
                            }
                        } else {
                            if (mActivity.getClass() == TrainingActivity.class) {
                                ((TrainingActivity) mActivity).setAnimating(false);
                            } else {
                                Log.d("Log", "WARNING: Tried to stop animating while not in TrainingActivity.");
                            }
                        }
                    } else if (messageStr.startsWith("DISPENSE")) {
                        // TODO extract this dupe code (also found in TestingActivity)
                        // Play a sound so animals know they did it right.
                        MediaPlayer player = MediaPlayer.create(mActivity, R.raw.whistle);
                        player.setAudioStreamType(AudioManager.STREAM_MUSIC);
                        player.start();

                        // Then dispense food.
                        ArduinoMessager.send(mActivity, ArduinoMessager.ARDUINO_DISPENSE);
                    } else if (messageStr.startsWith("CONVEYORBACKFAR")) {
                        ArduinoMessager.send(mActivity, ArduinoMessager.ARDUINO_BACKFAR);
                    } else if (messageStr.startsWith("CONVEYORBACK")) {
                        ArduinoMessager.send(mActivity, ArduinoMessager.ARDUINO_BACK);
                    } else if (messageStr.startsWith("ABORTTESTING")) {
                        if (mActivity.getClass() == TestingActivity.class) {
                            ((TestingActivity) mActivity).abort();
                        }
                    } else if (messageStr.startsWith("STARTSESSION")) {
                        String phase = messageStr.contains("phase1") ? TestingActivity.PHASE1 : TestingActivity.PHASE2;
                        if (mActivity.getClass() == TestingActivity.class) {
                            ((TestingActivity) mActivity).mPhase = phase;
                            ((TestingActivity) mActivity).start();
                        }
                    }
                    break;
                default:
                    Log.d("LOG", "Warning: Activity received unknown message from BT thread: " + message);
            }
        } catch (Exception e) {
            // Just in case there's a bug we miss, keep this thread from dying when there's an exception.
            // It needs to always be ready to handle incoming Bluetooth messages.
            Log.d("LOG", "Error: Uncaught exception in BTMessageHandler.handleMessage(). Gracefully continuing.");
            e.printStackTrace();
        }
    }

}
