package com.tom.Camera;

import java.io.IOException;

import com.tom.codecanativewin.R;

import android.app.Activity;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.SurfaceHolder;
import android.widget.LinearLayout;
import android.widget.Toast;

@SuppressWarnings("deprecation")
public class OldCameraActivity extends Activity implements
		SurfaceHolder.Callback {

	private static final String TAG = "cam1";

	private Handler mUIHandler = new MyHandler();

	private TomGLSurfaceView mPanoView = null;
	private SurfaceTexture mSurfaceTexture = null;
	private Camera mCamera = null;
	private final int WIDTH = 1280;
	private final int HEIGHT = 720;
	private final int FPS = 15;

	
	public class MyHandler extends Handler
	{

		@Override
		public void handleMessage(Message msg) {
			
			switch(msg.what){
			case MessageType.MSG_RENDER_CREATED:
				
				Log.d(TAG, "Render.surfaceCreated !");
				mSurfaceTexture = mPanoView.getSurfaceTexture();
				if( mCamera == null) {
					prepareCamera();
				}
				try {
					mCamera.setPreviewTexture(mSurfaceTexture);
				} catch (IOException e) {
					Log.e(TAG , "Can NOT setPreviewTexture " + e.getMessage() );
					ToastMsg("Can NOT setPreviewTexture ");
					e.printStackTrace();
					return ;
				}
				mCamera.startPreview();
				
				break;
			case MessageType.MSG_RENDER_CHANGE:
				break;
			default:
				break;
			}
			super.handleMessage(msg);
		}
		
	}
	private void ToastMsg(final String msg) {
		mUIHandler.post(new Runnable() {
			@Override
			public void run() {
				Toast.makeText(OldCameraActivity.this, msg, Toast.LENGTH_LONG)
						.show();
			}
		});
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_new_camera);

		mPanoView = new TomGLSurfaceView(this);
		mPanoView.setCallback(mUIHandler);
		mPanoView.getHolder().addCallback(this);
		LinearLayout parentView = (LinearLayout) findViewById(R.id.videoview);
		parentView.addView(mPanoView);

	}

	@Override
	public void onResume() {
		/*
		 *  1.The owner of this view must call this method when the activity is resumed.
		 *  2.Calling this method will recreate the OpenGL display and resume the rendering thread.
		 *  3.Must not be called before a renderer has been set. 
		 */
		mPanoView.onResume();
		super.onResume();
	}

	@Override
	public void onPause() {
		
		if( mCamera != null && mSurfaceTexture !=null ){
			Log.d(TAG, "Activity.onPause stop previewing !");
			mCamera.stopPreview(); 
		}
		/*
		 *  1. The owner of this view must call this method when the activity is paused. 
		 *  2. Calling this method will pause the rendering thread. 
		 *  3. Must not be called before a renderer has been set. 
		 * */
		mPanoView.onPause(); // 否则GLSurfaceView不会自己调用onPause 当退到后台 
							 // 调用GLSurfaceView.onPause之后 会重新调用GLSurfaceView.onResume();会GLSurfaceView.Renderer.onSurfaceCreated
							 // 如果不调用GLSurfaceView的onPause和onResume ，那么不会再回调GLSurfaceView.Renderer.onSurfaceCreated
		super.onPause();
	}



	@Override
	protected void onDestroy() {
		if(mCamera != null){
			mCamera.stopPreview();
			mCamera.release(); 
		}
		super.onDestroy();
	}

	@Override
	public void surfaceChanged(SurfaceHolder arg0, int arg1, int arg2, int arg3) {
		Log.d(TAG, "SurfaceHolder.surfaceChanged !");
	}

	@Override
	public void surfaceCreated(SurfaceHolder arg0) {
		Log.d(TAG, "SurfaceHolder.surfaceCreated !");
//		if( mCamera != null && mSurfaceTexture !=null ){
//			Log.d(TAG, "SurfaceHolder.surfaceCreated continue to preview !");
//			mCamera.startPreview();
//		}
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder arg0) {
		Log.d(TAG, "SurfaceHolder.surfaceDestroyed !");
//		if( mCamera != null && mSurfaceTexture !=null ){
//			Log.d(TAG, "SurfaceHolder.surfaceDestroyed stop previewing !");
//			mCamera.stopPreview(); 
//		}
		
	}

	private void prepareCamera() {
		if (mCamera != null) {
			throw new RuntimeException("camera already initialized");
		}

		Camera.CameraInfo info = new Camera.CameraInfo();

		int numCameras = Camera.getNumberOfCameras();
		for (int i = 0; i < numCameras; i++) {
			Camera.getCameraInfo(i, info);
			if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {//CAMERA_FACING_BACK  CAMERA_FACING_FRONT
				Log.d(TAG, "camera " + i + " " + " face front ! ");
				mCamera = Camera.open(i);
				break;
			}
		}
		if (mCamera == null) {
			Log.d(TAG, "No front-facing camera found; opening default");
			mCamera = Camera.open(); // opens first back-facing camera
		}
		if (mCamera == null) {
			throw new RuntimeException("Unable to open camera");
		}

		Camera.Parameters parms = mCamera.getParameters();
		choosePreviewSize(parms, WIDTH, HEIGHT);
		setPreviewFrameRate(parms, FPS);
		mCamera.setParameters(parms);

		Camera.Size size = parms.getPreviewSize();
		int fps = parms.getPreviewFrameRate();
		Log.d(TAG, "Camera preview size is " + size.width + "x" + size.height
				+ " at " + fps + " fps ");
	}

	private void setPreviewFrameRate(Camera.Parameters parms, int frameRate) {
		for (Integer fps : parms.getSupportedPreviewFrameRates()) {
			Log.d(TAG, "Camera support: " + fps + " fps ");
		}
		parms.setPreviewFrameRate(frameRate);
	}

	private void choosePreviewSize(Camera.Parameters parms, int width,
			int height) {

		Camera.Size ppsfv = parms.getPreferredPreviewSizeForVideo();
		if (ppsfv != null) {
			Log.d(TAG, "Camera preferred preview size for video is "
					+ ppsfv.width + "x" + ppsfv.height);
		}

		for (Camera.Size size : parms.getSupportedPreviewSizes()) {
			Log.d(TAG, "Camera support: " + size.width + "x" + size.height);
		}

		for (Camera.Size size : parms.getSupportedPreviewSizes()) {
			if (size.width == width && size.height == height) {
				Log.d(TAG, "set target preview size to " + width + "x" + height);
				parms.setPreviewSize(width, height);
				return;
			}
		}

		Log.w(TAG, "Unable to set preview size to " + width + "x" + height);
		if (ppsfv != null) {
			Log.w(TAG, "use preferered size ");
			parms.setPreviewSize(ppsfv.width, ppsfv.height);
		} else {
			Log.e(TAG, "no size used !!!");
		}
	}

}
