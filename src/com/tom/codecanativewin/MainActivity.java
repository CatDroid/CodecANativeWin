package com.tom.codecanativewin;

import android.app.Activity;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;

public class MainActivity extends Activity {

	final public static String TAG = "main" ; // Native Window Codec 
 
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); 
		setContentView(R.layout.activity_main);
	}
	
	/*
	 * 	如果你把这个Btn3OnClick写在别的activity里面就会出错抛异常提示你“非法状态异常” 。
		如果你把public写成private也会提示你“非法状态异常”。
		如果你没有在括号里加(View view)也会提示你“非法状态异常”。
		最后正确的格式就是public void xxxx(View view){...}
	 * */
	public void funcOnClick(View view){
		Intent start = null;
		switch ( view.getId() ){
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
		default:
			Log.d(TAG, "unknown Btn");
			break;
		}
	}

}