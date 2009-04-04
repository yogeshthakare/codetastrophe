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

import java.util.Hashtable;

import com.android.internal.location.protocol.GLocation;
import com.codetastrophe.cellfinder.LocationFetcher;
import com.codetastrophe.cellfinder.R;
import com.codetastrophe.cellfinder.LocationFetcher.LocationCallback;
import com.codetastrophe.cellfinder.utils.StyledResourceHelper;

import android.app.Activity;
import android.content.Context;
import android.location.Location;
import android.telephony.CellLocation;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.gsm.GsmCellLocation;
import android.util.Log;
import android.widget.TextView;

public class MyPhoneStateListener extends PhoneStateListener {
	public static String LOCTYPE_CENTROID = "centroid";
	public static String LOCTYPE_TOWER = "tower";
	public static String LOCTYPE_UNKNOWN = "unknown";
	
	// the "waiting" location type means we made a request but are 
	// still waiting on a response
	public static String LOCTYPE_WAITING = "waiting";  
	
	private TextView mTvOperText = null;
	private TextView mTvOperNum = null;
	private TextView mTvSignalStr = null;
	private TextView mTvCellCidLac = null;
	private Context mContext = null;
	private boolean mDirectQuery = false;
	private LocationFetcher mLocationFetcher = null;
	private LocationCache mLocationCache = null;
	private Location mWaitingLocation = null;
	private MyLocationListener mLocationListener = null;

	private String mOperStr = "";
	private int mCid = -1;
	private int mLac = -1;
	private int mDbm = -1;
	private int mMcc = -1;
	private int mMnc = -1;
	
	public MyPhoneStateListener(Activity a) {
		// initialize our textviews
		mTvOperText = (TextView) a.findViewById(R.id.tv_oper_text);
		mTvOperNum = (TextView) a.findViewById(R.id.tv_oper_num);
		mTvSignalStr = (TextView) a.findViewById(R.id.tv_signal_str);
		mTvCellCidLac = (TextView) a.findViewById(R.id.tv_cell_cid_lac);

		// we need the context to get various string values
		mContext = a;
	}

	public void setLocationListener(MyLocationListener listener) {
		mLocationListener = listener;
	}
	
	@Override
	public void onCellLocationChanged(CellLocation location) {
		//Log.d(CellFinderMapActivity.CELLFINDER,
		//		"MyPhoneStateListener.OnCellLocationChanged()");
		super.onCellLocationChanged(location);

		if (location.getClass() == GsmCellLocation.class) {
			GsmCellLocation gsmloc = (GsmCellLocation) location;

			final int cid = mCid = gsmloc.getCid();
			final int lac = mLac = gsmloc.getLac();
			String unknown = mContext.getString(R.string.unknown);

			// don't print a -1 in the UI, that's hella weak
			String cidStr = cid == -1 ? unknown : Integer.toString(cid);
			String lacStr = lac == -1 ? unknown : Integer.toString(lac);

			mTvCellCidLac.setText(StyledResourceHelper.GetStyledString(
					mContext, R.string.tvcellinfo_fmt, cidStr, lacStr));
			
			// do direct query if it's enabled and we have enough info
			if(mMcc != -1 && mMnc != -1 && cid != -1 && lac != -1 && mDirectQuery) {
				if(mLocationFetcher == null) {
					mLocationFetcher = new LocationFetcher(mContext);
					mLocationCache = new LocationCache();
					mWaitingLocation = new Location("waiting");
				}
				
				try {
					final int mcc = mMcc;
					final int mnc = mMnc;
				
					final Location loc = mLocationCache.GetLocation(mcc, mnc, cid, lac);
					if(loc == null) {
						mLocationCache.SetLocation(mcc, mnc, cid, lac, mWaitingLocation);
						
						LocationCallback callback = new LocationCallback() {
							@Override
							public void Error(String msg) {
								mLocationCache.SetLocation(mcc, mnc, cid, lac, null);
								Log.d("LOCATION", "ERROR: " + msg);
							}
							
							@Override
							public void GotLocation(final int lac2, final int cid2, 
									final int mcc2, final int mnc2, 
									double lat, double lon, int alt, int acc, int locType) {

								// get string for location type
								String locstr;
								if(locType == GLocation.LOCTYPE_CENTROID) {
									locstr = LOCTYPE_CENTROID;
								} else if (locType == GLocation.LOCTYPE_TOWER_LOCATION) {
									locstr = LOCTYPE_TOWER;
								} else {
									locstr = LOCTYPE_UNKNOWN + locType;
								}
								
								// use location type as 'provider' even though it sucks
								// to do that
								final Location newloc = new Location(locstr);
								newloc.setLatitude(lat);
								newloc.setLongitude(lon);
								newloc.setAccuracy(acc);
								newloc.setAltitude(alt);
								
								Log.d("LOCATION", String.format("storing location %d/%d/%d/%d from %s",
									mcc2, mnc2, lac2, cid2, locType));
								
								mLocationCache.SetLocation(mcc2, mnc2, cid2, lac2, newloc);
								
								if(mLocationListener != null) {
									// post a runnable on the UI thread - doesn't really
									// matter which view object we pick
									mTvOperText.post(new Runnable() {
										@Override
										public void run() {
											mLocationListener.directQueryLocationChanged(
													mOperStr, mcc2, mnc2, lac2, cid2,
													mDbm, newloc);
										}
									});
								}
							}
						};
					
						mLocationFetcher.getLocationFromCell(lac, cid, mcc, mnc, callback);
					} else {
						if(!loc.equals(mWaitingLocation)) {
							if(mLocationListener != null) {
								mTvOperText.post(new Runnable() {
									@Override
									public void run() {
										mLocationListener.directQueryLocationChanged(mOperStr, 
												mcc, mnc, lac, cid, mDbm, loc);
									}
								});
							}
						}
					}
				} catch (NumberFormatException nfe) {
					
				}
			}
		}
	}

