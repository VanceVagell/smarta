package com.vagell.lemurcolor;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Environment;
import android.util.Log;

import com.google.gdata.client.spreadsheet.SpreadsheetService;
import com.google.gdata.data.spreadsheet.ListEntry;
import com.google.gdata.data.spreadsheet.SpreadsheetEntry;
import com.google.gdata.data.spreadsheet.SpreadsheetFeed;
import com.google.gdata.data.spreadsheet.WorksheetEntry;
import com.google.gdata.data.spreadsheet.WorksheetFeed;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.net.URL;
import java.util.List;

public class TrainingDataSaverThread extends Thread {
    private Activity mActivity;
    private String mSubjectName;
    private String mStartTime;
    private String mElapsedTime;
    private String mOAuthToken;
    private static SpreadsheetService mService = null;
    private static URL mListFeedUrl = null;

    private static final String SPREADSHEET_NAME = "Lemur data from SMARTA"; // TODO make configurable in UI

    public TrainingDataSaverThread(Activity activity, String subjectName, String startTime, String elapsedTime, String oAuthToken) {
        mActivity = activity;
        mSubjectName = subjectName;
        mStartTime = startTime;
        mElapsedTime = elapsedTime;
        mOAuthToken = oAuthToken;
    }

    @Override
    public void run() {
        // String we'll save locally if there's any failure.
        String backupData = mSubjectName + "," + mStartTime + "," + mElapsedTime + "\r\n";

        try {
            if (mService == null) {
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
                                    .setMessage("This account has no spreadsheets in Drive. Restart app and choose account with spreadsheet " + SPREADSHEET_NAME + ". Data saved to text file for now.")
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
                for (int i = 0; i < spreadsheets.size(); i++) {
                    if (spreadsheets.get(i).getTitle().getPlainText().equals(SPREADSHEET_NAME)) {
                        dataSpreadsheet = spreadsheets.get(i);
                        Log.d("LOG", "Found spreadsheet: " + spreadsheets.get(i).getTitle().getPlainText());
                        break;
                    }
                }

                if (dataSpreadsheet == null) {
                    Log.d("LOG", "FATAL ERROR: No spreadsheet named " + SPREADSHEET_NAME + " in this account. Data saved to text file for now.");

                    appendBackupData(backupData);
                    mActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            new AlertDialog.Builder(mActivity)
                                    .setTitle("No spreadsheets in Drive")
                                    .setMessage("This account has no spreadsheets in Drive. Restart app and choose account with spreadsheet " + SPREADSHEET_NAME + ". Data saved to text file for now.")
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
                WorksheetEntry worksheet = worksheets.get(0); // 0 is the training sheet.

                // Fetch the list feed of the worksheet.
                mListFeedUrl = worksheet.getListFeedUrl();
            }

            // Create a local representation of the new row.
            ListEntry row = new ListEntry();
            row.getCustomElements().setValueLocal("subject", mSubjectName);
            row.getCustomElements().setValueLocal("trainingstart", mStartTime);
            row.getCustomElements().setValueLocal("trainingduration", mElapsedTime);

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
                            .setMessage("Check phone's internet connection, then restart app. Data saved to text file for now.")
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

    private static final String BACKUP_TEXT_FILE_NAME = "smarta-backup-training-data.txt";

    private void appendBackupData(String backupData) {
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