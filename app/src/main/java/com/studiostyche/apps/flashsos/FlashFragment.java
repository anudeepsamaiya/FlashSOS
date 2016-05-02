package com.studiostyche.apps.flashsos;

import android.Manifest;
import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Created by AnudeepSamaiya on 02-05-16.
 */
public class FlashFragment extends Fragment {
    final String TAG = this.getClass().getSimpleName();

    private static final String FRAGMENT_DIALOG = "dialog";

    private static final int REQUEST_CAMERA_PERMISSION = 1;

    ImageView imageView;
    private boolean flashState = false;

    CameraManager cameraManager;
    CameraCharacteristics cameraCharacteristics;
    CameraDevice cameraDevice;
    CaptureRequest.Builder builder;
    CameraCaptureSession cameraCaptureSession;
    /**
     * A {@link Semaphore} to prevent the app from exiting before closing the camera.
     */
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);

    SurfaceTexture surfaceTexture;
    Surface surface;
    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;

    public static Fragment newInstance() {
        return new FlashFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.content_main, container, false);
    }

    @Override
    public void onViewCreated(final View view, Bundle savedInstanceState) {

        setupCamera();
        setupFlashImageView(view);
    }

    @Override
    public void onResume() {
        super.onResume();
        startBackgroundThread();
        //setupCamera();
    }

    @Override
    public void onPause() {
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    private void closeCamera() {
        try {
            mCameraOpenCloseLock.acquire();
            if (null != cameraCaptureSession) {
                cameraCaptureSession.close();
                cameraCaptureSession = null;
            }
            if (null != cameraDevice) {
                cameraDevice.close();
                cameraDevice = null;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            mCameraOpenCloseLock.release();
        }
    }

    private void requestCameraPermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(getActivity(), Manifest.permission.CAMERA)) {

            ConfirmationDialog.newInstance(REQUEST_CAMERA_PERMISSION)
                    .show(getChildFragmentManager(), FRAGMENT_DIALOG);
        } else {
            ActivityCompat.requestPermissions(getActivity(),
                    new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length != 1 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                showErrorDialog(getString(R.string.request_permission));
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private void setupFlashImageView(View view) {
        // Switch button click event to toggle flash on/off
        imageView = (ImageView) view.findViewById(R.id.fab);
        imageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!flashState) {
                    flashState = true;
                    toggleFlashLight(flashState);
                    ((ImageView) v).setImageResource(R.drawable.ic_flash_off_black_24dp);

                    Snackbar.make(v, "Flash Enabled", Snackbar.LENGTH_LONG)
                            .setAction("Turn Off", new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    flashState = false;
                                    toggleFlashLight(flashState);
                                }
                            })
                            .show();
                } else {
                    flashState = false;
                    toggleFlashLight(flashState);
                    ((ImageView) v).setImageResource(R.drawable.ic_flash_on_black_24dp);

                    Snackbar.make(v, "Flash disabled", Snackbar.LENGTH_LONG)
                            .setAction("Turn On", new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    flashState = true;
                                    toggleFlashLight(flashState);
                                }
                            })
                            .show();
                }
            }
        });
    }

    private void setupCamera() {
        cameraManager =
                (CameraManager) getActivity().getSystemService(Context.CAMERA_SERVICE);
        try {
            String[] cameraId = cameraManager.getCameraIdList();
            if (null != cameraId && cameraId.length > 0) {
                cameraCharacteristics =
                        cameraManager.getCameraCharacteristics(cameraId[0]);
                Boolean flashSupported =
                        cameraCharacteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (flashSupported) {
                    if (ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                        requestCameraPermission();
                        return;
                    }
                    if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                        throw new RuntimeException("Time out waiting to lock camera opening.");
                    }
                    cameraManager.openCamera(cameraId[0], mStateCallback, mBackgroundHandler);
                }
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
            showErrorDialog(e.toString());
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }

    CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            mCameraOpenCloseLock.release();
            cameraDevice = camera;
            try {
                builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                List<Surface> list = new ArrayList<Surface>();
                surfaceTexture = new SurfaceTexture(1);
                Size size = getSmallestSize(cameraDevice.getId());
                surfaceTexture.setDefaultBufferSize(size.getWidth(), size.getHeight());
                surface = new Surface(surfaceTexture);
                list.add(surface);
                builder.addTarget(surface);
                camera.createCaptureSession(list, mCameraCaptureSessionStateCallback, mBackgroundHandler);

            } catch (CameraAccessException e) {
                e.printStackTrace();
                showErrorDialog(e.toString());
            }
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            cameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            cameraDevice = null;
            Activity activity = getActivity();
            if (null != activity) {
                activity.finish();
            }
        }
    };

    CameraCaptureSession.StateCallback mCameraCaptureSessionStateCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(CameraCaptureSession session) {
            cameraCaptureSession = session;
            try {
                cameraCaptureSession.setRepeatingRequest(builder.build(), null, null);
            } catch (CameraAccessException e) {
                e.printStackTrace();
                showErrorDialog(e.toString());
            }
        }

        @Override
        public void onConfigureFailed(CameraCaptureSession session) {

        }
    };


    private Size getSmallestSize(String cameraId) throws CameraAccessException {
        Size[] outputSizes;
        outputSizes = cameraManager.getCameraCharacteristics(cameraId)
                .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                .getOutputSizes(SurfaceTexture.class);
        if (null == outputSizes || outputSizes.length == 0) {
            throw new IllegalStateException("Camera " + cameraId
                    + " doesn't support any outputSize");
        }

        Size chosen = outputSizes[0];

        for (Size s : outputSizes)
            if (chosen.getWidth() >= s.getWidth() && chosen.getHeight() >= s.getHeight())
                chosen = s;

        return chosen;
    }

    public void toggleFlashLight(boolean flashState) {
        try {
            if (flashState)
                builder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_TORCH);
            else builder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF);

            cameraCaptureSession.setRepeatingRequest(builder.build(), null, null);

        } catch (Exception e) {
            e.printStackTrace();
            showErrorDialog("Flash failed:\n" + e.toString());
        }
    }

    /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void showErrorDialog(String message) {
        AlertDialog alertDialog =
                new AlertDialog.Builder(getActivity())
                        .setTitle("Camera initialization Error")
                        .setMessage(message)
                        .setCancelable(true)
                        .setNegativeButton("Dismiss", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {

                            }
                        }).create();
        alertDialog.show();
    }
}

