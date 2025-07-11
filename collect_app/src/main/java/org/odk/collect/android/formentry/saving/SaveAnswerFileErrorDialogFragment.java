package org.odk.collect.android.formentry.saving;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.odk.collect.android.R;
import org.odk.collect.android.injection.DaggerUtils;

import javax.inject.Inject;

public class SaveAnswerFileErrorDialogFragment extends DialogFragment {

    @Inject
    FormSaveViewModel.FactoryFactory formSaveViewModelFactoryFactory;
    private FormSaveViewModel formSaveViewModel;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        DaggerUtils.getComponent(context).inject(this);

        ViewModelProvider viewModelProvider = new ViewModelProvider(requireActivity(), formSaveViewModelFactoryFactory.create(requireActivity(), null));
        formSaveViewModel = viewModelProvider.get(FormSaveViewModel.class);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        return new MaterialAlertDialogBuilder(requireContext())
                .setTitle(org.odk.collect.strings.R.string.error_occured)
                .setMessage(getString(org.odk.collect.strings.R.string.answer_file_copy_failed_message, formSaveViewModel.getAnswerFileError().getValue()))
                .setPositiveButton(org.odk.collect.strings.R.string.ok, null)
                .create();
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
        formSaveViewModel.answerFileErrorDisplayed();
    }
}
