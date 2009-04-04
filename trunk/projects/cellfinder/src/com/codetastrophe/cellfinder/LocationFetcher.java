/* Hacked up code to get latitude and longitude for a cell id from google's servers.
 * 
 * Most of this code came from the Android Open Source Project and carries the 
 * copyright and license information below.
 */

/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.codetastrophe.cellfinder;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.Locale;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.location.ProtoRequestListener;
import com.android.internal.location.protocol.GCell;
import com.android.internal.location.protocol.GCellularProfile;
import com.android.internal.location.protocol.GDeviceLocation;
import com.android.internal.location.protocol.GLatLng;
import com.android.internal.location.protocol.GLocReply;
import com.android.internal.location.protocol.GLocReplyElement;
import com.android.internal.location.protocol.GLocRequest;
import com.android.internal.location.protocol.GLocRequestElement;
import com.android.internal.location.protocol.GLocation;
import com.android.internal.location.protocol.GPlatformProfile;
import com.android.internal.location.protocol.GPrefetchMode;
import com.android.internal.location.protocol.GcellularMessageTypes;
import com.android.internal.location.protocol.GlocationMessageTypes;
import com.android.internal.location.protocol.LocserverMessageTypes;
import com.android.internal.location.protocol.ResponseCodes;
import com.google.common.Config;
import com.google.common.android.AndroidConfig;
import com.google.common.io.protocol.ProtoBuf;
import com.google.masf.MobileServiceMux;
import com.google.masf.ServiceCallback;
import com.google.masf.protocol.PlainRequest;
import com.google.masf.protocol.Request;

public class LocationFetcher {
	private static final String TAG = "LOCATIONFETCHER";
	
    private static final String REQUEST_QUERY_LOC = "g:loc/ql";
    private static final String MASF_SERVER_ADDRESS = "http://www.google.com/loc/m/api";
    private static final String APPLICATION_NAME = "location";
    private static final String APPLICATION_VERSION = "1.0";
    private static final String PLATFORM_ID = "android";
    private static final String DISTRIBUTION_CHANNEL = "android";
    private static final String PLATFORM_BUILD = "android";
    
    private static final String KEY_FILE = "gls.key";
    
    private static final double E7 = 10000000.0;
    
    private String mPlatformKey;
    private Context mContext;
    
    public interface LocationCallback {
    	public void GotLocation(int lac, int cid, int mcc, int mnc, double lat, double lon, int alt, int acc, int locType);
    	public void Error(String msg);
    }
    
    public LocationFetcher(Context context) {
        AndroidConfig config = new AndroidConfig(context);
        Config.setConfig(config);

        MobileServiceMux.initialize
        	(MASF_SERVER_ADDRESS,
            APPLICATION_NAME,
            APPLICATION_VERSION,
            PLATFORM_ID,
            DISTRIBUTION_CHANNEL);
        
        mContext = context;
    }
	
	public void getLocationFromCell(int lac, int cid, int mcc, int mnc, final LocationCallback callback) {
		Log.d(TAG, String.format("looking for location for %d %d %d %d", mcc, mnc, lac, cid));
		
		ProtoBuf requestElement = new ProtoBuf(LocserverMessageTypes.GLOC_REQUEST_ELEMENT);
		
		ProtoBuf cellularProfile = getCellularProfile(lac, cid, mcc, mnc);
        requestElement.setProtoBuf(GLocRequestElement.CELLULAR_PROFILE, cellularProfile);

        // Request to send over wire
        ProtoBuf request = new ProtoBuf(LocserverMessageTypes.GLOC_REQUEST);
        request.addProtoBuf(GLocRequest.REQUEST_ELEMENTS, requestElement);

        // Create a Platform Profile
        ProtoBuf platformProfile = createPlatformProfile();
        request.setProtoBuf(GLocRequest.PLATFORM_PROFILE, platformProfile);
        
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        try {
            request.outputTo(payload);
        } catch (IOException e) {
            Log.e(TAG, "getNetworkLocation(): unable to write request to payload", e);
            return;
        }
        
        // Creates  request and a listener with a call back function
        ProtoBuf reply = new ProtoBuf(LocserverMessageTypes.GLOC_REPLY);
        Request plainRequest =
            new PlainRequest(REQUEST_QUERY_LOC, (short)0, payload.toByteArray());

        ProtoRequestListener listener = new ProtoRequestListener(reply, new ServiceCallback() {
            public void onRequestComplete(Object result) {
                ProtoBuf response = (ProtoBuf) result;
                parseNetworkLocationReply(response, callback);

            }
        });
        plainRequest.setListener(listener);

        // Send request
        MobileServiceMux serviceMux = MobileServiceMux.getSingleton();
        serviceMux.submitRequest(plainRequest, true);
	}
	
