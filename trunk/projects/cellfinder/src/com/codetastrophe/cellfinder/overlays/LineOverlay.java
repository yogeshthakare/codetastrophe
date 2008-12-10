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

package com.codetastrophe.cellfinder.overlays;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapView;
import com.google.android.maps.Overlay;
import com.google.android.maps.Projection;

public class LineOverlay extends Overlay {
	private GeoPoint mSource = null;
	private GeoPoint mDest = null;
	private Paint mPaint = null;

	public LineOverlay(GeoPoint source, GeoPoint dest, Paint paint) {
		mSource = source;
		mDest = dest;
		mPaint = paint;
	}

	@Override
	public void draw(Canvas canvas, MapView mapView, boolean shadow) {
		super.draw(canvas, mapView, shadow);

		if (mSource != null && mDest != null) {
			Projection proj = mapView.getProjection();
			Point sourcePoint = proj.toPixels(mSource, null);
			Point destPoint = proj.toPixels(mDest, null);

			canvas.drawLine(sourcePoint.x, sourcePoint.y, destPoint.x,
					destPoint.y, mPaint);
		}
	}

	public void setPositions(GeoPoint source, GeoPoint dest) {
		mSource = source;
		mDest = dest;
	}
}
