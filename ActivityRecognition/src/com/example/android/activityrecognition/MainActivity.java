/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.activityrecognition;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.location.DetectedActivity;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.IntentService;
import android.app.ProgressDialog;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.content.LocalBroadcastManager;
import android.telephony.TelephonyManager;
import android.text.Spanned;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.Calendar;
import java.util.List;
import com.example.android.activityrecognition.ActivityUtils.REQUEST_TYPE;


public class MainActivity extends Activity {
	private SharedPreferences mPrefs;
	private String message = "";
	private static final int MAX_LOG_SIZE = 5000;
	// Instantiates a log file utility object, used to log status updates
	private LogFile mLogFile;
	// Instantiates a XML to store the updates
	private XMLWriter xml ;
	// Store the current request type (ADD or REMOVE)
	private REQUEST_TYPE mRequestType;
	// Holds the ListView object in the UI
	private ListView mStatusListView;
	// Holds activity recognition data, in the form of strings that can contain markup
	private ArrayAdapter<Spanned> mStatusAdapter;
	// Intent filter for incoming broadcasts from th IntentService
	IntentFilter mBroadcastFilter;
	// Instance of a local broadcast manager
	private LocalBroadcastManager mBroadcastManager;
	// The activity recognition update request object
	private DetectionRequester mDetectionRequester;
	// The activity recognition update removal object
	private DetectionRemover mDetectionRemover;

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		displayMessage("An internet connection is required so that the application works correctly");
		// Get a handle to the repository
		// use this to start and trigger a service
		Intent i= new Intent(this, GPSLoggerService.class);
		// potentially add data to the intent
		this.startService(i);
		// Get a handle to the repository
		mPrefs = getApplicationContext().getSharedPreferences(ActivityUtils.SHARED_PREFERENCES, Context.MODE_PRIVATE);


