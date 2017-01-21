package com.tom.Camera;

import java.io.IOException;
import java.util.Arrays;

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
		Log.d(TAG,"onFrameAvailable Thread id = " + Thread.currentThread().getId() + " name = " + Thread.currentThread().getName() );
		requestRender();  // android.opengl.GLSurfaceView.requestRender
	}

	// From android.opengl.GLSurfaceView.Renderer 
	@Override
	public void onDrawFrame(GL10 gl) {
		Log.d(TAG,"GLSurfaceView.Renderer.onDrawFrame");
		
	     /**
         * SurfaceTexture
         * 从一个 照片流(an image stream)捕捉帧(frames)  作为 一个OpenGL ES纹理 (OpenGL ES texture.)
         * 这个照片流 可以来自 摄像头预览 或者 解码器输出 
         * 一个从SurfaceTexture创建的Surface可以作为camera2和MediaCodec MediaPlayer Allocation的输出目标 (ANativeWindow)
         * 
         * 当 updateTexImage被调用  指定的texture(SurfaceTexture已经创建)内容被更新到最近的一个照片
         * 这可能导致流中的某些帧被丢掉
         * 
         * 使用旧的Camera API指定目标时候  SurfaceTexture可以取代SurfaceHolder
         * 这样做 会使照片流的所有帧 发送到 SurfaceTexture而不是设备的显示 
         * 
         * 当要从texture采样，必须先改变纹理坐标系(texture coordinates) 使用从getTransformMatrix(float[])查询到的矩阵(4x4 matrix)
         * 这个变换矩阵可每次调用  updateTexImage()后改变 ， 所以每次  updateTexImage()之后必须重新查询
         * 
         * 这个矩阵改变 传统2D OpenGL ES纹理坐标  列向量  格式为(s, t, 0, 1) (其中s和t是闭合区间[0 1]之间的值，对应在已在流的纹理(streamed texture)采样位置(proper sampling location))
         * 这个变换补偿   照片流源 的 任何特性   导致它显示不同与传统的 2D OpenGL ES纹理
         * 比如  从左下角开始采样的图片 可以通过查询矩阵得到的列向量(0,0,0,1)变换来完成
         * 同样，从上右角开始采样的图片 可以通过(1,1,0,1)变换完成
         * 
         * 纹理对象(texture object ) 使用  GL_TEXTURE_EXTERNAL_OES 纹理目标(texture target) (定义在OpenGL ES扩展 GL_OES_EGL_image_external )
         * 这个限制纹理的使用. 每一次绑定纹理 它必须绑定到 GL_TEXTURE_EXTERNAL_OES 目标 而不是  GL_TEXTURE_2D 目标 
         * 另外 任何从纹理(texture)采样的OpenGL ES2.0的shader必须声明它使用的扩展 
         * 比如 通过一条指令 "#extension GL_OES_EGL_image_external : require"
         * 这样的sharders 必须 通过 samplerExternalOES GLSL sampler type 来访问纹理
         * 
         * SurfaceTexture可以在任何线程创建
         * 但是updateTexImage一般只能在 包含纹理对象的GL上下文( OpenGL ES context) 所在线程上调用
         * 
         * 由于'帧到来'(frame-available callback )的回调在任何线程上，
         * 所以除非采取特别的措施,updateTexImage不应该在'帧到来'的回调上直接调用
         */
        mSurface.updateTexImage();  
   
        float[] mtx = new float[16];  
        mSurface.getTransformMatrix(mtx);  // 每次都查询 纹理坐标系矩阵 4x4
        mDirectDrawer.draw(mtx);  
        Log.d(TAG,"mtx = " + Arrays.toString(mtx));
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
	 
		// Create Texture Here 必须在 onSurfaceCreated 不能在构造函数
		
		// // 如果进来这里的话 必须重新  new DirectDrawer 和 SurfaceTexture 否则错误:
		/**
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