	@Override
	public void onServiceStateChanged(ServiceState serviceState) {
		// Log.d(CellFinderMapActivity.CELLFINDER,
		// "MyPhoneStateListener.onServiceStateChanged()");
		super.onServiceStateChanged(serviceState);

		int state = serviceState.getState();
		switch (state) {
		case ServiceState.STATE_IN_SERVICE:
		case ServiceState.STATE_EMERGENCY_ONLY:
			mOperStr = serviceState.getOperatorAlphaLong();
			mTvOperText.setText(StyledResourceHelper.GetStyledString(mContext,
					R.string.operinfo_fmt,
					serviceState.getOperatorAlphaShort(), mOperStr));
			

			String op = serviceState.getOperatorNumeric();
			
			if (op.length() > 3) {
				String mccStr = op.substring(0, 3);
				String mncStr = op.substring(3);
				
				try {
					mMcc = Integer.parseInt(mccStr);
					mMnc = Integer.parseInt(mncStr);
				} catch (Exception e) { }
				
				mTvOperNum.setText(StyledResourceHelper.GetStyledString(
						mContext, R.string.opernum_fmt, mccStr, mncStr));
			} else {
				mTvOperNum.setText(op);
			}

			break;
		case ServiceState.STATE_POWER_OFF:
			clearTextViews();
			mOperStr = mContext.getString(R.string.service_state_poweroff);
			mTvOperText.setText(mOperStr);
			break;
		case ServiceState.STATE_OUT_OF_SERVICE:
			clearTextViews();
			
			mOperStr = mContext.getString(R.string.service_state_noservice);
			mTvOperText.setText(mOperStr);
			
			break;
		}
	}

	@Override
	public void onSignalStrengthChanged(int asu) {
		// Log.d(CellFinderMapActivity.CELLFINDER,
		// "MyPhoneStateListener.onSignalStrengthChanged()");
		super.onSignalStrengthChanged(asu);

		// dBm calculation comes from PhoneStateIntentReceiver.java in the 
		// Android source code
		String dbmStr = "Unknown";
		
		if(asu != -1) {
			mDbm = ((Integer)(-113 + 2*asu));
			
			if(mDbm == -113) dbmStr = "-113 or less";
			else if (mDbm >= -51) dbmStr = "-51 or greater";
			else {
				dbmStr = Integer.toString(mDbm);
			}
		}
		
		mTvSignalStr.setText(StyledResourceHelper.GetStyledString(mContext,
				R.string.signal_fmt, asu, dbmStr));
	}

	private void clearTextViews() {
		mTvOperText.setText("");
		mTvOperNum.setText("");
		mTvCellCidLac.setText("");
		mTvSignalStr.setText("");
	}

	public String getOperStr() {
		return mOperStr;
	}

	public int getCid() {
		return mCid;
	}

	public int getLac() {
		return mLac;
	}

	public int getDbm() {
		return mDbm;
	}

	public int getMcc() {
		return mMcc;
	}

	public int getMnc() {
		return mMnc;
	}
	
	public void setDirectQueryMode(boolean directQuery) {
		mDirectQuery = directQuery;
	}
	
	public Location getCurrentLocationDirect() {
		if(mLocationCache != null) {
			return mLocationCache.GetLocation(mMcc, mMnc, mCid, mLac);
		} else {
			return null;
		}
	}
	
	private static class LocationCache {
		private Hashtable<String, Location> _hash = new Hashtable<String, Location>();
		
		public LocationCache() { }
		
		public Location GetLocation(int mcc, int mnc, int cid, int lac) {
			String key = getKey(mcc, mnc, cid, lac);
			if(_hash.containsKey(key)) {
				return _hash.get(key);
			} else {
				return null;
			}
		}
		
		public void SetLocation(int mcc, int mnc, int cid, int lac, Location location) {
			_hash.put(getKey(mcc, mnc, cid, lac), location);
		}
		
		private static String getKey(int mcc, int mnc, int cid, int lac) {
			return String.format("%d:%d:%d:%d", mcc, mnc, cid, lac);
		}
	}
}