	private ProtoBuf getCellularProfile(int lac, int cid, int mcc, int mnc) {
		Date now = new Date();
		
        ProtoBuf cellularProfile = new ProtoBuf(GcellularMessageTypes.GCELLULAR_PROFILE);
        cellularProfile.setLong(GCellularProfile.TIMESTAMP, now.getTime());
        cellularProfile.setInt(GCellularProfile.PREFETCH_MODE,
            GPrefetchMode.PREFETCH_MODE_MORE_NEIGHBORS);
        
        ProtoBuf primaryCell = new ProtoBuf(GcellularMessageTypes.GCELL);
        primaryCell.setInt(GCell.LAC, lac);
        primaryCell.setInt(GCell.CELLID, cid);
        primaryCell.setInt(GCell.MCC, mcc);
        primaryCell.setInt(GCell.MNC, mnc);
        
        cellularProfile.setProtoBuf(GCellularProfile.PRIMARY_CELL, primaryCell);
        
        return cellularProfile;
	}
	
    private void parseNetworkLocationReply(ProtoBuf response, LocationCallback callback) {
        if (response == null) {
            callback.Error("response is null");
            return;
        }

        int status1 = response.getInt(GLocReply.STATUS);
        if (status1 != ResponseCodes.STATUS_STATUS_SUCCESS) {
            callback.Error("RPC failed with status " + status1);
            return;
        }
        
        if (response.has(GLocReply.PLATFORM_KEY)) {
            String platformKey = response.getString(GLocReply.PLATFORM_KEY);
            if (!TextUtils.isEmpty(platformKey)) {
            	setPlatformKey(platformKey);
            }
        }

        if (!response.has(GLocReply.REPLY_ELEMENTS)) {
            callback.Error("no ReplyElement");
            return;
        }
        ProtoBuf replyElement = response.getProtoBuf(GLocReply.REPLY_ELEMENTS);

        int status2 = replyElement.getInt(GLocReplyElement.STATUS);
        if (status2 != ResponseCodes.STATUS_STATUS_SUCCESS &&
            status2 != ResponseCodes.STATUS_STATUS_FAILED) {
        	callback.Error("failed with status " + status2);
            return;
        }
        
        double lat = 0, lng = 0;
        int mcc = -1, mnc = -1, cid = -1, lac = -1;
        int locType = -1;
        int acc = -1, alt = 0;
        
        Log.d(TAG, "getNetworkLocation(): Number of prefetched entries " +
                replyElement.getCount(GLocReplyElement.DEVICE_LOCATION));
        
        // most of the time there is only one response here, but loop through all of them
        // just in case one of them is the real tower location and not just the centroid
        for (int i = 0; i < replyElement.getCount(GLocReplyElement.DEVICE_LOCATION); i++ ) {
            ProtoBuf device = replyElement.getProtoBuf(GLocReplyElement.DEVICE_LOCATION, i);
            if (device.has(GDeviceLocation.LOCATION)) {
                ProtoBuf deviceLocation = device.getProtoBuf(GDeviceLocation.LOCATION);
                if (deviceLocation.has(GLocation.LAT_LNG)) {
                    lat = deviceLocation.getProtoBuf(GLocation.LAT_LNG).
                        getInt(GLatLng.LAT_E7) / E7;
                    lng = deviceLocation.getProtoBuf(GLocation.LAT_LNG).
                        getInt(GLatLng.LNG_E7) / E7;
                }
                if(deviceLocation.has(GLocation.ACCURACY)) {
                	acc = deviceLocation.getInt(GLocation.ACCURACY);
                }
                
                if(deviceLocation.has(GLocation.ALTITUDE)) {
                	alt = deviceLocation.getInt(GLocation.ALTITUDE);
                }

                if (deviceLocation.has(GLocation.LOC_TYPE)) {
                    locType = deviceLocation.getInt(GLocation.LOC_TYPE);
                }
            }
            
            Log.d(TAG, String.format("lat %f lon %f locType %d", 
            		lat, lng, locType));

            // get cell info
            if (device.has(GDeviceLocation.CELL)) {
                ProtoBuf deviceCell = device.getProtoBuf(GDeviceLocation.CELL);
                cid = deviceCell.getInt(GCell.CELLID);
                lac = deviceCell.getInt(GCell.LAC);
                if (deviceCell.has(GCell.MNC) && deviceCell.has(GCell.MCC)) {
                    mcc = deviceCell.getInt(GCell.MCC);
                    mnc = deviceCell.getInt(GCell.MNC);
                }
            }
         
            Log.d(TAG, String.format("mcc %d mnc %d lac %d cid %d lat %f lon %f locType %d", 
            		mcc, mnc, lac, cid, lat, lng, locType));

            // if we have the actual tower location, break, otherwise keep going
            // just in case
            if(locType == GLocation.LOCTYPE_TOWER_LOCATION) {
            	break;
            }
        }
        
        if(cid != -1 && lac != -1) {
        	callback.GotLocation(lac, cid, mcc, mnc, lat, lng, alt, acc, locType);
        }  else {
        	callback.Error("no cell information");
        }
    }
    
