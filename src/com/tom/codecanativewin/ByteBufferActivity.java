package com.tom.codecanativewin;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

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

	// adb logcat -s jni_decodeh264 java_decodeh264  ByteBuffer ABuffer
	
	private class ByteBufferListener implements DecodeH264.onDataListener
	{
		@Override
		public void onData(int data_type, ABuffer adata) {
			
			ByteBuffer data = adata.mData ;
			if( data_type == DecodeH264.MEDIA_H264_SAMPLE ){
				String log = String.format("[%d %d][%x %x %x %x][pos:%d lef:%d cap:%d lim:%d dir:%b rd:%b] ",
						adata.mTimestamp ,adata.mActualSize ,
						0xFF&data.get(0), 
						0xFF&data.get(1),
						0xFF&data.get(2),
						0xFF&data.get(3),
						data.position(),
						data.remaining(),
						data.capacity(),
						data.limit(),
						data.isDirect(),
						data.isReadOnly() );	
				/*
				 * asReadOnlyBuffer:
				 * ByteBuffer: [1479453319 9827][0 0 0 1][pos:0 lef:9827 cap:9827 lim:9827 dir:true]
				 * ByteBuffer: [1479453319 11140][0 0 0 1][pos:0 lef:11140 cap:11140 lim:11140 dir:true]
				 * ByteBuffer: [1479453318 5297][0 0 0 1][pos:0 lef:5297 cap:5297 lim:5297 dir:true]
				 *
				 * 不是 asReadOnlyBuffer
				 * ByteBuffer: [1479453722 9431][0 0 0 1][pos:0 lef:9431 cap:9431 lim:9431 dir:true rd:false]
				 * ByteBuffer: [1479453722 3399][0 0 0 1][pos:0 lef:3399 cap:3399 lim:3399 dir:true rd:false]
				 * ByteBuffer: [1479453722 9677][0 0 0 1][pos:0 lef:9677 cap:9677 lim:9677 dir:true rd:false]
				 * ByteBuffer: [1479453722 10880][0 0 0 1][pos:0 lef:10880 cap:10880 lim:10880 dir:true rd:false]
				 *
				 */
				final ABuffer delayBuffer = adata ;
				mUIHandler.postDelayed(new Runnable(){
					@Override
					public void run() {
						Log.d(TAG , "delay release");
						delayBuffer.release();
					}
				},1000); // delay 1 second , and then release buffer
				
				Log.d(TAG,log);
				
				/*
				 * 1.对于 DirectByteBuffer
				 * 	不可以
				 * 		byte[] array = data.array();
				 * 	会出现异常
				 * 		Pending exception java.lang.UnsupportedOperationException:
				 * 
				 * 	但是可以 (write或者read only都可以)
				 *		IntBuffer buffer = data.asIntBuffer();
				 *
				 *	对于可write的direct buffer:
				 *		buffer.put(12); // postion会向前移动 , 使用put和get来读取数据
				 *	对于only read的direct buffer
				 *		不能进行put操作 否则 ReadOnlyBufferException
				 *	
				 *	
				 * 2.ReadOnly的DirectByteBuffer,不能进行put
				 * 	否则 
				 * 		Pending exception java.nio.ReadOnlyBufferException
				 * 	try{
				 *		data.put((byte)12);
				 * 	}catch(java.nio.ReadOnlyBufferException ronly){
				 *		Log.e(TAG,"ronly " + ronly.getMessage() );
				 * 	}
				 * 
				 * 3.如果put大于容量 将会出现exception:
				 * 		java.nio.BufferOverflowException
				 * 
				 * 
				 * 4. 由于HeapByteBuffer和DirectByteBuffer类都是default类型的 所以你无法字节访问到
				 * 		你只能通过ByteBuffer间接访问到它 因为JVM不想让你访问到它
				 * 		在NIO的框架下,很多框架会采用DirectByteBuffer来操作 
				 * 		这样分配的内存不再是在java heap上,而是在C heap上 
				 * 		经过性能测试,可以得到非常快速的网络交互,在大量的网络交互下,一般速度会比HeapByteBuffer要快速好几倍
				 * 	  !!!! 在OpenJDK 和 Android的Java DirectByteBuffer.java中 没有 Cleaner !	(sun.misc.Cleaner)
				 */
				 
//				IntBuffer buffer = data.asIntBuffer();
//				Log.d(TAG, ">>> 0: " + buffer.get(0) + " pos:" + buffer.position() );
//				buffer.put(10);
//				Log.d(TAG, "<<< 0: " + buffer.get(0) + " pos:" + buffer.position() );
				
 
			}
			
		}
	}
}
