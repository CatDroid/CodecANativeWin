package com.tom.entry;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import com.tom.Camera.CamRecbyOpenGL;
import com.tom.Camera.NewCameraActivity;
import com.tom.Camera.OldCameraActivity;
import com.tom.MemThread.ThreadActivity;
import com.tom.codecanativewin.AudioDecActivity;
import com.tom.codecanativewin.ByteBufferActivity;
import com.tom.codecanativewin.De2FileAndEn2FileActivity;
import com.tom.codecanativewin.DisplayH264Activity;
import com.tom.codecanativewin.FormatActivity;
import com.tom.codecanativewin.JBitmapActivity;
import com.tom.codecanativewin.MediaPlayerActivity;
import com.tom.codecanativewin.NativeHeapActivity;
import com.tom.codecanativewin.NativeWinCodecActivity;
import com.tom.codecanativewin.R;
import com.tom.codecanativewin.RtspPlayerActivity;
import com.tom.opengl.one.Texture1RGBActivity;
import com.tom.opengl.three.DecodeYUVGLActivity;
import com.tom.opengl.two.TextureUV2RGBActivity;
import com.tom.codecanativewin.R.id;
import com.tom.codecanativewin.R.layout;

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
		
		Log.d(TAG, "package name " + MainActivity.this.getPackageName() );
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
		int i = view.getId();
		if (i == id.bNativeWinCodec) {
			start = new Intent(MainActivity.this, NativeWinCodecActivity.class);
			this.startActivity(start);
			this.finish();

		} else if (i == id.bCamera) {
			start = new Intent(MainActivity.this, CamRecbyOpenGL.class);
			this.startActivity(start);
			this.finish();

		} else if (i == id.bDeEnCodec) {
			start = new Intent(MainActivity.this, De2FileAndEn2FileActivity.class);
			this.startActivity(start);
			this.finish();

		} else if (i == id.bPlayMediaPlayer) {
			start = new Intent(MainActivity.this, MediaPlayerActivity.class);
			this.startActivity(start);
			this.finish();

		} else if (i == id.bDisplayH264) {
			start = new Intent(MainActivity.this, DisplayH264Activity.class);
			this.startActivity(start);
			this.finish();

		} else if (i == id.bAccFileDecode) {
			start = new Intent(MainActivity.this, AudioDecActivity.class);
			this.startActivity(start);
			this.finish();

		} else if (i == id.bByteBuffer) {
			start = new Intent(MainActivity.this, ByteBufferActivity.class);
			this.startActivity(start);
			this.finish();

		} else if (i == id.bRtsp) {
			start = new Intent(MainActivity.this, RtspPlayerActivity.class);
			this.startActivity(start);
			this.finish();

		} else if (i == id.bCam2OpenGL) {
			start = new Intent(MainActivity.this, OldCameraActivity.class);
			this.startActivity(start);
			this.finish();

		} else if (i == id.bColorFormat) {
			start = new Intent(MainActivity.this, FormatActivity.class);
			this.startActivity(start);
			this.finish();

		} else if (i == id.bDecodeYUVGL) {
			start = new Intent(MainActivity.this, DecodeYUVGLActivity.class);
			this.startActivity(start);
			this.finish();

		} else if (i == id.bDecodeYUVGL2Plane) {
			start = new Intent(MainActivity.this, TextureUV2RGBActivity.class);
			this.startActivity(start);
			this.finish();

		} else if (i == id.bDecodeYUVGL1Plane) {
			start = new Intent(MainActivity.this, Texture1RGBActivity.class);
			this.startActivity(start);
			this.finish();

		} else if (i == id.bNativeHeap) {
			start = new Intent(MainActivity.this, NativeHeapActivity.class);
			this.startActivity(start);
			this.finish();

		} else if (i == id.jbitmap) {
			start = new Intent(MainActivity.this, JBitmapActivity.class);
			this.startActivity(start);
			this.finish();

		} else if( i == id.ManyThreads){

			start = new Intent(MainActivity.this, ThreadActivity.class);
			this.startActivity(start);
			this.finish();

		} else {
			Log.d(TAG, "unknown Btn");

		}
	}

}
