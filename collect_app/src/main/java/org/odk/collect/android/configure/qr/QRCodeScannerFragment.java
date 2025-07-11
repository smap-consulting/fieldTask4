package org.odk.collect.android.configure.qr;

import static android.app.Activity.RESULT_OK;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import com.google.zxing.integration.android.IntentIntegrator;
import com.journeyapps.barcodescanner.BarcodeResult;

import org.json.JSONException;
import org.json.JSONObject;
import org.odk.collect.android.R;
import org.odk.collect.android.activities.MainMenuActivity;
import org.odk.collect.android.activities.SmapMain;
import org.odk.collect.android.analytics.Analytics;
import org.odk.collect.android.analytics.AnalyticsEvents;
import org.odk.collect.android.configure.SettingsImporter;
import org.odk.collect.android.fragments.BarCodeScannerFragment;
import org.odk.collect.android.injection.DaggerUtils;
import org.odk.collect.android.utilities.FileUtils;
import org.odk.collect.android.utilities.ToastUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.zip.DataFormatException;

import javax.inject.Inject;

import static org.odk.collect.android.activities.ActivityUtils.startActivityAndCloseAllOthers;
import static org.odk.collect.android.utilities.CompressionUtils.decompress;
import static org.odk.collect.android.utilities.SharedPreferencesUtils.put;

public class QRCodeScannerFragment extends BarCodeScannerFragment {

    @Inject
    SettingsImporter settingsImporter;

    @Inject
    Analytics analytics;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        DaggerUtils.getComponent(context).inject(this);
    }

    @Override
    protected void handleScanningResult(BarcodeResult result) throws IOException, DataFormatException {

        JSONObject jsonObject;
        String url = null;
        String token = null;
        String username = null;
        boolean importSuccess = false;
        try {
            jsonObject = new JSONObject(result.getText());

            // validate
            url = jsonObject.getString("server_url");
            token = jsonObject.getString("auth_token");
            username = jsonObject.getString("username");

            if(url == null || token == null || username == null) {
                importSuccess = false;
            } else {
                importSuccess = settingsImporter.fromJSONSmap(jsonObject);
            }
        } catch(JSONException e) {
            // Ignore
        }

        if (importSuccess) {
            ToastUtils.showLongToast(getString(org.odk.collect.strings.R.string.successfully_imported_settings));
            Intent data = new Intent();
            data.putExtra("server_url",url);
            data.putExtra("auth_token",token);
            data.putExtra("username",username);
            getActivity().setResult(RESULT_OK, data);
            getActivity().finish();
        } else {
            ToastUtils.showLongToast(getString(org.odk.collect.strings.R.string.invalid_qrcode));
        }

    }

    @Override
    protected Collection<String> getSupportedCodeFormats() {
        return Collections.singletonList(IntentIntegrator.QR_CODE);
    }
}
