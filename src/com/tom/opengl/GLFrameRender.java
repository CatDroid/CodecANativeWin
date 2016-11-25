package com.tom.opengl;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLSurfaceView.Renderer;
import android.os.AsyncTask;
import android.util.Log;

public class GLFrameRender implements Renderer {
	
	private final static String TAG = "GLFrameRender" ;
	
    private GLSurfaceView mTargetSurface;
    private GLProgram mProgram = null; 
    private int mScreenWidth, mScreenHeight;
    private int mVideoWidth, mVideoHeight;
    private ByteBuffer y;
    private ByteBuffer u;
    private ByteBuffer v;


    public GLFrameRender(GLSurfaceView surface ) {
        mTargetSurface = surface;
    
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
    	Log.d(TAG, "onSurfaceCreated");
        if (mProgram == null ) {
        	mProgram = new GLProgram(0); 
        	mProgram.buildProgram();
        }
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
        mScreenWidth = width;
        mScreenHeight = height;
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        synchronized (this) {
            if (y != null) {
                // reset position, have to be done
                y.position(0);
                u.position(0);
                v.position(0);
                //GLES20.glViewport(0, 0, mVideoHeight, mVideoWidth);
                mProgram.buildTextures(y, u, v, mVideoWidth, mVideoHeight); // 提供图像 给纹理
                GLES20.glClearColor(0.5f, 0.5f, 0.5f, 1.0f);
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
                mProgram.drawFrame();

      
            }
        }
    }

    /**
     * this method will be called from native code, it happens when the video is about to play or
     * the video size changes.
     */
    public void update(int w, int h) {
        if (w > 0 && h > 0) {
            // 初始化容器
            if (w != mVideoWidth && h != mVideoHeight) {
                this.mVideoWidth = w;
                this.mVideoHeight = h;
                int yArraySize = w * h;
                int uvArraySize = yArraySize / 4;
                synchronized (this) {
                    y = ByteBuffer.allocate(yArraySize);
                    u = ByteBuffer.allocate(uvArraySize);
                    v = ByteBuffer.allocate(uvArraySize);
                }

                if (mVideoWidth > 0 && mVideoHeight > 0) {
                    float f1 = 1f * mVideoHeight / mVideoWidth;
                    float f2 = 1f * h / w;
                    Log.d(TAG, "update ? " + (f1 == f2) );
                    if (f1 == f2) {
                    	mProgram.createBuffers(GLProgram.squareVertices); // 顶点 fullscreen 
                    } else if (f1 < f2) {
                        float widScale = f1 / f2;
                        mProgram.createBuffers(new float[] { -widScale, -1.0f, widScale, -1.0f,
                                -widScale, 1.0f, widScale,1.0f, });
                    } else {
                        float heightScale = f2 / f1;
                        mProgram.createBuffers(new float[] { -1.0f, -heightScale, 1.0f, -heightScale,
                                -1.0f, heightScale, 1.0f, heightScale, });
                    }
                }
            }
            // 调整比例
            /*if (mScreenWidth == 0) {
                mScreenWidth = w;
            }

            if (mScreenHeight == 0) {
                mScreenHeight = h;
            }*/
        }
    }

    /**
     * this method will be called from native code, it's used for passing yuv data to me.
     */
    public void update(ByteBuffer yData, ByteBuffer uData, ByteBuffer vData) {
        synchronized (this) {
            y.clear();
            u.clear();
            v.clear();
            y.put(yData);
            u.put(uData);
            v.put(vData); // positon会改变
        }

        // request to render
        mTargetSurface.requestRender();
    }
}