    private ProtoBuf mPlatformProfile;
    
    private ProtoBuf createPlatformProfile() {
        if (mPlatformProfile == null) {
            mPlatformProfile = new ProtoBuf(GlocationMessageTypes.GPLATFORM_PROFILE);
            mPlatformProfile.setString(GPlatformProfile.VERSION, APPLICATION_VERSION);
            mPlatformProfile.setString(GPlatformProfile.PLATFORM, PLATFORM_BUILD);
        }

        // Add Locale
        Locale locale = Locale.getDefault();
        if ((locale != null) && (locale.toString() != null)) {
            mPlatformProfile.setString(GPlatformProfile.LOCALE, locale.toString());
        }
        
        // Add Platform Key
        String platformKey = getPlatformKey();
        if (!TextUtils.isEmpty(platformKey)) {
            mPlatformProfile.setString(GPlatformProfile.PLATFORM_KEY, platformKey);
        }

        // Clear out cellular platform profile
        mPlatformProfile.setProtoBuf(GPlatformProfile.CELLULAR_PLATFORM_PROFILE, null);

        return mPlatformProfile;
    }
    
    private String getPlatformKey() {
    	if (mPlatformKey != null) {
            return mPlatformKey;
        }
    	
    	try {
    		FileInputStream in = mContext.openFileInput(KEY_FILE);
    		DataInputStream ind = new DataInputStream(in);
    		mPlatformKey = ind.readUTF();
    		Log.d(TAG, "Using existing platform key " + mPlatformKey);
    		return mPlatformKey;
    	} catch (IOException e) {
    		
    	}
    	
    	Log.d(TAG, "No platform key found");
    	return null;
    }
    
    private void setPlatformKey(String platformKey) {
    	mPlatformKey = platformKey;
    	
    	try {
			FileOutputStream out = mContext.openFileOutput(KEY_FILE, Context.MODE_PRIVATE);
			DataOutputStream outd = new DataOutputStream(out);
			outd.writeUTF(platformKey);
			outd.flush();
			outd.close();
		} catch (IOException e) {
			
		}
    }
}
