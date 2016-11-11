package com.tom.codecanativewin;

import java.io.IOException;

import com.tom.codecanativewin.jni.NativeWinCodec;

import android.app.Activity;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.hardware.camera2.CameraDevice;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.os.Bundle;
import android.util.Log;
import android.util.Range;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;

public class NativeWinCodecActivity extends Activity {

	final public static String TAG = "nwc"; // Native Window Codec
	private SurfaceView mSv = null;
	private SurfaceHolder mSh = null;
	private NativeWinCodec mNativeWinCodec = null;
	private final String PLAY_THIS_FILE =  "/mnt/sdcard/fuzhubao.3gp";// "/mnt/sdcard/mtv.mp4";//"/mnt/sdcard/test1080p60fps.mp4";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		setContentView(R.layout.activity_native_win_codec);

		mSv = (SurfaceView) findViewById(R.id.mySurface);
		mSh = mSv.getHolder();
		mSh.setFormat(PixelFormat.RGBA_8888);// RGBX8888 RGBA8888 RGB565

		mSh.setKeepScreenOn(true);

		// mSh.setFixedSize(800, 1280);
		// surfaceHolder.setFixedSize(?, ?);是设置分辨率
		// 视频窗口的大小是由surfaceView的大小决定的。只要设置surfaceView的layout就行了,
		// mSh.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
		mSh.addCallback(new SurfaceCallback());

		Button btn = (Button) findViewById(R.id.bTestLockANWwithCodec);
		btn.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				if (mNativeWinCodec != null) {
					mNativeWinCodec.testLockANWwithCodec();
				}

			}
		});

	}


	private class SurfaceCallback implements SurfaceHolder.Callback {

		@Override
		public void surfaceChanged(SurfaceHolder arg0, int arg1, int arg2, int arg3) {

			Log.d(TAG, "surfaceChanged " + " arg1 = " + arg1 + " arg2 = " + arg2 + " arg3 = " + arg3);
		}

		@Override
		public void surfaceCreated(SurfaceHolder arg0) {
			Log.d(TAG, "surfaceCreated play");
			mNativeWinCodec = new NativeWinCodec();
			mNativeWinCodec.setAndplay(PLAY_THIS_FILE, arg0.getSurface());
		}

		@Override
		public void surfaceDestroyed(SurfaceHolder arg0) {

			Log.d(TAG, "surfaceCreated close");
			mNativeWinCodec.stop();
		}
	}
}
