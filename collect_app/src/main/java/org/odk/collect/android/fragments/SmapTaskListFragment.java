/*
 * Copyright (C) 2017 Smap Consulting Pty Ltd
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

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.MenuItemCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.ListFragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.tabs.TabLayout;

import org.odk.collect.android.R;
import org.odk.collect.android.activities.AboutActivity;
import org.odk.collect.android.activities.FillBlankFormActivity;
import org.odk.collect.android.activities.FormDownloadListActivity;
import org.odk.collect.android.activities.SmapMain;
import org.odk.collect.android.activities.viewmodels.SurveyDataViewModel;
import org.odk.collect.android.adapters.SortDialogAdapter;
import org.odk.collect.android.adapters.TaskListArrayAdapter;
import org.odk.collect.android.database.DatabaseInstancesRepository;
import org.odk.collect.android.instances.Instance;
import org.odk.collect.android.listeners.OnTaskOptionsClickListener;
import org.odk.collect.android.loaders.SurveyData;
import org.odk.collect.android.loaders.TaskEntry;
import org.odk.collect.android.location.SystemLocationProvider;
import org.odk.collect.android.preferences.AdminKeys;
import org.odk.collect.android.preferences.AdminPreferencesActivity;
import org.odk.collect.android.preferences.GeneralKeys;
import org.odk.collect.android.preferences.PreferencesActivity;
import org.odk.collect.android.smap.utilities.LocationRegister;
import org.odk.collect.android.utilities.MultiClickGuard;
import org.odk.collect.android.utilities.SnackbarUtils;
import org.odk.collect.android.utilities.ThemeUtils;
import org.odk.collect.android.utilities.Utilities;

import timber.log.Timber;

/**
 * Responsible for displaying tasks on the main fieldTask screen
 */
public class SmapTaskListFragment extends ListFragment {

    private static final int MENU_ENTERDATA = Menu.FIRST + 2;
    private static final int MENU_GETFORMS = Menu.FIRST + 3;
    private static final int MENU_SENDDATA = Menu.FIRST + 4;
    private static final int MENU_MANAGEFILES = Menu.FIRST + 5;
    private static final int MENU_EXIT = Menu.FIRST + 6;
    private static final int MENU_HISTORY = Menu.FIRST + 7;

    protected int[] sortingOptions;

    View rootView;

    private String filterText;

    private BottomSheetDialog bottomSheetDialog;

    private SharedPreferences adminPreferences;

    private TaskListArrayAdapter mAdapter;

    SurveyDataViewModel model;

    public static SmapTaskListFragment newInstance() {
        return new SmapTaskListFragment();
    }

    public SmapTaskListFragment() {
    }

