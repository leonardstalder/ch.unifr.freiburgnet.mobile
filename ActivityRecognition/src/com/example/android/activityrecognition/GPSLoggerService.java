package com.example.android.activityrecognition;

import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.widget.Toast;

public class GPSLoggerService extends Service {
	// Store the app's shared preferences repository
	private SharedPreferences mPrefs;
	private LocationManager lm;
	private LocationListener locationListener;
	private static float minAccuracyMeters = 35;
	private int lastStatus = 0;
	private static boolean showingDebugToast = false;
	private Location lastLocation;
	private SharedPreferences.Editor editor;
	


	/** Called when the activity is first created. */
	private void startLoggerService() {
		// ---use the LocationManager class to obtain GPS locations---
		lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		locationListener = new MyLocationListener();
		lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000, 10, locationListener);
		
		editor =getApplicationContext().getSharedPreferences(ActivityUtils.SHARED_PREFERENCES, Context.MODE_PRIVATE).edit();
		editor.putString("logitude", "44.444");
		editor.putString("latitude", "7.444");
		editor.commit();
		
	}

	public class MyLocationListener implements LocationListener {

		public void onLocationChanged(Location loc) {
			
			if (loc != null) {
				editor =getApplicationContext().getSharedPreferences(ActivityUtils.SHARED_PREFERENCES, Context.MODE_PRIVATE).edit();
				editor.putFloat("longitude", (float) loc.getLongitude());
				editor.putFloat("latitude", (float) loc.getLatitude());
				editor.commit();
			
			}
		}

		public void onProviderDisabled(String provider) {
			if (showingDebugToast) Toast.makeText(getBaseContext(), "onProviderDisabled: " + provider,
					Toast.LENGTH_SHORT).show();

		}

		public void onProviderEnabled(String provider) {
			if (showingDebugToast) Toast.makeText(getBaseContext(), "onProviderEnabled: " + provider,
					Toast.LENGTH_SHORT).show();

		}

		public void onStatusChanged(String provider, int status, Bundle extras) {
			String showStatus = null;
			if (status == LocationProvider.AVAILABLE)
				showStatus = "Available";
			if (status == LocationProvider.TEMPORARILY_UNAVAILABLE)
				showStatus = "Temporarily Unavailable";
			if (status == LocationProvider.OUT_OF_SERVICE)
				showStatus = "Out of Service";
			if (status != lastStatus && showingDebugToast) {
				Toast.makeText(getBaseContext(),
						"new status: " + showStatus,
						Toast.LENGTH_SHORT).show();
			}
			lastStatus = status;
		}

	}

	// Below is the service framework methods


	@Override
	public void onCreate() {
		super.onCreate();

		startLoggerService();

		// Display a notification about us starting. We put an icon in the
		// status bar.
		showNotification();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();


	
	}

	/**
	 * Show a notification while this service is running.
	 */
	private void showNotification() {
		// In this sample, we'll use the same text for the ticker and the
		// expanded notification

		new Notification();

		PendingIntent.getActivity(this, 0,
				new Intent(this, GPSLoggerService.class), 0);



	}

	// This is the object that receives interactions from clients. See
	// RemoteService for a more complete example.
	private final IBinder mBinder = new LocalBinder();

	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}

	public static float getMinAccuracyMeters() {
		return minAccuracyMeters;
	}

	public static void setMinAccuracyMeters(float minAccuracyMeters) {
		GPSLoggerService.minAccuracyMeters = minAccuracyMeters;
	}

	public static void setShowingDebugToast(boolean showingDebugToast) {
		GPSLoggerService.showingDebugToast = showingDebugToast;
	}

	public static boolean isShowingDebugToast() {
		return showingDebugToast;
	}

	/**
	 * Class for clients to access. Because we know this service always runs in
	 * the same process as its clients, we don't need to deal with IPC.
	 */
	public class LocalBinder extends Binder {
		GPSLoggerService getService() {
			return GPSLoggerService.this;
		}
	}
	
	/**
	 * Function to show settings alert dialog
	 * On pressing Settings button will lauch Settings Options
	 * */
	public void showSettingsAlert(){
		AlertDialog.Builder alertDialog = new AlertDialog.Builder(getBaseContext());
		// Setting Dialog Title
		alertDialog.setTitle("GPS is settings");
		// Setting Dialog Message
		alertDialog.setMessage("GPS is not enabled. Do you want to go to settings menu?");
		// On pressing Settings button
		alertDialog.setPositiveButton("Settings", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog,int which) {
				Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
				getBaseContext().startActivity(intent);
			}
		});
		// on pressing cancel button
		alertDialog.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				dialog.cancel();
			}
		});
		// Showing Alert Message
		alertDialog.show();
	}

}