package com.tom.opengl.one;

 
import java.nio.ByteBuffer;
 

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import com.tom.opengl.two.FastinOnePlane;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLSurfaceView.Renderer;
 
import android.util.Log;

public class GLFrameRender implements Renderer {
	
	private final static String TAG = "GLFrameRender" ;
	
    private GLSurfaceView mTargetSurface;
    //private GLProgram420SP mProgram = null; 
    private GLProgram420P mProgram = null; 
 
    private int mHeight = -1;
    private int mWidth = -1 ;
    private ByteBuffer yuv;
 
 
    public GLFrameRender(GLSurfaceView surface ) {
        mTargetSurface = surface;
    
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
    	Log.d(TAG, "onSurfaceCreated");
        if (mProgram == null ) {
        	//mProgram = new GLProgram420SP(0); 
        	mProgram = new GLProgram420P(0); 
        	mProgram.buildProgram();
        }
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        
        if (yuv != null) {
        	//GLES20.glViewport(0, 0, mVideoHeight, mVideoWidth);
            yuv.position(0);
            GLES20.glClearColor(0.5f, 0.5f, 0.5f, 1.0f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            mProgram.drawFrame(yuv);
        }
        synchronized (mDrawDone) {
        	mDrawDone = true ;
        }
      
    }

 
    public void setupTempBuffer(int w, int h) { 
        if (w > 0 && h > 0) {
            //yuv = ByteBuffer.allocateDirect(w*h*3/2);
        	yuv = ByteBuffer.allocateDirect( w * h * 2 );
            mProgram.setHW(w, h);
        	mHeight = h ;
        	mWidth = w ;
        	
        	Log.d(TAG, String.format("setup temp Buffer %d %d %d ",
        					w , h , yuv.capacity()));
         
        }
    }

    private Boolean mDrawDone = true ;
 
    public void update(ByteBuffer yData, ByteBuffer uData , ByteBuffer vData , int stride ) {
        synchronized (mDrawDone) {
        	if( mDrawDone ){
        		mDrawDone = false; 
        	}else{
        		Log.e(TAG,"last frame have NOT drawn");
        		return ;
        	}
        }
        
        yuv.clear();
     
//        if(!FastinOnePlane.convert(uv, uData ,vData, mWidth , mHeight , stride, FastinOnePlane.TO_420SP)){
//        	Log.e(TAG, "convert error ");
//        }
        if(!FastinOnePlane.convert2(yuv, yData, uData ,vData, mWidth , mHeight , stride, FastinOnePlane.TO_420P)){
    	  Log.e(TAG, "convert error ");
      	}        
        mTargetSurface.requestRender();
    }
    
}
