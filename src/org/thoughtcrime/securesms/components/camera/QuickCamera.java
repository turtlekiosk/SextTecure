package org.thoughtcrime.securesms.components.camera;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.support.annotation.NonNull;
import android.support.v4.view.GestureDetectorCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;

import com.commonsware.cwac.camera.CameraHost.FailureReason;
import com.commonsware.cwac.camera.SimpleCameraHost;
import com.nineoldandroids.animation.Animator;
import com.nineoldandroids.animation.AnimatorInflater;
import com.nineoldandroids.animation.AnimatorListenerAdapter;
import com.nineoldandroids.animation.ValueAnimator;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.BitmapUtil;

import java.io.IOException;
import java.util.List;

@SuppressWarnings("deprecation") public class QuickCamera extends CameraView {
  private static final String TAG = QuickCamera.class.getSimpleName();

  private QuickCameraListener   listener;
  private boolean               capturing;
  private boolean               started;
  private QuickCameraHost       cameraHost;
  private GestureDetectorCompat gestureDetector;
  private boolean               focusAnimationRunning;
  private float                 focusCircleX, focusCircleY, focusCircleRadius;
  private Paint                 focusCirclePaint;
  private ValueAnimator         focusCircleExpandAnimation, focusCircleFadeOutAnimation;

  public QuickCamera(Context context) {
    this(context, null);
  }

  public QuickCamera(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public QuickCamera(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    cameraHost = new QuickCameraHost(context);
    setHost(cameraHost);
    if (Build.VERSION.SDK_INT >= VERSION_CODES.ICE_CREAM_SANDWICH) setupFocusAnimation();
  }

  @Override
  public void onResume() {
    if (started) return;
    super.onResume();
    started = true;
  }

  @Override
  public void onPause() {
    if (!started) return;
    super.onPause();
    started = false;
    focusAnimationRunning = false;
  }

  private void setupFocusAnimation() {
    TapToFocusListener tapToFocusListener = new TapToFocusListener();
    gestureDetector = new GestureDetectorCompat(getContext(), tapToFocusListener);
    gestureDetector.setIsLongpressEnabled(false);
    focusAnimationRunning = false;
    focusCircleX = focusCircleY = 0;
    focusCirclePaint = new Paint();
    focusCirclePaint.setAntiAlias(true);
    focusCirclePaint.setColor(Color.WHITE);
    focusCirclePaint.setStyle(Paint.Style.STROKE);
    focusCirclePaint.setStrokeWidth(5);

    focusCircleExpandAnimation = (ValueAnimator) AnimatorInflater.loadAnimator(getContext(), R.animator.quick_camera_focus_circle_expand);
    focusCircleExpandAnimation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
      @Override
      public void onAnimationUpdate(ValueAnimator animation) {
        focusCircleRadius = (Float) animation.getAnimatedValue();
        invalidate();
      }
    });
    focusCircleExpandAnimation.addListener(new AnimatorListenerAdapter() {
      @Override
      public void onAnimationStart(Animator animation) {
        focusCirclePaint.setAlpha(255);
        focusAnimationRunning = true;
      }
    });

