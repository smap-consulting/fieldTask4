package org.odk.collect.android.formentry;

import android.app.Dialog;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.odk.collect.android.R;

public class RecordingWarningDialogFragment extends DialogFragment {

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        return new MaterialAlertDialogBuilder(requireActivity())
                .setTitle(org.odk.collect.strings.R.string.recording)
                .setMessage(org.odk.collect.strings.R.string.recording_warning)
                .setPositiveButton(org.odk.collect.strings.R.string.ok, null)
                .create();
    }
}
