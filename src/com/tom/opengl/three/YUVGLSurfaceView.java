package com.tom.opengl.three;

import java.io.IOException;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import android.content.Context;
import android.opengl.EGL14;
import android.opengl.GLSurfaceView;
import android.os.Handler;
import android.os.Message;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture ;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;

public class YUVGLSurfaceView extends GLSurfaceView  {
	// 	Android4.0 SurfaceView
	//	Android4.2 GLSurfaceView --> EGLDisplay EGLContext
	// 	Android4.4 TextureView   --> SurfaceTexure
	
	private static final String TAG = "YUVGLSurfaceView" ;
	
	private SurfaceTexture mSurface;  
    private Handler mCallback;
    private GLFrameRender mGLFrameRender ; 
    
    public GLFrameRender getRenderUpdate()
    {
    	Log.d(TAG, "YUVGLSurfaceView getRenderUpdate  = " + mGLFrameRender);
    	return mGLFrameRender ; // call mGLFrameRender.update 
    }
    
	public YUVGLSurfaceView(Context context) {
		super(context);
		setEGLContextClientVersion(2); 
		mGLFrameRender = new GLFrameRender(this);
        setRenderer(mGLFrameRender);  
        setRenderMode(RENDERMODE_WHEN_DIRTY); 
	}

	public YUVGLSurfaceView(Context context, AttributeSet attrs) {
		super(context, attrs);
		Log.d(TAG, "YUVGLSurfaceView (Context attrs) context = " + context);
		setEGLContextClientVersion(2); 
		mGLFrameRender = new GLFrameRender(this);
        setRenderer(mGLFrameRender);  
        setRenderMode(RENDERMODE_WHEN_DIRTY); 
	}
	
	@Override
	protected void onAttachedToWindow() {
		Log.d(TAG,"onAttachedToWindow");
		super.onAttachedToWindow();
	}

	@Override
	protected void onDetachedFromWindow() {
		Log.d(TAG,"onDetachedFromWindow");
		super.onDetachedFromWindow();
	}

	@Override
	public void onPause() {
		Log.d(TAG, "onPause");
		super.onPause();
	}

	@Override
	public void onResume() {
		Log.d(TAG, "onResume");
		super.onResume(); 
	}

}
