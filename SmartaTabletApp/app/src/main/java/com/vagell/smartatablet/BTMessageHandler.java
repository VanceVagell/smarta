package com.vagell.smartatablet;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
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
                            String mapJson = messageStr.substring("GOTO TrainingOn".length());
                            Map<String, RGBColor> colorMap = new Gson().fromJson(mapJson, new TypeToken<HashMap<String, RGBColor>>() {}.getType());

                            // TODO don't rely on these fragile names (share enums with phone app?)
                            RGBColor bgColor = colorMap.get("Training colors Background");
                            RGBColor objColor = colorMap.get("Training colors Object");

                            if (mActivity.getClass() == TrainingActivity.class) {
                                ((TrainingActivity) mActivity).setObjColor(objColor.toIntColor());
                                ((TrainingActivity) mActivity).setBgColor(bgColor.toIntColor());
                                ((TrainingActivity) mActivity).setObjVisible(true);
                            } else {
                                intent = new Intent(mActivity, TrainingActivity.class);
                                intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
                                intent.putExtra(TrainingActivity.TRAINING_OBJ_COLOR_EXTRA, objColor.toIntColor());
                                intent.putExtra(TrainingActivity.TRAINING_BG_COLOR_EXTRA, bgColor.toIntColor());
                                mActivity.startActivity(intent);
                            }
                        } else if (messageStr.contains("TrainingOff")) {
                            if (mActivity.getClass() == TrainingActivity.class) {
                                ((TrainingActivity) mActivity).setObjVisible(false);
                            } else {
                                intent = new Intent(mActivity, TrainingActivity.class);
                                intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
                                intent.putExtra(TrainingActivity.TRAINING_OBJ_COLOR_EXTRA, Color.BLACK);
                                intent.putExtra(TrainingActivity.TRAINING_BG_COLOR_EXTRA, Color.BLACK);
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
