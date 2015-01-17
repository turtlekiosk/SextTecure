package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.content.res.Configuration;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Environment;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class QuickCamera extends SurfaceView implements SurfaceHolder.Callback {
    public static final int MEDIA_TYPE_IMAGE = 1;
    public static final int MEDIA_TYPE_VIDEO = 2;
    private SurfaceHolder surfaceHolder;
    private Camera camera;
    private Context context;
    private boolean running;
    private final String TAG = "QuickCamera";
    private int cameraId = Camera.CameraInfo.CAMERA_FACING_BACK;

    public QuickCamera(Context context) {
        super(context);
        this.context = context;
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        int width = displayMetrics.widthPixels;
        int height = displayMetrics.heightPixels;
        ViewGroup.LayoutParams layoutParams = new ViewGroup.LayoutParams(width, height);
        setLayoutParams(layoutParams);
        surfaceHolder = getHolder();
        surfaceHolder.addCallback(this);
        surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        running = false;
        camera = getCameraInstance(cameraId);
    }

    public static Camera getCameraInstance(int cameraId){
        Camera c = null;
        try {
            c = Camera.open(cameraId);
        }
        catch (Exception e){
            //TODO: explain in view that camera is unavailable
        }
        return c;
    }

    private Camera.Size getOptimalPreviewSize(List<Camera.Size> sizes, int width, int height) {
        final double ASPECT_TOLERANCE = 0.1;
        double targetRatio=(double)height / width;

        if (sizes == null) return null;

        Camera.Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;

        int targetHeight = height;

        for (Camera.Size size : sizes) {
            double ratio = (double) size.width / size.height;
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue;
            if (Math.abs(size.height - targetHeight) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.height - targetHeight);
            }
        }

        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE;
            for (Camera.Size size : sizes) {
                if (Math.abs(size.height - targetHeight) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - targetHeight);
                }
            }
        }
        return optimalSize;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (camera == null)
            camera = getCameraInstance(cameraId);
        try {
            camera.setPreviewDisplay(holder);
            if (running)
                camera.startPreview();
        } catch(IOException e){
            //TODO: explain in view that camera is unavailable
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        if (surfaceHolder.getSurface() == null){
            return;
        }
        try {
            camera.stopPreview();
        } catch (Exception e){
        }
        startPreview();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        //TODO: clean up everything
        if (camera != null) {
            camera.stopPreview();
            camera.release();
            camera = null;
        }
    }

    public void startPreview() {
        running = true;
        if (camera == null) camera = getCameraInstance(cameraId);
        setupPreview();
        camera.startPreview();
    }

    public void stopPreview() {
        running = false;
        if (camera != null) {
            camera.stopPreview();
            camera.release();
            camera = null;
        }
    }

    public void takePicture(final QuickMediaPreview.Callback callback) {
        if (camera != null) {
            camera.takePicture(null, null, new Camera.PictureCallback() {
                @Override
                public void onPictureTaken(byte[] data, Camera camera) {
                    File pictureFile = getOutputMediaFile(MEDIA_TYPE_IMAGE);
                    if (pictureFile == null){
                        return;
                    }

                    try {
                        FileOutputStream fos = new FileOutputStream(pictureFile);
                        fos.write(data);
                        fos.close();
                    } catch (FileNotFoundException e) {
                        Log.d(TAG, "File not found: " + e.getMessage());
                    } catch (IOException e) {
                        Log.d(TAG, "Error accessing file: " + e.getMessage());
                    }
                    callback.onImageCapture(getOutputMediaFileUri(MEDIA_TYPE_IMAGE));
                }
            });
        }
    }

    public void setupPreview() {
        try {
            camera.setPreviewDisplay(surfaceHolder);
            int rotation = (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT ? 90 : 0);
            camera.setDisplayOrientation(rotation);
            Camera.Parameters parameters = camera.getParameters();
            Camera.Size previewSize = getOptimalPreviewSize(parameters.getSupportedPreviewSizes(), getWidth(), getHeight());
            parameters.setPreviewSize(previewSize.width, previewSize.height);
            camera.setParameters(parameters);
        } catch (Exception e) {
            //TODO: explain in view that camera preview is unavailable
        }
    }

    private static Uri getOutputMediaFileUri(int type){
        return Uri.fromFile(getOutputMediaFile(type));
    }

    private static File getOutputMediaFile(int type){

        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), "ts_file");
        if (! mediaStorageDir.exists()){
            if (! mediaStorageDir.mkdirs()){
                Log.d("ts_file", "failed to create directory");
                return null;
            }
        }

        // Create a media file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File mediaFile;
        if (type == MEDIA_TYPE_IMAGE){
            mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                    "IMG_"+ timeStamp + ".jpg");
        } else if(type == MEDIA_TYPE_VIDEO) {
            mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                    "VID_"+ timeStamp + ".mp4");
        } else {
            return null;
        }

        return mediaFile;
    }

    public boolean isMultipleCameras() {
        return Camera.getNumberOfCameras() > 1;
    }

    public void swapCamera() {
        if (isMultipleCameras()) {
            cameraId = (cameraId == Camera.CameraInfo.CAMERA_FACING_BACK) ? Camera.CameraInfo.CAMERA_FACING_FRONT : Camera.CameraInfo.CAMERA_FACING_BACK;
            stopPreview();
            startPreview();
        }
    }

    public boolean isBackCamera() {
        return cameraId == Camera.CameraInfo.CAMERA_FACING_BACK;
    }
}
