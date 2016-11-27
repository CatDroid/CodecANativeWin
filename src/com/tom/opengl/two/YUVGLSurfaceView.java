package com.tom.opengl.two;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.util.Log;
 

public class YUVGLSurfaceView extends GLSurfaceView  {
	// 	Android4.0 SurfaceView
	//	Android4.2 GLSurfaceView --> EGLDisplay EGLContext
	// 	Android4.4 TextureView   --> SurfaceTexure
	
	private static final String TAG = "YUVGLSurfaceView" ;
	
 
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
