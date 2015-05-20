package com.vagell.lemurcolor;

import android.accounts.AccountManager;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Vibrator;
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
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TabHost;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.auth.GoogleAuthException;
import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.UserRecoverableAuthException;
import com.google.android.gms.common.AccountPicker;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;


public class MainActivity extends Activity {
    private TabHost mTabs = null;

    private static final String BT_MESSAGE_DIVIDER = "***";

    private static final String CALIBRATION_MODE_NONE = "none";
    private static final String CALIBRATION_MODE_TRAINING = "training";
    private static final String CALIBRATION_MODE_TESTING = "testing";
    private static final String COLOR_R = "R";
    private static final String COLOR_G = "G";
    private static final String COLOR_B = "B";

    private String mCalibrationMode = CALIBRATION_MODE_NONE;
    private boolean mIsTrainingTab = true;
    private int mDispenseRemaining = 7; // how many food rewards are left to dispense


    /**
     * Custom code for our request to enable Bluetooth. Value doesn't matter, we get it back
     * in the callback.
     */
    private static final int OAUTH_CALLBACK = 2389;
    private static final int PICK_ACCOUNT_CALLBACK = 8403;

    /**
     * The Bluetooth adapter we'll use to communicate with the phone app.
     */
    private final BluetoothAdapter mBluetoothAdapter;

    private BTConnectThread mBtConnectThread = null;

    private String mSelectedSubject = "";
    private String mSelectedPhase = "";
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
        loadCalibration();

        setContentView(R.layout.main_activity);

