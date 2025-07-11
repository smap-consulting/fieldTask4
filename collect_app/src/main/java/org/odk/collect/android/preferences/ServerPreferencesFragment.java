/*
 * Copyright 2017 Shobhit
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

package org.odk.collect.android.preferences;

import android.accounts.AccountManager;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import timber.log.Timber;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListPopupWindow;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.SwitchPreference;

import org.jetbrains.annotations.NotNull;
import org.odk.collect.android.R;
import org.odk.collect.android.activities.SmapMain;
import org.odk.collect.android.analytics.Analytics;
import org.odk.collect.android.backgroundwork.FormUpdateManager;
import org.odk.collect.android.configure.ServerRepository;
import org.odk.collect.android.configure.qr.QRCodeTabsActivity;
import org.odk.collect.android.gdrive.GoogleAccountsManager;
import org.odk.collect.android.injection.DaggerUtils;
import org.odk.collect.android.listeners.OnBackPressedListener;
import org.odk.collect.android.listeners.PermissionListener;
import org.odk.collect.android.preferences.filters.ControlCharacterFilter;
import org.odk.collect.android.preferences.filters.WhitespaceFilter;
import org.odk.collect.android.utilities.FileUtils;
import org.odk.collect.android.utilities.MultiClickGuard;
import org.odk.collect.android.permissions.PermissionsProvider;
import org.odk.collect.android.utilities.PlayServicesChecker;
import org.odk.collect.android.utilities.SoftKeyboardController;
import org.odk.collect.android.utilities.ToastUtils;
import org.odk.collect.android.utilities.Utilities;
import org.odk.collect.android.utilities.Validator;

import java.io.ByteArrayInputStream;
import java.util.Locale;

import javax.inject.Inject;

import static android.app.Activity.RESULT_OK;
import static android.content.Intent.getIntent;
import static org.odk.collect.android.activities.ActivityUtils.startActivityAndCloseAllOthers;
import static org.odk.collect.android.analytics.AnalyticsEvents.SET_FALLBACK_SHEETS_URL;
import static org.odk.collect.android.analytics.AnalyticsEvents.SET_SERVER;
import static org.odk.collect.android.preferences.GeneralKeys.KEY_FORMLIST_URL;
import static org.odk.collect.android.preferences.GeneralKeys.KEY_PROTOCOL;
import static org.odk.collect.android.preferences.GeneralKeys.KEY_SELECTED_GOOGLE_ACCOUNT;
import static org.odk.collect.android.preferences.GeneralKeys.KEY_SUBMISSION_URL;
import static org.odk.collect.android.utilities.DialogUtils.showDialog;

public class ServerPreferencesFragment extends BasePreferenceFragment implements View.OnTouchListener, OnBackPressedListener {

    private static final int REQUEST_ACCOUNT_PICKER = 1000;

    private EditTextPreference passwordPreference;
    private EditTextPreference serverUrlPreference;
    private EditTextPreference usernamePreference;
    private Preference scanButton;
    private EditTextPreference authTokenPreference;
    @Inject
    GoogleAccountsManager accountsManager;

    @Inject
    Analytics analytics;

    @Inject
    PreferencesProvider preferencesProvider;

    @Inject
    FormUpdateManager formUpdateManager;

    @Inject
    ServerRepository serverRepository;

    @Inject
    SoftKeyboardController softKeyboardController;

    @Inject
    PermissionsProvider permissionsProvider;

    private ListPopupWindow listPopupWindow;
    private Preference selectedGoogleAccountPreference;
    private boolean allowClickSelectedGoogleAccountPreference = true;

    private final ActivityResultLauncher<Intent> formLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        setResult(RESULT_OK, result.getData());
    });

    private void setResult(int resultOk, Intent data) {
        authTokenPreference.setSummary(data.getStringExtra("auth_token"));
        serverUrlPreference.setSummary(data.getStringExtra("server_url"));
        usernamePreference.setSummary(data.getStringExtra("username"));
    }

    @Override
    public void onAttach(@NotNull Context context) {
        super.onAttach(context);
        DaggerUtils.getComponent(context).inject(this);

        ((PreferencesActivity) context).setOnBackPressedListener(this);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.server_preferences, rootKey);
        //initProtocolPrefs(); smap only allow server authentication
        addAggregatePreferences();  // smap - cut straight to the chase
    }

    @Override
    public void onDestroyView() {
        // to avoid leaking listPopupWindow
        listPopupWindow = null;
        super.onDestroyView();
    }

    private void initProtocolPrefs() {
        ListPreference protocolPref = (ListPreference) findPreference(KEY_PROTOCOL);
        protocolPref.setSummary(protocolPref.getEntry());
        protocolPref.setOnPreferenceChangeListener((preference, newValue) -> {
            if (preference.getKey().equals(KEY_PROTOCOL)) {
                String stringValue = (String) newValue;
                ListPreference lpref = (ListPreference) preference;
                String oldValue = lpref.getValue();
                lpref.setValue(stringValue);

                if (!newValue.equals(oldValue)) {
                    getPreferenceScreen().removeAll();
                    addPreferencesFromResource(R.xml.server_preferences);
                    initProtocolPrefs();
                }
            }
            return true;
        });

        String value = protocolPref.getValue();
        switch (Protocol.parse(getActivity(), value)) {
            case ODK:
                addAggregatePreferences();
                break;
            case GOOGLE:
                addGooglePreferences();
                break;
        }
    }

    public void addAggregatePreferences() {
        if (!new AggregatePreferencesAdder(this).add()) {
            return;
        }
        serverUrlPreference = (EditTextPreference) findPreference(GeneralKeys.KEY_SERVER_URL);
        usernamePreference = (EditTextPreference) findPreference(GeneralKeys.KEY_USERNAME);
        passwordPreference = (EditTextPreference) findPreference(GeneralKeys.KEY_PASSWORD);

        serverUrlPreference.setOnPreferenceChangeListener(createChangeListener());
        serverUrlPreference.setSummary(serverUrlPreference.getText());

        serverUrlPreference.setOnBindEditTextListener(editText -> {
            urlDropdownSetup(editText);
            editText.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_arrow_drop_down, 0);
            editText.setFilters(new InputFilter[]{new ControlCharacterFilter(), new WhitespaceFilter()});
            editText.setOnTouchListener(this);
        });

        usernamePreference.setOnPreferenceChangeListener(createChangeListener());
        // smap begin hack to try and set user name default
        String un = usernamePreference.getText();
        if(un == null || un.equals("")) {
            usernamePreference.setText(getString(R.string.default_username));
            usernamePreference.setSummary(R.string.default_username);
        }
        // smap end
        usernamePreference.setSummary(usernamePreference.getText());

        usernamePreference.setOnBindEditTextListener(editText -> {
            editText.setFilters(new InputFilter[]{new ControlCharacterFilter()});
        });

        passwordPreference.setOnPreferenceChangeListener(createChangeListener());
        maskPasswordSummary(passwordPreference.getText());

        passwordPreference.setOnBindEditTextListener(editText -> {
            editText.setFilters(new InputFilter[]{new ControlCharacterFilter()});
            editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        });

        // smap
        // Button to scan for a QR code
        scanButton = (Preference) findPreference(GeneralKeys.KEY_SMAP_SCAN_TOKEN);
        scanButton.setOnPreferenceClickListener(preference -> {
            formLauncher.launch(new Intent(getActivity(), QRCodeTabsActivity.class));
            //startActivity(new Intent(getActivity(), QRCodeTabsActivity.class));
            return true;
        });

        // Smap show the authToken when it changes
        authTokenPreference = (EditTextPreference) findPreference(GeneralKeys.KEY_SMAP_AUTH_TOKEN);
        authTokenPreference.setSummary(authTokenPreference.getText());
        authTokenPreference.setEnabled(false);

        // Respond to changes in authentication approach
        boolean forceToken = (Boolean) GeneralSharedPreferences.getInstance().get(GeneralKeys.KEY_SMAP_FORCE_TOKEN);
        SwitchPreference useTokenPreference = findPreference(GeneralKeys.KEY_SMAP_USE_TOKEN);
        if(forceToken) {
            useTokenPreference.setChecked(true);
            useTokenPreference.setEnabled(false);
        }

        useTokenPreference.setOnPreferenceChangeListener((preference, newValue) -> {
            return useTokenChanged((boolean) newValue);
        });
        useTokenChanged(useTokenPreference.isChecked());
        // End Smap

    }

    // smap
    private boolean useTokenChanged(boolean useToken) {
        // show or hide basic authentication preferences
        serverUrlPreference.setEnabled(!useToken);
        usernamePreference.setEnabled(!useToken);
        passwordPreference.setVisible(!useToken);

        // show or hide tken authentication preferences
        scanButton.setVisible(useToken);
        authTokenPreference.setVisible(useToken);

        return true;
    }

    private void urlDropdownSetup(EditText editText) {
        listPopupWindow = new ListPopupWindow(getActivity());
        setupUrlDropdownAdapter(listPopupWindow);
        listPopupWindow.setAnchorView(editText);
        listPopupWindow.setModal(true);
        listPopupWindow.setOnItemClickListener((parent, view, position, id) -> {
            editText.setText(serverRepository.getServers().get(position));
            listPopupWindow.dismiss();
        });
    }

    public void setupUrlDropdownAdapter(ListPopupWindow listPopupWindow) {
        ArrayAdapter adapter = new ArrayAdapter<>(getActivity(), android.R.layout.simple_list_item_1, serverRepository.getServers());
        listPopupWindow.setAdapter(adapter);
    }

    public void addGooglePreferences() {
        addPreferencesFromResource(R.xml.google_preferences);
        selectedGoogleAccountPreference = findPreference(KEY_SELECTED_GOOGLE_ACCOUNT);

        EditTextPreference googleSheetsUrlPreference = (EditTextPreference) findPreference(
                GeneralKeys.KEY_GOOGLE_SHEETS_URL);
        googleSheetsUrlPreference.setOnBindEditTextListener(editText -> editText.setFilters(new InputFilter[] {new ControlCharacterFilter(), new WhitespaceFilter() }));
        googleSheetsUrlPreference.setOnPreferenceChangeListener(createChangeListener());

        String currentGoogleSheetsURL = googleSheetsUrlPreference.getText();
        if (currentGoogleSheetsURL != null && currentGoogleSheetsURL.length() > 0) {
            googleSheetsUrlPreference.setSummary(currentGoogleSheetsURL + "\n\n"
                    + getString(org.odk.collect.strings.R.string.google_sheets_url_hint));
        }
        initAccountPreferences();
    }

    public void initAccountPreferences() {
        selectedGoogleAccountPreference.setSummary(accountsManager.getLastSelectedAccountIfValid());
        selectedGoogleAccountPreference.setOnPreferenceClickListener(preference -> {
            if (allowClickSelectedGoogleAccountPreference) {
                if (new PlayServicesChecker().isGooglePlayServicesAvailable(getActivity())) {
                    allowClickSelectedGoogleAccountPreference = false;
                    requestAccountsPermission();
                } else {
                    new PlayServicesChecker().showGooglePlayServicesAvailabilityErrorDialog(getActivity());
                }
            }
            return true;
        });
    }

    private void requestAccountsPermission() {
        permissionsProvider.requestGetAccountsPermission(getActivity(), new PermissionListener() {
            @Override
            public void granted() {
                Intent intent = accountsManager.getAccountChooserIntent();
                startActivityForResult(intent, REQUEST_ACCOUNT_PICKER);
            }

            @Override
            public void denied() {
                allowClickSelectedGoogleAccountPreference = true;
            }
        });
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        final int DRAWABLE_RIGHT = 2;
        if (event.getAction() == MotionEvent.ACTION_UP) {
            if (event.getX() >= (v.getWidth() - ((EditText) v)
                    .getCompoundDrawables()[DRAWABLE_RIGHT].getBounds().width())) {
                softKeyboardController.hideSoftKeyboard(v);
                listPopupWindow.show();
                return true;
            }
        }
        return false;
    }

    private Preference.OnPreferenceChangeListener createChangeListener() {
        return (preference, newValue) -> {
            switch (preference.getKey()) {
                case GeneralKeys.KEY_SERVER_URL:

                    String url = newValue.toString();

                    // remove all trailing "/"s
                    while (url.endsWith("/")) {
                        url = url.substring(0, url.length() - 1);
                    }

                    if (Validator.isUrlValid(url)) {
                        sendAnalyticsEvent(url);

                        preference.setSummary(newValue.toString());
                        setupUrlDropdownAdapter(listPopupWindow);
                    } else {
                        ToastUtils.showShortToast(org.odk.collect.strings.R.string.url_error);
                        return false;
                    }
                    break;

                case GeneralKeys.KEY_USERNAME:
                    String username = newValue.toString();

                    // do not allow leading and trailing whitespace
                    if (!username.equals(username.trim())) {
                        ToastUtils.showShortToast(org.odk.collect.strings.R.string.username_error_whitespace);
                        return false;
                    }

                    preference.setSummary(username);
                    return true;

                case GeneralKeys.KEY_PASSWORD:
                    String pw = newValue.toString();

                    // do not allow leading and trailing whitespace
                    if (!pw.equals(pw.trim())) {
                        ToastUtils.showShortToast(org.odk.collect.strings.R.string.password_error_whitespace);
                        return false;
                    }

                    maskPasswordSummary(pw);
                    break;

                case GeneralKeys.KEY_GOOGLE_SHEETS_URL:
                    url = newValue.toString();

                    // remove all trailing "/"s
                    while (url.endsWith("/")) {
                        url = url.substring(0, url.length() - 1);
                    }

                    if (Validator.isUrlValid(url)) {
                        preference.setSummary(url + "\n\n" + getString(org.odk.collect.strings.R.string.google_sheets_url_hint));

                        String urlHash = FileUtils.getMd5Hash(new ByteArrayInputStream(url.getBytes()));
                        analytics.logEvent(SET_FALLBACK_SHEETS_URL, urlHash);
                    } else if (url.length() == 0) {
                        preference.setSummary(getString(org.odk.collect.strings.R.string.google_sheets_url_hint));
                    } else {
                        ToastUtils.showShortToast(org.odk.collect.strings.R.string.url_error);
                        return false;
                    }
                    break;
                case KEY_FORMLIST_URL:
                case KEY_SUBMISSION_URL:
                    preference.setSummary(newValue.toString());
                    break;
            }
            return true;
        };
    }

    /**
     * Remotely log the URL scheme, whether the URL is on one of 3 common hosts, and a URL hash.
     * This will help inform decisions on whether or not to allow insecure server configurations
     * (HTTP) and on which hosts to strengthen support for.
     *
     * @param url the URL that the server setting has just been set to
     */
    private void sendAnalyticsEvent(String url) {
        String upperCaseURL = url.toUpperCase(Locale.ENGLISH);
        String scheme = upperCaseURL.split(":")[0];

        String host = "Other";
        if (upperCaseURL.contains("APPSPOT")) {
            host = "Appspot";
        } else if (upperCaseURL.contains("KOBOTOOLBOX.ORG") ||
                upperCaseURL.contains("HUMANITARIANRESPONSE.INFO")) {
            host = "Kobo";
        } else if (upperCaseURL.contains("ONA.IO")) {
            host = "Ona";
        }

        String urlHash = FileUtils.getMd5Hash(
                new ByteArrayInputStream(url.getBytes()));

        analytics.logEvent(SET_SERVER, scheme + " " + host, urlHash);
    }

    private void maskPasswordSummary(String password) {
        passwordPreference.setSummary(password != null && password.length() > 0
                ? "********"
                : "");
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_ACCOUNT_PICKER:
                if (resultCode == RESULT_OK && data != null && data.getExtras() != null) {
                    String accountName = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                    accountsManager.selectAccount(accountName);
                    selectedGoogleAccountPreference.setSummary(accountName);
                }
                allowClickSelectedGoogleAccountPreference = true;
                break;
        }
    }

    private void runGoogleAccountValidation() {
        String account = (String) GeneralSharedPreferences.getInstance().get(KEY_SELECTED_GOOGLE_ACCOUNT);
        String protocol = (String) GeneralSharedPreferences.getInstance().get(KEY_PROTOCOL);

        if (TextUtils.isEmpty(account) && protocol.equals(getString(org.odk.collect.strings.R.string.protocol_google_sheets))) {

            AlertDialog alertDialog = new AlertDialog.Builder(getActivity())
                    .setIcon(android.R.drawable.ic_dialog_info)
                    .setTitle(org.odk.collect.strings.R.string.missing_google_account_dialog_title)
                    .setMessage(org.odk.collect.strings.R.string.missing_google_account_dialog_desc)
                    .setPositiveButton(getString(org.odk.collect.strings.R.string.ok), (dialog, which) -> dialog.dismiss())
                    .create();

            showDialog(alertDialog, getActivity());
        } else {
            continueOnBackPressed();
        }
    }

    private void continueOnBackPressed() {
        ((PreferencesActivity) getActivity()).setOnBackPressedListener(null);
        getActivity().onBackPressed();
    }

    @Override
    public void doBack() {
        try {   // smap
            Utilities.updateServerRegistration(false);     // Re-register with the server - smap
            Intent intent = new Intent("org.smap.smapTask.refresh");      // Notify task list of change
            LocalBroadcastManager.getInstance(getActivity()).sendBroadcast(intent);
            Timber.i("######## send org.smap.smapTask.refresh from taskAddressActivity");
        } catch (Exception e) {

        }
        runGoogleAccountValidation();
    }
}
