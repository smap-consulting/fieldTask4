package org.odk.collect.android.configure.qr;

import android.content.Context;
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

        boolean importSuccess = settingsImporter.fromJSONSmap(result.getText());
        //boolean importSuccess = settingsImporter.fromJSON(decompress(result.getText()));
        String settingsHash = FileUtils.getMd5Hash(new ByteArrayInputStream(result.getText().getBytes()));

        if (importSuccess) {
            ToastUtils.showLongToast(getString(R.string.successfully_imported_settings));
            getActivity().finish();
        } else {
            ToastUtils.showLongToast(getString(R.string.invalid_qrcode));
        }

    }

    @Override
    protected Collection<String> getSupportedCodeFormats() {
        return Collections.singletonList(IntentIntegrator.QR_CODE);
    }
}
