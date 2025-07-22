package org.odk.collect.location;

import android.annotation.SuppressLint;
import android.os.Looper;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;

@SuppressLint("MissingPermission")
public class FusedLocationProviderClientWrapper {

    private FusedLocationProviderClient fusedLocationProviderClient;
    private LocationListener listener;
    private android.location.Location lastLocation = null;

    private LocationCallback locationCallback;
    public FusedLocationProviderClientWrapper(FusedLocationProviderClient fusedLocationProviderClient) {

        this.fusedLocationProviderClient = fusedLocationProviderClient;
        this.locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                for(android.location.Location location : locationResult.getLocations()) {
                    listener.onLocationChanged(location);
                }
            }
        };
    }

    public void start(LocationListener listener) {
        this.listener = listener;
        fusedLocationProviderClient
                .getLastLocation()
                .addOnSuccessListener(location -> {lastLocation = location;});
    }

    public void requestLocationUpdates(LocationRequest locationRequest) {
        fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
    }

    public void removeLocationUpdates() {
        fusedLocationProviderClient.removeLocationUpdates(locationCallback);
    }

    public android.location.Location getLastLocation() {
        return lastLocation;
    }
}
