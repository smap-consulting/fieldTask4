/*
 * Copyright 2018 Nafundi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.odk.collect.android.utilities;

import android.content.Context;

import org.odk.collect.android.R;
import org.odk.collect.android.application.Collect;
import org.odk.collect.android.instances.Instance;
import org.odk.collect.android.instances.InstancesRepository;
import org.odk.collect.android.forms.Form;
import org.odk.collect.android.forms.FormsRepository;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class InstanceUploaderUtils {

    public static final String DEFAULT_SUCCESSFUL_TEXT = "full submission upload was successful!";
    public static final String SPREADSHEET_UPLOADED_TO_GOOGLE_DRIVE = "Failed. Records can only be submitted to spreadsheets created in Google Sheets. The submission spreadsheet specified was uploaded to Google Drive.";

    private InstanceUploaderUtils() {
    }

    /**
     * Returns a formatted message including submission results for all the filled forms accessible
     * through instancesProcessed in the following structure:
     *
     * Instance name 1 - result
     *
     * Instance name 2 - result
     */
    public static String getUploadResultMessage(InstancesRepository instancesRepository, Context context, Map<String, String> result) {
        Set<String> keys = result.keySet();
        Iterator<String> it = keys.iterator();
        StringBuilder message = new StringBuilder();

        while (it.hasNext()) {
            Instance instance = instancesRepository.get(Long.valueOf(it.next()));
            message.append(getUploadResultMessageForInstances(instance, result));
        }

        if (message.length() == 0) {
            message = new StringBuilder(context.getString(org.odk.collect.strings.R.string.no_forms_uploaded));
        }

        return message.toString().trim();
    }

    private static String getUploadResultMessageForInstances(Instance instance, Map<String, String> resultMessagesByInstanceId) {
        StringBuilder uploadResultMessage = new StringBuilder();
        if (instance != null) {
            String name = instance.getDisplayName();
            String text = localizeDefaultAggregateSuccessfulText(resultMessagesByInstanceId.get(instance.getId().toString()));
            uploadResultMessage
                    .append(name)
                    .append(" - ")
                    .append(text)
                    .append("\n\n");
        }
        return uploadResultMessage.toString();
    }

    private static String localizeDefaultAggregateSuccessfulText(String text) {
        if (text != null && text.equals(DEFAULT_SUCCESSFUL_TEXT)) {
            text = TranslationHandler.getString(Collect.getInstance(), org.odk.collect.strings.R.string.success);
        }
        return text;
    }

    // If a spreadsheet is created using Excel (or a similar tool) and uploaded to GD it contains:
    // drive.google.com/file/d/ instead of docs.google.com/spreadsheets/d/
    // Such a file can't be used. We can write data only to documents generated via Google Sheets
    // https://forum.getodk.org/t/error-400-bad-request-failed-precondition-on-collect-to-google-sheets/19801/5?u=grzesiek2010
    public static boolean doesUrlRefersToGoogleSheetsFile(String url) {
        return !url.contains("drive.google.com/file/d/");
    }

    /**
     * Returns whether instances of the form specified should be auto-deleted after successful
     * update.
     *
     * If the form explicitly sets the auto-delete property, then it overrides the preference.
     */
    public static boolean shouldFormBeDeleted(FormsRepository formsRepository, String jrFormId, String jrFormVersion, boolean isAutoDeleteAppSettingEnabled) {
        Form form = formsRepository.getLatestByFormIdAndVersion(jrFormId, jrFormVersion);
        if (form == null) {
            return false;
        }

        return form.getAutoDelete() == null ? isAutoDeleteAppSettingEnabled : Boolean.valueOf(form.getAutoDelete());
    }
}