    // this method is only called once for this fragment
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // retain this fragment
        setRetainInstance(true);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.smap_task_layout, container, false);

        setHasOptionsMenu(true);
        return rootView;
    }

    @Override
    public void onActivityCreated(Bundle b) {
        super.onActivityCreated(b);

        OnTaskOptionsClickListener taskClickLisener = new OnTaskOptionsClickListener() {
            final DatabaseInstancesRepository di = new DatabaseInstancesRepository();

            @Override
            public void onAcceptClicked(TaskEntry taskEntry) {
                if (Utilities.canAccept(taskEntry.taskStatus)) {
                    Utilities.setStatusForTask(taskEntry.id, Utilities.STATUS_T_ACCEPTED, "");
                    Intent intent = new Intent("org.smap.smapTask.refresh");      // Notify map and task list of change
                    LocalBroadcastManager.getInstance(requireActivity().getApplication()).sendBroadcast(intent);
                    Timber.i("######## send org.smap.smapTask.refresh from instanceUploaderActivity2");
                } else {
                    AlertDialog error = new AlertDialog.Builder(requireContext())
                            .setMessage(getString(R.string.smap_cannot_accept))
                            .create();
                    error.show();
                }
            }

            @Override
            public void onSMSClicked(TaskEntry taskEntry) {
                Instance instance = di.getInstanceByTaskId(taskEntry.assId);
                String number = null;
                if (instance != null) {
                    number = instance.getPhone();
                }
                if (number != null) {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.fromParts("sms", number, null)));
                } else {
                    AlertDialog error = new AlertDialog.Builder(requireContext())
                            .setMessage(requireContext().getString(R.string.smap_phone_number_not_found))
                            .create();
                    error.show();
                }
            }

            @Override
            public void onPhoneClicked(TaskEntry taskEntry) {
                Instance instance = di.getInstanceByTaskId(taskEntry.assId);
                if (instance != null) {
                    String number = instance.getPhone();
                    if (number != null) {
                        Intent callIntent = new Intent(Intent.ACTION_DIAL);
                        callIntent.setData(Uri.parse("tel:" + number));
                        startActivity(callIntent);
                    } else {
                        AlertDialog error = new AlertDialog.Builder(requireContext())
                                .setMessage(requireContext().getString(R.string.smap_phone_number_not_found))
                                .create();
                        error.show();
                    }
                }
            }

            @Override
            public void onRejectClicked(TaskEntry taskEntry) {
                View reject_popup = getLayoutInflater().inflate(R.layout.reject_task, null);
                AlertDialog dialog = new AlertDialog.Builder(getContext())
                        .setNegativeButton(null, null)
                        .setPositiveButton(null, null)
                        .setView(reject_popup)
                        .create();
                dialog.show();
                EditText editText = reject_popup.findViewById(R.id.input_reason);
                Button ok = reject_popup.findViewById(R.id.ok);
                Button cancel = reject_popup.findViewById(R.id.cancel);

                if(taskEntry.taskType != null && taskEntry.taskType.equals("case")) {
                    TextView titleText = reject_popup.findViewById(R.id.reject_title);
                    titleText.setText(getContext().getString(R.string.smap_release_case));
                }
                ok.setOnClickListener(view -> {
                    String reason = editText.getText().toString();
                    rejectTask(reason, taskEntry);
                    dialog.dismiss();
                });
                cancel.setOnClickListener(view -> dialog.dismiss());
            }

            @Override
            public void onDirectionsClicked(TaskEntry taskEntry) {
                String uri = String.format(
                        "geo:0,0?q=%f,%f (%s)",
                        taskEntry.schedLat,
                        taskEntry.schedLon,
                        taskEntry.name
                );
                Intent intent = new Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(uri)
                );
                startActivity(intent);
            }

            @Override
            public void onLocateClick(TaskEntry taskEntry) {
                SmapMain activity = ((SmapMain) getActivity());
                activity.locateTaskOnMap(taskEntry);
            }
        };

        mAdapter = new TaskListArrayAdapter(getActivity(), false, taskClickLisener);
        setListAdapter(mAdapter);

        adminPreferences = getActivity().getSharedPreferences(
                AdminPreferencesActivity.ADMIN_PREFERENCES, 0);

    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {

        sortingOptions = new int[]{
                R.string.sort_by_name_asc, R.string.sort_by_name_desc,
                R.string.sort_by_date_asc, R.string.sort_by_date_desc,
                R.string.sort_by_status_asc, R.string.sort_by_status_desc,
                R.string.sort_by_distance_asc, R.string.sort_by_distance_desc
        };
        model = getViewMode();
        model.getSurveyData().observe(getViewLifecycleOwner(), surveyData -> {
            Timber.i("-------------------------------------- Task List Fragment got Data ");
            setData(surveyData);
        });

        super.onViewCreated(view, savedInstanceState);

        // Notify the user if tracking is turned on
        if (new LocationRegister().locationEnabled()
                && PreferenceManager.getDefaultSharedPreferences(getContext()).getBoolean(GeneralKeys.KEY_SMAP_USER_LOCATION, false)) {
            SnackbarUtils.showLongSnackbar(getActivity().findViewById(R.id.llParent), getString(R.string.smap_location_tracking));
        }
    }

    @Override
    public void onDestroyView() {
        rootView = null;
        super.onDestroyView();
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle bundle) {
        super.onViewStateRestored(bundle);
    }

    @Override
    public void onResume() {
        super.onResume();
        Toolbar toolbar = (Toolbar) getActivity().findViewById(R.id.toolbar);
        toolbar.setNavigationIcon(R.mipmap.ic_nav);

        if (bottomSheetDialog == null) {
            setupBottomSheet();
        }

    }

    private void setupBottomSheet() {
        bottomSheetDialog = new BottomSheetDialog(getActivity(), new ThemeUtils(getContext()).getBottomDialogTheme());
        View sheetView = getActivity().getLayoutInflater().inflate(R.layout.bottom_sheet, null);
        final RecyclerView recyclerView = sheetView.findViewById(R.id.recyclerView);

        final SortDialogAdapter adapter = new SortDialogAdapter(getActivity(), sortingOptions, model.getTaskSortingOrder(),
                (itAdapter, position) -> {
                    model.saveTaskSelectedSortingOrder(position);
                    itAdapter.updateSelectedPosition(position);
                    reloadData();
                    bottomSheetDialog.dismiss();
                }, new SystemLocationProvider(getActivity()));
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(getActivity());
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);
        recyclerView.setItemAnimator(new DefaultItemAnimator());

        bottomSheetDialog.setContentView(sheetView);
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
    }

    public void setData(SurveyData data) {
        int count = 0;
        if (mAdapter != null) {
            if (data != null) {
                count = mAdapter.setData(data.tasks);
            } else {
                mAdapter.setData(null);
            }
        }

        FragmentActivity activity = (SmapMain) getActivity();
        if (activity != null) {
            TabLayout tabLayout = (TabLayout) (activity).findViewById(R.id.tabs);
            if (tabLayout != null) {
                TabLayout.Tab tab = tabLayout.getTabAt(1);
                if (tab != null) {
                    tab.setText(getString(R.string.smap_tasks) + "(" + count + ")");
                }
            }
        }
    }


    @Override
    public void onListItemClick(ListView l, View v, int position, long rowId) {
        if (MultiClickGuard.allowClick(getClass().getName())) {
            super.onListItemClick(l, v, position, rowId);

            TaskEntry entry = (TaskEntry) getListAdapter().getItem(position);

            if (entry.type.equals("task")) {
                if (entry.locationTrigger != null && entry.locationTrigger.length() > 0) {
                    Toast.makeText(
                            getActivity(),
                            getString(R.string.smap_must_start_from_nfc),
                            Toast.LENGTH_LONG).show();
                } else {
                    ((SmapMain) getActivity()).completeTask(entry, false);
                }
            } else {
                ((SmapMain) getActivity()).completeForm(entry, false, null);
            }
        }
    }

    public SurveyDataViewModel getViewMode() {
        return ((SmapMain) getActivity()).getViewModel();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {

        super.onCreateOptionsMenu(menu, inflater);

        getActivity().getMenuInflater().inflate(R.menu.smap_menu, menu);


        boolean odkMenus = PreferenceManager
                .getDefaultSharedPreferences(getContext())
                .getBoolean(GeneralKeys.KEY_SMAP_ODK_STYLE_MENUS, true);

        if (odkMenus) {
            menu
                    .add(0, MENU_ENTERDATA, 0, R.string.enter_data)
                    .setIcon(android.R.drawable.ic_menu_edit)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);

            menu
                    .add(0, MENU_GETFORMS, 0, R.string.get_forms)
                    .setIcon(android.R.drawable.ic_input_add)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);

            menu
                    .add(0, MENU_SENDDATA, 0, R.string.send_data)
                    .setIcon(android.R.drawable.ic_menu_send)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);

            menu
                    .add(0, MENU_MANAGEFILES, 0, R.string.manage_files)
                    .setIcon(android.R.drawable.ic_delete)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        }

        menu
                .add(0, MENU_HISTORY, 0, R.string.smap_history)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);

        menu
                .add(0, MENU_EXIT, 0, R.string.exit)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);

        boolean adminMenu = PreferenceManager
                .getDefaultSharedPreferences(getContext())
                .getBoolean(GeneralKeys.KEY_SMAP_ODK_ADMIN_MENU, false);

        if (adminMenu) {
            menu
                    .add(0, R.id.menu_admin_preferences, 0,
                            R.string.admin_preferences)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        }

        final MenuItem sortItem = menu.findItem(R.id.menu_sort);
        final MenuItem searchItem = menu.findItem(R.id.menu_filter);
        final SearchView searchView = (SearchView) MenuItemCompat.getActionView(searchItem);
        searchView.setQueryHint(getResources().getString(R.string.search));
        searchView.setMaxWidth(Integer.MAX_VALUE);

        if (filterText == null) {
            filterText = "";
        }

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                filterText = query;
                reloadData();
                searchView.clearFocus();
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                if (!filterText.equals(newText)) {
                    filterText = newText;
                    reloadData();
                }
                return false;
            }
        });

        MenuItemCompat.setOnActionExpandListener(searchItem, new MenuItemCompat.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                sortItem.setVisible(false);
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                sortItem.setVisible(true);
                return true;
            }
        });

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_about:
                startActivity(new Intent(getActivity(), AboutActivity.class));
                return true;
            case R.id.menu_general_preferences:
                startActivity(new Intent(getActivity(), PreferencesActivity.class));
                return true;
            case R.id.menu_admin_preferences:
                String pw = adminPreferences.getString(
                        AdminKeys.KEY_ADMIN_PW, "");
                if ("".equalsIgnoreCase(pw)) {
                    Intent i = new Intent(getActivity(),
                            AdminPreferencesActivity.class);
                    startActivity(i);
                } else {
                    ((SmapMain) getActivity()).processAdminMenu();
                }
                return true;
            case R.id.menu_gettasks:
                ((SmapMain) getActivity()).processGetTask(true);
                return true;
            case MENU_ENTERDATA:
                processEnterData();
                return true;
            case MENU_GETFORMS:
                processGetForms();
                return true;
            case MENU_SENDDATA:
                processSendData();
                return true;
            case MENU_MANAGEFILES:
                processManageFiles();
                return true;
            case MENU_HISTORY:
                ((SmapMain) getActivity()).processHistory();
                return true;
            case R.id.menu_sort:
                bottomSheetDialog.show();
                return true;
            case MENU_EXIT:
                ((SmapMain) getActivity()).exit();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    protected CharSequence getFilterText() {
        return filterText != null ? filterText : "";
    }

    protected void reloadData() {
        if (model != null) {
            model.updateFilter(getFilterText());
            model.loadData();
        }
    }

    private void processEnterData() {
        if (MultiClickGuard.allowClick(getClass().getName())) {
            Intent i = new Intent(getContext(),
                    FillBlankFormActivity.class);
            startActivity(i);
        }
    }

    // Get new forms
    private void processGetForms() {

        Intent i = new Intent(getContext(), FormDownloadListActivity.class);
        startActivity(i);
    }

    // Send data
    private void processSendData() {
        Intent i = new Intent(getContext(), org.odk.collect.android.activities.InstanceUploaderListActivity.class);
        startActivity(i);
    }

    private void processManageFiles() {
        Intent i = new Intent(getContext(), org.odk.collect.android.activities.DeleteSavedFormActivity.class);
        startActivity(i);
    }

    private void rejectTask(String reason, TaskEntry taskEntry) {
        if (Utilities.canReject(taskEntry.taskStatus)) {
            if (!taskEntry.taskStatus.equals("new") && reason != null && reason.trim().length() < 5) {
                AlertDialog error = new AlertDialog.Builder(requireContext())
                        .setMessage(getString(R.string.smap_reason_not_specified))
                        .create();
                error.show();
            } else {
                Utilities.setStatusForTask(taskEntry.id, Utilities.STATUS_T_REJECTED, reason);
                Intent intent = new Intent("org.smap.smapTask.refresh");      // Notify map and task list of change
                LocalBroadcastManager.getInstance(requireActivity().getApplication()).sendBroadcast(intent);
                Timber.i("######## send org.smap.smapTask.refresh from taskAddressActivity");
            }
        } else {
            AlertDialog error = new AlertDialog.Builder(requireContext())
                    .setMessage(getString(R.string.smap_cannot_reject))
                    .create();
            error.show();
        }
    }


}
