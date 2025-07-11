/* Copyright (C) 2017 Shobhit
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

package org.odk.collect.android.fragments;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.google.zxing.client.android.BeepManager;
import com.google.zxing.client.android.Intents;
import com.google.zxing.integration.android.IntentIntegrator;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.CaptureManager;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;
import com.journeyapps.barcodescanner.camera.CameraSettings;

import org.jetbrains.annotations.NotNull;
import org.odk.collect.android.R;
import org.odk.collect.android.analytics.Analytics;
import org.odk.collect.android.analytics.AnalyticsEvents;
import org.odk.collect.android.utilities.CameraUtils;
import org.odk.collect.android.utilities.ToastUtils;
import org.odk.collect.android.utilities.Appearances;
import org.odk.collect.android.views.BarcodeViewDecoder;

import java.io.IOException;
import java.util.Collection;
import java.util.zip.DataFormatException;

import javax.inject.Inject;

import static org.odk.collect.android.injection.DaggerUtils.getComponent;

public abstract class BarCodeScannerFragment extends Fragment implements DecoratedBarcodeView.TorchListener {

    private CaptureManager capture;
    private DecoratedBarcodeView barcodeScannerView;

    private Button switchFlashlightButton;
    private BeepManager beepManager;

    @Inject
    BarcodeViewDecoder barcodeViewDecoder;

    @Inject
    Analytics analytics;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        getComponent(context).inject(this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        beepManager = new BeepManager(getActivity());

        View rootView = inflater.inflate(R.layout.fragment_scan, container, false);
        barcodeScannerView = rootView.findViewById(R.id.barcode_view);
        barcodeScannerView.setTorchListener(this);

        switchFlashlightButton = rootView.findViewById(R.id.switch_flashlight);
        switchFlashlightButton.setOnClickListener(v -> switchFlashlight());
        // if the device does not have flashlight in its camera, then remove the switch flashlight button...
        if (!hasFlash() || frontCameraUsed()) {
            switchFlashlightButton.setVisibility(View.GONE);
        }

        startScanning(savedInstanceState);
        return rootView;
    }

    private void startScanning(Bundle savedInstanceState) {
        capture = new CaptureManager(getActivity(), barcodeScannerView);
        capture.initializeFromIntent(getIntent(), savedInstanceState);
        capture.decode();

        // Must be called after setting up CaptureManager
        if (frontCameraUsed()) {
            switchToFrontCamera();
        }

        barcodeViewDecoder.waitForBarcode(barcodeScannerView).observe(getViewLifecycleOwner(), barcodeResult -> {
            beepManager.playBeepSoundAndVibrate();

            try {
                handleScanningResult(barcodeResult);
            } catch (IOException | DataFormatException | IllegalArgumentException e) {
                ToastUtils.showShortToast(getString(org.odk.collect.strings.R.string.invalid_qrcode));
                analytics.logEvent(AnalyticsEvents.SETTINGS_IMPORT_QR, "No valid settings", "none");
            }
        });
    }

    private void switchToFrontCamera() {
        CameraSettings cameraSettings = new CameraSettings();
        cameraSettings.setRequestedCameraId(CameraUtils.getFrontCameraId());
        barcodeScannerView.getBarcodeView().setCameraSettings(cameraSettings);
    }

    private Intent getIntent() {
        Intent intent = new IntentIntegrator(getActivity())
                .setDesiredBarcodeFormats(getSupportedCodeFormats())
                .setPrompt(getContext().getString(org.odk.collect.strings.R.string.barcode_scanner_prompt))
                .createScanIntent();
        intent.putExtra(Intents.Scan.SCAN_TYPE, Intents.Scan.MIXED_SCAN);
        return intent;
    }

    @Override
    public void onSaveInstanceState(@NotNull Bundle outState) {
        capture.onSaveInstanceState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onPause() {
        super.onPause();
        barcodeScannerView.pauseAndWait();
        capture.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        barcodeScannerView.resume();
        capture.onResume();
    }

    @Override
    public void onDestroy() {
        capture.onDestroy();
        super.onDestroy();
    }

    private boolean hasFlash() {
        return getActivity().getApplicationContext().getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH);
    }

    private boolean frontCameraUsed() {
        Bundle bundle = getActivity().getIntent().getExtras();
        return bundle != null && bundle.getBoolean(Appearances.FRONT);
    }

    private void switchFlashlight() {
        if (getString(org.odk.collect.strings.R.string.turn_on_flashlight).equals(switchFlashlightButton.getText())) {
            barcodeScannerView.setTorchOn();
        } else {
            barcodeScannerView.setTorchOff();
        }
    }

    @Override
    public void onTorchOn() {
        switchFlashlightButton.setText(org.odk.collect.strings.R.string.turn_off_flashlight);
    }

    @Override
    public void onTorchOff() {
        switchFlashlightButton.setText(org.odk.collect.strings.R.string.turn_on_flashlight);
    }

    protected abstract Collection<String> getSupportedCodeFormats();

    protected abstract void handleScanningResult(BarcodeResult result) throws IOException, DataFormatException;
}
