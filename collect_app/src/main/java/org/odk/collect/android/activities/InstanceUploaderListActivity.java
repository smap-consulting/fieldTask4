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

import android.content.Intent;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnLongClickListener;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.LiveData;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.Loader;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import org.odk.collect.android.R;
import org.odk.collect.android.adapters.InstanceUploaderAdapter;
import org.odk.collect.android.analytics.Analytics;
import org.odk.collect.android.backgroundwork.SchedulerFormUpdateAndSubmitManager;
import org.odk.collect.android.dao.InstancesDao;
import org.odk.collect.android.gdrive.GoogleSheetsUploaderActivity;
import org.odk.collect.android.injection.DaggerUtils;
import org.odk.collect.android.listeners.DiskSyncListener;
import org.odk.collect.android.listeners.PermissionListener;
import org.odk.collect.android.network.NetworkStateProvider;
import org.odk.collect.android.preferences.GeneralSharedPreferences;
import org.odk.collect.android.preferences.PreferencesActivity;
import org.odk.collect.android.tasks.InstanceSyncTask;
import org.odk.collect.android.utilities.MultiClickGuard;
import org.odk.collect.android.utilities.PlayServicesChecker;
import org.odk.collect.android.utilities.ToastUtils;

import java.util.List;

import javax.inject.Inject;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import timber.log.Timber;

import static org.odk.collect.android.preferences.GeneralKeys.KEY_PROTOCOL;

/**
 * Responsible for displaying all the valid forms in the forms directory. Stores
 * the path to selected form for use by {@link MainMenuActivity}.
 *
 * @author Carl Hartung (carlhartung@gmail.com)
 * @author Yaw Anokwa (yanokwa@gmail.com)
 */

