package com.mustafa.datecontroller;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.SparseArray;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Toast;

import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.text.TextBlock;
import com.google.android.gms.vision.text.TextRecognizer;
import com.mustafa.datecontroller.databinding.ActivityMainBinding;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class MainActivity extends AppCompatActivity  implements SurfaceHolder.Callback, Camera.PreviewCallback {

    private ActivityMainBinding binding;
    private static final String TAG = "MainActivity";
    private static final int REQUEST_CAMERA_PERMISSION = 100;

    private Camera mCamera;
    private SurfaceHolder mSurfaceHolder;
    private boolean mIsPreviewing = false;
    private Handler mHandler = new Handler();
    private Runnable mCheckExpirationDateTask = new Runnable() {
        @Override
        public void run() {
            checkExpirationDate();
        }
    };

    private static final String CHANNEL_ID = "channel_id";
    private static final int NOTIFICATION_ID = 1;
    private static final String KEY_LAST_NOTIFICATION_DATE = "last_notification_date";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mSurfaceHolder = binding.surfaceView.getHolder();
        mSurfaceHolder.addCallback(this);

        createNotificationChannel();

        // Check camera permission
        checkCameraPermission();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Channel Name", NotificationManager.IMPORTANCE_DEFAULT);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        mCamera = Camera.open();

        Camera.Parameters parameters = mCamera.getParameters();
        parameters.setPreviewSize(640, 480);
        parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
        parameters.setPreviewFormat(ImageFormat.NV21);
        mCamera.setParameters(parameters);

        try {
            mCamera.setPreviewDisplay(surfaceHolder);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int format, int width, int height) {
        if (mIsPreviewing) {
            mCamera.stopPreview();
        }

        try {
            mCamera.setPreviewDisplay(surfaceHolder);
            mCamera.setPreviewCallback(this);
            mCamera.startPreview();
            mIsPreviewing = true;

            // Start checking expiration date every minute
            mHandler.postDelayed(mCheckExpirationDateTask, 60000);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        mHandler.removeCallbacks(mCheckExpirationDateTask);

        mCamera.setPreviewCallback(null);
        mCamera.stopPreview();
        mCamera.release();
        mCamera = null;
        mIsPreviewing = false;
    }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        // Convert camera preview frame to YuvImage format
        Camera.Parameters parameters = camera.getParameters();
        int imageFormat = parameters.getPreviewFormat();
        int previewWidth = parameters.getPreviewSize().width;
        int previewHeight = parameters.getPreviewSize().height;
        YuvImage yuvImage = new YuvImage(data, imageFormat, previewWidth, previewHeight, null);
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, previewWidth, previewHeight), 100, byteArrayOutputStream);

        // Convert JPEG data to byte array
        byte[] jpegData = byteArrayOutputStream.toByteArray();

        // Decode the JPEG data to a Bitmap and analyze it for expiration date
        // ...

        // Save last notification date
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        sharedPreferences.edit().putLong(KEY_LAST_NOTIFICATION_DATE, System.currentTimeMillis()).apply();
    }

    private void checkExpirationDate() {
        // Get last notification date
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        long lastNotificationDate = sharedPreferences.getLong(KEY_LAST_NOTIFICATION_DATE, 0);

        // Check if 10 days have passed since the last notification
        long currentTime = System.currentTimeMillis();
        long diff = currentTime - lastNotificationDate;
        long diffInDays = diff / (24 * 60 * 60 * 1000);
        if (diffInDays >= 10) {
            // Send notification
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle("Expiration date is approaching")
                    .setContentText("The expiration date is less than 10 days away. Please check the product.")
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT);

            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
            notificationManager.notify(NOTIFICATION_ID, builder.build());
        }

        // Schedule next check in a minute
        mHandler.postDelayed(mCheckExpirationDateTask, 60000);
    }



    // Check if the camera permission is granted, and request it if not
    private void checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(getApplicationContext(), android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        } else {
            // Permission already granted, start camera preview
            startCameraPreview();
        }
    }

    // Handle the result of the camera permission request
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, start camera preview
                startCameraPreview();
            } else {
                // Permission denied, show message and close the activity
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    // Start camera preview
    private void startCameraPreview() {
        // Check if the device has a camera
        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            // Open the camera
            mCamera = Camera.open();

            // Set the camera parameters
            Camera.Parameters parameters = mCamera.getParameters();
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            parameters.setFlashMode(Camera.Parameters.FLASH_MODE_AUTO);
            mCamera.setParameters(parameters);

            // Set the preview display
            try {
                mCamera.setPreviewDisplay(mSurfaceHolder);
            } catch (Exception e) {
                e.printStackTrace();
            }

            // Start the preview
            mCamera.startPreview();
            mIsPreviewing = true;

            // Start checking expiration date every minute
            mHandler.postDelayed(mCheckExpirationDateTask, 6);
        } else {
            // Device doesn't have a camera, show message and close the activity
            Toast.makeText(this, "This device doesn't have a camera", Toast.LENGTH_SHORT).show();
            finish();
        }
    }


}