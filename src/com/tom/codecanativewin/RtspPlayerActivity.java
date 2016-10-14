package com.tom.codecanativewin;

import java.io.IOException;
import java.nio.ByteBuffer;

import android.app.Activity;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class RtspPlayerActivity extends Activity {

	private final static String TAG = "Rtsp" ; 
	
	private MediaPlayer mp  = null;
	private SurfaceView mSv = null;
	private SurfaceHolder mSh = null;
	
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_rtsp_player);
		
		mp = new MediaPlayer();
		
		try {
			mp.setDataSource("rtsp://192.168.11.220/");
		} catch (IllegalArgumentException e) {
			Log.e(TAG, "IllegalArgumentException");
			e.printStackTrace();
		} catch (SecurityException e) {
			Log.e(TAG, "SecurityException");
			e.printStackTrace();
		} catch (IllegalStateException e) {
			Log.e(TAG, "IllegalStateException");
			e.printStackTrace();
		} catch (IOException e) {
			Log.e(TAG, "IOException");
			e.printStackTrace();
		}
		Log.d(TAG, "set done");
		
		 
		
		mp.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
			@Override
			public void onPrepared(MediaPlayer mp) {
				Log.d(TAG, "prepared done !");
				//mp.start();
			}
		});
		
		mp.setOnErrorListener(new MediaPlayer.OnErrorListener() {
			
			@Override
			public boolean onError(MediaPlayer mp, int what, int extra) {
				Log.e(TAG, "onError " + what + " : " + extra );
				return false;
			}
		});
		
		mSv = (SurfaceView) findViewById(R.id.rtspSurface);
		mSh = mSv.getHolder();
		mSh.setKeepScreenOn(true);
		mSh.addCallback(new SurfaceCallback());
		

		
		
	}
	
	
	private class SurfaceCallback implements SurfaceHolder.Callback {

		@Override
		public void surfaceChanged(SurfaceHolder arg0, int arg1, int arg2, int arg3) {

			Log.d(TAG, "surfaceChanged " + " arg1 = " + arg1 + " arg2 = " + arg2 + " arg3 = " + arg3);
		}

		@Override
		public void surfaceCreated(SurfaceHolder arg0) {
 
			
			mp.prepareAsync();
		}

		@Override
		public void surfaceDestroyed(SurfaceHolder arg0) {
			Log.d(TAG, "surfaceCreated destroyed");
		}
	}
	
	
	
}
