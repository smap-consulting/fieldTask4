package org.odk.collect.android.preferences;

import androidx.appcompat.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.EditText;

import androidx.fragment.app.DialogFragment;

import org.jetbrains.annotations.NotNull;
import org.odk.collect.android.R;
import org.odk.collect.android.injection.DaggerUtils;
import org.odk.collect.android.utilities.AdminPasswordProvider;

import javax.inject.Inject;

public class AdminPasswordDialogFragment extends DialogFragment {

    public static final String ARG_ACTION = "ACTION";

    public enum Action { ADMIN_SETTINGS, STORAGE_MIGRATION, SCAN_QR_CODE }

    private EditText input;
    private AdminPasswordDialogCallback callback;

    @Inject
    AdminPasswordProvider adminPasswordProvider;

    @Override
    public void onAttach(@NotNull Context context) {
        super.onAttach(context);

        DaggerUtils.getComponent(context).inject(this);

        if (context instanceof AdminPasswordDialogCallback) {
            callback = (AdminPasswordDialogCallback) context;
        }
    }

    @Override
    public void onStart() {
        super.onStart();

        Dialog dialog = getDialog();
        if (dialog != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        }
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        View dialogView = getActivity().getLayoutInflater().inflate(R.layout.admin_password_dialog_layout, null);
        CheckBox checkBox = dialogView.findViewById(R.id.checkBox);
        input = dialogView.findViewById(R.id.editText);

        checkBox.setOnCheckedChangeListener((compoundButton, b) -> {
            if (!checkBox.isChecked()) {
                input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            } else {
                input.setInputType(InputType.TYPE_TEXT_VARIATION_PASSWORD);
            }
        });

        return new AlertDialog.Builder(getActivity())
                .setView(dialogView)
                .setTitle(getString(org.odk.collect.strings.R.string.enter_admin_password))
                .setPositiveButton(getString(org.odk.collect.strings.R.string.ok), (dialog, whichButton) -> {
                            if (adminPasswordProvider.getAdminPassword().equals(input.getText().toString())) {
                                callback.onCorrectAdminPassword((Action) getArguments().getSerializable(ARG_ACTION));
                            } else {
                                callback.onIncorrectAdminPassword();
                            }
                            dismiss();
                        })
                .setNegativeButton(getString(org.odk.collect.strings.R.string.cancel), (dialogInterface, i) -> dismiss())
                .create();
    }

    public EditText getInput() {
        return input;
    }

    public interface AdminPasswordDialogCallback {
        void onCorrectAdminPassword(Action action);
        void onIncorrectAdminPassword();
    }
}
