package org.odk.collect.android.fragments;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.odk.collect.android.R;
import org.odk.collect.android.injection.DaggerUtils;
import org.odk.collect.android.network.NetworkStateProvider;
import org.odk.collect.android.preferences.PreferencesProvider;

import javax.inject.Inject;

import static org.odk.collect.android.preferences.MetaKeys.KEY_MAPBOX_INITIALIZED;

/*
 * This class was used for Mapbox but has been disabled as this version of Mapbox does not meet google page size requirements
 */
public class MapBoxInitializationFragment extends Fragment {

    @Inject
    PreferencesProvider preferencesProvider;

    @Inject
    NetworkStateProvider connectivityProvider;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DaggerUtils.getComponent(requireActivity()).inject(this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.mapbox_fragment_layout, container, false);
        initMapBox(rootView);

        return rootView;
    }

    @Override
    public void onStart() {
        super.onStart();

    }

    @Override
    public void onResume() {
        super.onResume();

    }

    @Override
    public void onPause() {
        super.onPause();

    }

    @Override
    public void onStop() {
        super.onStop();

    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();

    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

    }

    private void initMapBox(View rootView) {

        SharedPreferences metaSharedPreferences = preferencesProvider.getMetaSharedPreferences();
        if (!metaSharedPreferences.getBoolean(KEY_MAPBOX_INITIALIZED, false) && connectivityProvider.isDeviceOnline()) {
            // This "one weird trick" lets us initialize MapBox at app start when the internet is
            // most likely to be available. This is annoyingly needed for offline tiles to work.
        }
    }
}
