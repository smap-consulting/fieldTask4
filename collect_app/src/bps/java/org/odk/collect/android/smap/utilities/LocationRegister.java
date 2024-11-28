package org.odk.collect.android.smap.utilities;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;

import org.odk.collect.android.R;
import org.odk.collect.android.listeners.PermissionListener;

import org.odk.collect.android.activities.SmapMain;
import org.odk.collect.android.permissions.PermissionsProvider;
import org.odk.collect.android.preferences.GeneralKeys;

public class LocationRegister {

    public boolean locationEnabled() {
        return false;
    }

    public boolean taskLocationEnabled() {
        return false;
    }

    public void register(Context context, Location location) {
        // Do nothing
    }

    public int getMessageId() {
        return R.string.smap_request_foreground_location_permission;
    }
    /*
     * Disable permissions concerned with background location
     */
    public void set(SharedPreferences.Editor editor, String sendLocation) {
        editor.putBoolean(GeneralKeys.KEY_SMAP_USER_LOCATION, false);
        editor.putBoolean(GeneralKeys.KEY_SMAP_OVERRIDE_LOCATION, true);
    }

    // Start foreground location recording
    public void locationStart(Activity currentActivity, PermissionsProvider permissionsProvider) {
        permissionsProvider.requestLocationPermissions(currentActivity, new PermissionListener() {
            @Override
            public void granted() {
                ((SmapMain) currentActivity).startLocationService();
            }

            @Override
            public void denied() {
            }
        });
    }

    // Check that the installation is not on a rooted device
    public void isValidInstallation(Context context) {
    }

    // Return true if the default for a new installation is to logon with a token rather than a password
    public static boolean defaultForceToken() {
        return false;
    }
}
