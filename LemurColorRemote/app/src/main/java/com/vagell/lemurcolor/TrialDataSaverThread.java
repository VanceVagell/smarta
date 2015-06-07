package com.vagell.lemurcolor;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Environment;
import android.util.Log;

import com.google.gdata.client.spreadsheet.*;
import com.google.gdata.data.spreadsheet.*;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class TrialDataSaverThread extends Thread {
    private Activity mActivity;
    private TrialData mTrialData;
    String mSubjectName, mPhase;
    private String mOAuthToken;
    private Date mSessionStartTime;
    private static SpreadsheetService mService = null;
    private static URL mListFeedUrl = null;
    private static String mLastFileName = null;

    private SimpleDateFormat START_TIME_FORMAT = new SimpleDateFormat(RenderedTimer.TIME_FORMAT_STRING);

    public TrialDataSaverThread(Activity activity, String subjectName, String phase, TrialData trialData, String oAuthToken, Date sessionStartTime) {
        mActivity = activity;
        mSubjectName = subjectName;
        mPhase = phase;
        mTrialData = trialData;
        mOAuthToken = oAuthToken;
        mSessionStartTime = sessionStartTime;
    }

    @Override
    public void run() {
        // String we'll save locally if there's any failure.
        String backupData = mSubjectName + "," + mPhase + "," + START_TIME_FORMAT.format(mSessionStartTime) + "," + (mTrialData.count + 1) + "," + mTrialData.duration + "," +
            mTrialData.leftColor.toString() + "," + mTrialData.rightColor.toString() + "," + mTrialData.timedOut + "," + ((mTrialData.timedOut) ? "-" : "" + mTrialData.subjectChoseCorrectly) + "\r\n";

        try {
            if (mService == null || mListFeedUrl == null || !MainActivity.getSpreadsheetName().equals(mLastFileName) /* user picked different spreadsheet */) {
                mService = new SpreadsheetService("SMARTA-v1");
                mService.setAuthSubToken(mOAuthToken);

                // Define the URL to request.  This should never change.
                URL SPREADSHEET_FEED_URL = new URL("https://spreadsheets.google.com/feeds/spreadsheets/private/full");

                // Make a request to the API and get all spreadsheets.
                SpreadsheetFeed feed = mService.getFeed(SPREADSHEET_FEED_URL, SpreadsheetFeed.class);
                List<SpreadsheetEntry> spreadsheets = feed.getEntries();

                if (spreadsheets.size() == 0) {
                    Log.d("LOG", "FATAL ERROR: No spreadsheets in this account. Data spreadsheet missing.");

                    appendBackupData(backupData);
                    mActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            new AlertDialog.Builder(mActivity)
                                    .setTitle("No spreadsheets in Drive")
                                    .setMessage("This account has no spreadsheets in Drive. Change account or pick another spreadsheet. Data saved to text file for now.")
                                    .setCancelable(false)
                                    .setPositiveButton("Close", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            // Do nothing.
                                        }
                                    })
                                    .show();
                        }
                    });

                    return;
                }

                SpreadsheetEntry dataSpreadsheet = null;
                // TODO find a cleaner way to detect when spreadsheet was changed. This isn't
                // thread safe (but user unlikely to change it during a save operation).
                mLastFileName = MainActivity.getSpreadsheetName();
                for (int i = 0; i < spreadsheets.size(); i++) {
                    if (spreadsheets.get(i).getTitle().getPlainText().equals(mLastFileName)) {
                        dataSpreadsheet = spreadsheets.get(i);
                        Log.d("LOG", "Found spreadsheet: " + spreadsheets.get(i).getTitle().getPlainText());
                        break;
                    }
                }

                if (dataSpreadsheet == null) {
                    Log.d("LOG", "FATAL ERROR: No spreadsheet named " + mLastFileName + " in this account. Data saved to text file for now.");

                    appendBackupData(backupData);
                    mActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            new AlertDialog.Builder(mActivity)
                                    .setTitle("Spreadsheet not found")
                                    .setMessage("This account doesn't have a spreadsheet named " + mLastFileName + ". Change account or pick another spreadsheet. Data saved to text file for now.")
                                    .setCancelable(false)
                                    .setPositiveButton("Close", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            // Do nothing.
                                        }
                                    })
                                    .show();
                        }
                    });

                    return;
                }

                WorksheetFeed worksheetFeed = mService.getFeed(dataSpreadsheet.getWorksheetFeedUrl(), WorksheetFeed.class);
                List<WorksheetEntry> worksheets = worksheetFeed.getEntries();
                WorksheetEntry worksheet = worksheets.get(1); // 1 is the testing sheet.

                // Fetch the list feed of the worksheet.
                mListFeedUrl = worksheet.getListFeedUrl();
            }

            // Create a local representation of the new row.
            ListEntry row = new ListEntry();
            row.getCustomElements().setValueLocal("subject", mSubjectName);
            row.getCustomElements().setValueLocal("phase", mPhase);
            row.getCustomElements().setValueLocal("sessionstart", START_TIME_FORMAT.format(mSessionStartTime));
            row.getCustomElements().setValueLocal("trial", "" + (mTrialData.count + 1));
            row.getCustomElements().setValueLocal("trialduration", "" + mTrialData.duration);
            row.getCustomElements().setValueLocal("leftcolor", mTrialData.leftColor.toString());
            row.getCustomElements().setValueLocal("rightcolor", mTrialData.rightColor.toString());
            row.getCustomElements().setValueLocal("timedout", "" + mTrialData.timedOut);
            row.getCustomElements().setValueLocal("correct", ((mTrialData.timedOut) ? "-" : "" + mTrialData.subjectChoseCorrectly));

            // Send the new row to the API for insertion.
            row = mService.insert(mListFeedUrl, row);
            Log.d("LOG", "Saved data to spreadsheet.");
        } catch (Exception e) {
            e.printStackTrace();
            appendBackupData(backupData);

            mActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    new AlertDialog.Builder(mActivity)
                            .setTitle("Can't save online")
                            .setMessage("Check internet connection then restart app. Data saved to text file.")
                            .setCancelable(false)
                            .setPositiveButton("Close", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    // Do nothing.
                                }
                            })
                            .show();
                }
            });
        }
    }

    public static final String BACKUP_TEXT_FILE_NAME = "smarta-backup-test-data.txt";

    private synchronized void appendBackupData(String backupData) {
        Log.d("LOG", "Saving backup data to text file, can't access online spreadsheet.");
        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
        if (!dir.exists()) {
            dir.mkdir();
        }
        File backupFile = new File(dir, BACKUP_TEXT_FILE_NAME);
        try {
            if (!backupFile.exists()) {
                backupFile.createNewFile();
            }
            BufferedWriter outputStream = new BufferedWriter(new FileWriter(backupFile.getAbsoluteFile(), true /* append */));
            outputStream.append(backupData);
            outputStream.flush();
            outputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
            Log.d("LOG", "ERROR: Couldn't log backup data: " + backupData);
        }
    }
}