package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.support.v4.view.GestureDetectorCompat;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;

import com.nineoldandroids.animation.Animator;
import com.nineoldandroids.animation.ObjectAnimator;

import org.thoughtcrime.securesms.R;

public class QuickMediaPreview extends FrameLayout implements
        GestureDetector.OnGestureListener, QuickCamera.Callback {
    private static final int ANIMATION_LENGTH = 200;
    private final Context context;
    private QuickCamera quickCamera;
    private View cameraControls;
    private boolean fullscreen = false;
    private Callback callback;
    private GestureDetectorCompat mDetector;
    private ImageButton fullScreenButton;
    private int newOffset;
    private boolean forceFullscreen = false;
    private ViewGroup coverView;
    private float baseY;

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

    public void setCoverView(ViewGroup coverView) {
        this.coverView = coverView;
        baseY = coverView.getTop();
    }

    public void stop() {
        if (quickCamera != null)
            quickCamera.stopPreview();
    }

    public void setForceFullscreen(boolean forceFullscreen) {
        this.forceFullscreen = forceFullscreen;
        if (fullScreenButton != null)
            fullScreenButton.setImageResource(forceFullscreen ? R.drawable.quick_camera_hide : R.drawable.quick_camera_fullscreen);
    }

    private void setFullscreenCapture(boolean fullscreen) {
        this.fullscreen = fullscreen;
        if (callback != null) callback.onSetFullScreen(fullscreen);
        if (fullscreen) {
            animateVerticalTranslationToPosition(quickCamera, 0f);
            if (fullScreenButton != null)
                fullScreenButton.setImageResource(forceFullscreen ? R.drawable.quick_camera_hide : R.drawable.quick_camera_exit_fullscreen);
            if (coverView != null)
                animateVerticalTranslationToPosition(coverView, baseY - coverView.getHeight());
        } else {
            newOffset = (getHeight() - getResources().getDimensionPixelOffset(R.dimen.media_preview_height)) / 2;
            animateVerticalTranslationToPosition(quickCamera, newOffset);
            if (fullScreenButton != null && !forceFullscreen)
                fullScreenButton.setImageResource(R.drawable.quick_camera_fullscreen);
            if (coverView != null) {
                coverView.setVisibility(View.VISIBLE);
                float newY = forceFullscreen ? baseY : baseY - getResources().getDimension(R.dimen.media_preview_height);
                animateVerticalTranslationToPosition(coverView, newY);
            }
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
        fullScreenButton = (ImageButton) cameraControls.findViewById(R.id.fullscreen_button);
        fullScreenButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (forceFullscreen) {
                    hide();
                    setFullscreenCapture(false);
                } else {
                    setFullscreenCapture(!fullscreen);
                }
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
        if (quickCamera != null)
            quickCamera.takePicture(callback);
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
        if (coverView != null) {
            if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                float startY = coverView.getY();
                float newY = startY - distanceY;
                coverView.setY(newY);
                coverView.requestLayout();
                if (quickCamera != null) {
                    startY = quickCamera.getY();
                    newY = startY - distanceY;
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
        if (velocityY > 0) {
            if (fullscreen && !forceFullscreen) {
                setFullscreenCapture(false);
            } else {
                setFullscreenCapture(false);
                hide();
            }
            return true;
        } else if (velocityY < 0) {
            setFullscreenCapture(true);
            return true;
        }
        return false;
    }

    public interface Callback {
        public void onImageCapture(Uri imageUri);
        public void onSetFullScreen(boolean fullscreen);
    }

    public void show() {
        start();
        setVisibility(View.VISIBLE);
        setFullscreenCapture(forceFullscreen);
        InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(getWindowToken(), 0);
    }

    public void hide() {
        if (coverView != null) {
            ObjectAnimator slidedownAnimator = ObjectAnimator.ofFloat(coverView, "translationY", baseY);
            slidedownAnimator.setDuration(200);
            slidedownAnimator.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    setVisibility(View.INVISIBLE);
                    stop();
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                }

                @Override
                public void onAnimationRepeat(Animator animation) {
                }
            });
            slidedownAnimator.start();
        }
    }

    public boolean isShown() {
        return getVisibility() == View.VISIBLE;
    }

    public boolean isFullscreen() {
        return fullscreen;
    }

    public void start() {
        removeAllViews();
        quickCamera = new QuickCamera(context, this);
        addView(quickCamera);
        initializeCameraControls();
        setVisibility(View.VISIBLE);
        newOffset = (getHeight() - getResources().getDimensionPixelOffset(R.dimen.media_preview_height)) / 2;
        animateVerticalTranslationToPosition(quickCamera, newOffset);
        quickCamera.startPreview();
    }

    @Override
    public void displayCameraInUseCopy(boolean inUse) {
        if (cameraControls !=  null) {
            View errorCopy = cameraControls.findViewById(R.id.camera_unavailable_label);
            if (errorCopy != null) errorCopy.setVisibility(inUse ? View.VISIBLE : View.GONE);
        }
    }

    private static void animateVerticalTranslationToPosition(View view, float position) {
        if (view != null) {
            float offset = position - view.getTop();
            ObjectAnimator slideAnimator = ObjectAnimator.ofFloat(view, "translationY", offset);
            slideAnimator.setDuration(ANIMATION_LENGTH);
            slideAnimator.start();
        }
    }
}
