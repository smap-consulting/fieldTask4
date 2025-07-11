/*
 * Copyright (C) 2018 Nafundi
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package org.odk.collect.android.activities;

import static org.odk.collect.android.widgets.utilities.ActivityGeoDataRequester.READ_ONLY;

import android.content.Context;
import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.VisibleForTesting;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import org.odk.collect.android.R;
import org.odk.collect.android.application.Collect;
import org.odk.collect.android.geo.CompoundDialogFragment;
import org.odk.collect.android.geo.MapFragment;
import org.odk.collect.android.geo.MapPoint;
import org.odk.collect.android.geo.MapProvider;
import org.odk.collect.android.geo.SettingsDialogFragment;
import org.odk.collect.android.geo.models.CompoundMarker;
import org.odk.collect.android.geo.models.GeoCompoundData;
import org.odk.collect.android.geo.models.MarkerType;
import org.odk.collect.android.injection.DaggerUtils;
import org.odk.collect.android.preferences.MapsPreferences;
import org.odk.collect.android.utilities.DialogUtils;
import org.odk.collect.android.utilities.GeoUtils;
import org.odk.collect.android.utilities.ToastUtils;
import org.odk.collect.location.Location;
import org.odk.collect.location.tracker.LocationTracker;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import timber.log.Timber;

public class GeoCompoundActivity extends BaseGeoMapActivity implements SettingsDialogFragment.SettingsDialogCallback,
        CompoundDialogFragment.SettingsDialogCallback {
    public static final String ANSWER_KEY = "answer";
    public static final String FEATURE_KEY = "feature";
    public static final String APPEARANCE_KEY = "appearances";
    public static final String MAP_CENTER_KEY = "map_center";
    public static final String MAP_ZOOM_KEY = "map_zoom";
    public static final String POINTS_KEY = "points";
    public static final String MARKERS_KEY = "markers";
    public static final String INPUT_ACTIVE_KEY = "input_active";
    public static final String RECORDING_ENABLED_KEY = "recording_enabled";
    public static final String RECORDING_AUTOMATIC_KEY = "recording_automatic";
    public static final String INTERVAL_INDEX_KEY = "interval_index";
    public static final String ACCURACY_THRESHOLD_INDEX_KEY = "accuracy_threshold_index";
    public static final String INTENT_QUESTION_PATH_KEY = "question_path";

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture schedulerHandler;

    @Inject
    MapProvider mapProvider;

    @Inject
    LocationTracker locationTracker;

    private HashMap<Integer, CompoundMarker> markers = new HashMap<>();
    private HashMap<String, MarkerType> markerTypes = new HashMap<>();
    private MapFragment map;
    private int featureId = -1;  // will be a positive featureId once map is ready
    private String originalAnswerString = "";
    private String originalAppearanceString = "";

    private ImageButton zoomButton;
    private ImageButton playButton;
    private ImageButton clearButton;
    private Button recordButton;
    private ImageButton pauseButton;
    private ImageButton backspaceButton;

    private TextView locationStatus;
    private TextView collectionStatus;

    private View settingsView;

    private static final int[] INTERVAL_OPTIONS = {
        1, 5, 10, 20, 30, 60, 300, 600, 1200, 1800
    };
    private static final int DEFAULT_INTERVAL_INDEX = 3; // default is 20 seconds

    private static final int[] ACCURACY_THRESHOLD_OPTIONS = {
        0, 3, 5, 10, 15, 20
    };
    private static final int DEFAULT_ACCURACY_THRESHOLD_INDEX = 3; // default is 10 meters

    private boolean inputActive; // whether we are ready for the user to add points
    private boolean recordingEnabled; // whether points are taken from GPS readings (if not, placed by tapping)
    private boolean recordingAutomatic; // whether GPS readings are taken at regular intervals (if not, only when user-directed)
    private boolean intentReadOnly; // whether the intent requested for the path to be read-only.

    private String questionPath;    // Used to look up previous locations in a repeat

    private int intervalIndex = DEFAULT_INTERVAL_INDEX;

    private int accuracyThresholdIndex = DEFAULT_ACCURACY_THRESHOLD_INDEX;

    // restored from savedInstanceState
    private MapPoint restoredMapCenter;
    private Double restoredMapZoom;
    private List<MapPoint> restoredPoints;
    private List<CompoundMarker> restoredMarkers;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DaggerUtils.getComponent(this).inject(this);

        if (savedInstanceState != null) {
            featureId = savedInstanceState.getInt(FEATURE_KEY);
            restoredMapCenter = savedInstanceState.getParcelable(MAP_CENTER_KEY);
            restoredMapZoom = savedInstanceState.getDouble(MAP_ZOOM_KEY);
            restoredPoints = savedInstanceState.getParcelableArrayList(POINTS_KEY);
            restoredMarkers = savedInstanceState.getParcelableArrayList(MARKERS_KEY);
            inputActive = savedInstanceState.getBoolean(INPUT_ACTIVE_KEY, false);
            recordingEnabled = savedInstanceState.getBoolean(RECORDING_ENABLED_KEY, false);
            recordingAutomatic = savedInstanceState.getBoolean(RECORDING_AUTOMATIC_KEY, false);
            intervalIndex = savedInstanceState.getInt(INTERVAL_INDEX_KEY, DEFAULT_INTERVAL_INDEX);
            accuracyThresholdIndex = savedInstanceState.getInt(
                ACCURACY_THRESHOLD_INDEX_KEY, DEFAULT_ACCURACY_THRESHOLD_INDEX);
            questionPath = savedInstanceState.getString(INTENT_QUESTION_PATH_KEY, null);
            originalAppearanceString = savedInstanceState.getString(APPEARANCE_KEY, "");
        }

        intentReadOnly = getIntent().getBooleanExtra(READ_ONLY, false);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setTitle(getString(org.odk.collect.strings.R.string.geocompound_title));
        setContentView(R.layout.geopoly_layout);

        Context context = getApplicationContext();
        mapProvider.createMapFragment(context)
            .addTo(this, R.id.map_container, this::initMap, this::finish);

        Collect.getInstance().clearCompoundAddresses();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        if (map == null) {
            // initMap() is called asynchronously, so map can be null if the activity
            // is stopped (e.g. by screen rotation) before initMap() gets to run.
            // In this case, preserve any provided instance state.
            if (previousState != null) {
                state.putAll(previousState);
            }
            return;
        }
        state.putParcelable(MAP_CENTER_KEY, map.getCenter());
        state.putInt(FEATURE_KEY, featureId);
        state.putDouble(MAP_ZOOM_KEY, map.getZoom());
        state.putParcelableArrayList(POINTS_KEY, new ArrayList<>(map.getPolyPoints(featureId)));
        state.putParcelableArrayList(MARKERS_KEY, new ArrayList<>(getMarkerArray()));
        state.putBoolean(INPUT_ACTIVE_KEY, inputActive);
        state.putBoolean(RECORDING_ENABLED_KEY, recordingEnabled);
        state.putBoolean(RECORDING_AUTOMATIC_KEY, recordingAutomatic);
        state.putInt(INTERVAL_INDEX_KEY, intervalIndex);
        state.putInt(ACCURACY_THRESHOLD_INDEX_KEY, accuracyThresholdIndex);
        state.putString(INTENT_QUESTION_PATH_KEY, questionPath);
        state.putString(APPEARANCE_KEY, originalAppearanceString);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();

        // We're not using managed dialogs, so we have to dismiss the dialog to prevent it from
        // leaking memory.
        //if (locationDialog != null && locationDialog.isShowing()) {
        //    locationDialog.dismiss();
        //}
    }

    @Override protected void onDestroy() {
        if (schedulerHandler != null && !schedulerHandler.isCancelled()) {
            schedulerHandler.cancel(true);
        }

        locationTracker.stop();
        super.onDestroy();
    }

    public void initMap(MapFragment newMapFragment) {
        map = newMapFragment;

        locationStatus = findViewById(R.id.location_status);
        collectionStatus = findViewById(R.id.collection_status);
        settingsView = getLayoutInflater().inflate(R.layout.geopoly_dialog, null);

        clearButton = findViewById(R.id.clear);
        clearButton.setOnClickListener(v -> showClearDialog());

        pauseButton = findViewById(R.id.pause);
        pauseButton.setOnClickListener(v -> {
            inputActive = false;
            try {
                schedulerHandler.cancel(true);
            } catch (Exception e) {
                // Do nothing
            }
            updateUi();
        });

        backspaceButton = findViewById(R.id.backspace);
        backspaceButton.setOnClickListener(v -> removeLastPoint());

        ImageButton saveButton = findViewById(R.id.save);
        saveButton.setOnClickListener(v -> {
            saveAsGeoCompound();
        });

        playButton = findViewById(R.id.play);
        playButton.setOnClickListener(v -> {
            if (map.getPolyPoints(featureId).isEmpty()) {
                DialogUtils.showIfNotShowing(SettingsDialogFragment.class, getSupportFragmentManager());
            } else {
                startInput();
            }
        });

        recordButton = findViewById(R.id.record_button);
        recordButton.setOnClickListener(v -> recordPoint(map.getGpsLocation()));

        findViewById(R.id.layers).setOnClickListener(v -> {
            MapsPreferences.showReferenceLayerDialog(this);
        });

        zoomButton = findViewById(R.id.zoom);
        zoomButton.setOnClickListener(v -> map.zoomToPoint(map.getGpsLocation(), true));

        // Add
        Intent intent = getIntent();

        // Get the marker types from the appearance
        if (intent != null && intent.hasExtra(APPEARANCE_KEY)) {
            originalAppearanceString = intent.getStringExtra(APPEARANCE_KEY);
            markerTypes = getMarkerTypes(originalAppearanceString);
        }
        if(originalAppearanceString != null) {
            markerTypes = getMarkerTypes(originalAppearanceString);
        }

        List<MapPoint> points = new ArrayList<>();
        if (intent != null && intent.hasExtra(ANSWER_KEY)) {
            originalAnswerString = intent.getStringExtra(ANSWER_KEY);
            GeoCompoundData cd = new GeoCompoundData(originalAnswerString, markerTypes);
            points = cd.points;
            markers = cd.markers;
        }
        if (restoredPoints != null) {
            points = restoredPoints;
            markers = getMarkerHashMap(restoredMarkers);
        }

        if(map.getPolyPoints(featureId).size() > 0) {
            points = map.getPolyPoints(featureId);
        } else {
            featureId = map.addDraggablePoly(points, false, markers);
        }
        map.setCompoundMarkerListener(this::onCompoundMarkerClicked);
        if (inputActive && !intentReadOnly) {
            startInput();
        }

        map.setClickListener(this::onClick);
        // Also allow long press to place point to match prior versions
        map.setLongPressListener(this::onClick);
        map.setGpsLocationEnabled(true);
        map.setGpsLocationListener(this::onGpsLocation);
        if (restoredMapCenter != null && restoredMapZoom != null) {
            map.zoomToPoint(restoredMapCenter, restoredMapZoom, false);
        } else if (!points.isEmpty()) {
            map.zoomToBoundingBox(points, 0.6, false);
        } else {
            map.runOnGpsLocationReady(this::onGpsLocationReady);
        }
        updateUi();
    }

    private void saveAsGeoCompound() {

        String result = "";     // Default result if there are no points
        if (map.getPolyPoints(featureId).size() == 1) {
            ToastUtils.showShortToastInMiddle(getString(org.odk.collect.strings.R.string.polyline_validator));
            return;     // do not finish
        } else if (map.getPolyPoints(featureId).size() > 1) {
            List<MapPoint> points = map.getPolyPoints(featureId);
            StringBuilder rb = new StringBuilder("line:")
                    .append(GeoUtils.formatPointsResultString(points, false))
                    .append(getMarkersAsText(points));
            result = rb.toString();
        }
        setResult(RESULT_OK, new Intent().putExtra(FormEntryActivity.ANSWER_KEY, result));
        finish();
    }

    @Override public void onBackPressed() {
        if (map != null && !parsePoints(originalAnswerString).equals(map.getPolyPoints(featureId))) {
            showBackDialog();
        } else {
            finish();
        }
    }

    /**
     * Parses a form result string, as previously formatted by formatPoints,
     * into a list of vertices.
     */
    private List<MapPoint> parsePoints(String coords) {
        List<MapPoint> points = new ArrayList<>();
        for (String vertex : (coords == null ? "" : coords).split(";")) {
            String[] words = vertex.trim().split(" ");
            if (words.length >= 2) {
                double lat;
                double lon;
                double alt;
                double sd;
                try {
                    lat = Double.parseDouble(words[0]);
                    lon = Double.parseDouble(words[1]);
                    alt = words.length > 2 ? Double.parseDouble(words[2]) : 0;
                    sd = words.length > 3 ? Double.parseDouble(words[3]) : 0;
                } catch (NumberFormatException e) {
                    continue;
                }
                points.add(new MapPoint(lat, lon, alt, sd));
            }
        }
        return points;
    }

    @Override
    public void startInput() {
        inputActive = true;
        if (recordingEnabled && recordingAutomatic) {
            locationTracker.start();

            recordPoint(map.getGpsLocation());
            schedulerHandler = scheduler.scheduleAtFixedRate(() -> runOnUiThread(() -> {
                Location currentLocation = locationTracker.getCurrentLocation();

                if (currentLocation != null) {
                    MapPoint currentMapPoint = new MapPoint(
                            currentLocation.getLatitude(),
                            currentLocation.getLongitude(),
                            currentLocation.getAltitude(),
                            currentLocation.getAccuracy()
                    );

                    recordPoint(currentMapPoint);
                }
            }), 0, INTERVAL_OPTIONS[intervalIndex], TimeUnit.SECONDS);
        }
        updateUi();
    }

    @Override
    public void updateMarker(int markerId, String markerType) {
        CompoundMarker cm = markers.get(markerId);
        if(cm == null) {
            cm = new CompoundMarker(markerId, markerType, getMarkerTypeLabel(markerType));
            markers.put(markerId,cm);
        } else {
            cm.type = markerType;
            cm.label = getMarkerTypeLabel(markerType);
        }
        map.updatePolyPointIcon(featureId, markerId, cm);
    }

    @Override
    public void updateRecordingMode(int id) {
        recordingEnabled = id != R.id.placement_mode;
        recordingAutomatic = id == R.id.automatic_mode;
    }

    @Override
    public int getCheckedId() {
        if (recordingEnabled) {
            return recordingAutomatic ? R.id.automatic_mode : R.id.manual_mode;
        } else {
            return R.id.placement_mode;
        }
    }

    @Override
    public int getIntervalIndex() {
        return intervalIndex;
    }

    @Override
    public int getAccuracyThresholdIndex() {
        return accuracyThresholdIndex;
    }

    @Override
    public void setIntervalIndex(int intervalIndex) {
        this.intervalIndex = intervalIndex;
    }

    @Override
    public void setAccuracyThresholdIndex(int accuracyThresholdIndex) {
        this.accuracyThresholdIndex = accuracyThresholdIndex;
    }

    /**
     * Reacts to a tap on a Marker by showing a dialog to get the marker type
     */
    public void onCompoundMarkerClicked(int markerIdx) {
        if(!inputActive ) { // Don't allow selection of a marker while recording new points
            Timber.i("Marker: %s", markerIdx);
            CompoundMarker marker = markers.get(markerIdx);
            DialogFragment df = new CompoundDialogFragment();
            Bundle args = new Bundle();
            args.putString(CompoundDialogFragment.PIT_KEY, getMarkerTypeName("pit"));
            args.putString(CompoundDialogFragment.FAULT_KEY, getMarkerTypeName("fault"));
            args.putInt(CompoundDialogFragment.FEATUREID_KEY, markerIdx);
            args.putString(CompoundDialogFragment.LABEL_KEY, getMarkerLabel(markerIdx));
            if (marker != null) {
                args.putString(CompoundDialogFragment.VALUE_KEY, marker.type);
            }
            df.setArguments(args);
            df.show(getSupportFragmentManager(), CompoundDialogFragment.class.getName());
        }
    }

    private void onClick(MapPoint point) {
        if (inputActive && !recordingEnabled) {
            map.appendPointToPoly(featureId, point);
            updateUi();
        }
    }

    private void onGpsLocationReady(MapFragment map) {
        // Don't zoom to current location if a user is manually entering points
        if (getWindow().isActive() && (!inputActive || recordingEnabled)) {
            map.zoomToPoint(map.getGpsLocation(), true);
        }
        updateUi();
    }

    private void onGpsLocation(MapPoint point) {
        if (inputActive && recordingEnabled) {
            map.setCenter(point, false);
        }
        updateUi();
    }

    private void recordPoint(MapPoint point) {
        if (point != null && isLocationAcceptable(point)) {
            map.appendPointToPoly(featureId, point);
            updateUi();
        }
    }

    private boolean isLocationAcceptable(MapPoint point) {
        if (!isAccuracyThresholdActive()) {
            return true;
        }
        return point.sd <= ACCURACY_THRESHOLD_OPTIONS[accuracyThresholdIndex];
    }

    private boolean isAccuracyThresholdActive() {
        int meters = ACCURACY_THRESHOLD_OPTIONS[accuracyThresholdIndex];
        return recordingEnabled && recordingAutomatic && meters > 0;
    }

    private void removeLastPoint() {
        if (featureId != -1) {
            map.removePolyLastPoint(featureId);
            updateUi();
        }
    }

    private void clear() {
        map.clearFeatures();
        featureId = map.addDraggablePoly(new ArrayList<>(), false, null);
        inputActive = false;
        updateUi();
    }

    /** Updates the state of various UI widgets to reflect internal state. */
    private void updateUi() {
        final int numPoints = map.getPolyPoints(featureId).size();
        final MapPoint location = map.getGpsLocation();

        // Visibility state
        playButton.setVisibility(inputActive ? View.GONE : View.VISIBLE);
        pauseButton.setVisibility(inputActive ? View.VISIBLE : View.GONE);
        recordButton.setVisibility(inputActive && recordingEnabled && !recordingAutomatic ? View.VISIBLE : View.GONE);

        // Enabled state
        zoomButton.setEnabled(location != null);
        backspaceButton.setEnabled(numPoints > 0);
        clearButton.setEnabled(!inputActive && numPoints > 0);
        settingsView.findViewById(R.id.manual_mode).setEnabled(location != null);
        settingsView.findViewById(R.id.automatic_mode).setEnabled(location != null);

        if (intentReadOnly) {
            playButton.setEnabled(false);
            backspaceButton.setEnabled(false);
            clearButton.setEnabled(false);
        }
        // Settings dialog

        // GPS status
        boolean usingThreshold = isAccuracyThresholdActive();
        boolean acceptable = location != null && isLocationAcceptable(location);
        int seconds = INTERVAL_OPTIONS[intervalIndex];
        int minutes = seconds / 60;
        int meters = ACCURACY_THRESHOLD_OPTIONS[accuracyThresholdIndex];
        locationStatus.setText(
            location == null ? getString(org.odk.collect.strings.R.string.location_status_searching)
                : !usingThreshold ? getString(org.odk.collect.strings.R.string.location_status_accuracy, location.sd)
                : acceptable ? getString(org.odk.collect.strings.R.string.location_status_acceptable, location.sd)
                : getString(org.odk.collect.strings.R.string.location_status_unacceptable, location.sd)
        );
        locationStatus.setBackgroundColor(getResources().getColor(
            location == null ? R.color.locationStatusSearching
                : acceptable ? R.color.locationStatusAcceptable
                : R.color.locationStatusUnacceptable
        ));
        collectionStatus.setText(
            !inputActive ? getString(org.odk.collect.strings.R.string.collection_status_paused, numPoints)
                : !recordingEnabled ? getString(org.odk.collect.strings.R.string.collection_status_placement, numPoints)
                : !recordingAutomatic ? getString(org.odk.collect.strings.R.string.collection_status_manual, numPoints)
                : !usingThreshold ? (
                    minutes > 0 ?
                        getString(org.odk.collect.strings.R.string.collection_status_auto_minutes, numPoints, minutes) :
                        getString(org.odk.collect.strings.R.string.collection_status_auto_seconds, numPoints, seconds)
                )
                : (
                    minutes > 0 ?
                        getString(org.odk.collect.strings.R.string.collection_status_auto_minutes_accuracy, numPoints, minutes, meters) :
                        getString(org.odk.collect.strings.R.string.collection_status_auto_seconds_accuracy, numPoints, seconds, meters)
                )
        );
    }

    private void showClearDialog() {
        if (!map.getPolyPoints(featureId).isEmpty()) {
            new AlertDialog.Builder(this)
                .setMessage(org.odk.collect.strings.R.string.geo_clear_warning)
                .setPositiveButton(org.odk.collect.strings.R.string.clear, (dialog, id) -> clear())
                .setNegativeButton(org.odk.collect.strings.R.string.cancel, null)
                .show();
        }
    }

    private void showBackDialog() {
        new AlertDialog.Builder(this)
            .setMessage(getString(org.odk.collect.strings.R.string.geo_exit_warning))
            .setPositiveButton(org.odk.collect.strings.R.string.discard, (dialog, id) -> finish())
            .setNegativeButton(org.odk.collect.strings.R.string.cancel, null)
            .show();

    }

    @VisibleForTesting public MapFragment getMapFragment() {
        return map;
    }

    private String getMarkersAsText(List<MapPoint> points) {
        StringBuilder out = new StringBuilder("");

        Collection<Integer> indexes = markers.keySet();
        List<Integer> list = new ArrayList<>(indexes);
        java.util.Collections.sort(list);

        Geocoder geocoder = new Geocoder(this, Locale.getDefault());

        for(int index : list) {
            MapPoint point = points.get(index);
            CompoundMarker cm = markers.get(index);

            String location = String.format(Locale.US, "%s %s %s %s;",
                    point.lat, point.lon,
                    point.alt, (float) point.sd);

            out.append("#marker:")
                    .append(location)
                    .append(":index=")
                    .append(index)
                    .append(";type=")
                    .append(cm.type);
            try {
                String address = getAddress(geocoder, point.lat, point.lon);
                if(address.length() > 0) {
                    String label = getMarkerLabel(index);
                    Collect.getInstance().putCompoundAddress(label, address);
                }
            } catch (Exception e) {
                Timber.e(e);
            }
        }
        return out.toString();
    }

    private String getAddress(Geocoder geocoder, Double lat, Double lon) throws Exception{
        StringBuilder sAddress = new StringBuilder("");
        List<Address> addresses = geocoder.getFromLocation(lat, lon, 1);

        if (addresses != null) {
            Address a1 = addresses.get(0);

            for (int i = 0; i <= a1.getMaxAddressLineIndex(); i++) {
                if(i > 0) {
                    sAddress.append(", ");
                }
                sAddress.append(a1.getAddressLine(i));
            }
        }
        return sAddress.toString();
    }

    private String getMarkerTypeName(String type) {
        String name = type;
        MarkerType mt = markerTypes.get(type);
        if(mt != null) {
            name = mt.name;
        }
        return name;
    }

    private String getMarkerTypeLabel(String type) {
        String label = "";
        MarkerType mt = markerTypes.get(type);
        if(mt != null) {
            label = mt.label;
        }
        return label;
    }

    /*
     *  Append an index number to the label
     *  If marker is the third one encountered of the same type starting from the first marker
     *   then append "3" to the label and so on.
     */
    private String getMarkerLabel(int markerIdx) {
        String label = "";
        CompoundMarker marker = markers.get(markerIdx);

        if(marker != null) {
            Collection<Integer> indexes = markers.keySet();
            List<Integer> list = new ArrayList<>(indexes);
            java.util.Collections.sort(list);

            int labelIndex = 1;
            for (int index : list) {
                if (index == markerIdx) {
                    return marker.label + labelIndex;
                }
                CompoundMarker cm = markers.get(index);
                if (cm != null && cm.type.equals(marker.type)) {
                    labelIndex++;
                }

            }
        }
        return label;
    }

    private ArrayList<CompoundMarker> getMarkerArray() {
        return new ArrayList(markers.values());
    }

    private HashMap<Integer, CompoundMarker> getMarkerHashMap(List<CompoundMarker> markerArray) {
        HashMap<Integer, CompoundMarker> markers = new HashMap<>();
        if(markerArray != null) {
            for(CompoundMarker cm : markerArray) {
                markers.put(cm.index, cm);
            }
        }
        return markers;
    }

    private HashMap<String, MarkerType> getMarkerTypes(String appearance) {
        HashMap<String, MarkerType> markerTypes = new HashMap<>();
        if(appearance != null) {
            String components[] = appearance.split(" ");
            for (int i = 0; i < components.length; i++) {
                if(components[i].startsWith("marker:")) {
                    String componentParts [] = components[i].split(":");
                    if(componentParts.length >= 4) {
                        String type = componentParts[1];
                        String name = componentParts[2];
                        String label = componentParts[3];
                        markerTypes.put(type, new MarkerType(type, name, label));
                    }
                }
            }
        }
        return markerTypes;
    }
}
