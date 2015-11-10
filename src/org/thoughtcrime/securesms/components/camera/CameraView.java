/***
 Copyright (c) 2013-2014 CommonsWare, LLC
 Portions Copyright (C) 2007 The Android Open Source Project

 Licensed under the Apache License, Version 2.0 (the "License"); you may
 not use this file except in compliance with the License. You may obtain
 a copy of the License at
 http://www.apache.org/licenses/LICENSE-2.0
 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */

package org.thoughtcrime.securesms.components.camera;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
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
import android.os.Build.VERSION;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.view.GestureDetectorCompat;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.widget.FrameLayout;

import com.nineoldandroids.animation.Animator;
import com.nineoldandroids.animation.AnimatorInflater;
import com.nineoldandroids.animation.AnimatorListenerAdapter;
import com.nineoldandroids.animation.ValueAnimator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.BitmapUtil;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.jobqueue.Job;
import org.whispersystems.jobqueue.JobParameters;
import org.whispersystems.libaxolotl.util.guava.Optional;

@SuppressWarnings("deprecation")
public class CameraView extends FrameLayout {
  private static final String TAG = CameraView.class.getSimpleName();

  private final CameraSurfaceView   surface;
  private final OnOrientationChange onOrientationChange;

  private @NonNull volatile Optional<Camera> camera   = Optional.absent();
  private          volatile int              cameraId = CameraInfo.CAMERA_FACING_BACK;

  private           boolean            started;
  private @Nullable CameraViewListener listener;
  private           int                displayOrientation     = -1;
  private           int                outputOrientation      = -1;

  private           int                   focusAreaSize   = -1;
  private           GestureDetectorCompat gestureDetector;
  private           boolean               focusEnabled, focusAnimationRunning, focusComplete;
  private           float                 focusCircleX, focusCircleY, focusCircleRadius;
  private           Paint                 focusCirclePaint;
  private           ValueAnimator         focusCircleExpandAnimation, focusCircleFadeOutAnimation;

  public CameraView(Context context) {
    this(context, null);
  }

  public CameraView(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public CameraView(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    setBackgroundColor(Color.BLACK);

    if (isMultiCamera()) cameraId = CameraInfo.CAMERA_FACING_FRONT;

    surface             = new CameraSurfaceView(getContext());
    onOrientationChange = new OnOrientationChange(context.getApplicationContext());
    addView(surface);
  }

  @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
  public void onResume() {
    if (started) return;
    started = true;
    Log.w(TAG, "onResume() queued");
    enqueueTask(new SerialAsyncTask<Camera>() {
      @Override
      protected
      @Nullable
      Camera onRunBackground() {
        try {
          return Camera.open(cameraId);
        } catch (Exception e) {
          Log.w(TAG, e);
          return null;
        }
      }

      @Override
      protected void onPostMain(@Nullable Camera camera) {
        if (camera == null) {
          Log.w(TAG, "tried to open camera but got null");
          if (listener != null) listener.onCameraFail();
          return;
        }

        CameraView.this.camera = Optional.of(camera);
        focusEnabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH &&
            camera.getParameters().getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_AUTO) &&
            (camera.getParameters().getMaxNumFocusAreas() > 0 || camera.getParameters().getMaxNumMeteringAreas() > 0);
        if (focusEnabled) setupFocusAnimation();
        try {
          if (getActivity().getRequestedOrientation() != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
            onOrientationChange.enable();
          }
          setCameraDisplayOrientation();
          synchronized (CameraView.this) {
            CameraView.this.notifyAll();
          }
          onCameraReady();
          requestLayout();
          invalidate();
          Log.w(TAG, "onResume() completed");
        } catch (RuntimeException e) {
          Log.w(TAG, "exception when starting camera preview", e);
          onPause();
        }
      }
    });
  }

  public void onPause() {
    if (!started) return;
    started = false;
    Log.w(TAG, "onPause() queued");

    enqueueTask(new SerialAsyncTask<Void>() {
      private Optional<Camera> cameraToDestroy;
      @Override protected void onPreMain() {
        cameraToDestroy = camera;
        camera = Optional.absent();
      }

      @Override protected Void onRunBackground() {
        if (cameraToDestroy.isPresent()) {
          try {
            stopPreview();
            cameraToDestroy.get().release();
            Log.w(TAG, "released old camera instance");
          } catch (Exception e) {
            Log.w(TAG, e);
          }
        }
        return null;
      }

      @Override protected void onPostMain(Void avoid) {
        onOrientationChange.disable();
        displayOrientation = -1;
        outputOrientation = -1;
        Log.w(TAG, "onPause() completed");
      }
    });
  }

  public boolean isStarted() {
    return started;
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);