    focusCircleFadeOutAnimation = (ValueAnimator) AnimatorInflater.loadAnimator(getContext(), R.animator.quick_camera_focus_circle_fade_out);
    focusCircleFadeOutAnimation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
      @Override
      public void onAnimationUpdate(ValueAnimator animation) {
        focusCirclePaint.setAlpha((int) animation.getAnimatedValue());
        invalidate();
      }
    });
    focusCircleFadeOutAnimation.addListener(new AnimatorListenerAdapter() {
      @Override
      public void onAnimationEnd(Animator animation) {
        focusAnimationRunning = false;
        invalidate();
      }
    });
  }

  public boolean isStarted() {
    return started;
  }

  public void takePicture(final Rect previewRect) {
    if (capturing) {
      Log.w(TAG, "takePicture() called while previous capture pending.");
      return;
    }

    final Parameters cameraParameters = getCameraParameters();
    if (cameraParameters == null) {
      Log.w(TAG, "camera not in capture-ready state");
      return;
    }

    setOneShotPreviewCallback(new Camera.PreviewCallback() {
      @Override
      public void onPreviewFrame(byte[] data, final Camera camera) {
        final int rotation      = getCameraPictureOrientation();
        final Size previewSize  = cameraParameters.getPreviewSize();
        final Rect croppingRect = getCroppedRect(previewSize, previewRect, rotation);

        Log.w(TAG, "previewSize: " + previewSize.width + "x" + previewSize.height);
        Log.w(TAG, "croppingRect: " + croppingRect.toString());
        Log.w(TAG, "rotation: " + rotation);
        new AsyncTask<byte[], Void, byte[]>() {
          @Override
          protected byte[] doInBackground(byte[]... params) {
            byte[] data = params[0];
            try {

              return BitmapUtil.createFromNV21(data,
                                               previewSize.width,
                                               previewSize.height,
                                               rotation,
                                               croppingRect);
            } catch (IOException e) {
              return null;
            }
          }

          @Override
          protected void onPostExecute(byte[] imageBytes) {
            capturing = false;
            if (imageBytes != null && listener != null) listener.onImageCapture(imageBytes);
          }
        }.execute(data);
      }
    });
  }

  private Rect getCroppedRect(Size cameraPreviewSize, Rect visibleRect, int rotation) {
    final int previewWidth  = cameraPreviewSize.width;
    final int previewHeight = cameraPreviewSize.height;

    if (rotation % 180 > 0) rotateRect(visibleRect);

    float scale = (float) previewWidth / visibleRect.width();
    if (visibleRect.height() * scale > previewHeight) {
      scale = (float) previewHeight / visibleRect.height();
    }
    final float newWidth  = visibleRect.width()  * scale;
    final float newHeight = visibleRect.height() * scale;
    final float centerX   = previewWidth         / 2;
    final float centerY   = previewHeight        / 2;
    visibleRect.set((int) (centerX - newWidth  / 2),
                    (int) (centerY - newHeight / 2),
                    (int) (centerX + newWidth  / 2),
                    (int) (centerY + newHeight / 2));

    if (rotation % 180 > 0) rotateRect(visibleRect);
    return visibleRect;
  }

  @SuppressWarnings("SuspiciousNameCombination")
  private void rotateRect(Rect rect) {
    rect.set(rect.top, rect.left, rect.bottom, rect.right);
  }

  public void setQuickCameraListener(QuickCameraListener listener) {
    this.listener = listener;
  }

  public boolean isMultipleCameras() {
    return Camera.getNumberOfCameras() > 1;
  }

  public boolean isRearCamera() {
    return cameraHost.getCameraId() == Camera.CameraInfo.CAMERA_FACING_BACK;
  }

  public void swapCamera() {
    cameraHost.swapCameraId();
    onPause();
    onResume();
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    return focusEnabled && gestureDetector != null ?
        this.gestureDetector.onTouchEvent(event) :
        super.onTouchEvent(event);
  }

  @Override
  protected void dispatchDraw(Canvas canvas) {
    super.dispatchDraw(canvas);
    if (focusEnabled && focusAnimationRunning)
      canvas.drawCircle(focusCircleX, focusCircleY, focusCircleRadius, focusCirclePaint);
  }

  private class TapToFocusListener extends GestureDetector.SimpleOnGestureListener
    implements Camera.AutoFocusCallback
  {
    @Override
    public boolean onSingleTapUp(MotionEvent e) {
      focusCircleX = e.getX();
      focusCircleY = e.getY();
      focusCircleExpandAnimation.cancel();
      focusCircleFadeOutAnimation.cancel();
      focusAnimationRunning = false;
      if (focusOnArea(focusCircleX, focusCircleY, this))
        focusCircleExpandAnimation.start();
      return true;
    }

    @Override
    public boolean onDown(MotionEvent e) {
      return true;
    }

    @Override
    public void onAutoFocus(boolean success, Camera camera) {
      focusCircleExpandAnimation.cancel();
      focusCircleFadeOutAnimation.start();
    }
  }

  public interface QuickCameraListener {
    void onImageCapture(@NonNull final byte[] imageBytes);
    void onCameraFail(FailureReason reason);
  }

  private class QuickCameraHost extends SimpleCameraHost {
    int cameraId = CameraInfo.CAMERA_FACING_BACK;

    public QuickCameraHost(Context context) {
      super(context);
    }

    @TargetApi(VERSION_CODES.ICE_CREAM_SANDWICH) @Override
    public Parameters adjustPreviewParameters(Parameters parameters) {
      List<String> focusModes = parameters.getSupportedFocusModes();
      if (focusModes.contains(Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
        parameters.setFocusMode(Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
      } else if (focusModes.contains(Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
        parameters.setFocusMode(Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
      }
      return parameters;
    }

    @Override
    public int getCameraId() {
      return cameraId;
    }

    public void swapCameraId() {
      if (isMultipleCameras()) {
        if (cameraId == CameraInfo.CAMERA_FACING_BACK) cameraId = CameraInfo.CAMERA_FACING_FRONT;
        else                                           cameraId = CameraInfo.CAMERA_FACING_BACK;
      }
    }

    @Override
    public void onCameraFail(FailureReason reason) {
      super.onCameraFail(reason);
      if (listener != null) listener.onCameraFail(reason);
    }
  }
}