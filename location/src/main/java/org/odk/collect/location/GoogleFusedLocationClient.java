package org.odk.collect.location;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.location.FusedLocationProviderApi;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import timber.log.Timber;

/**
 * An implementation of {@link LocationClient} that uses Google Play Services to retrieve the
 * User's location.
 * <p>
 * Should be used whenever there Google Play Services is present. In general, use
 * {@link LocationClientProvider} to retrieve a configured {@link LocationClient}.
 */
@SuppressLint("MissingPermission") // Permission checks for location services handled in components that use this class
public class GoogleFusedLocationClient
        extends BaseLocationClient
        implements LocationListener {

    /**
     * The default requested time between location updates, in milliseconds.
     */
    private static final long DEFAULT_UPDATE_INTERVAL = 5000;

    /**
     * The default maximum rate at which location updates can arrive (other updates will be throttled),
     * in milliseconds.
     */
    private static final long DEFAULT_FASTEST_UPDATE_INTERVAL = 2500;

    @NonNull
    private final FusedLocationProviderClientWrapper fusedLocationProviderClientWrapper;

    private final LocationManager locationManager;
    @Nullable
    private LocationListener locationListener;

    private long updateInterval = DEFAULT_UPDATE_INTERVAL;
    private long fastestUpdateInterval = DEFAULT_FASTEST_UPDATE_INTERVAL;
    private boolean retainMockAccuracy;

    /**
     * Constructs a new GoogleFusedLocationClient with the provided Context.
     * <p>
     * This Constructor should be used normally.
     *
     * @param application The application. Used as the Context for building the GoogleApiClient because
     *                    it doesn't release context.
     */
    public GoogleFusedLocationClient(@NonNull Application application) {
        this(application, (LocationManager) application.getSystemService(Context.LOCATION_SERVICE));
    }

    /**
     * Constructs a new AndroidLocationClient
     */
    public GoogleFusedLocationClient(Application application,
                                     @NonNull LocationManager locationManager) {
        super(locationManager);
        this.locationManager = locationManager;
        this.fusedLocationProviderClientWrapper = new FusedLocationProviderClientWrapper(LocationServices.getFusedLocationProviderClient(application));
    }

    // LocationClient:

    @Override
    public void start() {
        start(getListener());
    }

    public void start(LocationClientListener listener) {
        fusedLocationProviderClientWrapper.start(this);
        setListener(listener);
        listener.onClientStart();
    }


    public void stop() {
        stopLocationUpdates();
        getListener().onClientStop();
        setListener(null);
    }

    public void requestLocationUpdates(@NonNull LocationListener locationListener) {
        if (!isMonitoringLocation()) {
            fusedLocationProviderClientWrapper.requestLocationUpdates(createLocationRequest());
        }

        this.locationListener = locationListener;
    }

    public void stopLocationUpdates() {
        if (isMonitoringLocation()) {
            fusedLocationProviderClientWrapper.removeLocationUpdates();
            locationListener = null;
        }
    }

    @Override
    public void setRetainMockAccuracy(boolean retainMockAccuracy) {
        this.retainMockAccuracy = retainMockAccuracy;
    }

    @Override
    public Location getLastLocation() {
        return LocationUtils.sanitizeAccuracy(fusedLocationProviderClientWrapper.getLastLocation(), retainMockAccuracy);
    }

    @Override
    public boolean isMonitoringLocation() {
        return locationListener != null;
    }

    @Override
    public boolean canSetUpdateIntervals() {
        return true;
    }

    @Override
    public void setUpdateIntervals(long updateInterval, long fastestUpdateInterval) {
        Timber.i("GoogleFusedLocationClient setting update intervals: %d, %d", updateInterval, fastestUpdateInterval);

        this.updateInterval = updateInterval;
        this.fastestUpdateInterval = fastestUpdateInterval;
    }

    // GoogleFusedLocationClient:

    private LocationRequest createLocationRequest() {
        LocationRequest locationRequest = new LocationRequest();
        locationRequest.setPriority(getPriority().getValue());

        locationRequest.setInterval(updateInterval);
        locationRequest.setFastestInterval(fastestUpdateInterval);

        return locationRequest;
    }

    // ConnectionCallbacks:

    // LocationListener:

    @Override
    public void onLocationChanged(Location location) {
        Timber.i("Location changed: %s", location.toString());

        if (locationListener != null) {
            locationListener.onLocationChanged(LocationUtils.sanitizeAccuracy(location, retainMockAccuracy));
        }
    }

}
