/*
 * Copyright (C) 2017 University of Washington
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

package org.odk.collect.android.fragments;

import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.loader.content.CursorLoader;

import org.odk.collect.android.R;
import org.odk.collect.android.adapters.FormListAdapter;
import org.odk.collect.android.dao.FormsDao;
import org.odk.collect.android.forms.FormsRepository;
import org.odk.collect.android.fragments.dialogs.ProgressDialogFragment;
import org.odk.collect.android.injection.DaggerUtils;
import org.odk.collect.android.instances.InstancesRepository;
import org.odk.collect.android.listeners.DeleteFormsListener;
import org.odk.collect.android.listeners.DiskSyncListener;
import org.odk.collect.android.provider.FormsProviderAPI.FormsColumns;
import org.odk.collect.android.tasks.DeleteFormsTask;
import org.odk.collect.android.tasks.DiskSyncTask;
import org.odk.collect.android.utilities.DialogUtils;
import org.odk.collect.android.utilities.ToastUtils;

import javax.inject.Inject;

import timber.log.Timber;

/**
 * Responsible for displaying and deleting all the valid forms in the forms
 * directory.
 *
 * @author Carl Hartung (carlhartung@gmail.com)
 * @author Yaw Anokwa (yanokwa@gmail.com)
 */
public class BlankFormListFragment extends FormListFragment implements DiskSyncListener,
        DeleteFormsListener, View.OnClickListener {
    private static final String FORM_MANAGER_LIST_SORTING_ORDER = "formManagerListSortingOrder";
    private BackgroundTasks backgroundTasks; // handled across orientation changes
    private AlertDialog alertDialog;

    @Inject
    FormsRepository formsRepository;

    @Inject
    InstancesRepository instancesRepository;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        DaggerUtils.getComponent(context).inject(this);
    }

    @Override
    public void onViewCreated(@NonNull View rootView, Bundle savedInstanceState) {

        deleteButton.setOnClickListener(this);
        toggleButton.setOnClickListener(this);

        setupAdapter();

        if (backgroundTasks == null) {
            backgroundTasks = new BackgroundTasks();
            backgroundTasks.diskSyncTask = new DiskSyncTask();
            backgroundTasks.diskSyncTask.setDiskSyncListener(this);
            backgroundTasks.diskSyncTask.execute((Void[]) null);
        }
        super.onViewCreated(rootView, savedInstanceState);
    }

    @Override
    public void onResume() {
        // hook up to receive completion events
        backgroundTasks.diskSyncTask.setDiskSyncListener(this);
        if (backgroundTasks.deleteFormsTask != null) {
            backgroundTasks.deleteFormsTask.setDeleteListener(this);
        }
        super.onResume();
        // async task may have completed while we were reorienting...
        if (backgroundTasks.diskSyncTask.getStatus() == AsyncTask.Status.FINISHED) {
            syncComplete(backgroundTasks.diskSyncTask.getStatusMessage());
        }
        if (backgroundTasks.deleteFormsTask != null
                && backgroundTasks.deleteFormsTask.getStatus() == AsyncTask.Status.FINISHED) {
            deleteComplete(backgroundTasks.deleteFormsTask.getDeleteCount());
        }
        if (backgroundTasks.deleteFormsTask == null) {
            DialogUtils.dismissDialog(ProgressDialogFragment.class, getActivity().getSupportFragmentManager());
        }
    }

    @Override
    public void onPause() {
        if (backgroundTasks.diskSyncTask != null) {
            backgroundTasks.diskSyncTask.setDiskSyncListener(null);
        }
        if (backgroundTasks.deleteFormsTask != null) {
            backgroundTasks.deleteFormsTask.setDeleteListener(null);
        }
        if (alertDialog != null && alertDialog.isShowing()) {
            alertDialog.dismiss();
        }

        super.onPause();
    }

    private void setupAdapter() {
        String[] data = {
            FormsColumns.DISPLAY_NAME, FormsColumns.JR_VERSION,
            FormsColumns.DATE, FormsColumns.JR_FORM_ID};
        int[] view = {R.id.form_title, R.id.form_subtitle, R.id.form_subtitle2};

        listAdapter = new FormListAdapter(
                getListView(), FormsColumns.JR_VERSION, getActivity(),
                R.layout.form_chooser_list_item_multiple_choice, null, data, view);
        setListAdapter(listAdapter);
        checkPreviouslyCheckedItems();
    }

    @Override
    protected String getSortingOrderKey() {
        return FORM_MANAGER_LIST_SORTING_ORDER;
    }

    @Override
    protected CursorLoader getCursorLoader() {
        return new FormsDao().getFormsCursorLoader(getFilterText(), getSortingOrder());
    }

    /**
     * Create the form delete dialog
     */
    private void createDeleteFormsDialog() {
        alertDialog = new AlertDialog.Builder(getContext()).create();
        alertDialog.setTitle(getString(org.odk.collect.strings.R.string.delete_file));
        alertDialog.setMessage(getString(org.odk.collect.strings.R.string.delete_confirm,
                String.valueOf(getCheckedCount())));
        DialogInterface.OnClickListener dialogYesNoListener =
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int i) {
                        switch (i) {
                            case DialogInterface.BUTTON_POSITIVE: // delete
                                deleteSelectedForms();
                                if (getListView().getCount() == getCheckedCount()) {
                                    toggleButton.setEnabled(false);
                                }
                                break;
                        }
                    }
                };
        alertDialog.setCancelable(false);
        alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, getString(org.odk.collect.strings.R.string.delete_yes),
                dialogYesNoListener);
        alertDialog.setButton(DialogInterface.BUTTON_NEGATIVE, getString(org.odk.collect.strings.R.string.delete_no),
                dialogYesNoListener);
        alertDialog.show();
    }

    @Override
    public void progressUpdate(Integer progress, Integer total) {
        String message = String.format(getResources().getString(org.odk.collect.strings.R.string.deleting_form_dialog_update_message), progress, total);
        ProgressDialogFragment existingDialog = (ProgressDialogFragment) requireActivity().getSupportFragmentManager()
                .findFragmentByTag(ProgressDialogFragment.class.getName());

        if (existingDialog != null) {
            existingDialog.setMessage(message);
        }
    }

    /**
     * Deletes the selected files.First from the database then from the file
     * system
     */
    private void deleteSelectedForms() {
        // only start if no other task is running
        if (backgroundTasks.deleteFormsTask == null) {
            Bundle args = new Bundle();
            args.putSerializable(ProgressDialogFragment.MESSAGE, getResources().getString(org.odk.collect.strings.R.string.form_delete_message));
            args.putBoolean(ProgressDialogFragment.CANCELABLE, false);
            DialogUtils.showIfNotShowing(ProgressDialogFragment.class, args, getActivity().getSupportFragmentManager());

            backgroundTasks.deleteFormsTask = new DeleteFormsTask(formsRepository, instancesRepository);
            backgroundTasks.deleteFormsTask.setDeleteListener(this);
            backgroundTasks.deleteFormsTask.execute(getCheckedIdObjects());
        } else {
            ToastUtils.showLongToast(org.odk.collect.strings.R.string.file_delete_in_progress);
        }
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long rowId) {
        super.onListItemClick(l, v, position, rowId);
    }

    @Override
    public void syncComplete(@NonNull String result) {
        Timber.i("Disk scan complete");
        hideProgressBarAndAllow();
        showSnackbar(result);
    }

    @Override
    public void deleteComplete(int deletedForms) {
        Timber.i("Delete forms complete");
        final int toDeleteCount = backgroundTasks.deleteFormsTask.getToDeleteCount();

        if (deletedForms == toDeleteCount) {
            // all deletes were successful
            ToastUtils.showShortToast(getString(org.odk.collect.strings.R.string.file_deleted_ok, String.valueOf(deletedForms)));
        } else {
            // had some failures
            Timber.e("Failed to delete %d forms", toDeleteCount - deletedForms);
            ToastUtils.showLongToast(getString(org.odk.collect.strings.R.string.file_deleted_error, String.valueOf(getCheckedCount()
                    - deletedForms), String.valueOf(getCheckedCount())));
        }
        backgroundTasks.deleteFormsTask = null;
        getListView().clearChoices(); // doesn't unset the checkboxes
        for (int i = 0; i < getListView().getCount(); ++i) {
            getListView().setItemChecked(i, false);
        }
        deleteButton.setEnabled(false);

        DialogUtils.dismissDialog(ProgressDialogFragment.class, getActivity().getSupportFragmentManager());
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.delete_button:
                if (areCheckedItems()) {
                    createDeleteFormsDialog();
                } else {
                    ToastUtils.showShortToast(org.odk.collect.strings.R.string.noselect_error);
                }
                break;

            case R.id.toggle_button:
                ListView lv = getListView();
                boolean allChecked = toggleChecked(lv);
                if (allChecked) {
                    for (int i = 0; i < lv.getCount(); i++) {
                        selectedInstances.add(lv.getItemIdAtPosition(i));
                    }
                } else {
                    selectedInstances.clear();
                }
                toggleButtonLabel(toggleButton, getListView());
                deleteButton.setEnabled(allChecked);
                break;
        }
    }

    private static class BackgroundTasks {
        DiskSyncTask diskSyncTask;
        DeleteFormsTask deleteFormsTask;

        BackgroundTasks() {
        }
    }
}
