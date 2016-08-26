package com.tom.codecanativewin;


import com.tom.codecanativewin.jni.NativeWinCodec;

import android.app.Activity;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;

public class NativeWinCodecActivity extends Activity {

	final public static String TAG = "nwc" ; // Native Window Codec 
	private SurfaceView mSv = null;
	private SurfaceHolder mSh = null;
	private NativeWinCodec mNativeWinCodec = null;
	
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); 
		setContentView(R.layout.activity_native_win_codec);
		mSv = (SurfaceView) findViewById(R.id.mySurface);
		mSh = mSv.getHolder();
		mSh.setFormat(PixelFormat.RGBA_8888);// RGBX8888 RGBA8888 RGB565
		
		mSh.setKeepScreenOn(true);
		
		mSh.setFixedSize(200, 100);
		// surfaceHolder.setFixedSize(?, ?);是设置分辨率
		// 视频窗口的大小是由surfaceView的大小决定的。只要设置surfaceView的layout就行了,
		//mSh.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
		mSh.addCallback(new SurfaceCallback());
		
		Button btn = (Button)findViewById(R.id.bTestLockANWwithCodec);
		btn.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				if(mNativeWinCodec!=null){
					mNativeWinCodec.testLockANWwithCodec();
				}
				
			}
		});
	}

	
	private class SurfaceCallback implements SurfaceHolder.Callback
	{

		@Override
		public void surfaceChanged(SurfaceHolder arg0, int arg1, int arg2,
				int arg3) {
			
			Log.d(TAG , "surfaceChanged " + 
						" arg1 = " + arg1 +
						" arg2 = " + arg2 + 
						" arg3 = " + arg3 );
		}

		@Override
		public void surfaceCreated(SurfaceHolder arg0) {
			Log.d(TAG , "surfaceCreated play");
			mNativeWinCodec = new NativeWinCodec();
			mNativeWinCodec.setAndplay("/mnt/sdcard/test.mp4",  arg0.getSurface());
		}

		@Override
		public void surfaceDestroyed(SurfaceHolder arg0) {
			 
			Log.d(TAG , "surfaceCreated close");
			mNativeWinCodec.stop();
		}
	}
}
