package org.odk.collect.android.location.client;

import android.location.Location;

import androidx.annotation.NonNull;

import org.odk.collect.android.geo.MapboxMapFragment;
import org.odk.collect.location.LocationUtils;

import java.lang.ref.WeakReference;

/*
 * This class was used for Mapbox but has been disabled as this version of Mapbox does not meet google page size requirements
 */

// https://docs.mapbox.com/android/core/guides/#requesting-location-updates
// Replace mock location accuracy with 0 as in LocationClient implementations since Mapbox uses its own location engine.
public class MapboxLocationCallback {

    private final WeakReference<MapboxMapFragment> mapRef;
    private boolean retainMockAccuracy;

    public MapboxLocationCallback(MapboxMapFragment map) {
        mapRef = new WeakReference<>(map);
    }

    public void setRetainMockAccuracy(boolean retainMockAccuracy) {
        this.retainMockAccuracy = retainMockAccuracy;
    }
}
