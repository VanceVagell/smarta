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

import android.accounts.AccountManager;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Vibrator;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.Selection;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.auth.GoogleAuthException;
import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.UserRecoverableAuthException;
import com.google.android.gms.common.AccountPicker;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;


public class MainActivity extends Activity {

    private static final String BT_MESSAGE_DIVIDER = "***";

    private static final String CALIBRATION_MODE_NONE = "none";
    private static final String CALIBRATION_MODE_TRAINING = "training";
    private static final String CALIBRATION_MODE_TESTING = "testing";
    private static final String COLOR_R = "R";
    private static final String COLOR_G = "G";
    private static final String COLOR_B = "B";

    private String mCalibrationMode = CALIBRATION_MODE_NONE;
    private static final int FULL_DISPENSE_COUNT = 7;
    private int mDispenseRemaining = FULL_DISPENSE_COUNT; // how many food rewards are left to dispense

    // TODO refactor all this nonsense out into activities. complex because BT stack needs to be
    // reworked to support cross-Activity use
    private static final int SCREEN_SELECT = 0;
    private static final int SCREEN_TRAIN = 1;
    private static final int SCREEN_TEST = 2;
    private int mCurrentScreen = SCREEN_SELECT;

    private static final String PHASE_1 = "Phase 1";
    private static final String PHASE_2 = "Phase 2";

    private static String mSpreadsheetName = null;

    /**
     * Custom code for our request to enable Bluetooth. Value doesn't matter, we get it back
     * in the callback.
     */
    private static final int OAUTH_CALLBACK = 2389;
    private static final int PICK_ACCOUNT_CALLBACK = 8403;
    private static final int PICK_SPREADSHEET_CALLBACK = 3498;

    /**
     * The Bluetooth adapter we'll use to communicate with the phone app.
     */
    private final BluetoothAdapter mBluetoothAdapter;

    private BTConnectThread mBtConnectThread = null;

    private String mSelectedSubject = "";
    private String mSelectedTestingPhase = PHASE_1;
    private String mSelectedTrainingMode = "Blank";
    private String mOAuthToken = null;
    private BTMessageHandler mBtMessageHandler = null;
    private Date mSessionStartTime = null;

    private static final String SHEETS_SCOPE = "oauth2:https://spreadsheets.google.com/feeds";

