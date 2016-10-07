package com.tom.codecanativewin;

import java.nio.ByteBuffer;

import com.tom.codecanativewin.jni.ABuffer;
import com.tom.codecanativewin.jni.DecodeH264;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;

public class ByteBufferActivity extends Activity {

	final public static String TAG = "ByteBuffer";
	private DecodeH264 mH264de = null;

	private SurfaceView mSv = null;
	private SurfaceHolder mSh = null;
	private boolean surfaceCreated = false;
	private Handler mUIHandler = new Handler();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); 
		setContentView(R.layout.activity_display_h264);
	
		mSv = (SurfaceView) findViewById(R.id.viewSurface);
		mSh = mSv.getHolder();
		mSh.setKeepScreenOn(true);
		mSh.addCallback(new SurfaceCallback());

		((Button) findViewById(R.id.bStart)).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (mH264de == null){
					mH264de = new DecodeH264();
					mH264de.setInfoListener(new DecodeH264.onInfoListener() {
						@Override
						public void onInfo(int type, int arg1, int arg2) {
						//	Log.d(TAG, "1 postEventFromNative update time = " 
						//				+ arg1 + " sec ; " + " size = " + arg2 );
						}
					});
					mH264de.setOnDataListener(new ByteBufferListener());
				}
				
				if (surfaceCreated == false){
					Toast.makeText(ByteBufferActivity.this, "Surface Not Available now", Toast.LENGTH_LONG)
							.show();
				}else{
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
					mH264de.start(mSh .getSurface() , path1080p60fps , 
									sps1080p60fps  , pps1080p60fps );
				} 
			}
		});
		
		((Button) findViewById(R.id.bStop)).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (mH264de != null){
					mH264de.release();
					mH264de = null;
				}else{
					Toast.makeText(ByteBufferActivity.this, "DecodeH264 Not Created now", Toast.LENGTH_LONG)
					.show();
				}
			}
		});
	}

	@Override
	protected void onDestroy() {
		if (mH264de != null){
			mH264de.release();
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


	private class ByteBufferListener implements DecodeH264.onDataListener
	{
		@Override
		public void onData(int data_type, ABuffer adata) {
			
			ByteBuffer data = adata.mData ;
			if( data_type == DecodeH264.MEDIA_H264_SAMPLE ){
				String log = String.format("[%d %d][%x %x %x %x][pos:%d lef:%d cap:%d lim:%d dir:%b] ",
						adata.mTimestamp ,adata.mActualSize ,
						0xFF&data.get(0), 
						0xFF&data.get(1),
						0xFF&data.get(2),
						0xFF&data.get(3),
						data.position(),
						data.remaining(),
						data.capacity(),
						data.limit(),
						data.isDirect());	
				
				//try{
				//	data.put((byte)12);
				//}catch(java.nio.ReadOnlyBufferException ronly){
				//	Log.e(TAG,"ronly " + ronly.getMessage() );
				//}
				 
				final ABuffer delayBuffer = adata ;
				mUIHandler.postDelayed(new Runnable(){
					@Override
					public void run() {
						Log.d(TAG , "delay release");
						delayBuffer.release();
					}
				},1000); // delay 1 second , and then release buffer
				
				Log.d(TAG,log);
			}
			
		}
	}
}
