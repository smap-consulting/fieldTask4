package org.odk.collect.android.geo.mapboxsdk;

import android.content.Context;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

/*
 * This class was used for Mapbox but has been disabled as this version of Mapbox does not meet google page size requirements
 */
public class MapFragment extends Fragment {

    private OnMapViewReadyCallback mapViewReadyCallback;

    /**
     * Creates a default MapFragment instance
     *
     * @return MapFragment instantiated
     */
    public static MapFragment newInstance() {
        return new MapFragment();
    }

    /**
     * Called when this fragment is inflated, parses XML tag attributes.
     *
     * @param context            The context inflating this fragment.
     * @param attrs              The XML tag attributes.
     * @param savedInstanceState The saved instance state for the map fragment.
     */
    @Override
    public void onInflate(@NonNull Context context, AttributeSet attrs, Bundle savedInstanceState) {
        super.onInflate(context, attrs, savedInstanceState);
    }

    /**
     * Called when the context attaches to this fragment.
     *
     * @param context the context attaching
     */
    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof OnMapViewReadyCallback) {
            mapViewReadyCallback = (OnMapViewReadyCallback) context;
        }
    }

    /**

    /**
     * Called when the fragment view hierarchy is created.
     *
     * @param view               The content view of the fragment
     * @param savedInstanceState The saved instance state of the fragment
     */
    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
    }

    /**
     * Called when the fragment is visible for the users.
     */
    @Override
    public void onStart() {
        super.onStart();
    }

    /**
     * Called when the fragment is ready to be interacted with.
     */
    @Override
    public void onResume() {
        super.onResume();
    }

    /**
     * Called when the fragment is pausing.
     */
    @Override
    public void onPause() {
        super.onPause();
    }

    /**
     * Called when the fragment state needs to be saved.
     *
     * @param outState The saved state
     */
    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    /**
     * Called when the fragment is no longer visible for the user.
     */
    @Override
    public void onStop() {
        super.onStop();
    }

    /**
     * Called when the fragment receives onLowMemory call from the hosting Activity.
     */
    @Override
    public void onLowMemory() {
        super.onLowMemory();

    }

    /**
     * Called when the fragment is view hiearchy is being destroyed.
     */
    @Override
    public void onDestroyView() {
        super.onDestroyView();
    }

    /**
     * Called when the fragment is destroyed.
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
    }


    /**
     * Callback to be invoked when the map fragment has inflated its MapView.
     * <p>
     * To use this interface the context hosting the fragment must implement this interface.
     * That instance will be set as part of Fragment#onAttach(Context context).
     * </p>
     */
    public interface OnMapViewReadyCallback {

    }
}

