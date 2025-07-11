package org.odk.collect.android.formentry;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.common.collect.ImmutableList;

import org.odk.collect.android.R;
import org.odk.collect.android.adapters.IconMenuListAdapter;
import org.odk.collect.android.adapters.model.IconMenuItem;
import org.odk.collect.android.analytics.Analytics;
import org.odk.collect.android.dao.helpers.InstancesDaoHelper;
import org.odk.collect.android.formentry.saving.FormSaveViewModel;
import org.odk.collect.android.injection.DaggerUtils;
import org.odk.collect.android.preferences.AdminKeys;
import org.odk.collect.android.preferences.AdminSharedPreferences;
import org.odk.collect.android.utilities.DialogUtils;
import org.odk.collect.async.Scheduler;

import java.util.List;

import javax.inject.Inject;

import static android.app.Activity.RESULT_OK;

public class QuitFormDialogFragment extends DialogFragment {

    @Inject
    Analytics analytics;

    @Inject
    Scheduler scheduler;

    @Inject
    FormSaveViewModel.FactoryFactory formSaveViewModelFactoryFactory;

    private FormSaveViewModel formSaveViewModel;
    private Listener listener;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        DaggerUtils.getComponent(context).inject(this);

        ViewModelProvider.Factory factory = formSaveViewModelFactoryFactory.create(requireActivity(), null);
        formSaveViewModel = new ViewModelProvider(requireActivity(), factory).get(FormSaveViewModel.class);

        if (context instanceof Listener) {
            listener = (Listener) context;
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        super.onCreateDialog(savedInstanceState);

        String title =  formSaveViewModel.getFormName() == null ? getActivity().getString(org.odk.collect.strings.R.string.no_form_loaded) : formSaveViewModel.getFormName();

        List<IconMenuItem> items;
        if ((boolean) AdminSharedPreferences.getInstance().get(AdminKeys.KEY_SAVE_MID)) {
            items = ImmutableList.of(new IconMenuItem(R.drawable.ic_save, org.odk.collect.strings.R.string.keep_changes),
                    new IconMenuItem(R.drawable.ic_delete, org.odk.collect.strings.R.string.do_not_save));
        } else {
            items = ImmutableList.of(new IconMenuItem(R.drawable.ic_delete, org.odk.collect.strings.R.string.do_not_save));
        }

        ListView listView = DialogUtils.createActionListView(getActivity());

        final IconMenuListAdapter adapter = new IconMenuListAdapter(getActivity(), items);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            IconMenuItem item = (IconMenuItem) adapter.getItem(position);

            if (item.getTextResId() == org.odk.collect.strings.R.string.keep_changes) {
                if (listener != null) {
                    listener.onSaveChangesClicked();
                }
            } else {
                formSaveViewModel.ignoreChanges();

                String action = getActivity().getIntent().getAction();
                if (Intent.ACTION_PICK.equals(action) || Intent.ACTION_EDIT.equals(action)) {
                    // caller is waiting on a picked form
                    Uri uri = InstancesDaoHelper.getLastInstanceUri(formSaveViewModel.getAbsoluteInstancePath());
                    if (uri != null) {
                        getActivity().setResult(RESULT_OK, new Intent().setData(uri));
                    }
                }
                getActivity().finish();
            }

            if (getDialog() != null) {
                getDialog().dismiss();
            }
        });

        return new AlertDialog.Builder(getActivity())
                .setTitle(getString(org.odk.collect.strings.R.string.quit_application, title))
                .setNegativeButton(getActivity().getString(org.odk.collect.strings.R.string.do_not_exit), (dialog, id) -> {
                    dialog.cancel();
                    dismiss();
                })
                .setView(listView)
                .create();
    }

    public interface Listener {
        void onSaveChangesClicked();
    }
}
