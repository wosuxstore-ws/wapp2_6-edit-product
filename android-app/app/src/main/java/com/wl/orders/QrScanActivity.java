package com.wl.orders;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.Image;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Size;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.core.resolutionselector.ResolutionStrategy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Opened from the QR button in the middle of the bottom bar. Full-screen camera; when it reads a
 * Wosux product QR it unlocks it and opens the filled-in form as a popup ({@link QrFormSheet}) over
 * the camera. While the popup is open scanning is paused; closing it starts scanning again.
 * Codes that are not ours only show a short message and the camera keeps looking.
 */
public class QrScanActivity extends AppCompatActivity {

    private PreviewView previewView;
    private TextView tvMessage;
    private TextView tvHint;
    private ImageButton btnTorch;
    private QrScanOverlay scanOverlay;
    private ImageButton btnFlip;
    private boolean useFront = false;   // false = back camera, true = front (selfie) camera

    private ExecutorService cameraExecutor;
    private BarcodeScanner scanner;
    private ProcessCameraProvider cameraProvider;
    private Camera camera;
    private BottomSheetDialog formDialog;

    /** true while a code is being handled / the popup is open: camera frames are ignored. */
    private final AtomicBoolean busy = new AtomicBoolean(false);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable hideMessage = () -> tvMessage.setVisibility(View.GONE);

    private final ActivityResultLauncher<String> permissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    startCamera();
                } else {
                    tvHint.setVisibility(View.GONE);
                    scanOverlay.setVisibility(View.GONE);
                    showMessage(getString(R.string.qr_err_camera_permission), true);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan);

        previewView = findViewById(R.id.previewView);
        tvMessage = findViewById(R.id.tvMessage);
        tvHint = findViewById(R.id.tvHint);
        btnTorch = findViewById(R.id.btnTorch);
        scanOverlay = findViewById(R.id.scanOverlay);
        btnFlip = findViewById(R.id.btnFlip);
        if (savedInstanceState != null) useFront = savedInstanceState.getBoolean("use_front", false);
        btnFlip.setOnClickListener(v -> {
            useFront = !useFront;
            bindCamera();
        });
        findViewById(R.id.btnClose).setOnClickListener(v -> finish());

        btnTorch.setOnClickListener(v -> {
            if (camera == null) return;
            boolean on = !btnTorch.isActivated();
            camera.getCameraControl().enableTorch(on);
            btnTorch.setActivated(on);
        });

        cameraExecutor = Executors.newSingleThreadExecutor();
        scanner = BarcodeScanning.getClient(new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build());

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                cameraProvider = future.get();
                bindCamera();
            } catch (Exception e) {
                showMessage(getString(R.string.qr_err_camera_start), true);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCamera() {
        if (cameraProvider == null || isFinishing()) return;

        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        // Our codes are dense (encrypted data), so read frames at a good size.
        ResolutionSelector selector = new ResolutionSelector.Builder()
                .setResolutionStrategy(new ResolutionStrategy(new Size(1920, 1080),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                .build();
        ImageAnalysis analysis = new ImageAnalysis.Builder()
                .setResolutionSelector(selector)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();
        analysis.setAnalyzer(cameraExecutor, new QrAnalyzer());

        try {
            cameraProvider.unbindAll();
            boolean hasFront = cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA);
            boolean hasBack = cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA);
            if (useFront && !hasFront) useFront = false;
            if (!useFront && !hasBack && hasFront) useFront = true;
            btnFlip.setVisibility(hasFront && hasBack ? View.VISIBLE : View.GONE);

            camera = cameraProvider.bindToLifecycle(this,
                    useFront ? CameraSelector.DEFAULT_FRONT_CAMERA : CameraSelector.DEFAULT_BACK_CAMERA,
                    preview, analysis);

            // the light only exists on the back camera; it is off after every switch
            btnTorch.setActivated(false);
            btnTorch.setVisibility(camera.getCameraInfo().hasFlashUnit() ? View.VISIBLE : View.GONE);
        } catch (Exception e) {
            showMessage(getString(R.string.qr_err_camera_start), true);
        }
    }

    /** Runs on the camera thread for every frame. */
    private final class QrAnalyzer implements ImageAnalysis.Analyzer {
        @Override
        @ExperimentalGetImage
        public void analyze(@NonNull ImageProxy proxy) {
            Image media = proxy.getImage();
            if (media == null || busy.get()) {
                proxy.close();
                return;
            }
            InputImage input = InputImage.fromMediaImage(media, proxy.getImageInfo().getRotationDegrees());
            scanner.process(input)
                    .addOnSuccessListener(codes -> onCodes(codes))
                    .addOnCompleteListener(task -> proxy.close());
        }
    }

    /** Main thread. */
    private void onCodes(List<Barcode> codes) {
        if (busy.get() || isFinishing() || isDestroyed()) return;
        String lastError = null;
        for (Barcode b : codes) {
            String raw = b.getRawValue();
            if (raw == null) continue;
            try {
                accept(QrCrypto.parse(raw, BuildConfig.QR_SECRET_KEY_HEX));
                return;
            } catch (QrCrypto.QrException e) {
                lastError = e.getMessage();
            }
        }
        if (lastError != null) showMessage(lastError, false);
    }

    /** A valid Wosux QR was read: pause scanning and open the filled form as a popup. */
    private void accept(QrItem item) {
        if (!busy.compareAndSet(false, true)) return;
        previewView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        handler.removeCallbacks(hideMessage);
        tvMessage.setVisibility(View.GONE);
        scanOverlay.setActive(false);   // line fades out while the popup is open

        formDialog = QrFormSheet.show(this, item, new QrFormSheet.Listener() {
            @Override
            public void onSave(QrItem saved) {
                saveItem(saved);
            }

            @Override
            public void onClosed() {
                formDialog = null;
                scanOverlay.setActive(true);
                busy.set(false);   // start looking for the next sticker
            }
        });
    }

    /**
     * Save was tapped in the popup and the values are valid.
     * TODO: send {@code it} to your server here (same way the other pages call your PHP files).
     * Fields: it.id, it.sku, it.number, it.billId, it.name, it.buyPrice, it.minPrice, it.maxPrice,
     * it.date (YYYY-MM-DD), it.categoryId, it.storeId.
     */
    private void saveItem(QrItem it) {
        Toast.makeText(this, "Ready to save: " + it.name, Toast.LENGTH_SHORT).show();
    }

    private void showMessage(String text, boolean sticky) {
        tvMessage.setText(text);
        tvMessage.setVisibility(View.VISIBLE);
        handler.removeCallbacks(hideMessage);
        if (!sticky) handler.postDelayed(hideMessage, 3000);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("use_front", useFront);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (formDialog != null) {
            formDialog.setOnDismissListener(null);
            formDialog.dismiss();
            formDialog = null;
        }
        if (cameraExecutor != null) cameraExecutor.shutdown();
        if (scanner != null) {
            try {
                scanner.close();
            } catch (Exception ignored) {
                // nothing to do, the page is closing
            }
        }
        super.onDestroy();
    }
}