		// Set the main layout
		setContentView(R.layout.activity_main);
		// Get a handle to the activity update list
		mStatusListView = (ListView) findViewById(R.id.log_listview);
		// Instantiate an adapter to store update data from the log
		mStatusAdapter = new ArrayAdapter<Spanned>(this, R.layout.item_layout, R.id.log_text);
		// Bind the adapter to the status list
		mStatusListView.setAdapter(mStatusAdapter);
		// Set the broadcast receiver intent filer
		mBroadcastManager = LocalBroadcastManager.getInstance(this);
		// Create a new Intent filter for the broadcast receiver
		mBroadcastFilter = new IntentFilter(ActivityUtils.ACTION_REFRESH_STATUS_LIST);
		mBroadcastFilter.addCategory(ActivityUtils.CATEGORY_LOCATION_SERVICES);
		// Get detection requester and remover objects
		mDetectionRequester = new DetectionRequester(this);
		mDetectionRemover = new DetectionRemover(this);
		// Create a new LogFile object
		mLogFile = LogFile.getInstance(this);
		xml = XMLWriter.getInstance(this);

	}

	/*
	 * Handle results returned to this Activity by other Activities started with
	 * startActivityForResult(). In particular, the method onConnectionFailed() in
	 * DetectionRemover and DetectionRequester may call startResolutionForResult() to
	 * start an Activity that handles Google Play services problems. The result of this
	 * call returns here, to onActivityResult.
	 */
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
		// Choose what to do based on the request code
		switch (requestCode) {
		// If the request code matches the code sent in onConnectionFailed
		case ActivityUtils.CONNECTION_FAILURE_RESOLUTION_REQUEST :
			switch (resultCode) {
			// If Google Play services resolved the problem
			case Activity.RESULT_OK:
				// If the request was to start activity recognition updates
				if (ActivityUtils.REQUEST_TYPE.ADD == mRequestType) {
					// Restart the process of requesting activity recognition updates
					mDetectionRequester.requestUpdates();
					// If the request was to remove activity recognition updates
				} else if (ActivityUtils.REQUEST_TYPE.REMOVE == mRequestType ){
					// Restart the removal of all activity recognition updates for the PendingIntent.
					mDetectionRemover.removeUpdates(mDetectionRequester.getRequestPendingIntent());
				}
				break;
				// If any other result was returned by Google Play services
			default:
				// Report that Google Play services was unable to resolve the problem.
				Log.d(ActivityUtils.APPTAG, getString(R.string.no_resolution));
			}
			// If any other request code was received
		default:
			// Report that this Activity received an unknown requestCode
			Log.d(ActivityUtils.APPTAG,getString(R.string.unknown_activity_request_code, requestCode));
			break;
		}
	}

	/*
	 * Register the broadcast receiver and update the log of activity updates
	 */
	@Override
	protected void onResume() {
		super.onResume();
		// Register the broadcast receiver
		mBroadcastManager.registerReceiver(updateListReceiver,mBroadcastFilter);
		// Load updated activity history
		updateActivityHistory();
	}

	/*
	 * Create the menu
	 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.menu, menu);
		return true;
	}

	/*
	 * Handle selections from the menu
	 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle item selection
		switch (item.getItemId()) {
		// Clear the log display and remove the log files
		case R.id.menu_item_clearlog:
			// Clear the list adapter
			mStatusAdapter.clear();
			// Update the ListView from the empty adapter
			mStatusAdapter.notifyDataSetChanged();
			// Remove log files
			if (!mLogFile.removeLogFiles()) {
				Log.e(ActivityUtils.APPTAG, getString(R.string.log_file_deletion_error));
				// Display the results to the user
			} else {
				Toast.makeText(
						this,
						R.string.logs_deleted,
						Toast.LENGTH_LONG).show();
			}
			// Continue by passing true to the menu handler
			return true;
			// Display the update log
		case R.id.menu_item_showlog:
			// Update the ListView from log files
			updateActivityHistory();
			// Continue by passing true to the menu handler
			return true;
			// For any other choice, pass it to the super()
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	/*
	 * Unregister the receiver during a pause
	 */
	@Override
	protected void onPause() {
		// Stop listening to broadcasts when the Activity isn't visible.
		mBroadcastManager.unregisterReceiver(updateListReceiver);
		super.onPause();
	}

	/**
	 * Verify that Google Play services is available before making a request.
	 *
	 * @return true if Google Play services is available, otherwise false
	 */
	private boolean servicesConnected() {
		// Check that Google Play services is available
		int resultCode =
				GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
		// If Google Play services is available
		if (ConnectionResult.SUCCESS == resultCode) {
			// In debug mode, log the status
			Log.d(ActivityUtils.APPTAG, getString(R.string.play_services_available));
			// Continue
			return true;
			// Google Play services was not available for some reason
		} else {
			// Display an error dialog
			GooglePlayServicesUtil.getErrorDialog(resultCode, this, 0).show();
			return false;
		}
	}
	/**
	 * Respond to "Start" button by requesting activity recognition
	 * updates.
	 * @param view The view that triggered this method.
	 */
	public void onStartUpdates(View view) {
		// Check for Google Play services
		if (!servicesConnected()) {
			return;
		}
		mRequestType = ActivityUtils.REQUEST_TYPE.ADD;
		// Pass the update request to the requester object
		mDetectionRequester.requestUpdates();
		// check if GPS enabled     

	}

	/**
	 * Respond to "Stop" button by canceling updates.
	 * @param view The view that triggered this method.
	 */
	public void onStopUpdates(View view) {
		// Check for Google Play services
		if (!servicesConnected()) {
			return;
		}
		mRequestType = ActivityUtils.REQUEST_TYPE.REMOVE;
		// Pass the remove request to the remover object
		mDetectionRemover.removeUpdates(mDetectionRequester.getRequestPendingIntent());
		mDetectionRequester.getRequestPendingIntent().cancel();
	}

	/**
	 * Sends data to server
	 */
	public void onSendStats(View view){
		File file = new File(Environment.getExternalStorageDirectory(), "dataToSend.xml");
		if(!file.exists()){   
			displayMessage("No data to send, please retry when you have datas.");
		}else{
			final ProgressDialog ringProgressDialog = ProgressDialog.show(MainActivity.this, "Please wait ...",	"Sending XML to server ...", true);
			ringProgressDialog.setCancelable(true);
			new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						Calendar now = Calendar.getInstance();
						String time = now.get(Calendar.HOUR_OF_DAY) + ":" + now.get(Calendar.MINUTE)+ ":" + now.get(Calendar.SECOND);
						double d[] = new double[3];
						d = ApproxSwissProj.WGS84toLV03(mPrefs.getFloat("latitude", 0),mPrefs.getFloat("longitude", 0), 0);
						int previousType = mPrefs.getInt(ActivityUtils.KEY_PREVIOUS_ACTIVITY_TYPE, DetectedActivity.UNKNOWN);
						if (previousType == DetectedActivity.STILL){
							xml.addLeg("car");
							xml.addActivity("home", d[0], d[1], time);
						}else{
							xml.addActivity("home", d[0], d[1],"");
						}
						xml.sendStats(((TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE)).getDeviceId());
						mPrefs.edit().remove(ActivityUtils.KEY_PREVIOUS_ACTIVITY_TYPE).commit();
						boolean x = xml.deleteFile();
						message = "Upload done";
						mStatusAdapter.clear();
						mStatusAdapter.notifyDataSetChanged();
						mLogFile.removeLogFiles();
						Thread.sleep(5000);
					} catch (Exception e) {
						message = "Problem with the upload";
					}
					ringProgressDialog.dismiss();
				}
			}).start();
		}
	}



	/**
	 * Display the activity detection history stored in the
	 * log file
	 */
	private void updateActivityHistory() {
		// Try to load data from the history file
		try {
			// Load log file records into the List
			List<Spanned> activityDetectionHistory = mLogFile.loadLogFile();
			// Clear the adapter of existing data
			mStatusAdapter.clear();
			// Add each element of the history to the adapter
			for (Spanned activity : activityDetectionHistory) {
				mStatusAdapter.add(activity);
			}
			// If the number of loaded records is greater than the max log size
			if (mStatusAdapter.getCount() > MAX_LOG_SIZE) {
				// Delete the old log file
				if (!mLogFile.removeLogFiles()) {
					// Log an error if unable to delete the log file
					Log.e(ActivityUtils.APPTAG, getString(R.string.log_file_deletion_error));
				}
			}
			// Trigger the adapter to update the display
			mStatusAdapter.notifyDataSetChanged();
			// If an error occurs while reading the history file
		} catch (IOException e) {
			Log.e(ActivityUtils.APPTAG, e.getMessage(), e);
		}
	}

	/**
	 * Broadcast receiver that receives activity update intents
	 * It checks to see if the ListView contains items. If it
	 * doesn't, it pulls in history.
	 * This receiver is local only. It can't read broadcast Intents from other apps.
	 */
	BroadcastReceiver updateListReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			/*
			 * When an Intent is received from the update listener IntentService, update
			 * the displayed log.
			 */
			updateActivityHistory();
		}
	};

	public void displayMessage(String mess){
		AlertDialog alertDialog = new AlertDialog.Builder(this).create();
		alertDialog.setTitle("Information");
		alertDialog.setButton("OK", new DialogInterface.OnClickListener(){
			@Override
			public void onClick(DialogInterface dialog, int which) {
				dialog.dismiss();
			}
		});
		alertDialog.setMessage(mess);
		alertDialog.setCancelable(true);
		alertDialog.setCanceledOnTouchOutside(true);
		alertDialog.show();
	}

}

