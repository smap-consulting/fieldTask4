package org.odk.collect.android.activities;

/*
 * Copyright (C) 2019 Smap Consulting Pty Ltd
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

import android.content.Intent;
import android.os.Bundle;

import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.odk.collect.android.R;
import org.odk.collect.android.application.Collect;
import org.odk.collect.android.configure.qr.QRCodeTabsActivity;
import org.odk.collect.android.listeners.SmapLoginListener;
import org.odk.collect.android.preferences.GeneralKeys;
import org.odk.collect.android.preferences.GeneralSharedPreferences;
import org.odk.collect.android.tasks.SmapLoginTask;
import org.odk.collect.android.utilities.SnackbarUtils;
import org.odk.collect.android.utilities.Validator;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.widget.AppCompatSpinner;
import androidx.appcompat.widget.SwitchCompat;

import butterknife.BindView;
import butterknife.ButterKnife;
import timber.log.Timber;

public class SmapLoginActivity extends CollectAbstractActivity implements SmapLoginListener {

    @BindView(R.id.smap_use_token) SwitchCompat smapUseToken;
    @BindView(R.id.input_url) EditText urlText;
    @BindView(R.id.input_username) EditText userText;
    @BindView(R.id.input_password) EditText passwordText;
    @BindView(R.id.btn_login) Button loginButton;
    @BindView(R.id.progressBar) ProgressBar progressBar;
    @BindView(R.id.btn_scan) Button scanButton;
    @BindView(R.id.auth_token) EditText tokenText;


    private String url;
    private AppCompatSpinner urlSpinner;
    private ArrayAdapter<CharSequence> urlAdapter;
    private final ActivityResultLauncher<Intent> formLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        gotResult(RESULT_OK, result.getData());
    });

    private void gotResult(int resultOk, Intent data) {
        urlText.setText(data.getStringExtra("server_url"));
        userText.setText(data.getStringExtra("username"));
        tokenText.setText(data.getStringExtra("auth_token"));
    }
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //setTheme(R.style.DarkAppTheme);     // override theme for login
        setContentView(R.layout.smap_activity_login);
        ButterKnife.bind(this);

        // Responds to switch being checked/unchecked
        useTokenChanged(smapUseToken.isChecked());
        smapUseToken.setOnCheckedChangeListener(new SwitchCompat.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton var, boolean b) {
                useTokenChanged(b);
            }

        });

        url = (String) GeneralSharedPreferences.getInstance().get(GeneralKeys.KEY_SERVER_URL);
        urlText.setText(url);

        userText.setText((String) GeneralSharedPreferences.getInstance().get(GeneralKeys.KEY_USERNAME));

        passwordText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event){
                if(actionId == EditorInfo.IME_ACTION_DONE){
                    login();
                }
                return false;
            }
        });

        scanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                formLauncher.launch(new Intent(SmapLoginActivity.this, QRCodeTabsActivity.class));
            }
        });

        tokenText.setEnabled(false);

        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                login();
            }
        });
    }

    public void login() {
        Timber.i("Login started");

        boolean useToken = smapUseToken.isChecked();
        url = urlText.getText().toString();
        String username = userText.getText().toString();
        String password = passwordText.getText().toString();
        String token = tokenText.getText().toString();

        if (!validate(useToken, url, username, password, token)) {
            return;
        }

        loginButton.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);

        SmapLoginTask smapLoginTask = new SmapLoginTask();
        smapLoginTask.setListener(this);
        smapLoginTask.execute(String.valueOf(useToken), url, username, password, token);

    }

    @Override
    public void loginComplete(String status) {
        Timber.i("---------- %s", status);

        progressBar.setVisibility(View.GONE);
        loginButton.setEnabled(true);

        if(status == null || status.startsWith("error")) {
            loginFailed(status);
        } else if(status.equals("success")) {
            loginSuccess();
        } else if (status.equals("unauthorized")) {
            loginNotAuthorized(null);
        } else {
            loginFailed(null);
        }
    }

    public void loginSuccess() {

        // Update preferences with login values
        GeneralSharedPreferences prefs = GeneralSharedPreferences.getInstance();
        prefs.save(GeneralKeys.KEY_SERVER_URL, url);
        prefs.save(GeneralKeys.KEY_USERNAME, userText.getText().toString());
        prefs.save(GeneralKeys.KEY_SMAP_SCAN_TOKEN, smapUseToken.isChecked());

        if(smapUseToken.isChecked()) {
            prefs.save(GeneralKeys.KEY_SMAP_AUTH_TOKEN, tokenText.getText().toString());
        } else {
            prefs.save(GeneralKeys.KEY_PASSWORD, passwordText.getText().toString());
        }

        // Save the login time in case the password policy is set to periodic
        prefs.save(GeneralKeys.KEY_SMAP_LAST_LOGIN, String.valueOf(System.currentTimeMillis()));

        // Start Main Activity and initiate a refresh
        Intent i = new Intent(SmapLoginActivity.this, SmapMain.class);
        i.putExtra(SmapMain.EXTRA_REFRESH, "yes");
        i.putExtra(SmapMain.LOGIN_STATUS, "success");
        startActivity(i);  //smap
        finish();
    }

    public void loginFailed(String status) {

        // Attempt to login by comparing values agains stored preferences
        boolean useToken = smapUseToken.isChecked();
        String username = userText.getText().toString();
        String password = passwordText.getText().toString();
        String token = tokenText.getText().toString();

        String prefUrl = (String) GeneralSharedPreferences.getInstance().get(GeneralKeys.KEY_SERVER_URL);
        String prefUsername = (String) GeneralSharedPreferences.getInstance().get(GeneralKeys.KEY_USERNAME);
        String prefPassword = (String) GeneralSharedPreferences.getInstance().get(GeneralKeys.KEY_PASSWORD);
        String prefToken = (String) GeneralSharedPreferences.getInstance().get(GeneralKeys.KEY_SMAP_AUTH_TOKEN);
        if((useToken && url.equals(prefUrl) && username.equals(prefUsername) && token.equals(prefToken))
                || (!useToken && url.equals(prefUrl) && username.equals(prefUsername) && password.equals(prefPassword))) {
            // Start Main Activity no refresh as presumably there is no network
            Intent i = new Intent(SmapLoginActivity.this, SmapMain.class);
            i.putExtra(SmapMain.EXTRA_REFRESH, "no");
            i.putExtra(SmapMain.LOGIN_STATUS, "failed");
            startActivity(i);  //smap
            finish();
        } else {
            loginNotAuthorized(status);   // Credentials do not match
        }

    }

    public void loginNotAuthorized(String status) {
        String msg = Collect.getInstance().getString(R.string.smap_login_unauthorized);
        if(status != null && status.startsWith("error:")) {
            msg += "; " + status.substring(5);
        }
        SnackbarUtils.showShortSnackbar(findViewById(R.id.loginMain), msg);
    }

    public boolean validate(boolean useToken, String url, String username, String pw, String token) {
        boolean valid = true;

        // remove all trailing "/"s
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }

        if (!Validator.isUrlValid(url)) {
            urlText.setError(Collect.getInstance().getString(R.string.url_error));
            valid = false;
        } else {
            urlText.setError(null);
        }

        if(useToken) {
            if (token.isEmpty()) {
                passwordText.setError(Collect.getInstance().getString(R.string.password_error_whitespace));
                valid = false;
            } else {
                tokenText.setError(null);
            }
        } else {
            if (pw.isEmpty() || !pw.equals(pw.trim())) {
                passwordText.setError(Collect.getInstance().getString(R.string.password_error_whitespace));
                valid = false;
            } else {
                passwordText.setError(null);
            }

            if (username.isEmpty() || !username.equals(username.trim())) {
                userText.setError(Collect.getInstance().getString(R.string.username_error_whitespace));
                valid = false;
            } else {
                userText.setError(null);
            }
        }

        return valid;
    }

    private boolean useTokenChanged(boolean useToken) {
        // show or hide basic authentication preferences
        urlText.setEnabled(!useToken);
        userText.setEnabled(!useToken);
        this.findViewById(R.id.input_password_layout).setVisibility(useToken ? View.INVISIBLE : View.VISIBLE);

        // show or hide token authentication preferences
        scanButton.setVisibility(!useToken ? View.INVISIBLE : View.VISIBLE);
        this.findViewById(R.id.auth_token_layout).setVisibility(!useToken ? View.INVISIBLE : View.VISIBLE);

        return true;
    }

    private CharSequence[] getChoices() {
        CharSequence[] choices = new CharSequence[2];
        choices[0] = "https://app.kontrolid.org";
        choices[1] = "https://app.kontrolid.com";
        return choices;
    }
}
