package com.tom.codecanativewin;


import com.tom.codecanativewin.jni.NativeAudioCodec;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

public class AudioDecActivity extends Activity {


	final public static String TAG = "audio_dec";
 

	private SurfaceView mSv = null;
	private SurfaceHolder mSh = null;
	private boolean surfaceCreated = false;

	private NativeAudioCodec mNativeAudioCodec = null;
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_audio_dec);

		mSv = (SurfaceView) findViewById(R.id.viewSurface);
		mSh = mSv.getHolder();
		mSh.setKeepScreenOn(true);
		mSh.addCallback(new SurfaceCallback());
 
		((Button) findViewById(R.id.bStart)).setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {

				if (surfaceCreated == false){
					ToastMessage("Surface Not Available now");
				}
				
				if(mNativeAudioCodec == null ){
					mNativeAudioCodec = new NativeAudioCodec();
				}
				
				boolean result = mNativeAudioCodec.decodeAudioFile(mSh.getSurface());
				ToastMessage("NativeCodec startup result = " + result);
			 
			}
		});
		
		((Button) findViewById(R.id.bStop)).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {

				if(mNativeAudioCodec == null ){
					ToastMessage("NativeCodec is NOT created yet");
					return ;
				}
				mNativeAudioCodec.decodeForceClose();
			}
		});
		
	}
	
	
	
	
	@Override
	protected void onDestroy() {
		if(mNativeAudioCodec != null ){
			mNativeAudioCodec.decodeForceClose();
		}
		
		super.onDestroy();
	}




	private void ToastMessage(String msg )
	{
		Toast.makeText(AudioDecActivity.this, msg, Toast.LENGTH_LONG).show();
	}

	private class SurfaceCallback implements SurfaceHolder.Callback {

		@Override
		public void surfaceChanged(SurfaceHolder arg0, int arg1, int arg2, int arg3) {

			Log.d(TAG, "surfaceChanged " + " arg1 = " + arg1 + " arg2 = " + arg2 + " arg3 = " + arg3);
		}

		@Override
		public void surfaceCreated(SurfaceHolder arg0) {
			Log.d(TAG, "surfaceCreated created");
			surfaceCreated = true;
		}

		@Override
		public void surfaceDestroyed(SurfaceHolder arg0) {
			Log.d(TAG, "surfaceCreated destroyed");
		}
	}


}
