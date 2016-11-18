package com.tom.codecanativewin;


import com.tom.codecanativewin.jni.DecodeH264;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;

public class DisplayH264Activity extends Activity {

 
	final public static String TAG = "DisplayH264";
	private DecodeH264 mH264de = null;
	private DecodeH264 mH264de2 = null;	
	
	private SurfaceView mSv = null;
	private SurfaceHolder mSh = null;
	private SurfaceView mSv1 = null;
	private SurfaceHolder mSh1 = null;
	
	private boolean surfaceCreated = false;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); 
		
		setContentView(R.layout.activity_display_h264);

		mSv = (SurfaceView) findViewById(R.id.viewSurface);
		mSh = mSv.getHolder();
		mSh.setKeepScreenOn(true);
		mSh.addCallback(new SurfaceCallback());

		mSv1 = (SurfaceView) findViewById(R.id.viewSurface1);
		mSh1 = mSv1.getHolder();
		mSh1.setKeepScreenOn(true);
		mSh1.addCallback(new SurfaceCallback());
		
		//new DecodeH264();
		
		((Button) findViewById(R.id.bStart)).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {

				if (mH264de == null){
					mH264de = new DecodeH264();
					mH264de.setInfoListener(new DecodeH264.onInfoListener() {
						
						@Override
						public void onInfo(int type, int arg1, int arg2) {
							Log.d(TAG, "1 postEventFromNative update time = " 
										+ arg1 + " sec ; " + " size = " + arg2 );
						}
					});
					mH264de2 = new DecodeH264();
					mH264de2.setInfoListener(new DecodeH264.onInfoListener() {
						
						@Override
						public void onInfo(int type, int arg1, int arg2) {
							Log.d(TAG, "2 postEventFromNative update time = " 
										+ arg1 + " sec ; " + " size = " + arg2 );
						}
					});
			
					
				}
				
				if (surfaceCreated == false){
					Toast.makeText(DisplayH264Activity.this, "Surface Not Available now", Toast.LENGTH_LONG)
							.show();
				}else{
					/*
					 * 	可以两个不同h264，同时解码
					 * 
					 *  小米5 高通820 1080p 使用suface:59fps  不使用surface:78fps 
					 *  			1920*960 使用suface:60fps	不是用surface:133fps
					 */				
					String pathMachine = "/mnt/sdcard/" + "1920960machine.h264" ;
					byte[] spsMachine =  {
							(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x01,
							(byte)0x67,(byte)0x42,(byte)0x00,(byte)0x29,
							(byte)0x8d,(byte)0x8d,(byte)0x40,(byte)0x28,
							(byte)0x03,(byte)0xcd,(byte)0x00,(byte)0xf0,
							(byte)0x88,(byte)0x45,(byte)0x38,
					};
					byte[] ppsMachine = {
							(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x01,
							(byte)0x68,(byte)0xeb,(byte)0xef,(byte)0x2c,
					};
					mH264de.start( mSh.getSurface(), pathMachine ,
									spsMachine , ppsMachine);
					
					///////<<<<<
					
					String path1080p60fps = "/mnt/sdcard/" + "1080p60fps.h264" ;
					byte[] sps1080p60fps =  {
							(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x01,
							(byte)0x67,(byte)0x64,(byte)0x00,(byte)0x2a,
							(byte)0xac,(byte)0xd1,(byte)0x00,(byte)0x78,
							(byte)0x02,(byte)0x27,(byte)0xe5,(byte)0xc0,
							(byte)0x44,(byte)0x00,(byte)0x00,(byte)0x0f,
							(byte)0xa4,(byte)0x00,(byte)0x07,(byte)0x53,
							(byte)0x00,(byte)0x3c,(byte)0x60,(byte)0xc4,
							(byte)0x48,
					};
					byte[] pps1080p60fps = {
							(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x01,
							(byte)0x68,(byte)0xeb,(byte)0xef,(byte)0x2c,
					};
					mH264de2.start(mSh1.getSurface() , path1080p60fps , 
									sps1080p60fps  , pps1080p60fps );
				}
			}
		});
		
	 
		((Button) findViewById(R.id.bStop)).setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {

				if (mH264de != null){
					mH264de.release();
					mH264de2.release();
					mH264de = null;
					mH264de2 = null;
				}else{
					Toast.makeText(DisplayH264Activity.this, "DecodeH264 Not Created now", Toast.LENGTH_LONG)
					.show();
				}
			}
		});

	}

	
	
	@Override
	protected void onDestroy() {
		if (mH264de != null){
			mH264de.release();
			mH264de2.release();
		}
		super.onDestroy();
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
