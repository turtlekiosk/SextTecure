package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.content.res.Configuration;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Environment;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import org.thoughtcrime.securesms.R;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class QuickMediaDrawer extends LinearLayout {
    private final Context context;
    private ImageButton cameraButton, audioButton;
    private FrameLayout controlContainer, cameraContainer;
    private QuickCamera quickCamera;
    private View buttonBar, cameraControls;
    private boolean fullscreen = false;
    private Callback callback;

    public QuickMediaDrawer(Context context) {
        this(context, null);
    }

    public QuickMediaDrawer(final Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        this.context = context;
        buttonBar = inflate(this.context, R.layout.quick_media_bar, null);
        controlContainer = (FrameLayout) inflate(this.context, R.layout.quick_media_control_container, null);
        this.addView(buttonBar);
        this.addView(controlContainer);
        cameraButton = (ImageButton) findViewById(R.id.camera_button);
        audioButton = (ImageButton) findViewById(R.id.audio_button);
        cameraButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (controlContainer != null && cameraContainer != null) {
                    cameraContainer.removeAllViews();
                    quickCamera = new QuickCamera(context);
                    cameraContainer.addView(quickCamera);
                    initializeCameraControls();
                    cameraContainer.setVisibility(View.VISIBLE);
                    controlContainer.setVisibility(View.VISIBLE);
                    adjustQuickMediaOffset();
                }
            }
        });
    }

    public void setCameraContainer(FrameLayout cameraContainer) {
        this.cameraContainer = cameraContainer;
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public void start() {
        if (controlContainer != null && cameraContainer != null) {
            cameraContainer.removeAllViews();
            quickCamera = new QuickCamera(context);
            cameraContainer.addView(quickCamera);
            initializeCameraControls();
            cameraContainer.setVisibility(View.VISIBLE);
            controlContainer.setVisibility(View.VISIBLE);
            adjustQuickMediaOffset();
        }
        if (quickCamera != null)
            quickCamera.startPreview();
    }

    public void stop() {
        if (quickCamera != null)
            quickCamera.stopPreview();
        if (cameraContainer != null)
            cameraContainer.setVisibility(View.INVISIBLE);
        if (controlContainer != null)
            controlContainer.setVisibility(View.GONE);
    }

    private void adjustQuickMediaOffset() {
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        int pixelOffset = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                getResources().getDimensionPixelOffset(R.dimen.media_preview_height),
                getResources().getDisplayMetrics());
        int newOffset;
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT)
            newOffset = (displayMetrics.widthPixels - pixelOffset)/2;
        else
            newOffset = (displayMetrics.heightPixels - pixelOffset)/2;
        LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) controlContainer.getLayoutParams();
        layoutParams.setMargins(0, newOffset, 0, 0);
        controlContainer.setLayoutParams(layoutParams);
    }

    private void setFullscreenCapture(boolean fullscreen) {
        if (callback != null) callback.onSetFullScreen(fullscreen);
        if (fullscreen) {
            LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) controlContainer.getLayoutParams();
            layoutParams.setMargins(0, 0, 0, 0);
            controlContainer.setLayoutParams(layoutParams);
        } else {
            adjustQuickMediaOffset();
        }
    }

    private void initializeCameraControls() {
        cameraControls = inflate(context, R.layout.quick_camera_controls, null);
        controlContainer.addView(cameraControls);
        ImageButton captureButton = (ImageButton) cameraControls.findViewById(R.id.shutter_button);
        captureButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                takePicture();
            }
        });
        ImageButton fullscreenCaptureButton = (ImageButton) cameraControls.findViewById(R.id.fullscreen_button);
        fullscreenCaptureButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                setFullscreenCapture(!fullscreen);
                fullscreen = !fullscreen;
            }
        });
    }

    private void takePicture() {
        setFullscreenCapture(false);
        if (quickCamera != null) {
            quickCamera.takePicture(callback);
        }
    }

    public interface Callback {
        public void onImageCapture(Uri imageUri);
        public void onSetFullScreen(boolean fullscreen);
    }

    public void show() {
        start();
        setVisibility(View.VISIBLE);
    }

    public void hide() {
        stop();
        setVisibility(View.GONE);
    }

    public boolean isShown() {
        return getVisibility() == View.VISIBLE;
    }
}
