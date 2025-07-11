/*
 * Copyright 2019 Nafundi
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

package org.odk.collect.android.activities.viewmodels;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import org.odk.collect.android.R;
import org.odk.collect.android.analytics.Analytics;
import org.odk.collect.android.application.Collect;
import org.odk.collect.android.formmanagement.ServerFormDetails;
import org.odk.collect.android.utilities.FileUtils;
import org.odk.collect.android.utilities.TranslationHandler;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;

import static org.odk.collect.android.analytics.AnalyticsEvents.FIRST_FORM_DOWNLOAD;
import static org.odk.collect.android.analytics.AnalyticsEvents.SUBSEQUENT_FORM_DOWNLOAD;

public class FormDownloadListViewModel extends ViewModel {
    private final Analytics analytics;

    private HashMap<String, ServerFormDetails> formDetailsByFormId = new HashMap<>();

    /**
     * List of forms from the formList response. The map acts like a DisplayableForm object with
     * values for each component that shows up in the form list UI. See
     * FormDownloadListActivity.formListDownloadingComplete for keys.
     */
    private final ArrayList<HashMap<String, String>> formList = new ArrayList<>();

    private final LinkedHashSet<String> selectedFormIds = new LinkedHashSet<>();

    private String alertTitle;
    private String progressDialogMsg;
    private String alertDialogMsg;

    private boolean alertShowing;
    private boolean cancelDialogShowing;
    private boolean shouldExit;
    private boolean loadingCanceled;

    // Variables used when the activity is called from an external app
    private boolean isDownloadOnlyMode;
    private String[] formIdsToDownload;
    private String url;
    private String username;
    private String password;
    private final HashMap<String, Boolean> formResults = new HashMap<>();

    FormDownloadListViewModel(Analytics analytics) {
        this.analytics = analytics;
    }

    public HashMap<String, ServerFormDetails> getFormDetailsByFormId() {
        return formDetailsByFormId;
    }

    public void setFormDetailsByFormId(HashMap<String, ServerFormDetails> formDetailsByFormId) {
        this.formDetailsByFormId = formDetailsByFormId;
    }

    public void clearFormDetailsByFormId() {
        formDetailsByFormId.clear();
    }

    public String getAlertTitle() {
        return alertTitle;
    }

    public void setAlertTitle(String alertTitle) {
        this.alertTitle = alertTitle;
    }

    public String getProgressDialogMsg() {
        return progressDialogMsg == null ? TranslationHandler.getString(Collect.getInstance(), org.odk.collect.strings.R.string.please_wait) : progressDialogMsg;
    }

    public void setProgressDialogMsg(String progressDialogMsg) {
        this.progressDialogMsg = progressDialogMsg;
    }

    public String getAlertDialogMsg() {
        return alertDialogMsg;
    }

    public void setAlertDialogMsg(String alertDialogMsg) {
        this.alertDialogMsg = alertDialogMsg;
    }

    public boolean isAlertShowing() {
        return alertShowing;
    }

    public void setAlertShowing(boolean alertShowing) {
        this.alertShowing = alertShowing;
    }

    public boolean shouldExit() {
        return shouldExit;
    }

    public void setShouldExit(boolean shouldExit) {
        this.shouldExit = shouldExit;
    }

    public ArrayList<HashMap<String, String>> getFormList() {
        return formList;
    }

    public void clearFormList() {
        formList.clear();
    }

    public void addForm(HashMap<String, String> item) {
        formList.add(item);
    }

    public void addForm(int index, HashMap<String, String> item) {
        formList.add(index, item);
    }

    public LinkedHashSet<String> getSelectedFormIds() {
        return selectedFormIds;
    }

    public void addSelectedFormId(String selectedFormId) {
        selectedFormIds.add(selectedFormId);
    }

    public void removeSelectedFormId(String selectedFormId) {
        selectedFormIds.remove(selectedFormId);
    }

    public void clearSelectedFormIds() {
        selectedFormIds.clear();
    }

    public boolean isDownloadOnlyMode() {
        return isDownloadOnlyMode;
    }

    public void setDownloadOnlyMode(boolean downloadOnlyMode) {
        isDownloadOnlyMode = downloadOnlyMode;
    }

    public HashMap<String, Boolean> getFormResults() {
        return formResults;
    }

    public void putFormResult(String formId, boolean result) {
        formResults.put(formId, result);
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String[] getFormIdsToDownload() {
        return Arrays.copyOf(formIdsToDownload, formIdsToDownload.length);
    }

    public void setFormIdsToDownload(String[] formIdsToDownload) {
        this.formIdsToDownload = formIdsToDownload;
    }

    public boolean isCancelDialogShowing() {
        return cancelDialogShowing;
    }

    public void setCancelDialogShowing(boolean cancelDialogShowing) {
        this.cancelDialogShowing = cancelDialogShowing;
    }

    public boolean wasLoadingCanceled() {
        return loadingCanceled;
    }

    public void setLoadingCanceled(boolean loadingCanceled) {
        this.loadingCanceled = loadingCanceled;
    }

    public void logDownloadAnalyticsEvent(int downloadedFormCount, String serverUrl) {
        String analyticsEvent = getDownloadAnalyticsEvent(downloadedFormCount);
        String analyticsDesc = getDownloadAnalyticsDescription(serverUrl);
        analytics.logEvent(analyticsEvent, analyticsDesc);
    }

    private String getDownloadAnalyticsEvent(int downloadedFormCount) {
        return downloadedFormCount == 0 ? FIRST_FORM_DOWNLOAD : SUBSEQUENT_FORM_DOWNLOAD;
    }

    private String getDownloadAnalyticsDescription(String serverUrl) {
        // If a URL was set by intent, use that
        serverUrl = getUrl() != null ? getUrl() : serverUrl;

        String serverHash = FileUtils.getMd5Hash(new ByteArrayInputStream(serverUrl.getBytes()));
        return getSelectedFormIds().size() + "/" + getFormList().size() + "-" + serverHash;
    }

    public static class Factory implements ViewModelProvider.Factory {
        private final Analytics analytics;

        public Factory(Analytics analytics) {
            this.analytics = analytics;
        }

        @SuppressWarnings("unchecked")
        @NonNull
        @Override
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            return (T) new FormDownloadListViewModel(analytics);
        }
    }
}
