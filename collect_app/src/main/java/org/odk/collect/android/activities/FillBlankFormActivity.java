/*
 * Copyright (C) 2009 University of Washington
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

import android.content.ContentUris;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.Loader;

import org.odk.collect.android.R;
import org.odk.collect.android.adapters.FormListAdapter;
import org.odk.collect.android.dao.FormsDao;
import org.odk.collect.android.formmanagement.BlankFormListMenuDelegate;
import org.odk.collect.android.formmanagement.BlankFormsListViewModel;
import org.odk.collect.android.injection.DaggerUtils;
import org.odk.collect.android.listeners.DiskSyncListener;
import org.odk.collect.android.listeners.PermissionListener;
import org.odk.collect.android.network.NetworkStateProvider;
import org.odk.collect.android.preferences.GeneralKeys;
import org.odk.collect.android.preferences.GeneralSharedPreferences;
import org.odk.collect.android.preferences.ServerAuthDialogFragment;
import org.odk.collect.android.provider.FormsProviderAPI.FormsColumns;
import org.odk.collect.android.storage.StorageInitializer;
import org.odk.collect.android.tasks.DiskSyncTask;
import org.odk.collect.android.utilities.ApplicationConstants;
import org.odk.collect.android.utilities.DialogUtils;
import org.odk.collect.android.utilities.MultiClickGuard;
import org.odk.collect.android.views.ObviousProgressBar;

import javax.inject.Inject;

import timber.log.Timber;

/**
 * Responsible for displaying all the valid forms in the forms directory. Stores the path to
 * selected form for use by {@link MainMenuActivity}.
 *
 * @author Yaw Anokwa (yanokwa@gmail.com)
 * @author Carl Hartung (carlhartung@gmail.com)
 */
