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
import android.graphics.Point;
import android.graphics.drawable.Drawable;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapView;
import com.google.android.maps.Overlay;
import com.google.android.maps.Projection;

public class ImageOverlay extends Overlay {
	private Drawable mDrawable = null;
	private GeoPoint mLocation = null;

	public ImageOverlay(Drawable drawable, GeoPoint location) {
		mDrawable = drawable;
		mLocation = location;
	}

	@Override
	public void draw(Canvas canvas, MapView mapView, boolean shadow) {
		super.draw(canvas, mapView, shadow);

		if (mLocation != null) {
			Projection proj = mapView.getProjection();
			Point p = proj.toPixels(mLocation, null);

			int width = mDrawable.getMinimumWidth();
			int height = mDrawable.getMinimumHeight();

			int x = p.x - width / 2;
			int y = p.y - height / 2;

			mDrawable.setBounds(x, y, x + width, y + height);
			mDrawable.draw(canvas);
		}
	}

	public void setLocation(GeoPoint location) {
		mLocation = location;
	}
}
