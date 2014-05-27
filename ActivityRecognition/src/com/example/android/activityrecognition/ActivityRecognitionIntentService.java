package com.example.android.activityrecognition;
import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.DetectedActivity;
import android.app.IntentService;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.provider.Settings;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

/**
 * Service that receives ActivityRecognition updates. It receives updates
 * in the background, even if the main Activity is not visible.
 */
public class ActivityRecognitionIntentService extends IntentService {
	// Formats the timestamp in the log
	private static final String DATE_FORMAT_PATTERN = "yyyy-MM-dd HH:mm:ss.SSSZ";
	// A date formatter
	private SimpleDateFormat mDateFormat;
	// Store the app's shared preferences repository
	private SharedPreferences mPrefs;
	private XMLWriter xml;

	public ActivityRecognitionIntentService() {
		// Set the label for the service's background thread
		super("ActivityRecognitionIntentService");
		xml = XMLWriter.getInstance(this);
	}


	/**
	 * 
	 * Called when a new activity detection update is available.
	 */
	@Override
	protected void onHandleIntent(Intent intent) {
		// Get a date formatter, and catch errors in the returned timestamp
		try {
			mDateFormat = (SimpleDateFormat) DateFormat.getDateTimeInstance();
		} catch (Exception e) {
			Log.e(ActivityUtils.APPTAG, getString(R.string.date_format_error));
		}
		// Format the timestamp according to the pattern, then localize the pattern
		mDateFormat.applyPattern(DATE_FORMAT_PATTERN);
		mDateFormat.applyLocalizedPattern(mDateFormat.toLocalizedPattern());

		// If the intent contains an update
		if (ActivityRecognitionResult.hasResult(intent)) {
			ActivityRecognitionResult result = ActivityRecognitionResult.extractResult(intent);
			logActivityRecognitionResult(result); //log the update
			int confidence = result.getMostProbableActivity().getConfidence(); // Get the confidence precentage of the most probable act.
			int activityType = result.getMostProbableActivity().getType(); // Get the type of activity
			// Check to see if the repository contains a previous activity
			if(activityType == DetectedActivity.STILL || activityType == DetectedActivity.UNKNOWN || activityType == DetectedActivity.TILTING )
				activityType = DetectedActivity.STILL; // if still or unknow or tiliting redifine with still
			if (!mPrefs.contains(ActivityUtils.KEY_PREVIOUS_ACTIVITY_TYPE)) {
				// This is the first type an activity has been detected. Store the type
				mPrefs.edit().putInt(ActivityUtils.KEY_PREVIOUS_ACTIVITY_TYPE, activityType).commit();
				this.addAct("home");
			} 
			if (activityChanged(activityType)&& (confidence >= 40)) {
				if(activityType == DetectedActivity.ON_BICYCLE || activityType == DetectedActivity.IN_VEHICLE || activityType == DetectedActivity.ON_FOOT ){
					if(mPrefs.getInt(ActivityUtils.KEY_PREVIOUS_ACTIVITY_TYPE, DetectedActivity.STILL) == DetectedActivity.STILL){
						this.addLeg(this.getNameFromType(activityType));
					}else{
						this.addAct("dummy");
						this.addLeg(this.getNameFromType(activityType));
					}
				}else{
					this.addAct("dummy");
				}
				mPrefs.edit().putInt(ActivityUtils.KEY_PREVIOUS_ACTIVITY_TYPE, activityType).commit();
			}
		}
	}


	private void addAct(String act){
		Calendar now = Calendar.getInstance();
		String time = now.get(Calendar.HOUR_OF_DAY) + ":" + now.get(Calendar.MINUTE)+ ":" + now.get(Calendar.SECOND);
		double d[] = new double[3];
		d = ApproxSwissProj.WGS84toLV03(mPrefs.getFloat("latitude", 0),mPrefs.getFloat("longitude", 0), 0);
		System.out.println(act + ";"+d[0]+";"+ d[1]+ ";"+ time);
		xml.addActivity(act, d[0], d[1], time);
	}
	private void addLeg(String act){
		xml.addLeg(act);
	}



	/**
	 * Tests to see if the activity has changed
	 *
	 * @param currentType The current activity type
	 * @return true if the user's current activity is different from the previous most probable
	 * activity; otherwise, false.
	 */
	private boolean activityChanged(int currentType) {
		// Get the previous type, otherwise return the "unknown" type
		int previousType = mPrefs.getInt(ActivityUtils.KEY_PREVIOUS_ACTIVITY_TYPE, DetectedActivity.UNKNOWN);
		// If the previous type isn't the same as the current type, the activity has changed
		if (previousType != currentType) {
			return true;
			// Otherwise, it hasn't.
		} else {
			return false;
		}
	}


	/**
	 * Write the activity recognition update to the log file
	 * @param result The result extracted from the incoming Intent
	 */
	private void logActivityRecognitionResult(ActivityRecognitionResult result) {

		// Get a handle to the repository
		mPrefs = getApplicationContext().getSharedPreferences(ActivityUtils.SHARED_PREFERENCES, Context.MODE_PRIVATE);

		//System.out.println(mPrefs.getString("text", "NO_VALUE") + "");

		// Get all the probably activities from the updated result
		for (DetectedActivity detectedActivity : result.getProbableActivities()) {
			// Get the activity type, confidence level, and human-readable name
			int activityType = detectedActivity.getType();
			int confidence = detectedActivity.getConfidence();
			String activityName = getNameFromType(activityType);
			String timeStamp = mDateFormat.format(new Date());
			// Get the current log file or create a new one, then log the activity
			LogFile.getInstance(getApplicationContext()).log(
					activityName + ";" + mPrefs.getString("text", "NO_VALUE") + ";"+
							timeStamp + ";confidence:"+confidence
					);
		}
	}

	/**
	 * Map detected activity types to strings
	 * @param activityType The detected activity type
	 * @return A user-readable name for the type
	 */
	private String getNameFromType(int activityType) {
		switch(activityType) {
		case DetectedActivity.IN_VEHICLE:
			return "car";
		case DetectedActivity.ON_BICYCLE:
			return "bike";
		case DetectedActivity.ON_FOOT:
			return "foot";
		case DetectedActivity.STILL:
			return "dummy";
		case DetectedActivity.UNKNOWN:
			return "unknown";
		case DetectedActivity.TILTING:
			return "tilting";
		}
		return "unknown";
	}
}
