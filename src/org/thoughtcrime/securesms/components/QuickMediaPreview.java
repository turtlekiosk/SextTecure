package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.content.res.Configuration;
import android.net.Uri;
import android.support.v4.view.GestureDetectorCompat;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;

import org.thoughtcrime.securesms.R;

public class QuickMediaPreview extends FrameLayout implements GestureDetector.OnGestureListener {
    private final Context context;
    private FrameLayout controlContainer, cameraContainer;
    private QuickCamera quickCamera;
    private View cameraControls;
    private boolean fullscreen = false;
    private Callback callback;
    private GestureDetectorCompat mDetector;
    private ImageButton fullScreenButton;

    public QuickMediaPreview(Context context) {
        this(context, null);
    }

    public QuickMediaPreview(final Context context, AttributeSet attrs) {
        super(context, attrs);
        this.context = context;
        controlContainer = (FrameLayout) inflate(this.context, R.layout.quick_media_control_container, null);
        this.addView(controlContainer);
        mDetector = new GestureDetectorCompat(context,this);
        setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                mDetector.onTouchEvent(event);
                return true;
            }
        });
    }

    public void setCameraContainer(FrameLayout cameraContainer) {
        this.cameraContainer = cameraContainer;
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
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
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) controlContainer.getLayoutParams();
        layoutParams.setMargins(0, newOffset, 0, 0);
        controlContainer.setLayoutParams(layoutParams);
    }

    private void setFullscreenCapture(boolean fullscreen) {
        this.fullscreen = fullscreen;
        if (callback != null) callback.onSetFullScreen(fullscreen);
        if (fullscreen) {
            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) controlContainer.getLayoutParams();
            layoutParams.setMargins(0, 0, 0, 0);
            controlContainer.setLayoutParams(layoutParams);
            if (fullScreenButton != null)
                fullScreenButton.setImageResource(R.drawable.quick_camera_exit_fullscreen);
        } else {
            adjustQuickMediaOffset();
            if (fullScreenButton != null)
                fullScreenButton.setImageResource(R.drawable.quick_camera_fullscreen);
        }
    }

    private void initializeCameraControls() {
        cameraControls = inflate(context, R.layout.quick_camera_controls, null);
        controlContainer.removeAllViews();
        controlContainer.addView(cameraControls);
        ImageButton captureButton = (ImageButton) cameraControls.findViewById(R.id.shutter_button);
        captureButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                takePicture();
            }
        });
        final ImageButton fullscreenCaptureButton = (ImageButton) cameraControls.findViewById(R.id.fullscreen_button);
        fullScreenButton = fullscreenCaptureButton;
        fullscreenCaptureButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                setFullscreenCapture(!fullscreen);
            }
        });
        final ImageButton swapCameraButton = (ImageButton) cameraControls.findViewById(R.id.swap_camera_button);
        if (quickCamera.isMultipleCameras()) {
            swapCameraButton.setVisibility(View.VISIBLE);
            swapCameraButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    quickCamera.swapCamera();
                    swapCameraButton.setImageResource(quickCamera.isBackCamera() ? R.drawable.quick_camera_front : R.drawable.quick_camera_rear);
                }
            });
        }
    }

    private void takePicture() {
        setFullscreenCapture(false);
        if (quickCamera != null) {
            quickCamera.takePicture(callback);
        }
    }

    @Override
    public boolean onDown(MotionEvent e) {
        return true;
    }

    @Override
    public void onShowPress(MotionEvent e) {

    }

    @Override
    public boolean onSingleTapUp(MotionEvent e) {
        return false;
    }

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        //int newPadding = (int) (getPaddingTop() - distanceY);
        //setPadding(0, newPadding, 0, 0);
        if (callback != null) {
            callback.onScroll(distanceY);
            return true;
        }
        return false;
    }

    @Override
    public void onLongPress(MotionEvent e) {

    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        if (velocityY > 3) {
            if (fullscreen) {
                setFullscreenCapture(false);
            } else {
                hide();
            }
            return true;
        } else if (velocityY < -3) {
            setFullscreenCapture(true);
            return true;
        } else if (velocityY > 0) {
            if (callback != null) callback.onDragRelease(velocityY);
        }
        return false;
    }

    public interface Callback {
        public void onImageCapture(Uri imageUri);
        public void onSetFullScreen(boolean fullscreen);
        public void onScroll(float distanceY);
        public void onShow();
        public void onHide();
        public void onDragRelease(float distanceY);
    }

    public void show() {
        if (callback != null) callback.onShow();
        InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(getWindowToken(), 0);
    }

    public void hide() {
        if (callback != null) callback.onHide();
    }

    public boolean isShown() {
        return getVisibility() == View.VISIBLE;
    }

    public boolean isFullscreen() {
        return fullscreen;
    }

    public void switchAudio() {
        quickCamera.stopPreview();
        //TODO: add audio initialization
    }

    public void switchImages() {
        quickCamera.stopPreview();
        //TODO: add image initialization
    }

    public void switchCamera() {
        //TODO: pause other inputs
        if (controlContainer != null && cameraContainer != null) {
            cameraContainer.removeAllViews();
            quickCamera = new QuickCamera(context);
            cameraContainer.addView(quickCamera);
            initializeCameraControls();
            cameraContainer.setVisibility(View.VISIBLE);
            controlContainer.setVisibility(View.VISIBLE);
            adjustQuickMediaOffset();
            if (quickCamera != null)
                quickCamera.startPreview();
        }
    }
}
