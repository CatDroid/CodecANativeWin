package com.tom.codecanativewin;


import com.tom.codecanativewin.jni.De2FileAndEn2File;
import com.tom.codecanativewin.jni.NativeWinCodec;

import android.app.Activity;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;


public class De2FileAndEn2FileActivity extends Activity {

	final public static String TAG = "stream_java" ;
	private De2FileAndEn2File mDe2FileAndEn2File = null;
	
	private SurfaceView mSv = null;
	private SurfaceHolder mSh = null;
	private boolean surfaceCreated = false;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_de2_file_and_en2_file);
		
		
		mSv = (SurfaceView) findViewById(R.id.viewSurface);
		mSh = mSv.getHolder();
		mSh.setKeepScreenOn(true);
		mSh.addCallback(new SurfaceCallback());
		
		
		Button btn = (Button)findViewById(R.id.bStart);
		btn.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				
				if(mDe2FileAndEn2File == null) mDe2FileAndEn2File = new De2FileAndEn2File();
				if(surfaceCreated == false) Toast.makeText(De2FileAndEn2FileActivity.this, "Surface Not Available now", 
												Toast.LENGTH_LONG).show();
				mDe2FileAndEn2File.decodeAndEncode(mSh.getSurface());
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
			Log.d(TAG , "surfaceCreated created");
			surfaceCreated = true ;
		}

		@Override
		public void surfaceDestroyed(SurfaceHolder arg0) {
			Log.d(TAG , "surfaceCreated destroyed");
		}
	}
	

}
