package com.tom.Camera;

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

public class TomGLSurfaceView extends GLSurfaceView 
								implements GLSurfaceView.Renderer , SurfaceTexture.OnFrameAvailableListener {
	// 	Android4.0 SurfaceView
	//	Android4.2 GLSurfaceView --> EGLDisplay EGLContext
	// 	Android4.4 TextureView   --> SurfaceTexure
	
	private static final String TAG = "tom_gl" ;
	
	private SurfaceTexture mSurface;  
    private DirectDrawer mDirectDrawer;  
    private Handler mCallback;
    
	public TomGLSurfaceView(Context context) {
		super(context);
		Log.d(TAG, "TomGLSurfaceView (Context) context = " + context);
		Log.d(TAG, "TomGLSurfaceView App context = " + context.getApplicationContext());
		
		//	默认 GLSurfaceView 创建 PixelFormat.RGB_888 format的界面 
		//	如果需要半透明的界面   getHolder().setFormat(PixelFormat.TRANSLUCENT);
		//	a TRANSLUCENT surface是设备依赖的
		//	但他一定是32-bit-per-pixel 的 带有 8 bits每个颜色成分
		
		//getHolder().setFormat(PixelFormat.TRANSLUCENT);
		
		// android.opengl.GLSurfaceView.// 在setRenderer(Renderer)调用之前
		setEGLContextClientVersion(2);  // 告知 默认的 EGLContextFactory 和 默认的 EGLConfigChooser
										// 哪个 EGLContext client 版本被选择 
										// Android支持OpenGL ES1.1和2.0及3.0 
										// 如果设置了setEGLContextFactory(EGLContextFactory) 或者 
										// setEGLConfigChooser(EGLConfigChooser) 
										// 那么 EGLContextFactory或EGLConfigChooser 有责任对应创建合适版本的context或config
        setRenderer(this);  
        setRenderMode(RENDERMODE_WHEN_DIRTY); // onFrameAvailable --> this.requestRender(); 
        
//		mDirectDrawer = new DirectDrawer(); 
//		mSurface = new SurfaceTexture(mDirectDrawer.getTextureId());  
//		mSurface.setOnFrameAvailableListener(this);  
	}

	public TomGLSurfaceView(Context context, AttributeSet attrs) {
		super(context, attrs);
		Log.d(TAG, "TomGLSurfaceView (Context attrs) context = " + context);
		Log.d(TAG, "TomGLSurfaceView App context = " + context.getApplicationContext());
		
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
		//Log.d(TAG,"This method is used as part of the View class ");
		//Log.d(TAG,"and is not normally called or subclassed by clients of GLSurfaceView. ");
		super.onAttachedToWindow();
	}
	// onAttachedToWindow(如果第一次进入Activity或旋转屏幕) --> SurfaceHolder.surfaceCreated --> GLSurfaceView.Renderer.onSurfaceCreated
	// SurfaceHolder.surfaceDestroyed -->(如果退出Activity或旋转屏幕)onDetachedFromWindow
 

	@Override
	protected void onDetachedFromWindow() {
		Log.d(TAG,"onDetachedFromWindow");
		//Log.d(TAG,"This is called when the view is detached from a window. ");
		//Log.d(TAG,"At this point it no longer has a surface for drawing. ");
		super.onDetachedFromWindow();
	}

	@Override
	public void onPause() {
		// GLSurfaceView.setPreserveEGLContextOnPause 默认是false
		// 控制 EGL context 是否保留 当 GLSurfaceView 被 paused and resumed.
		//
		// true => EGL context可能被保留 根据设备是否支持任意数量的EGL context 有些设备只是支持有限数量的EGL Context 所以必须释放让其他程序可以使用GPU
		// false=> EGL context 会被释放 当 paused, 和重建 当GLSurfaceView   resumed. 
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
		Log.d(TAG,"onFrameAvailable");
		requestRender();  // android.opengl.GLSurfaceView.requestRender
	}

	// From android.opengl.GLSurfaceView.Renderer 
	@Override
	public void onDrawFrame(GL10 gl) {
		Log.d(TAG,"GLSurfaceView.Renderer.onDrawFrame");
		
        mSurface.updateTexImage();  
        float[] mtx = new float[16];  
        mSurface.getTransformMatrix(mtx);  
        mDirectDrawer.draw(mtx);  
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
		Log.d(TAG,"GLSurfaceView.Renderer.onSurfaceCreated");
		//Log.d(TAG,"Called when the surface is created or recreated. ");
		//Log.d(TAG,"Called when the rendering thread starts and whenever the EGL context is lost.");
		//Log.d(TAG,"The EGL context will typically be lost when the Android device awakes after going to sleep. ");
		//Log.d(TAG,"this method is a convenient place to put code to create resources that need to be created when the rendering starts,");
		//Log.d(TAG,"and that need to be recreated when the EGL context is lost. ");
		//Log.d(TAG,"Textures are an example of a resource that you might want to create here. ");
		//Log.d(TAG," when the EGL context is lost, all OpenGL resources associated with that context will be automatically deleted.");
		
		// Create Texture Here 必须在 onSurfaceCreated 不能在构造函数
		
		// // 如果进来这里的话 必须重新  new DirectDrawer 和 SurfaceTexture 否则错误:
		/*
		 * 不重新SurfaceTexture:
		 * E/GLConsumer(25902): checkAndUpdateEglState: invalid current EGLContext
		 * java.lang.IllegalStateException: Unable to update texture contents (see logcat for details)
		 *
		 * 不重新DirectDrawer:(构造函数中 使用 GLES20 获得纹理ID )
		 * W/GLConsumer(26504): bindTextureImage: clearing GL error: 0x501
		 * 
		 * 放在这里相当于 之前已经调用了
		 * 1.
		 * 	mEGLSurface = EGL14.eglCreateWindowSurface(mEGLDisplay, 
														xxx , 
														SurfaceView.SurfaceHolder.Surface, 
														xxx , 
														xxx ); 
	     * 跟SurfaceView(EGLSurface)相关联
	     * 2.
		 * EGL14.eglMakeCurrent(mEGLDisplay, mEGLSurface, mEGLSurface, mEGLContext);
		 * 把线程与一个EGLContext绑定在一起  OpenGL的操作都在这个EGLContext上 
		 * 
		 */
			
		
			mDirectDrawer = new DirectDrawer(); 
		
	        mSurface = new SurfaceTexture(mDirectDrawer.getTextureId());  
	        mSurface.setOnFrameAvailableListener(this);  
		 
        if(mCallback != null){
        	Message msg = mCallback.obtainMessage(MessageType.MSG_RENDER_CREATED);
        	mCallback.sendMessage(msg);
        }else{
        	Log.e(TAG, "no one be informed MSG_RENDER_CREATED");
        }

	}
	
	
	

}
