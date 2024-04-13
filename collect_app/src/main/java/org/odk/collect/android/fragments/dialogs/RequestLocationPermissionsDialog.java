/*
 * Copyright 2024 Smap Consulting
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.odk.collect.android.fragments.dialogs;

import static org.odk.collect.android.injection.DaggerUtils.getComponent;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;

import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import org.jetbrains.annotations.NotNull;
import org.odk.collect.android.R;
import org.odk.collect.android.activities.SmapMain;
import org.odk.collect.android.permissions.PermissionsProvider;
import org.odk.collect.android.preferences.GeneralKeys;
import org.odk.collect.android.preferences.GeneralSharedPreferences;
import org.odk.collect.android.smap.utilities.LocationRegister;

import javax.inject.Inject;

import timber.log.Timber;

public class RequestLocationPermissionsDialog extends DialogFragment {

    @Inject
    PermissionsProvider permissionsProvider;

    public RequestLocationPermissionsDialog() {
    }

    /*
    We keep this just in case to avoid problems if someone tries to show a dialog after
    the activity’s state have been saved. Basically it shouldn't take place since we should control
    the activity state if we want to show a dialog (especially after long tasks).
     */
    @Override
    public void show(FragmentManager manager, String tag) {
        try {
            manager
                    .beginTransaction()
                    .add(this, tag)
                    .commit();
        } catch (IllegalStateException e) {
            Timber.w(e);
        }
    }

    @Override
    public void onAttach(@NotNull Context context) {
        super.onAttach(context);
        getComponent(context).inject(this);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        setCancelable(false);

        Activity currentActivity = ((SmapMain) getActivity());
        LocationRegister lr = new LocationRegister();

        android.app.AlertDialog.Builder builder = new AlertDialog.Builder(currentActivity);
        builder.setMessage(lr.getMessageId())
                .setTitle(R.string.location_runtime_permissions_denied_title)
                .setPositiveButton(R.string.smap_accept2, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        lr.locationStart(currentActivity, permissionsProvider);
                        GeneralSharedPreferences.getInstance().save(GeneralKeys.KEY_SMAP_REQUEST_LOCATION_DONE, "accept");
                    }
                }).setNegativeButton(R.string.smap_deny, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        GeneralSharedPreferences.getInstance().save(GeneralKeys.KEY_SMAP_REQUEST_LOCATION_DONE, "denied");
                    }
                });

        return builder.create();
    }
}
