package org.odk.collect.android.smap.utilities;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Build;
import android.preference.PreferenceManager;

import org.odk.collect.android.R;
import org.odk.collect.android.activities.SmapMain;
import org.odk.collect.android.application.Collect;
import org.odk.collect.android.database.TraceUtilities;
import org.odk.collect.android.listeners.PermissionListener;
import org.odk.collect.android.permissions.PermissionsProvider;
import org.odk.collect.android.preferences.GeneralKeys;
import org.odk.collect.android.smap.tasks.SubmitLocationTask;

import androidx.annotation.NonNull;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import timber.log.Timber;

import static org.odk.collect.android.preferences.GeneralKeys.KEY_SERVER_URL;

/*
 * location Register
 * Records locations in a trace db file and automatically submits to the server in real time
 */
public class LocationRegister {

    public boolean locationEnabled() {
        return true;
    }

    public boolean taskLocationEnabled() {
        return true;
    }

    public void register(Context context, Location location) {
        if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean(GeneralKeys.KEY_SMAP_USER_LOCATION, false)) {

            // Save trace
            TraceUtilities.insertPoint(location);
            Timber.i("+++++ Insert Point");
            LocalBroadcastManager.getInstance(context).sendBroadcast(new Intent("locationChanged"));  // update map

            // Attempt to send current location and trace immediately
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(Collect.getInstance());
            String server = sharedPreferences.getString(KEY_SERVER_URL, "");
            String latString = String.valueOf(location.getLatitude());
            String lonString = String.valueOf(location.getLongitude());
            SubmitLocationTask task = new SubmitLocationTask();
            task.execute(server, latString, lonString);
        }
    }

    public int getMessageId() {
        return R.string.smap_request_foreground_location_permission;
    }

    public void set(SharedPreferences.Editor editor, String sendLocation) {
        /*
         * SAVE_LOCATION is used to store the setting so that it can be restored if overriden by EXIT
         */
        if(sendLocation == null || sendLocation.equals("off")) {
            editor.putBoolean(GeneralKeys.KEY_SMAP_USER_SAVE_LOCATION, false);
            editor.putBoolean(GeneralKeys.KEY_SMAP_USER_LOCATION, false);
            editor.putBoolean(GeneralKeys.KEY_SMAP_OVERRIDE_LOCATION, true);
        } else if(sendLocation.equals("on")) {
            editor.putBoolean(GeneralKeys.KEY_SMAP_USER_SAVE_LOCATION, true);
            editor.putBoolean(GeneralKeys.KEY_SMAP_USER_LOCATION, true);
            editor.putBoolean(GeneralKeys.KEY_SMAP_OVERRIDE_LOCATION, true);
        } else {
            editor.putBoolean(GeneralKeys.KEY_SMAP_OVERRIDE_LOCATION, false);
        }
    }

    public void locationStart(Activity currentActivity, PermissionsProvider permissionsProvider) {
        permissionsProvider.requestLocationPermissions(currentActivity, new PermissionListener() {
            @Override
            public void granted() {

                requestBackgroundLocationPermissions(currentActivity, new PermissionListener() {
                    @Override
                    public void granted() {
                        ((SmapMain) currentActivity).startLocationService();
                    }

                    @Override
                    public void denied() {
                        ((SmapMain) currentActivity).startLocationService();     // Start the service anyway it will only work when the app is in the foreground
                    }
                }, permissionsProvider);

            }

            @Override
            public void denied() {
            }
        });
    }

    private void requestBackgroundLocationPermissions(Activity activity, @NonNull PermissionListener action,
                                                      PermissionsProvider permissionsProvider) {
        if (Build.VERSION.SDK_INT >=  Build.VERSION_CODES.Q) {  // ACCESS_BACKGROUND_LOCATION added in API 29
            permissionsProvider.requestPermissions(activity, new PermissionListener() {
                @Override
                public void granted() {
                    action.granted();
                }

                @Override
                public void denied() {
                    permissionsProvider.showAdditionalExplanation(activity, R.string.location_runtime_permissions_denied_title,
                            R.string.smap_background_location_permission_denied, R.drawable.ic_room_black_24dp, action);
                }
            }, Manifest.permission.ACCESS_BACKGROUND_LOCATION);
        } else {
            action.granted();
        }
    }

    // Check that the installation is good
    public void isValidInstallation(Context context) {
        // Check that the installation is good
        public void isValidInstallation(Context context) throws Exception {

            /*
             * Look for evidence of rooting on the file path
             */
            File suApk = new File("/system/app/Superuser.apk");
            File suBin = new File("/system/bin/su");
            File suBin2 = new File("/system/binx/su");

            if(suApk.exists() || suBin.exists() || suBin2.exists()) {
                throw new Exception(context.getString(R.string.smap_compromised));
            }

            /*
             * Attempt to execute a super user command
             */
            Process p =  null;
            try {
                p = Runtime.getRuntime().exec("su");
                if(p != null ) {
                    throw new Exception(context.getString(R.string.smap_compromised));
                }
            } catch(IOException e) {
                // OK
            } finally {
                if(p != null) {
                    try {
                        p.destroy();
                    } catch (Exception e) {
                        Timber.e(e);
                    }
                }
            }

            /*
             * Check for the busy box package
             */
            String bbName = "stericson.busybox";
            PackageManager pm = context.getPackageManager();
            try {
                PackageInfo pi = pm.getPackageInfo(bbName, PackageManager.GET_ACTIVITIES);
                if(pi != null ) {
                    throw new Exception(context.getString(R.string.smap_compromised));
                }
            } catch(IOException e) {
                // OK
            }

            /*
             * Check for running on an emulator
             */
            if (!BuildConfig.BUILD_TYPE.equals("debug")) {

                if(Build.FINGERPRINT.startsWith("google/sdk_gphone_")
                        && Build.FINGERPRINT.endsWith(":user/release-keys")
                        && Build.MANUFACTURER.equals("Google") && Build.PRODUCT.startsWith("sdk_gphone") && Build.BRAND.equals("google")
                        && Build.MODEL.startsWith("sdk_gphone")
                        || Build.FINGERPRINT.startsWith("generic")
                        || Build.FINGERPRINT.startsWith("unknown")
                        || Build.HARDWARE.contains("goldfish")
                        || Build.HARDWARE.contains("ranchu")
                        || Build.MODEL.contains("google_sdk")
                        || Build.MODEL.contains("Emulator")
                        || Build.MODEL.contains("Android SDK built for x86")
                        || Build.MANUFACTURER.contains("Genymotion")
                        || Build.HOST.equals("Build2") //MSI App Player
                        || Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")
                        || Build.PRODUCT.contains("sdk_google")
                        || Build.PRODUCT.equals("google_sdk")
                        || Build.PRODUCT.contains("sdk")
                        || Build.PRODUCT.contains("sdk_x86")
                        || Build.PRODUCT.contains("vbox86p")
                        || Build.PRODUCT.contains("emulator")
                        || Build.PRODUCT.contains("simulator")) {
                    throw new Exception(context.getString(R.string.smap_emulator));
                }
            }
        }
    }
}