public class FillBlankFormActivity extends FormListActivity implements
        DiskSyncListener, AdapterView.OnItemClickListener, LoaderManager.LoaderCallbacks<Cursor> {

    private static final String FORM_CHOOSER_LIST_SORTING_ORDER = "formChooserListSortingOrder";
    private static final boolean EXIT = true;

    private DiskSyncTask diskSyncTask;

    @Inject
    NetworkStateProvider networkStateProvider;

    @Inject
    BlankFormsListViewModel.Factory blankFormsListViewModelFactory;

    BlankFormListMenuDelegate menuDelegate;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.form_chooser_list);
        DaggerUtils.getComponent(this).inject(this);

        setTitle(getString(org.odk.collect.strings.R.string.enter_data));

        BlankFormsListViewModel blankFormsListViewModel = new ViewModelProvider(this, blankFormsListViewModelFactory).get(BlankFormsListViewModel.class);
        blankFormsListViewModel.isSyncing().observe(this, syncing -> {
            ObviousProgressBar progressBar = findViewById(R.id.progressBar);

            if (syncing) {
                progressBar.show();
            } else {
                progressBar.hide(View.GONE);
            }
        });

        blankFormsListViewModel.isAuthenticationRequired().observe(this, authenticationRequired -> {
            if (authenticationRequired) {
                DialogUtils.showIfNotShowing(ServerAuthDialogFragment.class, getSupportFragmentManager());
            } else {
                DialogUtils.dismissDialog(ServerAuthDialogFragment.class, getSupportFragmentManager());
            }
        });

        menuDelegate = new BlankFormListMenuDelegate(this, blankFormsListViewModel, networkStateProvider);

        permissionsProvider.requestStoragePermissions(this, new PermissionListener() {
            @Override
            public void granted() {
                // must be at the beginning of any activity that can be called from an external intent
                try {
                    new StorageInitializer().createOdkDirsOnStorage();
                    init();
                } catch (RuntimeException e) {
                    createErrorDialog(e.getMessage(), EXIT);
                    return;
                }
            }

            @Override
            public void denied() {
                // The activity has to finish because ODK Collect cannot function without these permissions.
                finishAndRemoveTask();
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        boolean result = super.onCreateOptionsMenu(menu);
        menuDelegate.onCreateOptionsMenu(getMenuInflater(), menu);
        return result;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean result = super.onPrepareOptionsMenu(menu);
        menuDelegate.onPrepareOptionsMenu(menu);
        return result;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (!MultiClickGuard.allowClick(getClass().getName())) {
            return true;
        }

        if (super.onOptionsItemSelected(item)) {
            return true;
        } else {
            return menuDelegate.onOptionsItemSelected(item);
        }
    }

    private void init() {
        setupAdapter();

        // DiskSyncTask checks the disk for any forms not already in the content provider
        // that is, put here by dragging and dropping onto the SDCard
        diskSyncTask = (DiskSyncTask) getLastCustomNonConfigurationInstance();
        if (diskSyncTask == null) {
            Timber.i("Starting new disk sync task");
            diskSyncTask = new DiskSyncTask();
            diskSyncTask.setDiskSyncListener(this);
            diskSyncTask.execute((Void[]) null);
        }
        sortingOptions = new int[]{
                org.odk.collect.strings.R.string.sort_by_name_asc, org.odk.collect.strings.R.string.sort_by_name_desc,
                org.odk.collect.strings.R.string.sort_by_date_asc, org.odk.collect.strings.R.string.sort_by_date_desc,
        };

        setupAdapter();
        getSupportLoaderManager().initLoader(LOADER_ID, null, this);
    }

    @Override
    public Object onRetainCustomNonConfigurationInstance() {
        // pass the thread on restart
        return diskSyncTask;
    }

    /**
     * Stores the path of selected form and finishes.
     */
    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (MultiClickGuard.allowClick(getClass().getName())) {
            // get uri to form
            long idFormsTable = listView.getAdapter().getItemId(position);
            Uri formUri = ContentUris.withAppendedId(FormsColumns.CONTENT_URI, idFormsTable);

            String action = getIntent().getAction();
            if (Intent.ACTION_PICK.equals(action)) {
                // caller is waiting on a picked form
                setResult(RESULT_OK, new Intent().setData(formUri));
            } else {
                // caller wants to view/edit a form, so launch formentryactivity
                //Intent intent = new Intent(Intent.ACTION_EDIT, formUri);  // smap
                Intent intent = new Intent(this, org.odk.collect.android.activities.FormEntryActivity.class);
                intent.setData(formUri);   // smap
                intent.putExtra(ApplicationConstants.BundleKeys.FORM_MODE, ApplicationConstants.FormModes.EDIT_SAVED);
                startActivity(intent);
            }

            finish();
        }
    }

    public void onMapButtonClick(AdapterView<?> parent, View view, int position, long id) {
        final Uri formUri = ContentUris.withAppendedId(FormsColumns.CONTENT_URI, id);
        final Intent intent = new Intent(Intent.ACTION_EDIT, formUri, this, FormMapActivity.class);
        permissionsProvider.requestLocationPermissions(this, new PermissionListener() {
            @Override
            public void granted() {
                startActivity(intent);
            }

            @Override
            public void denied() {
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (diskSyncTask != null) {
            diskSyncTask.setDiskSyncListener(this);
            if (diskSyncTask.getStatus() == AsyncTask.Status.FINISHED) {
                syncComplete(diskSyncTask.getStatusMessage());
            }
        }
    }

    @Override
    protected void onPause() {
        if (diskSyncTask != null) {
            diskSyncTask.setDiskSyncListener(null);
        }
        super.onPause();
    }

    /**
     * Called by DiskSyncTask when the task is finished
     */
    @Override
    public void syncComplete(@NonNull String result) {
        Timber.i("Disk scan complete");
        hideProgressBarAndAllow();
        showSnackbar(result);
    }

    private void setupAdapter() {
        String[] columnNames = {
                FormsColumns.DISPLAY_NAME,
                FormsColumns.JR_VERSION,
                hideOldFormVersions() ? FormsColumns.MAX_DATE : FormsColumns.DATE
                // FormsColumns.GEOMETRY_XPATH  // smap
        };
        int[] viewIds = {
                R.id.form_title,
                R.id.form_subtitle,
                R.id.form_subtitle2,
                //R.id.map_view    // smap
        };

        listAdapter = new FormListAdapter(
                listView, FormsColumns.JR_VERSION, this, R.layout.form_chooser_list_item,
                this::onMapButtonClick, columnNames, viewIds);
        listView.setAdapter(listAdapter);
    }

    @Override
    protected String getSortingOrderKey() {
        return FORM_CHOOSER_LIST_SORTING_ORDER;
    }

    @Override
    protected void updateAdapter() {
        getSupportLoaderManager().restartLoader(LOADER_ID, null, this);
    }

    /**
     * Creates a dialog with the given message. Will exit the activity when the user preses "ok" if
     * shouldExit is set to true.
     */
    private void createErrorDialog(String errorMsg, final boolean shouldExit) {

        AlertDialog alertDialog = new AlertDialog.Builder(this).create();
        alertDialog.setIcon(android.R.drawable.ic_dialog_info);
        alertDialog.setMessage(errorMsg);
        DialogInterface.OnClickListener errorListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int i) {
                switch (i) {
                    case DialogInterface.BUTTON_POSITIVE:
                        if (shouldExit) {
                            finish();
                        }
                        break;
                }
            }
        };
        alertDialog.setCancelable(false);
        alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(org.odk.collect.strings.R.string.ok), errorListener);
        alertDialog.show();
    }

    @NonNull
    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        showProgressBar();

        return new FormsDao().getFormsCursorLoader(getFilterText(), getSortingOrder(), hideOldFormVersions());
    }

    @Override
    public void onLoadFinished(@NonNull Loader<Cursor> loader, Cursor cursor) {
        hideProgressBarIfAllowed();
        listAdapter.swapCursor(cursor);
    }

    @Override
    public void onLoaderReset(@NonNull Loader loader) {
        listAdapter.swapCursor(null);
    }

    private boolean hideOldFormVersions() {
        return GeneralSharedPreferences.getInstance().getBoolean(GeneralKeys.KEY_HIDE_OLD_FORM_VERSIONS, false);
    }
}
