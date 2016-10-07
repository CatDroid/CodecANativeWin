package com.tom.codecanativewin;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import android.app.Activity;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.storage.StorageManager;
import android.util.Base64;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;

public class MainActivity extends Activity {

	final public static String TAG = "main"; // Native Window Codec

 
	private Handler mHandler;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		setContentView(R.layout.activity_main);
		
		HandlerThread thread = new HandlerThread("session");
		thread.start();
		mHandler = new Handler(thread.getLooper());
	
		byte[] decodesps = Base64.decode("AAAAAWdCwB6adAPAPNgIgAAAAwCAAAAeR4sXUA==", Base64.NO_WRAP);
		byte[] decodepps = Base64.decode("AAAAAWjOPIA=", Base64.NO_WRAP);
		
		Log.d(TAG, "decodesps = " + Arrays.toString(decodesps ) );
		Log.d(TAG, "decodepps = " + Arrays.toString(decodepps ) );
	}
	
	

	@Override
	protected void onDestroy() {
		if(mHandler != null){
			mHandler.getLooper().quit();
		}
		super.onDestroy();
	}



	/*
	 * 如果你把这个Btn3OnClick写在别的activity里面就会出错抛异常提示你“非法状态异常” 。
	 * 如果你把public写成private也会提示你“非法状态异常”。 如果你没有在括号里加(View view)也会提示你“非法状态异常”。
	 * 最后正确的格式就是public void xxxx(View view){...}
	 */
	public void funcOnClick(View view) {
		Intent start = null;
		switch (view.getId()) {
		case R.id.bNativeWinCodec:
			start = new Intent(MainActivity.this, NativeWinCodecActivity.class);
			this.startActivity(start);
			this.finish();
			break;
		case R.id.bCamera:
			start = new Intent(MainActivity.this, CameraActivity.class);
			this.startActivity(start);
			this.finish();
			break;
		case R.id.bDeEnCodec:
			start = new Intent(MainActivity.this, De2FileAndEn2FileActivity.class);
			this.startActivity(start);
			this.finish();
			break;
		case R.id.bPlayMediaPlayer:
			start = new Intent(MainActivity.this, MediaPlayerActivity.class);
			this.startActivity(start);
			this.finish();
			break;
		case R.id.bDisplayH264:
			start = new Intent(MainActivity.this, DisplayH264Activity.class);
			this.startActivity(start);
			this.finish();
			break;	
		case R.id.bAccFileDecode:
			start = new Intent(MainActivity.this, AudioDecActivity.class);
			this.startActivity(start);
			this.finish();			
			break;
		case R.id.bByteBuffer:
			start = new Intent(MainActivity.this, ByteBufferActivity.class);
			this.startActivity(start);
			this.finish();			
			break;
			
		default:
			Log.d(TAG, "unknown Btn");
			break;
		}
	}

}
