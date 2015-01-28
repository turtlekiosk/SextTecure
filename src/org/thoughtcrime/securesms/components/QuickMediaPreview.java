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
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.nineoldandroids.animation.Animator;
import com.nineoldandroids.animation.AnimatorListenerAdapter;
import com.nineoldandroids.animation.ObjectAnimator;

import org.thoughtcrime.securesms.R;

public class QuickMediaPreview extends FrameLayout implements
        GestureDetector.OnGestureListener, QuickCamera.Callback {
    private static final int ANIMATION_LENGTH = 200;
    private QuickCamera quickCamera;
    private View cameraControls;
    private boolean fullscreen = false;
    private Callback callback;
    private GestureDetectorCompat mDetector;
    private ImageButton fullScreenButton;
    private boolean forceFullscreen = false;
    private ViewGroup coverView;
    private float baseY;
    private boolean shown = false;

    public QuickMediaPreview(Context context) {
        this(context, null);
    }

    public QuickMediaPreview(final Context context, AttributeSet attrs) {
        super(context, attrs);
        /*this.setClipChildren(false);
        this.setClipToPadding(false);
        this.setChildrenDrawingOrderEnabled(true);
        this.setStaticTransformationsEnabled(true);*/
        mDetector = new GestureDetectorCompat(context, this);
        setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                mDetector.onTouchEvent(event);
                return true;
            }
        });
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        coverView = (ViewGroup) findViewById(R.id.content);
        baseY = coverView.getTop();
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
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
            int newOffset = (getHeight() - getResources().getDimensionPixelOffset(R.dimen.media_preview_height)) / 2;
            animateVerticalTranslationToPosition(quickCamera, newOffset);
            if (fullScreenButton != null && !forceFullscreen)
                fullScreenButton.setImageResource(R.drawable.quick_camera_fullscreen);
            if (coverView != null) {
                float newY = forceFullscreen ? baseY : baseY - getResources().getDimension(R.dimen.media_preview_height);
                animateVerticalTranslationToPosition(coverView, newY);
            }
        }
    }

    private void initializeCameraControls() {
        if (cameraControls != null)
            removeView(cameraControls);
        cameraControls = inflate(getContext(), R.layout.quick_camera_controls, null);
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
            } else {
                if (!(coverView.getLayoutParams() instanceof MarginLayoutParams))
                    return false;
                MarginLayoutParams marginLayoutParams = (MarginLayoutParams) coverView.getLayoutParams();
                marginLayoutParams.bottomMargin = (int) (marginLayoutParams.bottomMargin + distanceY);
                if (!(quickCamera.getLayoutParams() instanceof MarginLayoutParams))
                    return false;
                MarginLayoutParams cameraMarginLayoutParams = (MarginLayoutParams) quickCamera.getLayoutParams();
                cameraMarginLayoutParams.bottomMargin = (int) (cameraMarginLayoutParams.bottomMargin + distanceY);
            }
            coverView.requestLayout();
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
        shown = true;
        start();
        setFullscreenCapture(forceFullscreen);
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(getWindowToken(), 0);
    }

    public void hide() {
        shown = false;
        animateVerticalTranslationToPosition(coverView, 0f);
        coverView.requestLayout();
    }

    public boolean isShown() {
        return shown;
    }

    public boolean isFullscreen() {
        return fullscreen;
    }

    public void start() {
        //removeAllViews();
        removeView(quickCamera);
        removeView(coverView);
        quickCamera = new QuickCamera(getContext(), this);
        addView(quickCamera);
        initializeCameraControls();
        addView(coverView);
        int newOffset = (getHeight() - getResources().getDimensionPixelOffset(R.dimen.media_preview_height)) / 2;
        animateVerticalTranslationToPosition(quickCamera, newOffset);
        quickCamera.startPreview();
    }

    @Override
    public void displayCameraInUseCopy(boolean inUse) {
        hide();
        removeView(coverView);
        addView(coverView);
        Toast.makeText(getContext(), R.string.quick_media_preview_camera_in_use, Toast.LENGTH_SHORT).show();
    }

    private static void animateVerticalTranslationToPosition(final View view, float position) {
        if (view != null) {
            final float offset = position - view.getTop();
            ObjectAnimator slideAnimator = ObjectAnimator.ofFloat(view, "translationY", offset);
            slideAnimator.setDuration(ANIMATION_LENGTH);
            /*if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
                slideAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) view.getLayoutParams();
                        if (offset > 0)
                            params.bottomMargin = (int) offset;
                        else
                            params.bottomMargin = 0;
                        view.setLayoutParams(params);
                        view.requestLayout();
                    }
                });
            }*/
            slideAnimator.start();
        }

    }
}
