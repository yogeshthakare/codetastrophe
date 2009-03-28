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

package com.codetastrophe.cellfinder.listeners;

import java.text.DecimalFormat;
import java.util.List;

import com.codetastrophe.cellfinder.CellFinderMapActivity;
import com.codetastrophe.cellfinder.R;
import com.codetastrophe.cellfinder.overlays.ImageOverlay;
import com.codetastrophe.cellfinder.overlays.LineOverlay;
import com.codetastrophe.cellfinder.utils.StyledResourceHelper;
import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapController;
import com.google.android.maps.MapView;
import com.google.android.maps.MyLocationOverlay;
import com.google.android.maps.Overlay;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationProvider;
import android.os.Bundle; //import android.util.Log;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MyLocationListener implements LocationListener {
	public static final int AUTO_CENTER_GPS = 0;
	public static final int AUTO_CENTER_NETWORK = 1;
	public static final int AUTO_CENTER_MIDPOINT = 2;
	public static final int AUTO_CENTER_NONE = 3;

	public static final int UNITS_METERS = 0;
	public static final int UNITS_FEET = 1;
	public static final int UNITS_MILES = 2;
	
	public static final int LOCATION_FMT_DDM = 0;
	public static final int LOCATION_FMT_DDMS = 1;
	public static final int LOCATION_FMT_DD = 2;

	private Context mContext = null;
	private GeoPoint mCoarseLastGP = null;
	private GeoPoint mFineLastGP = null;
	private Location mCoarseLastLocation = null;
	private Location mFineLastLocation = null;
	private TextView mTvLocationCoarse = null;
	private TextView mTvLocationFine = null;
	private MapView mMapView = null;
	private String mCoarseLocationProvider = null;
	private String mFineLocationProvider = null;
	private ImageOverlay mCoarseLocationOverlay = null;
	private ImageOverlay mFineLocationOverlay = null;
	private LineOverlay mLineOverlay = null;
	private TextView mTvCellBearing = null;
	private TextView mTvCellDirection = null;
	private boolean mAutoZoom = false;
	private int mAutoCenter = 0;
	private int mUnits = 0;
	private int mLocationFmt = 0;
	private boolean mCompass = false;
	
	private MyLocationOverlay mMyLocationOverlay;
	private LinearLayout mZoomLayout = null;
	private LinearLayout mZoom = null;

	private static String[] mDirs = new String[] { "N", "NE", "E", "SE", "S",
			"SW", "W", "NW", "N" };

	public MyLocationListener(Activity a) {
		// collect things we need from the activity
		mTvLocationCoarse = (TextView) a.findViewById(R.id.tv_location_coarse);
		mTvLocationFine = (TextView) a.findViewById(R.id.tv_location_fine);
		mMapView = (MapView) a.findViewById(R.id.map_view);
		
		mZoomLayout = (LinearLayout) a.findViewById(R.id.layout_zoom);
		mZoom = (LinearLayout) mMapView.getZoomControls();
		
		mTvCellBearing = (TextView) a.findViewById(R.id.tv_cell_bearing);
		mTvCellDirection = (TextView) a.findViewById(R.id.tv_cell_direction);
		
		CellFinderMapActivity cf = (CellFinderMapActivity) a;
		mCoarseLocationProvider = cf.getCoarseLocationProvider();
		mFineLocationProvider = cf.getFineLocationProvider();
		
		mCoarseLocationOverlay = new ImageOverlay(a.getResources().getDrawable(
				R.drawable.star_small), null);
		mFineLocationOverlay = new ImageOverlay(a.getResources().getDrawable(
				R.drawable.reticle), null);

		Paint paint = new Paint();
		paint.setStrokeWidth(3);
		paint.setColor(Color.parseColor("#00B000"));
		mLineOverlay = new LineOverlay(null, null, paint);

		mContext = a;

		// set text labels for location providers
		TextView tmp = (TextView) a.findViewById(R.id.tv_location_coarse_name);
		if (tmp != null) {
			tmp.setText(StyledResourceHelper.GetStyledString(mContext,
					R.string.bold_fmt, mCoarseLocationProvider));
		}

		tmp = (TextView) a.findViewById(R.id.tv_location_fine_name);
		if (tmp != null) {
			tmp.setText(StyledResourceHelper.GetStyledString(mContext,
					R.string.bold_fmt, mFineLocationProvider));
		}

		clearBearing();
	}

	public void onLocationChanged(Location location) {
		// Log.d(CellFinderMapActivity.CELLFINDER, String.format(
		// "MyLocationListener.onLocationChanged() - provider %s, lat %s, lon %s",
		// location.getProvider(), location.getLatitude(),
		// location.getLongitude()));

		// calculate geopoint for current location
		Double lat = location.getLatitude() * 1E6;
		Double lon = location.getLongitude() * 1E6;
		GeoPoint gp = new GeoPoint(lat.intValue(), lon.intValue());

		String prov = location.getProvider();
		if (prov.equals(mFineLocationProvider)) {
			// Log.d(CellFinderMapActivity.CELLFINDER,
			// "updating fine location");
			mFineLastLocation = location;

			// update the location text
			mTvLocationFine.setText(getNiceLocation(location));

			// if this is a new location, update the map
			if (!gp.equals(mFineLastGP)) {
				mFineLastGP = gp;

				UpdateOverlays();
				zoomAndCenterMap();
			}
		} else if (prov.equals(mCoarseLocationProvider)) {
			// Log.d(CellFinderMapActivity.CELLFINDER,
			// "updating coarse location");
			mCoarseLastLocation = location;

			// update the location text
			mTvLocationCoarse.setText(getNiceLocation(location));

			if (!gp.equals(mCoarseLastGP)) {
				mCoarseLastGP = gp;

				UpdateOverlays();
				zoomAndCenterMap();
			}

		}

		updateBearing();
	}

	public void onProviderDisabled(String provider) {
		TextView tv = getTextViewForProvider(provider);
		if (tv != null) {
			tv.setText(mContext.getString(R.string.provider_disabled));
			clearBearing();
		}
	}

	public void onProviderEnabled(String provider) {
		TextView tv = getTextViewForProvider(provider);
		if (tv != null) {
			tv.setText(mContext.getString(R.string.provider_waiting));
			clearBearing();
		}
	}

	public void onStatusChanged(String provider, int status, Bundle extras) {
		TextView tv = getTextViewForProvider(provider);
		switch (status) {
		case LocationProvider.TEMPORARILY_UNAVAILABLE:
			// if it's temporarily unavailable but we have the most recent
			// location,
			// italicize the previous one
			Location loc = getLastLocationForProvider(provider);
			if (loc != null) {
				tv.setText(StyledResourceHelper.GetStyledString(mContext,
						R.string.italics_fmt, getNiceLocation(loc)));
			} else {
				tv.setText(mContext.getString(R.string.provider_waiting));
				clearBearing();
			}
			break;
		case LocationProvider.OUT_OF_SERVICE:
			tv.setText(mContext.getString(R.string.provider_out_of_service));
			clearBearing();
			break;
		}
	}
	
	public void pause() {
		if(mMyLocationOverlay != null && mCompass) {
			mMyLocationOverlay.disableCompass();
		}
	}
	
	public void resume() {
		if(mMyLocationOverlay != null && mCompass) {
			mMyLocationOverlay.enableCompass();
		}
	}

	public void setAutoCenter(int autocenter) {
		mAutoCenter = autocenter;
		zoomAndCenterMap();
	}

	public void setAutoZoom(boolean enabled) {
		mAutoZoom = enabled;
		mZoomLayout.removeView(mZoom);
		
		if (!mAutoZoom) {
			// add zoom control back if auto-zoom is disabled

			mZoomLayout.addView(mZoom);
		}

		zoomAndCenterMap();
	}
	
	public void setLocationFormat(int fmt) {
		mLocationFmt = fmt;
	}

	public void setSatellite(boolean enabled) {
		mMapView.setSatellite(enabled);
	}

	public void setUnits(int units) {
		mUnits = units;
	}
	
	public void setCompass(boolean enabled) {
		mCompass = enabled;
		if(enabled) {
			mMyLocationOverlay = new MyLocationOverlay(mContext, mMapView);
			mMyLocationOverlay.disableMyLocation();
			mMyLocationOverlay.enableCompass();
		} else {
			mMyLocationOverlay = null;
		}
	}

	private void clearBearing() {
		mTvCellBearing.setText("");
		mTvCellDirection.setText("");
	}

	private String getDDM(double coord) {
		if (coord < 0)
			coord = -coord;

		int deg = (int) Math.floor(coord);
		coord -= deg;
		coord *= 60;

		DecimalFormat df = new DecimalFormat(mContext.getString(R.string.min_fmt));
		return String.format(mContext.getString(R.string.ddm_fmt), deg, df.format(coord));
	}
	
	private String getDDMS(double coord) {
		if (coord < 0)
			coord = -coord;

		int deg = (int) Math.floor(coord);
		coord -= deg;
		coord *= 60;

		int min = (int) Math.floor(coord);
		coord -= min;
		coord *= 60;
		
		DecimalFormat df = new DecimalFormat(mContext.getString(R.string.sec_fmt));
		return String.format(mContext.getString(R.string.ddms_fmt), deg, min, df.format(coord));
	}
	
	private String getDD(double coord) {
		if (coord < 0)
			coord = -coord;

		DecimalFormat df = new DecimalFormat(mContext.getString(R.string.deg_fmt));

		return String.format(mContext.getString(R.string.dd_fmt), df.format(coord));
	}

	private Location getLastLocationForProvider(String provider) {
		if (provider.equals(mFineLocationProvider)) {
			return mFineLastLocation;
		} else if (provider.equals(mCoarseLocationProvider)) {
			return mCoarseLastLocation;
		}

		return null;
	}

	private String getNiceDirection(float bearing) {
		if (bearing < -180 || bearing > 180)
			return "ERROR";

		if (bearing < 0)
			bearing += 360;
		
		bearing += 22.5;

		return mDirs[(int) (bearing / 45)];
	}

	private String getNiceLocation(Location loc) {
		StringBuilder sb = new StringBuilder();
		double lat = loc.getLatitude();
		double lon = loc.getLongitude();

		String latStr, lonStr;
		
		switch (mLocationFmt) {
		case LOCATION_FMT_DD:
			latStr = getDD(lat);
			lonStr = getDD(lon);
			break;
		case LOCATION_FMT_DDM:
			latStr = getDDM(lat);
			lonStr = getDDM(lon);
			break;
		default: // LOCATION_FMT_DDMS
			latStr = getDDMS(lat);
			lonStr = getDDMS(lon);
			break;
		}
		
		sb.append(latStr);
		if (lat > 0)
			sb.append("N");
		else
			sb.append("S");

		sb.append(", ");
		sb.append(lonStr);
		if (lon > 0)
			sb.append("E");
		else
			sb.append("W");

		return sb.toString();
	}

	private TextView getTextViewForProvider(String provider) {
		if (provider.equals(mFineLocationProvider)) {
			return mTvLocationFine;
		} else if (provider.equals(mCoarseLocationProvider)) {
			return mTvLocationCoarse;
		}

		return null;
	}

	private double metersToFeet(double meters) {
		return meters * 3.28;
	}

	private double metersToMiles(double meters) {
		return meters / 1609;
	}

	private void updateBearing() {
		if (mFineLastLocation != null && mCoarseLastLocation != null) {
			int bearing = (int) mFineLastLocation
					.bearingTo(mCoarseLastLocation);
			int distance = (int) mFineLastLocation
					.distanceTo(mCoarseLastLocation);
			String direction = getNiceDirection(bearing);

			int nicebearing = bearing;
			if (nicebearing < 0)
				nicebearing += 360;

			mTvCellBearing.setText(StyledResourceHelper.GetStyledString(
					mContext, R.string.bearing_fmt, direction, nicebearing));

			switch (mUnits) {
			case UNITS_MILES:
				mTvCellDirection.setText(StyledResourceHelper.GetStyledString(
						mContext, R.string.distance_fmt_miles,
						metersToMiles(distance)));
				break;
			case UNITS_FEET:
				mTvCellDirection.setText(StyledResourceHelper.GetStyledString(
						mContext, R.string.distance_fmt_feet,
						(int) metersToFeet(distance)));
				break;
			default:
				mTvCellDirection.setText(StyledResourceHelper.GetStyledString(
						mContext, R.string.distance_fmt_meters, distance));
			}

		}
	}

	private void UpdateOverlays() {
		mCoarseLocationOverlay.setLocation(mCoarseLastGP);
		mFineLocationOverlay.setLocation(mFineLastGP);
		mLineOverlay.setPositions(mFineLastGP, mCoarseLastGP);

		List<Overlay> overlays = mMapView.getOverlays();
		overlays.clear();
		overlays.add(mLineOverlay);
		overlays.add(mCoarseLocationOverlay);
		overlays.add(mFineLocationOverlay);
		
		if(mMyLocationOverlay != null && mCompass) {
			overlays.add(mMyLocationOverlay);
		}
	}

	private void zoomAndCenterMap() {
		MapController mc = mMapView.getController();

		if (mFineLastGP != null && mCoarseLastGP != null) {
			int lat1 = mFineLastGP.getLatitudeE6();
			int lon1 = mFineLastGP.getLongitudeE6();

			int lat2 = mCoarseLastGP.getLatitudeE6();
			int lon2 = mCoarseLastGP.getLongitudeE6();

			int latspan = Math.max(lat1, lat2) - Math.min(lat1, lat2);
			int lonspan = Math.max(lon1, lon2) - Math.min(lon1, lon2);

			// handle zooming
			if (mAutoZoom) {
				mc.zoomToSpan(latspan, lonspan);

				// if we're using a centering option that isn't the midpoint, we
				// have to
				// zoom out one level to see both points
				if (mAutoCenter != AUTO_CENTER_MIDPOINT)
					mc.setZoom(mMapView.getZoomLevel() - 1);
			}

			if (mAutoCenter == AUTO_CENTER_GPS) {
				mc.setCenter(mFineLastGP);
			} else if (mAutoCenter == AUTO_CENTER_NETWORK) {
				mc.setCenter(mCoarseLastGP);
			} else if (mAutoCenter == AUTO_CENTER_MIDPOINT) {
				int latcen = (Math.max(lat1, lat2) + Math.min(lat1, lat2)) / 2;
				int loncen = (Math.max(lon1, lon2) + Math.min(lon1, lon2)) / 2;
				mc.setCenter(new GeoPoint(latcen, loncen));
			}
		} else if(mFineLastGP != null) {
			// if we only have the fine location, use that
			mc.setCenter(mFineLastGP);
		} else if(mCoarseLastGP != null) {
			// if we only have the coarse location, use that
			mc.setCenter(mCoarseLastGP);
		}
	}
}