public class InstanceUploaderListActivity extends InstanceListActivity implements
        OnLongClickListener, DiskSyncListener, AdapterView.OnItemClickListener, LoaderManager.LoaderCallbacks<Cursor> {
    private static final String SHOW_ALL_MODE = "showAllMode";
    private static final String SHOW_INCOMPLETE = "showIncomplete";     // smap
    private static final String INSTANCE_UPLOADER_LIST_SORTING_ORDER = "instanceUploaderListSortingOrder";

    private static final int INSTANCE_UPLOADER = 0;

    @BindView(R.id.upload_button)
    Button uploadButton;

    @BindView(R.id.toggle_button)
    Button toggleSelsButton;

    private InstancesDao instancesDao;

    private InstanceSyncTask instanceSyncTask;

    private boolean showAllMode;
    private boolean showIncomplete;     // smap

    // Default to true so the send button is disabled until the worker status is updated by the
    // observer
    private boolean autoSendOngoing = true;

    //@Inject
    //Analytics analytics;

    @Inject
    NetworkStateProvider connectivityProvider;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Timber.i("onCreate");

        DaggerUtils.getComponent(this).inject(this);

        // set title
        setTitle(getString(org.odk.collect.strings.R.string.send_data));
        setContentView(R.layout.instance_uploader_list);

        ButterKnife.bind(this);

        if (savedInstanceState != null) {
            showAllMode = savedInstanceState.getBoolean(SHOW_ALL_MODE);
            showIncomplete = savedInstanceState.getBoolean(SHOW_INCOMPLETE);        // smap
        }

        permissionsProvider.requestStoragePermissions(this, new PermissionListener() {
            @Override
            public void granted() {
                init();
            }

            @Override
            public void denied() {
                // The activity has to finish because ODK Collect cannot function without these permissions.
                finishAndRemoveTask();
            }
        });
    }

    @OnClick({R.id.upload_button})
    public void onUploadButtonsClicked(Button button) {
        if (!connectivityProvider.isDeviceOnline()) {
            ToastUtils.showShortToast(org.odk.collect.strings.R.string.no_connection);
            return;
        }

        if (autoSendOngoing) {
            ToastUtils.showShortToast(org.odk.collect.strings.R.string.send_in_progress);
            return;
        }

        int checkedItemCount = getCheckedCount();

        if (checkedItemCount > 0) {
            // items selected
            uploadSelectedFiles();
            setAllToCheckedState(listView, false);
            toggleButtonLabel(findViewById(R.id.toggle_button), listView);
            uploadButton.setEnabled(false);
        } else {
            // no items selected
            ToastUtils.showLongToast(org.odk.collect.strings.R.string.noselect_error);
        }
    }

    void init() {
        uploadButton.setText(org.odk.collect.strings.R.string.send_selected_data);
        instancesDao = new InstancesDao();

        toggleSelsButton.setLongClickable(true);
        toggleSelsButton.setOnClickListener(v -> {
            ListView lv = listView;
            boolean allChecked = toggleChecked(lv);
            toggleButtonLabel(toggleSelsButton, lv);
            uploadButton.setEnabled(allChecked);
            if (allChecked) {
                for (int i = 0; i < lv.getCount(); i++) {
                    selectedInstances.add(lv.getItemIdAtPosition(i));
                }
            } else {
                selectedInstances.clear();
            }
        });
        toggleSelsButton.setOnLongClickListener(this);

        setupAdapter();

        listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        listView.setItemsCanFocus(false);
        listView.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            uploadButton.setEnabled(areCheckedItems());
        });

        instanceSyncTask = new InstanceSyncTask();
        instanceSyncTask.setDiskSyncListener(this);
        instanceSyncTask.execute();

        sortingOptions = new int[] {
                org.odk.collect.strings.R.string.sort_by_name_asc, org.odk.collect.strings.R.string.sort_by_name_desc,
                org.odk.collect.strings.R.string.sort_by_date_asc, org.odk.collect.strings.R.string.sort_by_date_desc
        };

        getSupportLoaderManager().initLoader(LOADER_ID, null, this);

        // Start observer that sets autoSendOngoing field based on AutoSendWorker status
        updateAutoSendStatus();
    }

    /**
     * Updates whether an auto-send job is ongoing.
     */
    private void updateAutoSendStatus() {
        LiveData<List<WorkInfo>> statuses = WorkManager.getInstance().getWorkInfosForUniqueWorkLiveData(SchedulerFormUpdateAndSubmitManager.AUTO_SEND_TAG);
        statuses.observe(this, workStatuses -> {
            if (workStatuses != null) {
                for (WorkInfo status : workStatuses) {
                    if (status.getState().equals(WorkInfo.State.RUNNING)) {
                        autoSendOngoing = true;
                        return;
                    }
                }
                autoSendOngoing = false;
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (instanceSyncTask != null) {
            instanceSyncTask.setDiskSyncListener(this);
            if (instanceSyncTask.getStatus() == AsyncTask.Status.FINISHED) {
                syncComplete(instanceSyncTask.getStatusMessage());
            }

        }
        uploadButton.setText(org.odk.collect.strings.R.string.send_selected_data);
    }

    @Override
    protected void onPause() {
        if (instanceSyncTask != null) {
            instanceSyncTask.setDiskSyncListener(null);
        }
        super.onPause();
    }

    @Override
    public void syncComplete(@NonNull String result) {
        Timber.i("Disk scan complete");
        hideProgressBarAndAllow();
        showSnackbar(result);
    }

    private void uploadSelectedFiles() {
        long[] instanceIds = listView.getCheckedItemIds();

        String server = (String) GeneralSharedPreferences.getInstance().get(KEY_PROTOCOL);

        if (server.equalsIgnoreCase(getString(org.odk.collect.strings.R.string.protocol_google_sheets))) {
            // if it's Sheets, start the Sheets uploader
            // first make sure we have a google account selected
            if (new PlayServicesChecker().isGooglePlayServicesAvailable(this)) {
                Intent i = new Intent(this, GoogleSheetsUploaderActivity.class);
                i.putExtra(FormEntryActivity.KEY_INSTANCES, instanceIds);
                startActivityForResult(i, INSTANCE_UPLOADER);
            } else {
                new PlayServicesChecker().showGooglePlayServicesAvailabilityErrorDialog(this);
            }
        } else {
            // otherwise, do the normal aggregate/other thing.
            Intent i = new Intent(this, InstanceUploaderActivity.class);
            i.putExtra(FormEntryActivity.KEY_INSTANCES, instanceIds);
            startActivityForResult(i, INSTANCE_UPLOADER);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.instance_uploader_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (!MultiClickGuard.allowClick(getClass().getName())) {
            return true;
        }

        switch (item.getItemId()) {
            case R.id.menu_preferences:
                createPreferencesMenu();
                return true;
            case R.id.menu_change_view:
                showSentAndUnsentChoices();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void createPreferencesMenu() {
        Intent i = new Intent(this, PreferencesActivity.class);
        startActivity(i);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long rowId) {
        if (listView.isItemChecked(position)) {
            selectedInstances.add(listView.getItemIdAtPosition(position));
        } else {
            selectedInstances.remove(listView.getItemIdAtPosition(position));
        }

        uploadButton.setEnabled(areCheckedItems());
        Button toggleSelectionsButton = findViewById(R.id.toggle_button);
        toggleButtonLabel(toggleSelectionsButton, listView);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(SHOW_ALL_MODE, showAllMode);
        outState.putBoolean(SHOW_INCOMPLETE, showIncomplete);       // smap
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (resultCode == RESULT_CANCELED) {
            selectedInstances.clear();
            return;
        }
        switch (requestCode) {
            // returns with a form path, start entry
            case INSTANCE_UPLOADER:
                if (intent.getBooleanExtra(FormEntryActivity.KEY_SUCCESS, false)) {
                    listView.clearChoices();
                    if (listAdapter.isEmpty()) {
                        finish();
                    }
                }
                break;
        }
        super.onActivityResult(requestCode, resultCode, intent);
    }

    private void setupAdapter() {
        listAdapter = new InstanceUploaderAdapter(this, null);
        listView.setAdapter(listAdapter);
        checkPreviouslyCheckedItems();
    }

    @Override
    protected String getSortingOrderKey() {
        return INSTANCE_UPLOADER_LIST_SORTING_ORDER;
    }

    @Override
    protected void updateAdapter() {
        getSupportLoaderManager().restartLoader(LOADER_ID, null, this);
    }

    @NonNull
    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        showProgressBar();
        if (showAllMode) {
            return instancesDao.getCompletedUndeletedInstancesCursorLoader(getFilterText(), getSortingOrder());
        } else if (showIncomplete) {        // smap
            return instancesDao.getIncompleteInstancesCursorLoader(getFilterText(), getSortingOrder());
        } else {
            return instancesDao.getFinalizedInstancesCursorLoader(getFilterText(), getSortingOrder());
        }
    }

    @Override
    public void onLoadFinished(@NonNull Loader<Cursor> loader, Cursor cursor) {
        hideProgressBarIfAllowed();
        listAdapter.changeCursor(cursor);
        checkPreviouslyCheckedItems();
        toggleButtonLabel(findViewById(R.id.toggle_button), listView);
    }

    @Override
    public void onLoaderReset(@NonNull Loader<Cursor> loader) {
        listAdapter.swapCursor(null);
    }

    @Override
    public boolean onLongClick(View v) {
        return showSentAndUnsentChoices();
    }

    /*
     * Create a dialog with options to save and exit, save, or quit without
     * saving
     */
    private boolean showSentAndUnsentChoices() {
        String[] items = {getString(org.odk.collect.strings.R.string.show_unsent_forms),
                getString(org.odk.collect.strings.R.string.show_sent_and_unsent_forms),
                getString(R.string.smap_show_incomplete)};  // smap

        AlertDialog alertDialog = new AlertDialog.Builder(this)
                .setIcon(android.R.drawable.ic_dialog_info)
                .setTitle(getString(org.odk.collect.strings.R.string.change_view))
                .setNeutralButton(getString(org.odk.collect.strings.R.string.cancel), (dialog, id) -> {
                    dialog.cancel();
                })
                .setItems(items, (dialog, which) -> {
                    switch (which) {
                        case 0: // show unsent
                            showAllMode = false;
                            updateAdapter();
                            break;

                        case 1: // show all
                            showAllMode = true;
                            updateAdapter();
                            break;

                        case 2:// smap Show incomplete forms
                            showAllMode = false;
                            showIncomplete = true;
                            updateAdapter();
                            break;

                        case 3:// do nothing
                            break;
                    }
                }).create();
        alertDialog.show();
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (listAdapter != null) {
            ((InstanceUploaderAdapter) listAdapter).onDestroy();
        }
    }
}