    if (getMeasuredWidth() > 0 && getMeasuredHeight() > 0 && camera.isPresent()) {
      final Size preferredPreviewSize = CameraUtils.getPreferredPreviewSize(displayOrientation,
                                                                            getMeasuredWidth(),
                                                                            getMeasuredHeight(),
                                                                            camera.get());
      final Parameters parameters = camera.get().getParameters();
      if (preferredPreviewSize != null && !parameters.getPreviewSize().equals(preferredPreviewSize)) {
        Log.w(TAG, "setting preview size to " + preferredPreviewSize.width + "x" + preferredPreviewSize.height);
        stopPreview();
        parameters.setPreviewSize(preferredPreviewSize.width, preferredPreviewSize.height);
        camera.get().setParameters(parameters);
        requestLayout();
        startPreview();
      }
      // Recommended focus area size from the manufacture is 1/8 of the image
      // width (i.e. longer edge of the image)
      focusAreaSize = Math.max(parameters.getPreviewSize().width, parameters.getPreviewSize().height) / 8;
    }
  }

  @SuppressWarnings("SuspiciousNameCombination")
  @Override
  protected void onLayout(boolean changed, int l, int t, int r, int b) {
    final int  width         = r - l;
    final int  height        = b - t;
    final int  previewWidth;
    final int  previewHeight;

    if (camera.isPresent()) {
      final Size previewSize = camera.get().getParameters().getPreviewSize();
      if (displayOrientation == 90 || displayOrientation == 270) {
        previewWidth  = previewSize.height;
        previewHeight = previewSize.width;
      } else {
        previewWidth  = previewSize.width;
        previewHeight = previewSize.height;
      }
    } else {
      previewWidth  = width;
      previewHeight = height;
    }

    if (previewHeight == 0 || previewWidth == 0) {
      Log.w(TAG, "skipping layout due to zero-width/height preview size");
      return;
    }
    Log.w(TAG, "layout " + width + "x" + height + ", target " + previewWidth + "x" + previewHeight);

    if (width * previewHeight > height * previewWidth) {
      final int scaledChildHeight = previewHeight * width / previewWidth;
      surface.layout(0, (height - scaledChildHeight) / 2, width, (height + scaledChildHeight) / 2);
    } else {
      final int scaledChildWidth = previewWidth * height / previewHeight;
      surface.layout((width - scaledChildWidth) / 2, 0, (width + scaledChildWidth) / 2, height);
    }
  }

  public void setListener(@Nullable CameraViewListener listener) {
    this.listener = listener;
  }

  public boolean isMultiCamera() {
    return Camera.getNumberOfCameras() > 1;
  }

  public boolean isRearCamera() {
    return cameraId == CameraInfo.CAMERA_FACING_BACK;
  }

  public void flipCamera() {
    if (Camera.getNumberOfCameras() > 1) {
      cameraId = cameraId == CameraInfo.CAMERA_FACING_BACK
                 ? CameraInfo.CAMERA_FACING_FRONT
          : CameraInfo.CAMERA_FACING_BACK;
      onPause();
      onResume();
    }
  }

  @TargetApi(14)
  private void onCameraReady() {
    if (!camera.isPresent()) return;

    final Parameters   parameters = camera.get().getParameters();
    final List<String> focusModes = parameters.getSupportedFocusModes();

    if (VERSION.SDK_INT >= 14) parameters.setRecordingHint(true);

    if (VERSION.SDK_INT >= 14 && focusModes.contains(Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
      parameters.setFocusMode(Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
    } else if (focusModes.contains(Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
      parameters.setFocusMode(Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
    }

    camera.get().setParameters(parameters);

    enqueueTask(new PostInitializationTask<Void>() {
      @Override
      protected void onPostMain(Void avoid) {
        if (camera.isPresent()) {
          try {
            camera.get().setPreviewDisplay(surface.getHolder());
            requestLayout();
          } catch (Exception e) {
            Log.w(TAG, e);
          }
        }
      }
    });
  }

  @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
  protected boolean focusOnArea(float x, float y, @Nullable Camera.AutoFocusCallback callback) {
    if (!camera.isPresent()) return false;
    Camera cameraInstance = camera.get();
    cameraInstance.cancelAutoFocus();

    final Size previewSize = camera.get().getParameters().getPreviewSize();
    float newX = 0, newY = 0;
    if (previewSize != null && (displayOrientation == 90 || displayOrientation == 270)) {
      newX = x / previewSize.height * 2000 - 1000;
      newY = y / previewSize.width * 2000 - 1000;
    } else if (previewSize != null) {
      newX = x / previewSize.width * 2000 - 1000;
      newY = y / previewSize.height * 2000 - 1000;
    }
    Rect focusRect = calculateTapArea(newX, newY, 1.f);
    // AE area is bigger because exposure is sensitive and
    // easy to over- or underexposure if area is too small.
    Rect meteringRect = calculateTapArea(newX, newY, 1.5f);

    Camera.Parameters parameters = cameraInstance.getParameters();
    if (parameters.getMaxNumFocusAreas() > 0) {
      parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
      ArrayList<Camera.Area> focusAreaArrayList = new ArrayList<>();
      focusAreaArrayList.add(new Camera.Area(focusRect, 1000));
      parameters.setFocusAreas(focusAreaArrayList);
    }
    if (parameters.getMaxNumMeteringAreas() > 0) {
      ArrayList<Camera.Area> meteringAreaArrayList = new ArrayList<>();
      meteringAreaArrayList.add(new Camera.Area(meteringRect, 1000));
      parameters.setMeteringAreas(meteringAreaArrayList);
    }
    cameraInstance.setParameters(parameters);
    cameraInstance.autoFocus(callback);
    return true;
  }

  private Rect calculateTapArea(float x, float y, float coefficient) {
    int scaledFocusAreaSize = (int) (coefficient * focusAreaSize);
    int left = clamp((int) x - scaledFocusAreaSize / 2, -1000, 1000 - scaledFocusAreaSize);
    int top = clamp((int) y - scaledFocusAreaSize / 2, -1000, 1000 - scaledFocusAreaSize);
    return new Rect(left, top, left + scaledFocusAreaSize, top + scaledFocusAreaSize);
  }

  private static int clamp(int x, int min, int max) {
    return Math.min(Math.max(x, min), max);
  }

  private void setupFocusAnimation() {
    TapToFocusListener tapToFocusListener = new TapToFocusListener();
    gestureDetector = new GestureDetectorCompat(getContext(), tapToFocusListener);
    gestureDetector.setIsLongpressEnabled(false);
    focusAnimationRunning = false;
    focusComplete = false;
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

      @Override
      public void onAnimationEnd(Animator animation) {
        if (focusComplete)
          focusCircleFadeOutAnimation.start();
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

  private void startPreview() {
    if (camera.isPresent()) {
      try {
        camera.get().startPreview();
      } catch (Exception e) {
        Log.w(TAG, e);
      }
    }
  }

  private void stopPreview() {
    if (camera.isPresent()) {
      try {
        camera.get().stopPreview();
      } catch (Exception e) {
        Log.w(TAG, e);
      }
    }
  }

  // based on
  // http://developer.android.com/reference/android/hardware/Camera.html#setDisplayOrientation(int)
  // and http://stackoverflow.com/a/10383164/115145
  private void setCameraDisplayOrientation() {
    Camera.CameraInfo info     = getCameraInfo();
    int               rotation = getActivity().getWindowManager().getDefaultDisplay().getRotation();
    int               degrees  = 0;
    DisplayMetrics    dm       = new DisplayMetrics();

    getActivity().getWindowManager().getDefaultDisplay().getMetrics(dm);

    switch (rotation) {
    case Surface.ROTATION_0:   degrees = 0;   break;
    case Surface.ROTATION_90:  degrees = 90;  break;
    case Surface.ROTATION_180: degrees = 180; break;
    case Surface.ROTATION_270: degrees = 270; break;
    }

    if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
      displayOrientation = (info.orientation + degrees           ) % 360;
      displayOrientation = (360              - displayOrientation) % 360;
    } else {
      displayOrientation = (info.orientation - degrees + 360) % 360;
    }

    stopPreview();
    camera.get().setDisplayOrientation(displayOrientation);
    startPreview();
  }

  public int getCameraPictureOrientation() {
    if (getActivity().getRequestedOrientation() != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
      outputOrientation = getCameraPictureRotation(getActivity().getWindowManager()
                                                                .getDefaultDisplay()
                                                                .getOrientation());
    } else if (getCameraInfo().facing == CameraInfo.CAMERA_FACING_FRONT) {
      outputOrientation = (360 - displayOrientation) % 360;
    } else {
      outputOrientation = displayOrientation;
    }

    return outputOrientation;
  }

  private @NonNull CameraInfo getCameraInfo() {
    final CameraInfo info = new Camera.CameraInfo();
    Camera.getCameraInfo(cameraId, info);
    return info;
  }

  // XXX this sucks
  private Activity getActivity() {
    return (Activity)getContext();
  }

  public int getCameraPictureRotation(int orientation) {
    final CameraInfo info = getCameraInfo();
    final int        rotation;

    orientation = (orientation + 45) / 90 * 90;

    if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
      rotation = (info.orientation - orientation + 360) % 360;
    } else {
      rotation = (info.orientation + orientation) % 360;
    }

    return rotation;
  }

  private class OnOrientationChange extends OrientationEventListener {
    public OnOrientationChange(Context context) {
      super(context);
      disable();
    }

    @Override
    public void onOrientationChanged(int orientation) {
      if (camera.isPresent() && orientation != ORIENTATION_UNKNOWN) {
        int newOutputOrientation = getCameraPictureRotation(orientation);

        if (newOutputOrientation != outputOrientation) {
          outputOrientation = newOutputOrientation;

          Camera.Parameters params = camera.get().getParameters();

          params.setRotation(outputOrientation);

          try {
            camera.get().setParameters(params);
          }
          catch (Exception e) {
            Log.e(TAG, "Exception updating camera parameters in orientation change", e);
          }
        }
      }
    }
  }

  public void takePicture(final Rect previewRect) {
    if (!camera.isPresent() || camera.get().getParameters() == null) {
      Log.w(TAG, "camera not in capture-ready state");
      return;
    }

    camera.get().setOneShotPreviewCallback(new Camera.PreviewCallback() {
      @Override
      public void onPreviewFrame(byte[] data, final Camera camera) {
        final int  rotation     = getCameraPictureOrientation();
        final Size previewSize  = camera.getParameters().getPreviewSize();
        final Rect croppingRect = getCroppedRect(previewSize, previewRect, rotation);

        Log.w(TAG, "previewSize: " + previewSize.width + "x" + previewSize.height);
        Log.w(TAG, "data bytes: " + data.length);
        Log.w(TAG, "previewFormat: " + camera.getParameters().getPreviewFormat());
        Log.w(TAG, "croppingRect: " + croppingRect.toString());
        Log.w(TAG, "rotation: " + rotation);
        new CaptureTask(previewSize, rotation, croppingRect).execute(data);
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
    final float centerX   = (VERSION.SDK_INT < 14) ? previewWidth - newWidth / 2 : previewWidth / 2;
    final float centerY   = previewHeight / 2;

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

  private void enqueueTask(SerialAsyncTask job) {
    ApplicationContext.getInstance(getContext()).getJobManager().add(job);
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
      focusComplete = false;
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
      focusComplete = true;
      if (!focusCircleExpandAnimation.isRunning())
        focusCircleFadeOutAnimation.start();
    }
  }

  private static abstract class SerialAsyncTask<Result> extends Job {

    public SerialAsyncTask() {
      super(JobParameters.newBuilder().withGroupId(CameraView.class.getSimpleName()).create());
    }

    @Override public void onAdded() {}

    @Override public final void onRun() {
      try {
        onWait();
        Util.runOnMainSync(new Runnable() {
          @Override public void run() {
            onPreMain();
          }
        });

        final Result result = onRunBackground();

        Util.runOnMainSync(new Runnable() {
          @Override public void run() {
            onPostMain(result);
          }
        });
      } catch (PreconditionsNotMetException e) {
        Log.w(TAG, "skipping task, preconditions not met in onWait()");
      }
    }

    @Override public boolean onShouldRetry(Exception e) {
      return false;
    }

    @Override public void onCanceled() { }

    protected void onWait() throws PreconditionsNotMetException {}
    protected void onPreMain() {}
    protected Result onRunBackground() { return null; }
    protected void onPostMain(Result result) {}
  }

  private abstract class PostInitializationTask<Result> extends SerialAsyncTask<Result> {
    @Override protected void onWait() throws PreconditionsNotMetException {
      synchronized (CameraView.this) {
        if (!camera.isPresent()) {
          throw new PreconditionsNotMetException();
        }
        while (getMeasuredHeight() <= 0 || getMeasuredWidth() <= 0 || !surface.isReady()) {
          Log.w(TAG, String.format("waiting. surface ready? %s", surface.isReady()));
          Util.wait(CameraView.this, 0);
        }
      }
    }
  }

  private class CaptureTask extends AsyncTask<byte[], Void, byte[]> {
    private final Size previewSize;
    private final int  rotation;
    private final Rect croppingRect;

    public CaptureTask(Size previewSize, int rotation, Rect croppingRect) {
      this.previewSize  = previewSize;
      this.rotation     = rotation;
      this.croppingRect = croppingRect;
    }

    @Override
    protected byte[] doInBackground(byte[]... params) {
      final byte[] data = params[0];
      try {
        return BitmapUtil.createFromNV21(data,
                                         previewSize.width,
                                         previewSize.height,
                                         rotation,
                                         croppingRect);
      } catch (IOException e) {
        Log.w(TAG, e);
        return null;
      }
    }

    @Override
    protected void onPostExecute(byte[] imageBytes) {
      if (imageBytes != null && listener != null) listener.onImageCapture(imageBytes);
    }
  }

  private static class PreconditionsNotMetException extends Exception {}

  public interface CameraViewListener {
    void onImageCapture(@NonNull final byte[] imageBytes);
    void onCameraFail();
  }
}
