package org.odk.collect.android.widgets.utilities;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.provider.MediaStore;
import android.widget.Toast;

import org.javarosa.form.api.FormEntryPrompt;
import org.odk.collect.android.R;
import org.odk.collect.android.analytics.AnalyticsEvents;
import org.odk.collect.android.formentry.FormEntryViewModel;
import org.odk.collect.android.listeners.PermissionListener;
import org.odk.collect.android.permissions.PermissionsProvider;
import org.odk.collect.android.utilities.ApplicationConstants;

public class ExternalAppRecordingRequester implements RecordingRequester {

    private final Activity activity;
    private final PermissionsProvider permissionsProvider;
    private final WaitingForDataRegistry waitingForDataRegistry;
    private final FormEntryViewModel formEntryViewModel;

    // smap removed availability check - not compatible with android 30
    public ExternalAppRecordingRequester(Activity activity, WaitingForDataRegistry waitingForDataRegistry, PermissionsProvider permissionsProvider, FormEntryViewModel formEntryViewModel) {
        this.activity = activity;
        this.permissionsProvider = permissionsProvider;
        this.waitingForDataRegistry = waitingForDataRegistry;
        this.formEntryViewModel = formEntryViewModel;
    }

    @Override
    public void requestRecording(FormEntryPrompt prompt) {
        permissionsProvider.requestRecordAudioPermission(activity, new PermissionListener() {
            @Override
            public void granted() {
                Intent intent = new Intent(MediaStore.Audio.Media.RECORD_SOUND_ACTION);
                intent.putExtra(MediaStore.EXTRA_OUTPUT,
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI.toString());

                try {
                    waitingForDataRegistry.waitForData(prompt.getIndex());
                    activity.startActivityForResult(intent, ApplicationConstants.RequestCodes.AUDIO_CAPTURE);
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(activity, activity.getString(org.odk.collect.strings.R.string.activity_not_found,
                            activity.getString(org.odk.collect.strings.R.string.capture_audio)), Toast.LENGTH_SHORT).show();
                    waitingForDataRegistry.cancelWaitingForData();
                } catch (Exception e) {
                    Toast.makeText(activity, e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
                    waitingForDataRegistry.cancelWaitingForData();
                }
            }

            @Override
            public void denied() {
            }
        });

        formEntryViewModel.logFormEvent(AnalyticsEvents.AUDIO_RECORDING_EXTERNAL);
    }
}
