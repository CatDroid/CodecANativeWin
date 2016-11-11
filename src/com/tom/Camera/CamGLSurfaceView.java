package com.tom.Camera;

import java.util.HashMap;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import android.content.Context;
import android.opengl.EGL14;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture ;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;

public class CamGLSurfaceView extends GLSurfaceView 
								implements GLSurfaceView.Renderer , SurfaceTexture.OnFrameAvailableListener {


	
	private static final String TAG = "CamGLSurfaceView" ;
	
	private SurfaceTexture mSurface;  
    private DirectDrawer mDirectDrawer;  
    private Handler mCallback;
    
	public CamGLSurfaceView(Context context) {
		super(context);
		Log.d(TAG, "TomGLSurfaceView (Context) context = " + context);
		Log.d(TAG, "TomGLSurfaceView App context = " + context.getApplicationContext());
		
		setEGLContextClientVersion(2);  
        setRenderer(this);  
        setRenderMode(RENDERMODE_WHEN_DIRTY);  
	}

	public CamGLSurfaceView(Context context, AttributeSet attrs) {
		super(context, attrs);
		Log.d(TAG, "TomGLSurfaceView (Context attrs) context = " + context);
		Log.d(TAG, "TomGLSurfaceView App context = " + context.getApplicationContext());
		
		setEGLContextClientVersion(2);  
        setRenderer(this);  
        setRenderMode(RENDERMODE_WHEN_DIRTY);  
	}
	
	public void setCallback(Handler handler ){
		if( mCallback != null){
			Log.w(TAG, "overlay the old one mCallback");
		}
		mCallback = handler ;
	}
	
	public SurfaceTexture getSurfaceTexture(){  
	        return mSurface;  
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

	// From android.graphics.SurfaceTexture.OnFrameAvailableListener
	@Override
	public void onFrameAvailable(SurfaceTexture arg0) {
		Log.d(TAG,"onFrameAvailable Thread id = " + Thread.currentThread().getId() + " name = " + Thread.currentThread().getName() );
		requestRender();  // android.opengl.GLSurfaceView.requestRender
	}

	// From android.opengl.GLSurfaceView.Renderer 
	@Override
	public void onDrawFrame(GL10 gl) {
		Log.d(TAG,"GLSurfaceView.Renderer.onDrawFrame");
        //mSurface.updateTexImage();  
   
        //float[] mtx = new float[16];  
        //mSurface.getTransformMatrix(mtx);  // 每次都查询 纹理坐标系矩阵 4x4
        //mDirectDrawer.draw(mtx);  
	}

	@Override
	public void onSurfaceChanged(GL10 gl, int width, int height) {
		Log.d(TAG,"GLSurfaceView.Renderer.onSurfaceChanged gl w:" + width +  " h: "+ height);
        if(mCallback != null){
        	Message msg = mCallback.obtainMessage(MessageType.MSG_RENDER_CHANGE);
        	msg.arg1 = width ;
        	msg.arg2 = height ;
        	mCallback.sendMessage(msg);
        }else{
        	Log.e(TAG, "no one be informed MSG_RENDER_CHANGE");
        }
	}

	@Override
	public void onSurfaceCreated(GL10 gl, EGLConfig config) {
		Log.d(TAG,"GLSurfaceView.Renderer.onSurfaceCreated thread id = " + Thread.currentThread().getId() + " name = " + Thread.currentThread().getName());
 
		// 内部调用了
		// EGL14.eglMakeCurrent(mEGLDisplay, mEGLSurface, mEGLSurface, mEGLContext); 
		 
		EGLContext eglcontext = EGL14.eglGetCurrentContext() ; 
		EGLDisplay egldisplay = EGL14.eglGetCurrentDisplay() ; 
		EGLSurface eglsurfaceRd = EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW);
		EGLSurface eglsurfaceWr = EGL14.eglGetCurrentSurface(EGL14.EGL_READ);
		Log.d(TAG, "eglcontext = " + eglcontext 
					+ " eglsurfaceRd = " + eglsurfaceRd 
					+ " eglsurfaceWr = " + eglsurfaceWr 
					+ " egldisplay = " + egldisplay );
		
        if(mCallback != null){
        	Message msg = mCallback.obtainMessage(MessageType.MSG_RENDER_CREATED);
        	HashMap<Integer,Object> b = new HashMap<Integer,Object>();
        	b.put(0, eglcontext);
        	b.put(1, eglsurfaceRd);
        	b.put(2, eglsurfaceWr);
        	b.put(3, egldisplay);
        	msg.obj = b  ;
        	mCallback.sendMessage(msg);
        }else{
        	Log.e(TAG, "no one be informed MSG_RENDER_CREATED");
        }
	}
}