        // Setup various UI components
        setupMainTabs();
        setupCalibrationScreen();
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
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    // TODO extract into a calibration activity. complex because BT stack needs updating to work across activities.
    private void setCalibrating(boolean calibrating) {
        findViewById(R.id.train_container).setVisibility(calibrating ? View.GONE : (mIsTrainingTab ? View.VISIBLE : View.GONE));
        findViewById(R.id.test_container).setVisibility(calibrating ? View.GONE : (!mIsTrainingTab ? View.VISIBLE : View.GONE));
        findViewById(R.id.calibrate_container).setVisibility(calibrating ? View.VISIBLE : View.GONE);
        findViewById(android.R.id.tabs).setVisibility(calibrating ? View.GONE : View.VISIBLE);

        if (calibrating) {
            setTitle("Colors");
            sendBtMessage("GOTO Calibrate1 " + new Gson().toJson(mColorBeingCalibrated));
        } else {
            setTitle(R.string.app_name);
        }
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

    /**
     * Whether user hit the "Start" button to begin training.
     */
    private boolean mTrainingStarted = false;

    private void setupTrainingScreen() {
        // Populate list of subjects
        Spinner spinner = (Spinner) findViewById(R.id.train_subject);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.subjects_array, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        // Listen for changes in subject selection
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                String newSubject = (String) parent.getItemAtPosition(pos);
                if (mSelectedSubject.equals(newSubject)) {
                    return;
                }
                mSelectedSubject = newSubject;
            }
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        // Listen for changes in object display on/off
        Switch objDisplaySwitch = (Switch) findViewById(R.id.train_object_display_switch);
        objDisplaySwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                if (!mTrainingStarted) {
                    return;
                }

                setTrainingObjectDisplayed(isChecked);
            }
        });

        // Listen for changes in video recording on/off
        Switch videoRecordingSwitch = (Switch) findViewById(R.id.train_record_switch);
        videoRecordingSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                if (!mTrainingStarted) {
                    return;
                }

                if (isChecked) {
                    startRecordingTrainingVideo();
                } else {
                    stopTrainingAndSaveVideo();
                }
            }
        });

        // Create timer
        mTrainingTimer = new RenderedTimer(this, (TextView) findViewById(R.id.train_timer));
    }

    private void setupTestingScreen() {
        // Populate list of subjects
        Spinner spinner = (Spinner) findViewById(R.id.test_subject);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.subjects_array, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        // Listen for changes in subject selection
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                String newSubject = (String) parent.getItemAtPosition(pos);
                if (mSelectedSubject.equals(newSubject)) {
                    return;
                }
                mSelectedSubject = newSubject;
            }

            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        // Populate phases
        spinner = (Spinner) findViewById(R.id.test_phase);
        adapter = ArrayAdapter.createFromResource(this,
                R.array.phases_array, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        // Listen for changes in phase selection
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                String newPhase = (String) parent.getItemAtPosition(pos);
                if (mSelectedPhase.equals(newPhase)) {
                    return;
                }
                mSelectedPhase = newPhase;
            }

            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        // Create timer
        mTestingTimer = new RenderedTimer(this, (TextView) findViewById(R.id.test_timer));
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
        if (focusedView.getClass() == EditText.class) {
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

    private void setupMainTabs() {
        mTabs = (TabHost) findViewById(R.id.tab_host);
        mTabs.setup();

        TabHost.TabSpec spec = mTabs.newTabSpec("Train");
        spec.setContent(R.id.train_container);
        spec.setIndicator("Train");
        mTabs.addTab(spec);

        spec = mTabs.newTabSpec("Test");
        spec.setContent(R.id.test_container);
        spec.setIndicator("Test");
        mTabs.addTab(spec);

        mTabs.setOnTabChangedListener(new TabHost.OnTabChangeListener() {
            @Override
            public void onTabChanged(String tabId) {
                int tabIdx = mTabs.getCurrentTab();

                switch (tabIdx) {
                    case 0:
                        mIsTrainingTab = true;
                        sendBtMessage("GOTO TrainingOff");
                        break;
                    case 1:
                        mIsTrainingTab = false;
                        sendBtMessage("GOTO Testing " + new Gson().toJson(mColorMap));
                        break;
                }
            }
        });
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
                BluetoothDevice firstBluetoothDevice = pairedDevices.toArray(new BluetoothDevice[pairedDevices.size()])[0];
                Handler connectHandler = new Handler(Looper.getMainLooper()) {
                    @Override
                    public void handleMessage(Message message) {
                        switch (message.what) {
                            case BTConnectThread.MESSAGE_BT_CONNECTED:
                                findViewById(R.id.connecting_message).setVisibility(View.INVISIBLE);
                                mTabs.setVisibility(View.VISIBLE);
                                break;
                            // TODO add a case for MESSAGE_BT_CONNECT_FAILED so we can retry (requires extracting this handler to avoid recursive ref)
                            default:
                                Log.d("LOG", "Unknown BT message: " + message);
                        }
                    }
                };
                mBtMessageHandler = new BTMessageHandler();
                mBtConnectThread = new BTConnectThread((LemurColorRemoteApplication) getApplication(), connectHandler, mBtMessageHandler, firstBluetoothDevice, mBluetoothAdapter, this);
                mBtConnectThread.start();
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

    private synchronized void handleTrialData(TrialData trialData) {
        mTrialCount++;

        if (mTrialCount > NUM_TRIALS_PER_SESSION) {
            setTestingStarted(false);
        } else {
            mTestingTimer.startCountDown(35);
            ((TextView) findViewById(R.id.test_count)).setText("Trial " + mTrialCount + " of 7");
        }

        if (trialData.timedOut) {
            Toast.makeText(this, "Trial out of time", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, trialData.subjectChoseCorrectly ? "Correct choice" : "Incorrect choice", Toast.LENGTH_LONG).show();
        }

        Log.d("LOG", "Saving trial data.");
        new TrialDataSaverThread(this, mSelectedSubject, mSelectedPhase, trialData, mOAuthToken, mSessionStartTime).start();
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
        }
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
        mSessionStartTime = new Date();

        // Enable or disable various testing UI elements depending on whether testing is active.
        findViewById(R.id.test_subject).setEnabled(!started);
        findViewById(R.id.test_phase).setEnabled(!started);
        findViewById(R.id.test_start).setVisibility(started ? View.GONE : View.VISIBLE);
        findViewById(R.id.test_stop).setVisibility(started ? View.VISIBLE : View.GONE);

        if (!started) {
            // We don't have any data to save, wait for tablet to send it to us.

            // Tell tablet to stop running trials
            sendBtMessage("RECORD Stop");
            sendBtMessage("ABORTTESTING");

            // Update the UI
            setMainTabsEnabled(true);
            findViewById(R.id.test_status).setVisibility(View.VISIBLE);
            findViewById(R.id.test_details).setVisibility(View.GONE);
            mTestingTimer.stop();
        } else {
            mTrialCount = 1;

            // Start session and recording video
            String phase = mSelectedPhase.equals("Phase 1") ? "phase1" : "phase2";
            sendBtMessage("STARTSESSION " + phase);
            sendBtMessage("RECORD Start " + getCurrentTimeString() + " - TESTING - " + mSelectedSubject);

            ((TextView) findViewById(R.id.test_count)).setText("Trial 1 of 7");
            findViewById(R.id.test_status).setVisibility(View.GONE);
            findViewById(R.id.test_details).setVisibility(View.VISIBLE);
            setMainTabsEnabled(false);
            mTestingTimer.startCountDown(30);
        }
    }

    private void setTrainingStarted(boolean started) {
        mTrainingStarted = started;

        // Enable or disable various training UI elements depending on whether training is active.
        findViewById(R.id.train_subject).setEnabled(!started);
        findViewById(R.id.train_start).setVisibility(!started ? View.VISIBLE : View.GONE);
        findViewById(R.id.train_stop).setVisibility(started ? View.VISIBLE : View.GONE);
        findViewById(R.id.train_object_display_switch).setVisibility(started ? View.VISIBLE : View.INVISIBLE);
        findViewById(R.id.train_record_switch).setVisibility(started ? View.VISIBLE : View.INVISIBLE);

        if (!started) {
            stopTrainingAndSaveVideo();
            setTrainingObjectDisplayed(false);
            setMainTabsEnabled(true);
            saveTrainingData(); // Save before stopping the timer.
            findViewById(R.id.train_status).setVisibility(View.VISIBLE);
            findViewById(R.id.train_timer).setVisibility(View.GONE);
            mTrainingTimer.stop(); // Do this last, so we can check time elapsed.
        } else {
            setMainTabsEnabled(false);
            setTrainingObjectDisplayed(false);
            findViewById(R.id.train_status).setVisibility(View.GONE);
            findViewById(R.id.train_timer).setVisibility(View.VISIBLE);
            mTrainingTimer.start();
        }
    }

    private void setMainTabsEnabled(boolean enabled) {
        mTabs.getTabWidget().setEnabled(enabled);
    }

    private void setTrainingObjectDisplayed(boolean display) {
        // Make sure switch matches this state.
        Switch trainingObjectDisplayedSwitch = (Switch) findViewById(R.id.train_object_display_switch);
        if (trainingObjectDisplayedSwitch.isChecked() != display) {
            trainingObjectDisplayedSwitch.setChecked(display);
        }

        if (display) {
            // TODO extract all these sendBtMessage calls to a model object that syncs via BT
            sendBtMessage("GOTO TrainingOn " + new Gson().toJson(mColorMap));
        } else {
            sendBtMessage("GOTO TrainingOff");
        }
    }

    private void startRecordingTrainingVideo() {
        // Make sure switch matches this state.
        Switch trainingVideoRecordingSwitch = (Switch) findViewById(R.id.train_record_switch);
        if (!trainingVideoRecordingSwitch.isChecked()) {
            trainingVideoRecordingSwitch.setChecked(true);
        }

        sendBtMessage("RECORD Start " + getCurrentTimeString() + " - TRAINING - " + mSelectedSubject);
    }

    private void stopTrainingAndSaveVideo() {
        // Make sure switch matches this state.
        Switch trainingVideoRecordingSwitch = (Switch) findViewById(R.id.train_record_switch);
        if (trainingVideoRecordingSwitch.isChecked()) {
            trainingVideoRecordingSwitch.setChecked(false);
        }

        sendBtMessage("RECORD Stop");
    }

    private void saveTrainingData() {
        Log.d("LOG", "Saving training data: " + mSelectedSubject + " " + mTrainingTimer.getStartTimeString() + " " + mTrainingTimer.getElapsedTimeString());
        new TrainingDataSaverThread(this, mSelectedSubject, mTrainingTimer.getStartTimeString(), mTrainingTimer.getElapsedTimeString(), mOAuthToken).start();
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
        sendBtMessage("DISPENSE");
        ((Vibrator) getSystemService(Context.VIBRATOR_SERVICE)).vibrate(DISPENSE_VIBRATE_MS);
        mDispenseRemaining--;
        mDispenseRemaining = Math.max(0, mDispenseRemaining);
        ((Button) findViewById(R.id.test_dispense)).setText("Dispense (" + mDispenseRemaining + ")");
    }

    public void testStartClicked(View v) {
        setTestingStarted(true);
    }

    public void testStopClicked(View v) {
        setTestingStarted(false);
    }

    public void conveyorBackClicked(View v) {
        sendBtMessage("CONVEYORBACK");
        ((Vibrator) getSystemService(Context.VIBRATOR_SERVICE)).vibrate(DISPENSE_VIBRATE_MS);
        mDispenseRemaining++;
        mDispenseRemaining = Math.min(7, mDispenseRemaining);
        ((Button) findViewById(R.id.test_dispense)).setText("Dispense (" + mDispenseRemaining + ")");
    }

    // No longer used in client. Unlikely all 7 dispenses will happen.
    public void conveyorBackFarClicked(View v) {
        sendBtMessage("CONVEYORBACKFAR");
    }

    public void closeCalibrationClicked(View v) {
        setCalibrating(false);
    }
}
