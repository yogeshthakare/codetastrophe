/*
 * Copyright (C) 2008 Jon Larimer <jlarimer@gmail.com>
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

/* SatInfo.java
 * 
 * Demonstrates using a 'hidden' Android System Service API call to collect
 * data that isn't available through the SDK.
 * 
 * To build this, you'll need to copy a few files from the Android source
 * code into this project:
 *
 * frameworks/base/location/java/android/location/IGpsStatusListener.aidl
 * frameworks/base/location/java/android/location/ILocationListener.aidl
 * frameworks/base/location/java/android/location/ILocationManager.aidl
 * frameworks/base/location/java/android/location/Address.aidl
 * 
 * Place those files in an "android.location" namespace in your project in
 * eclipse, and the Android plugin should generate the correct .java files
 * that will make this thing run.
 */

package com.codetastrophe.android.satinfo;

import android.app.Activity;
import android.content.Context;
import android.location.IGpsStatusListener;
import android.location.ILocationManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;
import android.widget.TextView;

import java.lang.reflect.*;

public class SatInfo extends Activity {
	public static final String DF = "DF";

	private TextView mTextView = null;
	private LocationManager mLocationManager = null;
	private ILocationManager mILM;
	private IGpsStatusListener mGpsListener = null;
	private LocationListener mLocListener = null;

	/** Called when the activity is first created. */
	@SuppressWarnings("unchecked")
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		mTextView = (TextView) findViewById(R.id.textview);

		try {
			// get a handle to the LocationManager system service
			mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

			// fix the private mService property to get access to
			// the underlying service client
			Class c = Class.forName(mLocationManager.getClass().getName());
			Field f = c.getDeclaredField("mService");
			f.setAccessible(true);
			mILM = (ILocationManager) f.get(mLocationManager);

			// add our gps status listener
			mGpsListener = new GpsListener();
			mILM.addGpsStatusListener(mGpsListener);

			// add a locationlistener, just to enable the GPS
			mLocListener = (LocationListener) mGpsListener;
			mLocationManager.requestLocationUpdates("gps", 0, 0, mLocListener);

		} catch (SecurityException e) {
			Log.d(DF, "Exception: " + e.getMessage());
		} catch (ClassNotFoundException e) {
			Log.d(DF, "Exception: " + e.getMessage());
		} catch (NoSuchFieldException e) {
			Log.d(DF, "Exception: " + e.getMessage());
		} catch (IllegalArgumentException e) {
			Log.d(DF, "Exception: " + e.getMessage());
		} catch (IllegalAccessException e) {
			Log.d(DF, "Exception: " + e.getMessage());
		} catch (RemoteException e) {
			Log.d(DF, "Exception: " + e.getMessage());
		}
	}

	@Override
	public void onPause() {
		super.onPause();

		// remove listeners
		try {
			mILM.removeGpsStatusListener(mGpsListener);
		} catch (RemoteException e) {
		}
		mLocationManager.removeUpdates(mLocListener);
	}

	private class GpsListener extends IGpsStatusListener.Stub implements
			LocationListener {

		// IGpsStatusListener overrides
		@Override
		public void onFirstFix(int ttff) throws RemoteException {
			Log.d(DF, "onFirstFix");
		}

		@Override
		public void onGpsStarted() throws RemoteException {
			Log.d(DF, "onGpsStarted");
		}

		@Override
		public void onGpsStopped() throws RemoteException {
			Log.d(DF, "onGpsStopped");
		}

		@Override
		public void onSvStatusChanged(int svCount, int[] prns, float[] snrs,
				float[] elevations, float[] azimuths, int ephemerisMask,
				int almanacMask, int usedInFixMask) throws RemoteException {

			Log.d(DF, "onSvStatusChanged, svCount " + svCount);

			try {
				// build a new text string for our textview
				final StringBuilder sb = new StringBuilder();
				for (int i = 0; i < svCount; i++) {
					if (prns.length > i && snrs.length > i
							&& elevations.length > i && azimuths.length > i) {

						String s = String.format(
								"PRN %d, SNR %.0f, ELEV %.0f, AZIM %.0f\n",
								prns[i], snrs[i] * 10, elevations[i],
								azimuths[i]);

						sb.append(s);
					}
				}

				// we can't set the textview text directly from this
				// thread
				mTextView.post(new Runnable() {
					@Override
					public void run() {
						mTextView.setText(sb.toString());
					}
				});
			} catch (Exception e) {
				Log.d(DF, "Exception: " + e.toString());
			}

		}

		// LocationListener implements
		@Override
		public void onLocationChanged(Location location) {
			Log.d(DF, "onLocationChanged");
		}

		@Override
		public void onProviderDisabled(String provider) {
			Log.d(DF, "onProviderDisabled");
		}

		@Override
		public void onProviderEnabled(String provider) {
			Log.d(DF, "onProviderEnabled");
		}

		@Override
		public void onStatusChanged(String provider, int status, Bundle extras) {
			Log.d(DF, "onStatusChanged");
		}
	}
}