    public MainActivity() {
        super();
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        loadAccount();
        loadSpreadsheetName();
        loadCalibration();

        setContentView(R.layout.main_activity);

        // Setup various UI components
        setupCalibrationScreen();
        setupSelectScreen();
        setupTestingScreen();
        setupTrainingScreen();

        // Initialize Bluetooth
        if (mBluetoothAdapter == null) {
            // Device does not support Bluetooth
            throw new RuntimeException("Device does not support Bluetooth.");
        }

        // Restart Bluetooth to workaround bug where an old Bluetooth socket won't connect.
        if (mBluetoothAdapter.isEnabled()) {
            BroadcastReceiver btReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    final String action = intent.getAction();

                    if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                        final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                        switch (state) {
                            case BluetoothAdapter.STATE_OFF:
                                Log.d("LOG", "BT disabled, enabling.");
                                mBluetoothAdapter.enable();
                                break;
                            default:
                                Log.d("LOG", "BT enabled, attempting to connect.");
                                resetBtConnection();
                                break;
                        }
                    }
                }
            };
            IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
            registerReceiver(btReceiver, filter);
            mBluetoothAdapter.disable();
        } else {
            mBluetoothAdapter.enable();
        }

        if (mEmailAccount == null) {
            pickAccount();
        } else {
            getOAuthTokenInAsyncTask();
        }
    }

    /**
     * Ask user which account to use.
     */
    private void pickAccount() {
        Intent intent = AccountPicker.newChooseAccountIntent(null, null, new String[] { GoogleAuthUtil.GOOGLE_ACCOUNT_TYPE }, false,
                null, GoogleAuthUtil.GOOGLE_ACCOUNT_TYPE, null, null);
        startActivityForResult(intent, PICK_ACCOUNT_CALLBACK);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu items for use in the action bar
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_actions, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle presses on the action bar items
        switch (item.getItemId()) {
            case R.id.action_set_colors:
                // TODO have a calibration activity instead. I'm doing this to avoid the advanced BT service handling
                // needed when you have >1 activity (as in LemurColor).
                setCalibrating(true);
                return true;
            case R.id.action_change_account:
                pickAccount();
                return true;
            case R.id.action_reset_dispense_count:
                setDispenseRemaining(FULL_DISPENSE_COUNT);
                return true;
            case R.id.action_force_dispense1:
                sendBtMessage("DISPENSE");
                return true;
            case R.id.action_force_rewind1:
                sendBtMessage("CONVEYORBACK");
                return true;
            case R.id.action_pick_spreadsheet:
                pickSpreadsheet();
                return true;
            case android.R.id.home:
                handleBack();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onBackPressed() {
        handleBack();
    }

    private void handleBack() {
        rewindAll();
        if (mCurrentScreen == SCREEN_TRAIN) {
            setTrainingStarted(false);
        } else if (mCurrentScreen == SCREEN_TEST) {
            setTestingStarted(false);
        }

        gotoScreen(SCREEN_SELECT);
    }

    // TODO extract into a calibration activity. complex because BT stack needs updating to work across activities.
    private void setCalibrating(boolean calibrating) {
        // Calibration isn't handled as a normal screen, because it's a temporary mode that can
        // come up from any screen.
        if (!calibrating) {
            findViewById(R.id.calibrate_container).setVisibility(View.GONE);
            gotoScreen(mCurrentScreen);
        } else {
            // Hide current screen
            findViewById(R.id.select_container).setVisibility(View.GONE);
            findViewById(R.id.train_container).setVisibility(View.GONE);
            findViewById(R.id.test_container).setVisibility(View.GONE);
            setTitle("Colors");
            sendBtMessage("GOTO Calibrate1 " + new Gson().toJson(mColorBeingCalibrated));
            findViewById(R.id.calibrate_container).setVisibility(View.VISIBLE);
        }
    }

    // TODO extract all this stuff into activities.
    private void gotoScreen(int screen) {
        mCurrentScreen = screen;

        switch (screen) {
            case SCREEN_SELECT:
                getActionBar().setDisplayHomeAsUpEnabled(false);
                setTitle(R.string.app_name);
                break;
            case SCREEN_TRAIN:
                getActionBar().setDisplayHomeAsUpEnabled(true);
                setTitle("Training " + mSelectedSubject);
                break;
            case SCREEN_TEST:
                getActionBar().setDisplayHomeAsUpEnabled(true);
                setTitle("Testing " + mSelectedSubject);
                ((TextView) findViewById(R.id.test_phase_label)).setText(mSelectedTestingPhase);
                break;
            default:
                Log.d("LOG", "Tried to go to unknown screen: " + screen);
                break;
        }

        findViewById(R.id.select_container).setVisibility(screen == SCREEN_SELECT ? View.VISIBLE : View.GONE);
        findViewById(R.id.train_container).setVisibility(screen == SCREEN_TRAIN ? View.VISIBLE : View.GONE);
        findViewById(R.id.test_container).setVisibility(screen == SCREEN_TEST ? View.VISIBLE : View.GONE);
    }

    private void getOAuthTokenInAsyncTask() {
        final Activity activity = this;
        AsyncTask task = new AsyncTask() {
            @Override
            protected Object doInBackground(Object[] objects) {
                try {
                    // Safe to update mOAuthToken asynchronously, because there's only 2 code points
                    // that modify this, and they're exclusive. Either here, or in the activity
                    // this spawns (below).
                    // TODO protect mOAuth token in synchronized get/set, or implement onPostExecute in UI thread.
                    mOAuthToken = GoogleAuthUtil.getToken(activity, mEmailAccount, SHEETS_SCOPE, null /* extras */);
                    Log.d("LOG", "Got OAuth2 token: " + mOAuthToken);

                    // TODO move this to only happen after spreadsheet has been picked
                    // We know we have internet access if we got an OAuth token. Check if we have
                    // any backup data stored locally that we could upload.
                    checkForBackupDataInAsyncTask();
                } catch (UserRecoverableAuthException e) {
                    startActivityForResult(e.getIntent(), OAUTH_CALLBACK);
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (GoogleAuthException e) {
                    e.printStackTrace();
                }

                return null;
            }
        };

        task.execute((Void) null);
    }


    private void checkForBackupDataInAsyncTask() {
        final Activity activity = this;
        AsyncTask task = new AsyncTask() {
            @Override
            protected Object doInBackground(Object[] objects) {
                File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
                if (dir.exists()) {
                    final File trainingBackupFile = new File(dir, TrainingDataSaverThread.BACKUP_TEXT_FILE_NAME);
                    final File testingBackupFile = new File(dir, TrialDataSaverThread.BACKUP_TEXT_FILE_NAME);
                    final boolean trainingDataExists = trainingBackupFile.exists();
                    final boolean testingDataExists = testingBackupFile.exists();
                    if (trainingDataExists || testingDataExists) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                new AlertDialog.Builder(activity)
                                        .setTitle("Upload backup data?")
                                        .setMessage("There's backup data on this phone that can be saved online.")
                                        .setCancelable(true)
                                        .setPositiveButton("Upload", new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which) {
                                                uploadBackupData(trainingDataExists, trainingBackupFile, testingDataExists, testingBackupFile);
                                                Log.d("Log", "Uploading training data...");
                                            }
                                        })
                                        .show();
                            }
                        });
                    }
                }

                return null;
            }
        };

        task.execute((Void) null);
    }


    // TODO extract this stuff to TrainingDataSaverThread and TrialDataSaverThread
    // TODO now that data isn't expected to be hand-pasted from backup files, change to JSON instead of CSV to simplify this parsing
    private void uploadBackupData(boolean trainingDataExists, File trainingBackupFile, boolean testingDataExists, File testingBackupFile) {
        boolean problem = false;
        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
        if (trainingDataExists) {
            // Move the backup file to a new name, if any rows fail they'll drop back into the normal file.
            File oldBackupFile = new File(dir, TrainingDataSaverThread.BACKUP_TEXT_FILE_NAME.replace(".txt", ".old-" + new Date().getTime() + ".txt")); // TODO break suffix out as constant
            trainingBackupFile.renameTo(oldBackupFile);

            BufferedReader inputStream = null;
            try {
                inputStream = new BufferedReader(new FileReader(oldBackupFile.getAbsoluteFile()));
                while (true) {
                    String line = inputStream.readLine();
                    if (line == null) {
                        break;
                    }
                    String[] values = line.split(",");
                    if (values.length != 3) { // 3 expected values, see TrainingDataSaverThread
                        Log.d("Log", "ERROR: Backup training data has corrupt entry: '" + line + "'");
                        continue;
                    }
                    new TrainingDataSaverThread(this, values[0] /* subject */, values[1] /* timestamp */, values[2] /* duration */, mOAuthToken).start();
                }
            } catch (Exception e) {
                e.printStackTrace();
                try {
                    inputStream.close();
                } catch (Exception e2) { }
                problem = true;
            }
        }

        if (testingDataExists) {
            // Move the backup file to a new name, if any rows fail they'll drop back into the normal file.
            File oldBackupFile = new File(dir, TrialDataSaverThread.BACKUP_TEXT_FILE_NAME.replace(".txt", ".old-" + new Date().getTime() + ".txt")); // TODO break suffix out as constant
            testingBackupFile.renameTo(oldBackupFile);

            BufferedReader inputStream = null;
            try {
                inputStream = new BufferedReader(new FileReader(oldBackupFile.getAbsoluteFile()));
                while (true) {
                    String line = inputStream.readLine();
                    if (line == null) {
                        break;
                    }
                    String[] values = line.split(",");
                    if (values.length != 9) { // 9 expected values, see TrialDataSaverThread
                        Log.d("Log", "ERROR: Backup testing data has corrupt entry: '" + line + "'");
                        continue;
                    }
                    TrialData trialData = new TrialData(Integer.parseInt(values[3]) - 1); // adjust to be 0-based
                    trialData.duration = Integer.parseInt(values[4]);
                    RGBColor leftColor = new RGBColor();
                    leftColor.r = Integer.parseInt(values[5].substring(3, values[5].indexOf("G:") - 1));
                    leftColor.g = Integer.parseInt(values[5].substring(values[5].indexOf("G:") + 3, values[5].indexOf("B:") - 1));
                    leftColor.b = Integer.parseInt(values[5].substring(values[5].indexOf("B:") + 3));
                    trialData.leftColor = leftColor;
                    RGBColor rightColor = new RGBColor();
                    rightColor.r = Integer.parseInt(values[6].substring(3, values[6].indexOf("G:") - 1));
                    rightColor.g = Integer.parseInt(values[6].substring(values[6].indexOf("G:") + 3, values[6].indexOf("B:") - 1));
                    rightColor.b = Integer.parseInt(values[6].substring(values[6].indexOf("B:") + 3));
                    trialData.rightColor = rightColor;
                    trialData.timedOut = Boolean.parseBoolean(values[7]);
                    trialData.subjectChoseCorrectly = Boolean.parseBoolean(values[8]);
                    Date sessionStartTime = new SimpleDateFormat(RenderedTimer.TIME_FORMAT_STRING).parse(values[2]);
                    new TrialDataSaverThread(this,
                            values[0] /* subject */, values[1] /* phase */, trialData,
                            mOAuthToken, sessionStartTime).start();
                }
            } catch (Exception e) {
                e.printStackTrace();
                try {
                    inputStream.close();
                } catch (Exception e2) { }
                problem = true;
            }
        }

        showToast(problem ? "Problem uploading data" : "Backup data uploaded");
    }


    private void showToast(String msg) {
        final Activity activity = this;
        final String finalMsg = new String(msg);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(activity, finalMsg, Toast.LENGTH_SHORT).show();
            }
        });
    }


    /**
     * Whether user hit the "Start" button to begin training.
     */
    private boolean mTrainingStarted = false;

    private void setupTrainingScreen() {
        // Create timer
        mTrainingTimer = new RenderedTimer(this, (TextView) findViewById(R.id.train_timer));
    }

    private void setupTestingScreen() {
        // Create timer
        mTestingTimer = new RenderedTimer(this, (TextView) findViewById(R.id.trial_timer1));
    }

    private void handleCalibrationColorSetChanged() {
        // Update color list to match new set
        Spinner colorSetsSpinner = (Spinner) findViewById(R.id.calibration_color_set);
        String colorSetName = colorSetsSpinner.getSelectedItem().toString();
        Log.d("LOG", "Selected color set: " + colorSetName);

        mCalibrationMode = colorSetName.equals("Training colors") ?
                CALIBRATION_MODE_TRAINING : CALIBRATION_MODE_TESTING;
        int array = (mCalibrationMode == CALIBRATION_MODE_TRAINING) ?
                R.array.training_colors_array : R.array.testing_colors_array;
        Spinner colorsSpinner = (Spinner) findViewById(R.id.calibration_color);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this, array,
            android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        colorsSpinner.setAdapter(adapter);

        handleCalibrationColorChanged();
    }

    private void handleCalibrationColorChanged() {
        // Update which color we're calibrating
        String colorSetName = ((Spinner) findViewById(R.id.calibration_color_set)).getSelectedItem().toString();
        String colorName = ((Spinner) findViewById(R.id.calibration_color)).getSelectedItem().toString();
        mColorBeingCalibrated = this.mColorMap.get(colorSetName + " " + colorName);

        // Update the local preview to show this color
        ((View) findViewById(R.id.calibratePreview)).setBackgroundColor(mColorBeingCalibrated.toIntColor());

        // Populate the input fields to match this color
        ((SeekBar) findViewById(R.id.seekBarR)).setProgress(mColorBeingCalibrated.r);
        ((EditText) findViewById(R.id.editTextR)).setText(String.valueOf(mColorBeingCalibrated.r));
        ((SeekBar) findViewById(R.id.seekBarG)).setProgress(mColorBeingCalibrated.g);
        ((EditText) findViewById(R.id.editTextG)).setText(String.valueOf(mColorBeingCalibrated.g));
        ((SeekBar) findViewById(R.id.seekBarB)).setProgress(mColorBeingCalibrated.b);
        ((EditText) findViewById(R.id.editTextB)).setText(String.valueOf(mColorBeingCalibrated.b));

        // Put the text cursor at the end of whichever field it's in. Without this, cursor moves
        // to beginning of input field after each character typed.
        // TODO instead restore it to previous position
        View focusedView = getCurrentFocus();
        if (focusedView != null && focusedView.getClass() == EditText.class) {
            EditText editText = ((EditText) focusedView);
            int endOfText = editText.length();
            Editable text = editText.getText();
            Selection.setSelection(text, endOfText);
        }
    }

    private void setupCalibrationScreen() {
        // Populate list of calibration color sets
        Spinner colorSetsSpinner = (Spinner) findViewById(R.id.calibration_color_set);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.calibration_color_sets_array, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        colorSetsSpinner.setAdapter(adapter);

        // Listen for changes in calibration color set selection
        colorSetsSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                handleCalibrationColorSetChanged();
            }
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        // Listen for changes to calibration color selection
        Spinner colorsSpinner = (Spinner) findViewById(R.id.calibration_color);
        colorsSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                handleCalibrationColorChanged();
            }
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        // Populate calibration colors
        handleCalibrationColorSetChanged();

        // Listen to color calibration inputs
        ((SeekBar) findViewById(R.id.seekBarR)).setOnSeekBarChangeListener(new SeekBarSettingUpdater(COLOR_R));
        ((EditText) findViewById(R.id.editTextR)).addTextChangedListener(new EditTextSettingUpdater(COLOR_R));
        ((SeekBar) findViewById(R.id.seekBarG)).setOnSeekBarChangeListener(new SeekBarSettingUpdater(COLOR_G));
        ((EditText) findViewById(R.id.editTextG)).addTextChangedListener(new EditTextSettingUpdater(COLOR_G));
        ((SeekBar) findViewById(R.id.seekBarB)).setOnSeekBarChangeListener(new SeekBarSettingUpdater(COLOR_B));
        ((EditText) findViewById(R.id.editTextB)).addTextChangedListener(new EditTextSettingUpdater(COLOR_B));
    }

    private void setupSelectScreen() {
        // Populate the list of subject names
        final ListView listView = ((ListView) findViewById(R.id.subject_names));
        ArrayAdapter<String> arrayAdapter = new ArrayAdapter<String>(
                this,
                android.R.layout.simple_list_item_activated_1,
                getResources().getStringArray(R.array.subjects_array));
        listView.setAdapter(arrayAdapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                mSelectedSubject = (String)(listView.getItemAtPosition(position));
            }
        });

        // Select first item by default
        listView.setItemChecked(0, true);
        listView.performItemClick(listView.getSelectedView(), 0, 0);
    }

    @Override
    protected void onResume() {
        super.onResume();

        resetBtConnection();
    }

    private void resetBtConnection() {
        // Reset any old connections.
        BluetoothSocket socket = ((LemurColorRemoteApplication) getApplication()).getBtSocket();
        if (socket == null || !socket.isConnected()) {
            if (mBtConnectThread != null) {
                mBtConnectThread.cancel();
            }
            Thread btCommThread = ((LemurColorRemoteApplication) getApplication()).getBtCommThread();
            if (btCommThread != null) {
                btCommThread.interrupt();
            }
            try {
                ((LemurColorRemoteApplication) getApplication()).getBtSocket().close();
            } catch (Exception e) {
            }

            // Connect to the tablet via Bluetooth.
            Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
            if (pairedDevices.size() == 0) {
                Log.d("LOG", "No paired Bluetooth devices.");
                return;
            } else {
                // Checks for a SMARTA tablet server on all paired devices.
                // TODO be smarter about what devices to try to connect to.
                for (int i = 0; i < pairedDevices.size(); i++) {
                    BluetoothDevice bluetoothDevice = pairedDevices.toArray(new BluetoothDevice[pairedDevices.size()])[i];
                    Handler connectHandler = new Handler(Looper.getMainLooper()) {
                        @Override
                        public void handleMessage(Message message) {
                            switch (message.what) {
                                case BTConnectThread.MESSAGE_BT_CONNECTED:
                                    findViewById(R.id.connecting_message).setVisibility(View.GONE);
                                    findViewById(R.id.main_content).setVisibility(View.VISIBLE);
                                    break;
                                // TODO add a case for MESSAGE_BT_CONNECT_FAILED so we can retry (requires extracting this handler to avoid recursive ref)
                                default:
                                    Log.d("LOG", "Unknown BT message: " + message);
                            }
                        }
                    };
                    mBtMessageHandler = new BTMessageHandler();
                    mBtConnectThread = new BTConnectThread((LemurColorRemoteApplication) getApplication(), connectHandler, mBtMessageHandler, bluetoothDevice, mBluetoothAdapter, this);
                    mBtConnectThread.start();
                }
            }
        }
    }

    public class BTMessageHandler extends Handler {
        public static final int MESSAGE_BT_READ = 2; // Read bytes from BT

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case MESSAGE_BT_READ:
                    Log.d("LOG", "BTMessageHandler received BT msg: '" + msg.obj + "'");
                    String messageStr = msg.obj.toString();

                    if (messageStr.startsWith("TRIALDATA")) {
                        String trialDataJson = messageStr.substring("TRIALDATA".length());
                        TrialData trialData =  new Gson().fromJson(trialDataJson, new TypeToken<TrialData>() {}.getType());
                        handleTrialData(trialData);
                    }

                    break;
            }
        }
    }

    private int mTrialCount = 1;
    private static final int NUM_TRIALS_PER_SESSION = 7;
    private static final int NO_ID = -1;

    private void highlightTestingRow(int previousTimerId, int previousRowId, int timerId, int rowId, int labelId, int iconId) {
        if (previousTimerId != NO_ID) {
            findViewById(previousTimerId).setVisibility(View.INVISIBLE);
        }
        findViewById(timerId).setVisibility(View.VISIBLE);
        mTestingTimer = new RenderedTimer(this, (TextView) findViewById(timerId));
        if (previousRowId != NO_ID) {
            findViewById(previousRowId).setBackgroundColor(getResources().getColor(R.color.transparent));
        }
        findViewById(rowId).setBackgroundColor(getResources().getColor(R.color.light_gray));
        ((TextView) findViewById(labelId)).setTextColor(getResources().getColor(R.color.dark_text));
        findViewById(iconId).setAlpha(0.87f);
    }

    private synchronized void handleTrialData(TrialData trialData) {
        mTrialCount++;

        if (mTrialCount > NUM_TRIALS_PER_SESSION) {
            setTestingStarted(false);
        } else {
            // TODO setup a data binding instead of manually twiddling all of these UI settings.
            switch (mTrialCount) {
                case 1:
                    highlightTestingRow(NO_ID, NO_ID, R.id.trial_timer1, R.id.test_trial_row1, R.id.test_trial_label1, R.id.test_trial_icon1);
                    break;
                case 2:
                    highlightTestingRow(R.id.trial_timer1, R.id.trial_timer1, R.id.trial_timer2, R.id.test_trial_row2, R.id.test_trial_label2, R.id.test_trial_icon2);
                    break;
                case 3:
                    highlightTestingRow(R.id.trial_timer2, R.id.trial_timer2, R.id.trial_timer3, R.id.test_trial_row3, R.id.test_trial_label3, R.id.test_trial_icon3);
                    break;
                case 4:
                    highlightTestingRow(R.id.trial_timer3, R.id.trial_timer3, R.id.trial_timer4, R.id.test_trial_row4, R.id.test_trial_label4, R.id.test_trial_icon4);
                    break;
                case 5:
                    highlightTestingRow(R.id.trial_timer4, R.id.trial_timer4, R.id.trial_timer5, R.id.test_trial_row5, R.id.test_trial_label5, R.id.test_trial_icon5);
                    break;
                case 6:
                    highlightTestingRow(R.id.trial_timer5, R.id.trial_timer5, R.id.trial_timer6, R.id.test_trial_row6, R.id.test_trial_label6, R.id.test_trial_icon6);
                    break;
                case 7:
                    highlightTestingRow(R.id.trial_timer6, R.id.trial_timer6, R.id.trial_timer7, R.id.test_trial_row7, R.id.test_trial_label7, R.id.test_trial_icon7);
                    break;
                default:
                    Log.d("LOG", "Unexpected trial count: " + mTrialCount);
                    break;
            }

            mTestingTimer.startCountDown(35);
        }

        if (trialData.timedOut) {
            Toast.makeText(this, "Trial out of time", Toast.LENGTH_LONG).show();
        } else {
            if (trialData.subjectChoseCorrectly) {
                setDispenseRemaining(mDispenseRemaining - 1);
            }
            Toast.makeText(this, trialData.subjectChoseCorrectly ? "Correct choice" : "Incorrect choice", Toast.LENGTH_LONG).show();
        }

        Log.d("LOG", "Saving trial data.");
        new TrialDataSaverThread(this, mSelectedSubject, mSelectedTestingPhase, trialData, mOAuthToken, mSessionStartTime).start();
    }

    private synchronized void sendBtMessage(String message) {
        Log.d("LOG", "Sending BT message: '" + message + "'");
        BluetoothSocket socket = ((LemurColorRemoteApplication) getApplication()).getBtSocket();
        if (socket != null) {
            try {
                socket.getOutputStream().write((message + BT_MESSAGE_DIVIDER).getBytes());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            ((LemurColorRemoteApplication) getApplication()).getBtSocket().close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String mEmailAccount = null;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == OAUTH_CALLBACK) {
            mOAuthToken = data.getExtras().getString("authtoken");
            Log.d("LOG", "Got OAuth2 token (via activity result): " + mOAuthToken);
        } else if (requestCode == PICK_ACCOUNT_CALLBACK) {
            mEmailAccount = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
            saveAccount();
            Log.d("LOG", "Email account picking callback: " + mEmailAccount);
            getOAuthTokenInAsyncTask();
        } else if (requestCode == PICK_SPREADSHEET_CALLBACK) {
            if (resultCode == Activity.RESULT_OK) {
                if (data != null) {
                    Uri uri = data.getData();
                    Log.d("LOG", "Data spreadsheet URI: " + uri.toString());

                    // TODO use Drive's GMS core APIs instead, so we can get the specific
                    // spreadsheet by ID (using OpenFileActivityBuilder). This requires a rewrite
                    // of much of the Drive code in this app, so I'm deferring it and just using the
                    // picker to get spreadsheet name right now. If there's multiple spreadsheets
                    // with this same name, it will just use the first one.

                    // Resolve to spreadsheet name.
                    Cursor cursor = getContentResolver().query(uri, null, null, null, null);
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    cursor.moveToFirst();
                    String fileName = cursor.getString(nameIndex);
                    Log.d("LOG", "Data spreadsheet name: " + fileName);
                    setSpreadsheetName(fileName);
                    saveSpreadsheetName();
                }
            }
        }
    }

    public static synchronized void setSpreadsheetName(String name) {
        mSpreadsheetName = name;
    }

    public static synchronized String getSpreadsheetName() {
        return mSpreadsheetName;
    }

    private void pickSpreadsheet() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, PICK_SPREADSHEET_CALLBACK);
    }

    private RGBColor mColorBeingCalibrated = null;

    /**
     * Keyed by combination of color set + color name, e.g. "Training colors Background".
     * TODO don't use UI strings for color map keys (will be stored in prefs, fragile)
     */
    private Map<String, RGBColor> mColorMap = new HashMap<String, RGBColor>();

    /**
     * Updates a setting when a seekbar's position changes.
     */
    private class SeekBarSettingUpdater implements SeekBar.OnSeekBarChangeListener {
        private String mForColor = null;

        public SeekBarSettingUpdater(String forColor) {
            mForColor = forColor;
        }

        @Override
        public void onProgressChanged(SeekBar seekBar, int i, boolean fromUser) {
            // Avoid loop where text field updates seekbar, updates text field, etc.
            if (!fromUser) {
                return;
            }

            if (mForColor.equals(COLOR_R)) {
                mColorBeingCalibrated.r = i;
                ((EditText) findViewById(R.id.editTextR)).setText(String.valueOf(mColorBeingCalibrated.r));
            } else if (mForColor.equals(COLOR_G)) {
                mColorBeingCalibrated.g = i;
                ((EditText) findViewById(R.id.editTextG)).setText(String.valueOf(mColorBeingCalibrated.g));
            } else if (mForColor.equals(COLOR_B)) {
                mColorBeingCalibrated.b = i;
                ((EditText) findViewById(R.id.editTextB)).setText(String.valueOf(mColorBeingCalibrated.b));
            }

            applyCalibration();
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) { }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) { }
    }

    /**
     * Updates a setting when an edittext's value changes.
     */
    private class EditTextSettingUpdater implements TextWatcher {
        private String mForColor = null;

        public EditTextSettingUpdater(String forColor) {
            mForColor = forColor;
        }

        @Override
        public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) { }

        @Override
        public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) { }

        @Override
        public void afterTextChanged(Editable editable) {
            int progress = 0;
            String text = editable.toString();
            try {
                progress = Integer.parseInt(text);
                if (progress < 0) {
                    progress = 0;
                } else if (progress > 255) {
                    progress = 255;
                }
            } catch (Exception e) {
                progress = 0;
            }

            if (mForColor.equals(COLOR_R) && mColorBeingCalibrated.r != progress) {
                mColorBeingCalibrated.r = progress;
                ((SeekBar) findViewById(R.id.seekBarR)).setProgress(mColorBeingCalibrated.r);
            } else if (mForColor.equals(COLOR_G) && mColorBeingCalibrated.g != progress) {
                mColorBeingCalibrated.g = progress;
                ((SeekBar) findViewById(R.id.seekBarG)).setProgress(mColorBeingCalibrated.g);
            } else if (mForColor.equals(COLOR_B) && mColorBeingCalibrated.b != progress) {
                mColorBeingCalibrated.b = progress;
                ((SeekBar) findViewById(R.id.seekBarB)).setProgress(mColorBeingCalibrated.b);
            }

            applyCalibration();
        }
    }

    /**
     * Send current calibration values to the tablet via BT.
     */
    private void applyCalibration() {
        saveCalibration();

        // Update the local preview to show this color
        ((View) findViewById(R.id.calibratePreview)).setBackgroundColor(mColorBeingCalibrated.toIntColor());

        // Send to tablet via BT.
        sendBtMessage("CALIBRATE " + new Gson().toJson(mColorBeingCalibrated));
    }

    private static final String PREFS_NAME = "lemur-color-remote-prefs";

    // Saves calibration to the device, so it can be loaded on next app start.
    private void saveCalibration() {
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        SharedPreferences.Editor editor = settings.edit();

        Set<String> keys = mColorMap.keySet();
        Iterator<String> iter = keys.iterator();
        while (iter.hasNext()) {
            String key = iter.next();
            RGBColor value = mColorMap.get(key);
            editor.putString(key, new Gson().toJson(value));
        }

        editor.commit();
    }

    private static String EMAIL_ACCOUNT_PREF = "email account";

    private void loadAccount() {
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        mEmailAccount = settings.getString(EMAIL_ACCOUNT_PREF, null);
        Log.d("LOG", "Using email account: " + mEmailAccount);
    }

    private void saveAccount() {
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(EMAIL_ACCOUNT_PREF, mEmailAccount);
        editor.commit();
        Log.d("LOG", "Saved email account: " + mEmailAccount);
    }

    private static String SPREADSHEET_PREF = "spreadsheet name";

    private void loadSpreadsheetName() {
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        String spreadsheetName = settings.getString(SPREADSHEET_PREF, "Lemur data from SMARTA");
        setSpreadsheetName(spreadsheetName);
        Log.d("LOG", "Using spreadsheet name: " + spreadsheetName);
    }

    private void saveSpreadsheetName() {
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(SPREADSHEET_PREF, getSpreadsheetName());
        editor.commit();
        Log.d("LOG", "Saved spreadsheet name: " + getSpreadsheetName());
    }

    private void loadCalibration() {
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);

        String colorSets[] = getResources().getStringArray(R.array.calibration_color_sets_array);
        String trainingSetName = colorSets[0];
        String testingSetName = colorSets[1];
        String trainingColorNames[] = getResources().getStringArray(R.array.training_colors_array);
        String testingColorNames[] =  getResources().getStringArray(R.array.testing_colors_array);
        String DEFAULT_COLOR = new Gson().toJson(new RGBColor());

        String key, serializedColor;
        RGBColor color = null;

        // Training colors
        for (int i = 0; i < trainingColorNames.length; i++) {
            key = trainingSetName + " " + trainingColorNames[i];
            serializedColor = settings.getString(key, DEFAULT_COLOR);
            try {
                color = new Gson().fromJson(serializedColor, new TypeToken<RGBColor>() {}.getType());
            } catch (Exception e) {
                Log.d("LOG", "Warning: Couldn't deserialize training color, resetting it.");
                color = new RGBColor();
            }
            mColorMap.put(key, color);
        }

        // Testing colors
        for (int i = 0; i < testingColorNames.length; i++) {
            key = testingSetName + " " + testingColorNames[i];
            serializedColor = settings.getString(key, DEFAULT_COLOR);
            try {
                color = new Gson().fromJson(serializedColor, new TypeToken<RGBColor>() {
                }.getType());
            } catch (Exception e) {
                Log.d("LOG", "Warning: Couldn't deserialize testing color, resetting it.");
                color = new RGBColor();
            }
            mColorMap.put(key, color);
        }
    }

    private boolean mTestingStarted = false;
    private RenderedTimer mTrainingTimer, mTestingTimer = null;

    private void setTestingStarted(boolean started) {
        mTestingStarted = started;
        if (started) {
            sendBtMessage("GOTO Testing " + new Gson().toJson(mColorMap));
            mSessionStartTime = new Date();

            mTrialCount = 1;

            // Start session and recording video
            String phase = mSelectedTestingPhase.equals(PHASE_1) ? "phase1" : "phase2";
            sendBtMessage("STARTSESSION " + phase);
            sendBtMessage("RECORD Start " + getCurrentTimeString() + " - TESTING - " + mSelectedSubject);
            highlightTestingRow(NO_ID, NO_ID, R.id.trial_timer1, R.id.test_trial_row1, R.id.test_trial_label1, R.id.test_trial_icon1);
            mTestingTimer.startCountDown(30);
        } else {
            // We don't have any data to save, wait for tablet to send it to us.

            // Tell tablet to stop running trials
            sendBtMessage("RECORD Stop");
            sendBtMessage("ABORTTESTING");

            mTestingTimer.stop();

            // Reset all trial rows
            // TODO use a data binding instead, so don't need to twiddle all these UI settings
            resetTestingRow(R.id.trial_timer1, R.id.test_trial_row1, R.id.test_trial_label1, R.id.test_trial_icon1);
            resetTestingRow(R.id.trial_timer2, R.id.test_trial_row2, R.id.test_trial_label2, R.id.test_trial_icon2);
            resetTestingRow(R.id.trial_timer3, R.id.test_trial_row3, R.id.test_trial_label3, R.id.test_trial_icon3);
            resetTestingRow(R.id.trial_timer4, R.id.test_trial_row4, R.id.test_trial_label4, R.id.test_trial_icon4);
            resetTestingRow(R.id.trial_timer5, R.id.test_trial_row5, R.id.test_trial_label5, R.id.test_trial_icon5);
            resetTestingRow(R.id.trial_timer6, R.id.test_trial_row6, R.id.test_trial_label6, R.id.test_trial_icon6);
            resetTestingRow(R.id.trial_timer7, R.id.test_trial_row7, R.id.test_trial_label7, R.id.test_trial_icon7);
        }
    }

    private void resetTestingRow(int timerId, int rowId, int labelId, int iconId) {
        findViewById(timerId).setVisibility(View.GONE);
        findViewById(rowId).setBackgroundColor(getResources().getColor(R.color.light_gray));
        ((TextView) findViewById(labelId)).setTextColor(getResources().getColor(R.color.light_gray));
        findViewById(iconId).setAlpha(0.54f);
        ((ImageView) findViewById(iconId)).setImageResource(R.drawable.ic_help_black_24dp);
    }

    private void setTrainingStarted(boolean started) {
        mTrainingStarted = started;

        if (started) {
            setTrainingObjectAnimated(false);
            mTrainingTimer.start();
        } else {
            stopTrainingAndSaveVideo();
            setTrainingObjectAnimated(false);
            saveTrainingData(); // Save before stopping the timer.
            mTrainingTimer.stop(); // Do this last, so we can check time elapsed.
        }

        updateTrainingDisplay(started);
    }

    private void updateTrainingDisplay(boolean display) {
        if (display) {
            // If in "Blank" or "All red" training mode, disable object animation toggle
            boolean modeSupportsAnimation = !(mSelectedTrainingMode.equals("Blank") || mSelectedTrainingMode.equals("All red") );
            ImageButton animButton = (ImageButton) findViewById(R.id.training_animate);
            animButton.setEnabled(modeSupportsAnimation);
            animButton.setAlpha(modeSupportsAnimation ? 0.54f : 0.27f);

            // TODO extract all these sendBtMessage calls to a model object that syncs via BT
            sendBtMessage("GOTO TrainingOn \"" + mSelectedTrainingMode + "\" " + new Gson().toJson(mColorMap));
        } else {
            sendBtMessage("GOTO TrainingOff");
        }
    }

    private boolean mTrainingObjectAnimated = false;

    private void setTrainingObjectAnimated(boolean animated) {
        // Make sure switch matches this state.
        mTrainingObjectAnimated = animated;
        ImageButton animButton = (ImageButton) findViewById(R.id.training_animate);
        animButton.setBackgroundColor(animated ? getResources().getColor(R.color.dark_gray) : getResources().getColor(R.color.transparent));

        if (animated) {
            // TODO extract all these sendBtMessage calls to a model object that syncs via BT
            sendBtMessage("ANIM Start");
        } else {
            sendBtMessage("ANIM Stop");
        }
    }

    private boolean mRecordingTrainingVideo = false;

    private void startRecordingTrainingVideo() {
        mRecordingTrainingVideo = true;
        findViewById(R.id.training_record).setBackgroundColor(getResources().getColor(R.color.dark_gray));
        sendBtMessage("RECORD Start " + getCurrentTimeString() + " - TRAINING - " + mSelectedSubject);
    }

    private void stopTrainingAndSaveVideo() {
        mRecordingTrainingVideo = false;
        findViewById(R.id.training_record).setBackgroundColor(getResources().getColor(R.color.transparent));
        sendBtMessage("RECORD Stop");
    }

    private void saveTrainingData() {
        Log.d("LOG", "Saving training data: " + mSelectedSubject + " " + mTrainingTimer.getStartTimeString() + " " + mTrainingTimer.getElapsedTimeString());
        new TrainingDataSaverThread(this, mSelectedSubject, mTrainingTimer.getStartTimeString(), mTrainingTimer.getElapsedTimeString(), mOAuthToken).start();
    }

    /**
     * Rewind as many spaces as there have been dispenses.
     */
    private void rewindAll() {
        ((Vibrator) getSystemService(Context.VIBRATOR_SERVICE)).vibrate(DISPENSE_VIBRATE_MS);
        int neededRewinds = FULL_DISPENSE_COUNT - mDispenseRemaining;
        for (int i = 0; i < neededRewinds; i++) {
            sendBtMessage("CONVEYORBACK");
        }
        setDispenseRemaining(FULL_DISPENSE_COUNT);
    }

    private SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("EEE MMM dd yyyy HH:mm:ss zzz");

    private String getCurrentTimeString() {
        return DATE_FORMAT.format(new Date());
    }

    public void trainStartClicked(View v) {
        setTrainingStarted(true);
    }

    public void trainStopClicked(View v) {
        setTrainingStarted(false);
    }

    private static final int DISPENSE_VIBRATE_MS = 500;

    public void dispenseClicked(View v) {
        if (mDispenseRemaining == 0) {
            return;
        }
        sendBtMessage("DISPENSE");
        ((Vibrator) getSystemService(Context.VIBRATOR_SERVICE)).vibrate(DISPENSE_VIBRATE_MS);
        setDispenseRemaining(mDispenseRemaining - 1);
    }

    private void setDispenseRemaining(int dispenseRemaining) {
        if (dispenseRemaining < 0) {
            mDispenseRemaining = 0;
        } else if (dispenseRemaining > FULL_DISPENSE_COUNT) {
            mDispenseRemaining = FULL_DISPENSE_COUNT;
        } else {
            mDispenseRemaining = dispenseRemaining;
        }
        ((Button) findViewById(R.id.train_dispense)).setText("Dispense (" + mDispenseRemaining + ")");

        if (mTrainingStarted) {
            ImageButton rewindButton = (ImageButton) findViewById(R.id.training_rewind_all);
            rewindButton.setEnabled(mDispenseRemaining < FULL_DISPENSE_COUNT);
            rewindButton.setAlpha(mDispenseRemaining < FULL_DISPENSE_COUNT ? 0.54f : 0.27f);
        }
    }

    public void startTest1Clicked(View v) {
        mSelectedTestingPhase = PHASE_1;
        gotoScreen(SCREEN_TEST);
        setTestingStarted(true);
    }

    public void startTest2Clicked(View v) {
        mSelectedTestingPhase = PHASE_2;
        gotoScreen(SCREEN_TEST);
        setTestingStarted(true);
    }

    public void startTrainingClicked(View v) {
        selectTrainingMode("Blank");
        gotoScreen(SCREEN_TRAIN);
        setTrainingStarted(true);
    }

    public void trainRecordClicked(View v) {
        if (mRecordingTrainingVideo) {
            stopTrainingAndSaveVideo();
        } else {
            startRecordingTrainingVideo();
        }
    }

    public void trainAnimateClicked(View v) {
        setTrainingObjectAnimated(!mTrainingObjectAnimated);
    }

    public void trainRewindAllClicked(View v) {
        rewindAll();
    }


    private void selectTrainingMode(String modeName) {
        // disable old tile
        updateTrainingModeTile(false, getTrainingTileViewId(), getTrainingTileIconId());

        // select new tile
        mSelectedTrainingMode = modeName;

        // enable new tile
        updateTrainingModeTile(true, getTrainingTileViewId(), getTrainingTileIconId());

        // update rest of training UI
        updateTrainingDisplay(true);
    }

    private int getTrainingTileViewId() {
        if (mSelectedTrainingMode.equals("Blank")) {
            return R.id.training_mode1;
        } else if (mSelectedTrainingMode.equals("All red")) {
            return R.id.training_mode2;
        } else if (mSelectedTrainingMode.equals("Large red box")) {
            return R.id.training_mode3;
        } else if (mSelectedTrainingMode.equals("Small red box")) {
            return R.id.training_mode4;
        } else if (mSelectedTrainingMode.equals("Red left")) {
            return R.id.training_mode5;
        } else if (mSelectedTrainingMode.equals("Red right")) {
            return R.id.training_mode6;
        } else if (mSelectedTrainingMode.equals("Red left gray right")) {
            return R.id.training_mode7;
        } else if (mSelectedTrainingMode.equals("Gray left red right")) {
            return R.id.training_mode8;
        }
        return -1;
    }

    private int getTrainingTileIconId() {
        if (mSelectedTrainingMode.equals("Blank")) {
            return R.id.training_mode_checkmark1;
        } else if (mSelectedTrainingMode.equals("All red")) {
            return R.id.training_mode_checkmark2;
        } else if (mSelectedTrainingMode.equals("Large red box")) {
            return R.id.training_mode_checkmark3;
        } else if (mSelectedTrainingMode.equals("Small red box")) {
            return R.id.training_mode_checkmark4;
        } else if (mSelectedTrainingMode.equals("Red left")) {
            return R.id.training_mode_checkmark5;
        } else if (mSelectedTrainingMode.equals("Red right")) {
            return R.id.training_mode_checkmark6;
        } else if (mSelectedTrainingMode.equals("Red left gray right")) {
            return R.id.training_mode_checkmark7;
        } else if (mSelectedTrainingMode.equals("Gray left red right")) {
            return R.id.training_mode_checkmark8;
        }
        return -1;
    }

    private void updateTrainingModeTile(boolean selected, int modeViewId, int modeIconId) {
        findViewById(modeViewId).setAlpha(selected ? 1.0f: 0.38f);
        findViewById(modeIconId).setVisibility(selected ? View.VISIBLE : View.INVISIBLE);
    }

    // TODO extract strings into constants (even better if shared with tablet app)
    public void trainingMode1Selected(View v) {
        selectTrainingMode("Blank");
    }

    public void trainingMode2Selected(View v) {
        selectTrainingMode("All red");
    }

    public void trainingMode3Selected(View v) {
        selectTrainingMode("Large red box");
    }

    public void trainingMode4Selected(View v) {
        selectTrainingMode("Small red box");
    }

    public void trainingMode5Selected(View v) {
        selectTrainingMode("Red left");
    }

    public void trainingMode6Selected(View v) {
        selectTrainingMode("Red right");
    }

    public void trainingMode7Selected(View v) {
        selectTrainingMode("Red left gray right");
    }

    public void trainingMode8Selected(View v) {
        selectTrainingMode("Gray left red right");
    }

    public void conveyorBackClicked(View v) {
        sendBtMessage("CONVEYORBACK");
        ((Vibrator) getSystemService(Context.VIBRATOR_SERVICE)).vibrate(DISPENSE_VIBRATE_MS);
        setDispenseRemaining(mDispenseRemaining + 1);
    }

    // No longer used in client. Unlikely all 7 dispenses will happen.
    public void conveyorBackFarClicked(View v) {
        sendBtMessage("CONVEYORBACKFAR");
    }

    public void closeCalibrationClicked(View v) {
        setCalibrating(false);
    }
}
