package org.odk.collect.android.gdrive;

import android.app.Activity;

import androidx.appcompat.app.AlertDialog;

import org.odk.collect.android.R;

import static org.odk.collect.android.utilities.DialogUtils.showDialog;

public class GoogleAccountNotSetDialog {

    private GoogleAccountNotSetDialog() {

    }

    public static void show(Activity activity) {
        AlertDialog alertDialog = new AlertDialog.Builder(activity)
                .setIcon(android.R.drawable.ic_dialog_info)
                .setTitle(org.odk.collect.strings.R.string.missing_google_account_dialog_title)
                .setMessage(org.odk.collect.strings.R.string.missing_google_account_dialog_desc)
                .setOnCancelListener(dialog -> {
                    dialog.dismiss();
                    if (activity != null) {
                        activity.finish();
                    }
                })
                .setPositiveButton(activity.getString(org.odk.collect.strings.R.string.ok), (dialog, which) -> {
                    dialog.dismiss();
                    activity.finish();
                })
                .create();

        showDialog(alertDialog, activity);
    }
}
