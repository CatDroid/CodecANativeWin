package com.tom.Camera;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.tom.codecanativewin.R;
import com.tom.codecanativewin.R.layout;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

public class NewCameraActivity extends Activity implements
		SurfaceHolder.Callback {

	private static final String TAG = "cam2";

	private Handler mUIHandler = new Handler();

	private CaptureRequest.Builder mPreviewBuilder;
	private CameraCaptureSession mPreviewSession;

	private Size mPreviewSize; // The {@link android.util.Size} of camera
								// preview.
	private Size mVideoSize; // The {@link android.util.Size} of video
								// recording.

	private CameraCharacteristics mCharacteristics;
	private CameraDevice mCameraDevice;
	private String mCameraId;
	private Integer mSensorOrientation;

	private HandlerThread mBackgroundThread;
	private Handler mBackgroundHandler;

	private TomGLSurfaceView mPanoView = null;
	private SurfaceTexture mSurfaceTexture = null;

	private void ToastMsg(final String msg) {
		mUIHandler.post(new Runnable() {
			@Override
			public void run() {
				Toast.makeText(NewCameraActivity.this, msg, Toast.LENGTH_LONG)
						.show();
			}
		});
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_new_camera);

		mPanoView = new TomGLSurfaceView(this);
		mPanoView.getHolder().addCallback(this);
		LinearLayout parentView = (LinearLayout) findViewById(R.id.videoview);
		parentView.addView(mPanoView);

	}

	@Override
	public void onResume() {
		super.onResume();
		startBackgroundThread();
	}

	@Override
	public void onPause() {
		stopBackgroundThread();
		super.onPause();
	}

	private void startPreview() {

		assert mSurfaceTexture != null;

		try {
			mPreviewBuilder = mCameraDevice
					.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

			Surface previewSurface = new Surface(mSurfaceTexture);
			mPreviewBuilder.addTarget(previewSurface);

			mCameraDevice.createCaptureSession(Arrays.asList(previewSurface),
					new CameraCaptureSession.StateCallback() {

						@Override
						public void onConfigured(
								CameraCaptureSession cameraCaptureSession) {
							mPreviewSession = cameraCaptureSession;
							updatePreview();
							ToastMsg("Capture onConfigured");
						}

						@Override
						public void onConfigureFailed(
								CameraCaptureSession cameraCaptureSession) {
							ToastMsg("Capture onConfigureFailed");
						}
					}, mBackgroundHandler);

		} catch (CameraAccessException e) {
			Log.e(TAG,
					"CameraAccessException when startPreivew " + e.getMessage());
			e.printStackTrace();
		}

	}

	private void updatePreview() {
		if (null == mCameraDevice) {
			return;
		}
		try {
			setUpCaptureRequestBuilder(mPreviewBuilder);
			HandlerThread thread = new HandlerThread("CameraPreview");
			thread.start();
			mPreviewSession.setRepeatingRequest(mPreviewBuilder.build(), null,
					mBackgroundHandler);
		} catch (CameraAccessException e) {
			e.printStackTrace();
		}
	}

	private void setUpCaptureRequestBuilder(CaptureRequest.Builder builder) {
		builder.set(CaptureRequest.CONTROL_MODE,
				CameraMetadata.CONTROL_MODE_AUTO);
	}

	private void openCamera(int width, int height) {

		CameraManager manager = (CameraManager) this
				.getSystemService(Context.CAMERA_SERVICE);
		try {

			String cameraId = manager.getCameraIdList()[0];

			// Choose the sizes for camera preview and video recording
			CameraCharacteristics characteristics = manager
					.getCameraCharacteristics(cameraId);
			StreamConfigurationMap map = characteristics
					.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

			mSensorOrientation = characteristics
					.get(CameraCharacteristics.SENSOR_ORIENTATION);

			mVideoSize = chooseVideoSize(map
					.getOutputSizes(MediaRecorder.class));
			mPreviewSize = chooseOptimalSize(
					map.getOutputSizes(SurfaceTexture.class), width, height,
					mVideoSize);

			manager.openCamera(cameraId, mStateCallback, null);
		} catch (CameraAccessException e) {
			ToastMsg("Cannot access the camera.");
		} catch (NullPointerException e) {
			// Currently an NPE is thrown when the Camera2API is used but not
			// supported on the
			// device this code runs.
			ToastMsg(" not supported Camera2API");
		}
	}

	private CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {
		@Override
		public void onOpened(CameraDevice cameraDevice) {
			ToastMsg("CameraDevice Opened");
			Log.d(TAG, "CameraDevice Opened");
			mCameraDevice = cameraDevice;
			startPreview();
		}

		@Override
		public void onDisconnected(CameraDevice cameraDevice) {
			ToastMsg("CameraDevice Disconnecte");
			Log.d(TAG, "CameraDevice Disconnecte");
			cameraDevice.close();
			mCameraDevice = null;
		}

		@Override
		public void onError(CameraDevice cameraDevice, int error) {
			ToastMsg("CameraDevice Error error = " + error);
			Log.e(TAG, "CameraDevice Error error = " + error);
			cameraDevice.close();
			mCameraDevice = null;
		}

	};

	/**
	 * Return true if the given array contains the given integer.
	 * 
	 * @param modes
	 *            array to check.
	 * @param mode
	 *            integer to get for.
	 * @return true if the array contains the given integer, otherwise false.
	 */
	private static boolean contains(int[] modes, int mode) {
		if (modes == null) {
			return false;
		}
		for (int i : modes) {
			if (i == mode) {
				return true;
			}
		}
		return false;
	}

	/**
	 * In this sample, we choose a video size with 3x4 aspect ratio. Also, we
	 * don't use sizes larger than 1080p, since MediaRecorder cannot handle such
	 * a high-resolution video.
	 * 
	 * @param choices
	 *            The list of available sizes
	 * @return The video size
	 */
	private static Size chooseVideoSize(Size[] choices) {
		for (Size size : choices) {
			Log.d(TAG, "option video choice w:" + size.getWidth() + " h:"
					+ size.getHeight());
			if (size.getWidth() == size.getHeight() * 4 / 3
					&& size.getWidth() <= 1080) {
				Log.d(TAG, "option video use w:" + size.getWidth() + " h:"
						+ size.getHeight());
				return size;
			}
		}
		Log.e(TAG, "Couldn't find any suitable video size");
		return choices[choices.length - 1];
	}

	/**
	 * Compares two {@code Size}s based on their areas.
	 */
	static class CompareSizesByArea implements Comparator<Size> {

		@Override
		public int compare(Size lhs, Size rhs) {
			// We cast here to ensure the multiplications won't overflow
			return Long.signum((long) lhs.getWidth() * lhs.getHeight()
					- (long) rhs.getWidth() * rhs.getHeight());
		}

	}

	/**
	 * Given {@code choices} of {@code Size}s supported by a camera, chooses the
	 * smallest one whose width and height are at least as large as the
	 * respective requested values, and whose aspect ratio matches with the
	 * specified value.
	 * 
	 * @param choices
	 *            The list of sizes that the camera supports for the intended
	 *            output class
	 * @param width
	 *            The minimum desired width
	 * @param height
	 *            The minimum desired height
	 * @param aspectRatio
	 *            The aspect ratio
	 * @return The optimal {@code Size}, or an arbitrary one if none were big
	 *         enough
	 */
	private static Size chooseOptimalSize(Size[] choices, int width,
			int height, Size aspectRatio) {
		// Collect the supported resolutions that are at least as big as the
		// preview Surface
		List<Size> bigEnough = new ArrayList<Size>();
		int w = aspectRatio.getWidth();
		int h = aspectRatio.getHeight();
		for (Size option : choices) {
			Log.d(TAG, "option preview choice w:" + option.getWidth() + " h:"
					+ option.getHeight());
			if (option.getHeight() == option.getWidth() * h / w
					&& option.getWidth() >= width
					&& option.getHeight() >= height) {
				bigEnough.add(option);
			}
		}

		// Pick the smallest of those, assuming we found any
		if (bigEnough.size() > 0) {
			Size use = Collections.min(bigEnough, new CompareSizesByArea());
			Log.d(TAG,
					"option preview use w:" + use.getWidth() + " h:"
							+ use.getHeight());
			return use;
		} else {
			Log.e(TAG, "Couldn't find any suitable preview size");
			return choices[0];
		}
	}

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

	@Override
	public void surfaceChanged(SurfaceHolder arg0, int arg1, int arg2, int arg3) {
		Log.d(TAG, "surfaceChanged !");

	}

	@Override
	public void surfaceCreated(SurfaceHolder arg0) {
		Log.d(TAG, "surfaceCreated !");
		mSurfaceTexture = mPanoView.getSurfaceTexture();
		openCamera(1280, 720);

	}

	@Override
	public void surfaceDestroyed(SurfaceHolder arg0) {
		Log.d(TAG, "surfaceDestroyed !");
		// stopPreview();
		// closeCamera();

	}

}
