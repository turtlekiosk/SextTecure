package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
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

import com.nineoldandroids.animation.ObjectAnimator;

import org.thoughtcrime.securesms.R;

public class QuickMediaPreview extends FrameLayout implements
        GestureDetector.OnGestureListener, QuickCamera.Callback {
    private final Context context;
    private QuickCamera quickCamera;
    private View cameraControls;
    private boolean fullscreen = false;
    private Callback callback;
    private GestureDetectorCompat mDetector;
    private ImageButton fullScreenButton;
    private int newOffset;

    public QuickMediaPreview(Context context) {
        this(context, null);
    }

    public QuickMediaPreview(final Context context, AttributeSet attrs) {
        super(context, attrs);
        this.context = context;
        mDetector = new GestureDetectorCompat(context,this);
        setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                mDetector.onTouchEvent(event);
                return true;
            }
        });
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public void stop() {
        if (quickCamera != null)
            quickCamera.stopPreview();
    }

    private void adjustQuickMediaOffset(float position) {
        if (quickCamera != null) {
            ObjectAnimator slideAnimator = ObjectAnimator.ofFloat(quickCamera, "translationY", position);
            slideAnimator.setDuration(0);
            slideAnimator.start();
        }
    }

    private void adjustQuickMediaOffsetWithDelay(float position) {
        if (quickCamera != null) {
            ObjectAnimator slideAnimator = ObjectAnimator.ofFloat(quickCamera, "translationY", position);
            slideAnimator.setDuration(200);
            slideAnimator.start();
        }
    }

    private void setFullscreenCapture(boolean fullscreen) {
        this.fullscreen = fullscreen;
        if (callback != null) callback.onSetFullScreen(fullscreen);
        if (fullscreen) {
            if (quickCamera != null) {
                adjustQuickMediaOffsetWithDelay(0f);
                if (fullScreenButton != null)
                    fullScreenButton.setImageResource(R.drawable.quick_camera_exit_fullscreen);
            }
        } else {
            newOffset = (getHeight() - getResources().getDimensionPixelOffset(R.dimen.media_preview_height)) / 2;
            adjustQuickMediaOffsetWithDelay(newOffset);
            if (fullScreenButton != null)
                fullScreenButton.setImageResource(R.drawable.quick_camera_fullscreen);
        }
    }

    private void initializeCameraControls() {
        if (cameraControls != null)
            removeView(cameraControls);
        cameraControls = inflate(context, R.layout.quick_camera_controls, null);
        addView(cameraControls);
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
        if (callback != null) {
            callback.onScroll(e1, e2, distanceX, distanceY);
            if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                if (quickCamera != null) {
                    float startY = quickCamera.getY();
                    float newY = startY - distanceY;
                    if (newY >= 0)
                        quickCamera.setY(newY);
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public void onLongPress(MotionEvent e) {

    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        if (velocityY > 1) {
            if (fullscreen) {
                setFullscreenCapture(false);
            } else {
                hide();
            }
            return true;
        } else if (velocityY < -1) {
            setFullscreenCapture(true);
            return true;
        } else {
            if (callback != null) callback.onDragRelease(velocityY);
        }
        return false;
    }

    public interface Callback {
        public void onImageCapture(Uri imageUri);
        public void onSetFullScreen(boolean fullscreen);
        public void onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY);
        public void onShow();
        public void onHide();
        public void onDragRelease(float distanceY);
    }

    public void show(boolean fullscreen) {
        if (callback != null) callback.onShow();
        setFullscreenCapture(fullscreen);
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
        removeAllViews();
        quickCamera = new QuickCamera(context, this);
        addView(quickCamera);
        initializeCameraControls();
        setVisibility(View.VISIBLE);
        newOffset = (getHeight() - getResources().getDimensionPixelOffset(R.dimen.media_preview_height)) / 2;
        adjustQuickMediaOffsetWithDelay(newOffset);
        if (quickCamera != null)
            quickCamera.startPreview();
    }

    @Override
    public void displayCameraInUseCopy(boolean inUse) {
        if (cameraControls !=  null) {
            View errorCopy = cameraControls.findViewById(R.id.camera_unavailable_label);
            if (errorCopy != null) errorCopy.setVisibility(inUse ? View.VISIBLE : View.GONE);
        }
    }
}
