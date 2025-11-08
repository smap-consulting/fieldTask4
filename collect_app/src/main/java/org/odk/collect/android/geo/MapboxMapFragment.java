package org.odk.collect.android.geo;

import android.content.Context;
import android.location.Location;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.fragment.app.FragmentActivity;

import com.google.android.gms.location.LocationListener;

import org.odk.collect.android.geo.models.CompoundMarker;
import org.odk.collect.android.injection.DaggerUtils;
import org.odk.collect.android.location.client.MapboxLocationCallback;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import timber.log.Timber;

/*
 * This class was used for Mapbox but has been disabled as this version of Mapbox does not meet google page size requirements
 */

public class MapboxMapFragment extends org.odk.collect.android.geo.mapboxsdk.MapFragment
    implements MapFragment,
    LocationListener {

    private static final long LOCATION_INTERVAL_MILLIS = 1000;
    private static final long LOCATION_MAX_WAIT_MILLIS = 5000;

    // Bundle keys understood by applyConfig().
    static final String KEY_STYLE_URL = "STYLE_URL";

    @Inject
    MapProvider mapProvider;
    private ReadyListener mapReadyListener;
    private final List<ReadyListener> gpsLocationReadyListeners = new ArrayList<>();
    private PointListener gpsLocationListener;
    private PointListener clickListener;
    private PointListener longPressListener;
    private FeatureListener featureClickListener;
    private FeatureListener featureLineListener;
    private FeatureListener dragEndListener;
    private CompoundMarkerListener compoundMarkerListener;

    private boolean clientWantsLocationUpdates;
    private MapPoint lastLocationFix;

    private int nextFeatureId = 1;
    private final Map<Integer, MapFeature> features = new HashMap<>();
    private boolean isDragging;
    private File referenceLayerFile;
    private final MapboxLocationCallback locationCallback = new MapboxLocationCallback(this);
    private static String lastLocationProvider;

    private TileHttpServer tileServer;

    private static final String PLACEHOLDER_LAYER_ID = "placeholder";

    // During Robolectric tests, Google Play Services is unavailable; sadly, the
    // "map" field will be null and many operations will need to be stubbed out.
    @SuppressFBWarnings(value = "MS_SHOULD_BE_FINAL", justification = "This flag is exposed for Robolectric tests to set")
    @VisibleForTesting public static boolean testMode;

    @Override public void addTo(
        @NonNull FragmentActivity activity, int containerId,
        @Nullable ReadyListener readyListener, @Nullable ErrorListener errorListener) {
        Context context = getContext();
        mapReadyListener = readyListener;

        // Mapbox SDK only knows how to fetch tiles via HTTP.  If we want it to
        // display tiles from a local file, we have to serve them locally over HTTP.
        try {
            tileServer = new TileHttpServer();
            tileServer.start();
        } catch (IOException e) {
            Timber.e(e, "Could not start the TileHttpServer");
        }

        // If the containing activity is being re-created upon screen rotation,
        // the FragmentManager will have also re-created a copy of the previous
        // MapboxMapFragment.  We don't want these useless copies of old fragments
        // to linger, so the following line calls .replace() instead of .add().
        activity.getSupportFragmentManager()
            .beginTransaction().replace(containerId, this).commitNow();
    }

    @Override public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        DaggerUtils.getComponent(context).inject(this);
    }

    @Override
    public void onLocationChanged(Location location) {

    }

    @Override public void onStart() {
        super.onStart();
        mapProvider.onMapFragmentStart(this);
        enableLocationUpdates(clientWantsLocationUpdates);
    }

    @Override public void onStop() {
        enableLocationUpdates(false);
        mapProvider.onMapFragmentStop(this);
        super.onStop();
    }

    @Override public void onDestroy() {
        if (tileServer != null) {
            tileServer.destroy();
        }
        super.onDestroy();
    }

    @Override public void applyConfig(Bundle config) {

    }

    @Override public @NonNull MapPoint getCenter() {
        return INITIAL_CENTER;
    }

    @Override public double getZoom() {
        return 0.0;
    }

    @Override public void setCenter(@Nullable MapPoint center, boolean animate) {

    }

    @Override public void zoomToPoint(@Nullable MapPoint center, boolean animate) {
        zoomToPoint(center, POINT_ZOOM, animate);
    }

    @Override public void zoomToPoint(@Nullable MapPoint center, double zoom, boolean animate) {

    }

    @Override public void zoomToBoundingBox(Iterable<MapPoint> points, double scaleFactor, boolean animate) {

    }

    @Override public int addMarker(MapPoint point, boolean draggable, @IconAnchor String iconAnchor) {
        int featureId = nextFeatureId++;
        return featureId;
    }

    /*
     * Smap
     * Add a layer of previous geopoints selected for this question TODO
     */
    @Override public void addPrevMarker(MapPoint point, @IconAnchor String iconAnchor) {

    }

    @Override public void setMarkerIcon(int featureId, int drawableId) {

    }

    @Override public @Nullable MapPoint getMarkerPoint(int featureId) {
        MapFeature feature = features.get(featureId);
        return feature instanceof MarkerFeature ?
            ((MarkerFeature) feature).getPoint() : null;
    }

    @Override public @NonNull List<MapPoint> getPolyPoints(int featureId) {
        return new ArrayList<>();
    }

    @Override public void setFeatureClickListener(@Nullable FeatureListener listener) {
        featureClickListener = listener;
    }

    @Override public void setDragEndListener(@Nullable FeatureListener listener) {
        dragEndListener = listener;
    }

    @Override public void setCompoundMarkerListener(@Nullable CompoundMarkerListener listener) {
        compoundMarkerListener = listener;
    }

    @Override public @Nullable String getLocationProvider() {
        return lastLocationProvider;
    }



    @Override public void addPrevPoly(@NonNull Iterable<MapPoint> points, boolean closedPolygon) {


    }

    @Override public int addDraggablePoly(@NonNull Iterable<MapPoint> points, boolean closedPolygon,
                                          HashMap<Integer, CompoundMarker> markers) {
        int featureId = nextFeatureId++;
        return featureId;
    }

    @Override public void appendPointToPoly(int featureId, @NonNull MapPoint point) {
        MapFeature feature = features.get(featureId);
        if (feature instanceof PolyFeature) {
            ((PolyFeature) feature).appendPoint(point);
        }
    }

    @Override public void updatePolyPointIcon(int featureId, int markerId, CompoundMarker cm) {
        MapFeature feature = features.get(featureId);
        if (feature instanceof PolyFeature) {
            ((PolyFeature) feature).updateMarkerIcon(markerId, cm);
        }
    }

    @Override public void removePolyLastPoint(int featureId) {
        MapFeature feature = features.get(featureId);
        if (feature instanceof PolyFeature) {
            ((PolyFeature) feature).removeLastPoint();
        }
    }

    @Override public void removeFeature(int featureId) {
        MapFeature feature = features.remove(featureId);
        if (feature != null) {
            feature.dispose();
        }
    }

    @Override public void clearFeatures() {
        features.clear();
        nextFeatureId = 1;
    }

    @Override public void setClickListener(@Nullable PointListener listener) {
        clickListener = listener;
    }

    @Override public void setLongPressListener(@Nullable PointListener listener) {
        longPressListener = listener;
    }

    @Override public void setGpsLocationListener(@Nullable PointListener listener) {
        gpsLocationListener = listener;
    }

    @Override public void setGpsLocationEnabled(boolean enable) {
        if (enable != clientWantsLocationUpdates) {
            clientWantsLocationUpdates = enable;
            enableLocationUpdates(clientWantsLocationUpdates);
        }
    }

    @Override
    public void setRetainMockAccuracy(boolean retainMockAccuracy) {
        locationCallback.setRetainMockAccuracy(retainMockAccuracy);
    }

    @Override public void runOnGpsLocationReady(@NonNull ReadyListener listener) {
        if (lastLocationFix != null) {
            listener.onReady(this);
        } else {
            gpsLocationReadyListeners.add(listener);
        }
    }

    @Override public @Nullable MapPoint getGpsLocation() {
        return lastLocationFix;
    }

    private static @Nullable MapPoint fromLocation(@Nullable Location location) {
        if (location == null) {
            return null;
        }
        return new MapPoint(location.getLatitude(), location.getLongitude(),
            location.getAltitude(), location.getAccuracy());
    }

    /** Adds an image to the style unless it's already present, and returns its ID. */
    private String addIconImage(int drawableId) {
        String imageId = "icon-" + drawableId;
        return imageId;
    }

    @SuppressWarnings({"MissingPermission"})  // permission checks for location services are handled in widgets
    private void enableLocationUpdates(boolean enable) {

    }

    /**
     * A MapFeature is a physical feature on a map, such as a point, a road,
     * a building, a region, etc.  It is presented to the user as one editable
     * object, though its appearance may be constructed from multiple overlays
     * (e.g. geometric elements, handles for manipulation, etc.).
     */
    interface MapFeature {
        /** Removes the feature from the map, leaving it no longer usable. */
        void dispose();
    }

    /** A Symbol that can optionally be dragged by the user. */
    private class MarkerFeature {

        private MapPoint point;

        public MapPoint getPoint() {
            return point;
        }

    }

    /** A polyline or polygon that can be manipulated by dragging Symbols at its vertices. */
    private class PolyFeature {
        public static final float STROKE_WIDTH = 5;

        private final SymbolClickListener symbolClickListener = new SymbolClickListener();
        private final LineClickListener lineClickListener = new LineClickListener();
        private final SymbolDragListener symbolDragListener = new SymbolDragListener();
        private final List<MapPoint> points = new ArrayList<>();

        public List<MapPoint> getPoints() {
            return points;
        }

        public void appendPoint(MapPoint point) {

        }

        public void updateMarkerIcon(int markerId, CompoundMarker cm) {

        }

        public void removeLastPoint() {

        }

        private void updateLine() {

        }

        class SymbolClickListener {

        }

        class LineClickListener {

        }

        class SymbolDragListener {

        }
    }
}
