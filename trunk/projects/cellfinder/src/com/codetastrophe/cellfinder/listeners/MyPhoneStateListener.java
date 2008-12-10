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

import com.codetastrophe.cellfinder.R;
import com.codetastrophe.cellfinder.utils.StyledResourceHelper;

import android.app.Activity;
import android.content.Context;
import android.telephony.CellLocation;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.gsm.GsmCellLocation;
//import android.util.Log;
import android.widget.TextView;

public class MyPhoneStateListener extends PhoneStateListener {
	private TextView mTvOperText = null;
	private TextView mTvOperNum = null;
	private TextView mTvSignalStr = null;
	private TextView mTvCellCidLac = null;
	private Context mContext = null;

	public MyPhoneStateListener(Activity a) {
		// initialize our textviews
		mTvOperText = (TextView) a.findViewById(R.id.tv_oper_text);
		mTvOperNum = (TextView) a.findViewById(R.id.tv_oper_num);
		mTvSignalStr = (TextView) a.findViewById(R.id.tv_signal_str);
		mTvCellCidLac = (TextView) a.findViewById(R.id.tv_cell_cid_lac);

		// we need the context to get various string values
		mContext = a;
	}

	@Override
	public void onCellLocationChanged(CellLocation location) {
		//Log.d(CellFinderMapActivity.CELLFINDER,
		//		"MyPhoneStateListener.OnCellLocationChanged()");
		super.onCellLocationChanged(location);

		if (location.getClass() == GsmCellLocation.class) {
			GsmCellLocation gsmloc = (GsmCellLocation) location;

			int cid = gsmloc.getCid();
			int lac = gsmloc.getLac();
			String unknown = mContext.getString(R.string.unknown);

			// don't print a -1 in the UI, that's hella weak
			String cidstr = cid == -1 ? unknown : Integer.toString(cid);
			String lacstr = lac == -1 ? unknown : Integer.toString(lac);

			mTvCellCidLac.setText(StyledResourceHelper.GetStyledString(
					mContext, R.string.tvcellinfo_fmt, cidstr, lacstr));
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
			mTvOperText.setText(StyledResourceHelper.GetStyledString(mContext,
					R.string.operinfo_fmt,
					serviceState.getOperatorAlphaShort(), serviceState
							.getOperatorAlphaLong()));

			String op = serviceState.getOperatorNumeric();
			if (op.length() > 3) {
				mTvOperNum.setText(StyledResourceHelper.GetStyledString(
						mContext, R.string.opernum_fmt, op.substring(0, 3), op
								.substring(3)));
			}

			break;
		case ServiceState.STATE_POWER_OFF:
			clearTextViews();
			mTvOperText.setText(mContext
					.getString(R.string.service_state_poweroff));
			break;
		case ServiceState.STATE_OUT_OF_SERVICE:
			clearTextViews();
			mTvOperText.setText(mContext
					.getString(R.string.service_state_noservice));
			break;
		}
	}

	@Override
	public void onSignalStrengthChanged(int asu) {
		// Log.d(CellFinderMapActivity.CELLFINDER,
		// "MyPhoneStateListener.onSignalStrengthChanged()");
		super.onSignalStrengthChanged(asu);

		mTvSignalStr.setText(StyledResourceHelper.GetStyledString(mContext,
				R.string.signal_fmt, asu));
	}

	private void clearTextViews() {
		mTvOperText.setText("");
		mTvOperNum.setText("");
		mTvCellCidLac.setText("");
		mTvSignalStr.setText("");
	}
}
