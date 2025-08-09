package com.example.vprdemo1;

import androidx.appcompat.app.AppCompatActivity;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import android.Manifest;
import android.os.Bundle;
import android.widget.TextView;
import android.util.Size;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import org.opencv.android.OpenCVLoader;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class MainActivity extends AppCompatActivity {

    private static final String TAG = "OCV";

    // Used to load the 'vprdemo1' library on application startup.
    static {
        System.loadLibrary("vprdemo1");
    }

    private PreviewView previewView;
    private TextView resultText;
    private ExecutorService analyzerExecutor;

    private long lastUiUpdateNs = 0;
    private double emaFps = 0.0;

    private final ActivityResultLauncher<String> reqCamPerm =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) startCamera();
                else resultText.setText("Camera permission denied");
            });

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (OpenCVLoader.initLocal()) {
            Log.i(TAG, "OpenCV loaded");
        } else {
            Log.e(TAG, "OpenCV init failed");
            Toast.makeText(this, "OpenCV init failed", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        setContentView(R.layout.activity_main);
        previewView = findViewById(R.id.preview);
        resultText  = findViewById(R.id.sample_text);
        resultText.setText("OpenCV: " + cvVersion());

        analyzerExecutor = Executors.newSingleThreadExecutor();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PERMISSION_GRANTED) {
            startCamera();
        } else {
            reqCamPerm.launch(Manifest.permission.CAMERA);
        }
    }

    private void startCamera() {
        ProcessCameraProvider.getInstance(this).addListener(() -> {
            try {
                ProcessCameraProvider provider = ProcessCameraProvider.getInstance(this).get();

                Preview preview = new Preview.Builder()
                        .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                        .build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setTargetResolution(new Size(1280, 720)) // keep reasonable; emulator OK
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                analysis.setAnalyzer(analyzerExecutor, image -> {
                    try { analyzeFrame(image); } finally { image.close(); }
                });

                provider.unbindAll();
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis);
            } catch (Exception e) {
                Log.e(TAG, "Camera init error", e);
                resultText.setText("Camera init error: " + e.getMessage());
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void analyzeFrame(@NonNull ImageProxy image) {
        ImageProxy.PlaneProxy[] planes = image.getPlanes();
        if (planes == null || planes.length != 3) return;

        ByteBuffer y = planes[0].getBuffer();
        ByteBuffer u = planes[1].getBuffer();
        ByteBuffer v = planes[2].getBuffer();
        if (!y.isDirect() || !u.isDirect() || !v.isDirect()) return;

        // Critical: ensure native reads from the start of each plane
        y.position(0); u.position(0); v.position(0);
        y.rewind();    u.rewind();    v.rewind();

        int w = image.getWidth(), h = image.getHeight();
        int yRS = planes[0].getRowStride(), uRS = planes[1].getRowStride(), vRS = planes[2].getRowStride();
        int uPS = planes[1].getPixelStride(), vPS = planes[2].getPixelStride();
        int rot = image.getImageInfo().getRotationDegrees();

        long t0 = System.nanoTime();
        long placeId = processYuv420(y, u, v, w, h, yRS, uRS, vRS, uPS, vPS, rot);
        long t1 = System.nanoTime();

        double ms  = (t1 - t0) / 1e6;
        double fps = 1000.0 / Math.max(ms, 1e-3);
        if (emaFps == 0.0) emaFps = fps; else emaFps = 0.9 * emaFps + 0.1 * fps;

        if (t1 - lastUiUpdateNs > 100_000_000L) { // ~10 Hz UI updates
            lastUiUpdateNs = t1;
            runOnUiThread(() ->
                    resultText.setText(String.format("OpenCV %s  |  Stub: %d  |  %.1f ms  %.1f FPS",
                            cvVersion(), placeId, ms, emaFps)));
        }
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (analyzerExecutor != null) analyzerExecutor.shutdown();
    }

    public native String stringFromJNI();
    public native String cvVersion();
    public static native long processYuv420(
            ByteBuffer y, ByteBuffer u, ByteBuffer v,
            int width, int height,
            int yRowStride, int uRowStride, int vRowStride,
            int uPixStride, int vPixStride,
            int rotationDegrees);
}