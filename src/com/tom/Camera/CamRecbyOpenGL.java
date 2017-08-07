package com.tom.Camera;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.HashMap;

import com.tom.Camera.OldCameraActivity.MyHandler;
import com.tom.codecanativewin.R;
import com.tom.codecanativewin.R.layout;

import android.app.Activity;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceView;
// import android.hardware.camera2.CameraDevice;
// import android.hardware.camera2.CameraManager;
// import android.hardware.camera2.CaptureResult;
// import android.hardware.camera2.CaptureRequest;
//
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaPlayer;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.SurfaceTexture;

public class CamRecbyOpenGL extends Activity {

	private final static String TAG = "camera";
	private static final boolean VERBOSE = false; // lots of logging

	// where to put the output file (note: /sdcard requires
	// WRITE_EXTERNAL_STORAGE permission)
	private static final File OUTPUT_DIR = Environment.getExternalStorageDirectory();

	// parameters for the encoder
	private static final String MIME_TYPE = "video/avc"; // H.264 Advanced Video
															// Coding
	private static final int FRAME_RATE = 30; // 30fps
	private static final int IFRAME_INTERVAL = 1; // 5 seconds between I-frames
	private static final long DURATION_SEC = 8; // 8 seconds of video
	private static final int ENC_WIDTH = 1280 ;
	private static final int ENC_HEIGHT = 960;
	private static final int ENC_BITRATE = 4000000  ;
	private static final int ENC_NEW_BITRATE = -1 ;
 
	
	// Fragment shader that swaps color channels around.
	private static final String SWAPPED_FRAGMENT_SHADER = 
			"#extension GL_OES_EGL_image_external : require\n"
			+ "precision mediump float;\n" 
			+ "varying vec2 vTextureCoord;\n" 
			+ "uniform samplerExternalOES sTexture;\n"
			+ "void main() {\n" 
			+ "  gl_FragColor = texture2D(sTexture, vTextureCoord).gbra;\n" +  // 注意 颜色的排列改变了 
			"}\n";

	// encoder / muxer state
	private MediaCodec mEncoder;
	private CodecInputSurface mInputSurface;
	private MediaMuxer mMuxer;
	private int mTrackIndex;
	private boolean mMuxerStarted;

	// camera state
	private Camera mCamera;
	private SurfaceTextureManager mStManager;

	// allocate one of these up front so we don't need to do it every time
	private MediaCodec.BufferInfo mBufferInfo;

	private EGLContext mSharedEGLContext = null;
	private EGLSurface mSharedEGLSurfaceRd = null;
	private EGLSurface mSharedEGLSurfaceWr = null;
	private EGLDisplay mSharedEGLDisplay= null;
	
	private Handler mGLViewHandler = new GLViewHandler();
	
	public class GLViewHandler extends Handler
	{

		@Override
		public void handleMessage(Message msg) {
			
			switch(msg.what){
			case MessageType.MSG_RENDER_CREATED:
				Log.d(TAG, "Render.surfaceCreated !");
//				if( msg.obj != null ){
//					HashMap<Integer,Object> map = (HashMap<Integer,Object>)msg.obj;
//					mSharedEGLContext = (EGLContext) map.get(0);
//					mSharedEGLSurfaceRd = (EGLSurface) map.get(1);
//					mSharedEGLSurfaceWr = (EGLSurface) map.get(2);
//					mSharedEGLDisplay = (EGLDisplay) map.get(3);
//					Log.d(TAG, "shared " + "eglcontext = " + mSharedEGLContext 
//							+ " eglsurfaceRd = " + mSharedEGLSurfaceRd 
//							+ " eglsurfaceWr = " + mSharedEGLSurfaceWr 
//							+ " egldisplay = " + mSharedEGLDisplay );
//				}
				
				// 可以不在同一个线程中 创建EGLDislpay和EGLContext
				// 但是makeCurrent要在使用OpenGL的线程中, 使线程成为渲染线程 , 这样后面才能使用OpenGL API
				// prepareEncoder(ENC_WIDTH, ENC_HEIGHT, ENC_BITRATE); 
				
				CameraToMpegWrapper wrapper = new CameraToMpegWrapper();
				Thread th = new Thread(wrapper, "codec test");
				th.start();
				
				break;
			case MessageType.MSG_RENDER_CHANGE:
				Log.d(TAG, "Render.surfaceChanged !");
				break;
			default:
				break;
			}
			super.handleMessage(msg);
		}
		
	}
	   
	   
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_camera);
		
		
		//int[] textures = new int[]{-1,-1,-1};
       // GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
       // GLES20.glGenTextures(3, textures, 0);
       // Log.d(TAG, "textures = " + Arrays.toString( textures ) ); //在非OpenGL线程  textures = [-1, -1, -1] 
        
		CamGLSurfaceView gs = (CamGLSurfaceView)findViewById(R.id.CamSurfaceView);
		gs.setCallback(mGLViewHandler);

	}
	
	
	private class CameraToMpegWrapper implements Runnable {
	
 
        public CameraToMpegWrapper(){
        }

        @Override
        public void run() {
	            try {
	            	encodeCamera2mp4_thread();
	            	// 在这里跑一段时间   使用Camera MediaCodec Surface进行录像  
	            	// 最后线程退出
	            } catch (Throwable th) {
	            	Log.e(TAG, "encodeCamera2mp4_thread exception " + th.getMessage() );
	            	th.printStackTrace();
	            }
        }
	}
	  

	/**
	 * Attempts to find a preview size that matches the provided width and
	 * height (which specify the dimensions of the encoded video). If it fails
	 * to find a match it just uses the default preview size.
	 * <p>
	 * TODO: should do a best-fit match.
	 */
	private static void choosePreviewSize(Camera.Parameters parms, int width, int height) {
		// We should make sure that the requested MPEG size is less than the
		// preferred
		// size, and has the same aspect ratio.
		Camera.Size ppsfv = parms.getPreferredPreviewSizeForVideo();
		if (VERBOSE && ppsfv != null) {
			Log.d(TAG, "Camera preferred preview size for video is " + ppsfv.width + "x" + ppsfv.height);
		}

		for (Camera.Size size : parms.getSupportedPreviewSizes()) {
			if (size.width == width && size.height == height) {
				parms.setPreviewSize(width, height);
				return;
			}
		}

		Log.w(TAG, "Unable to set preview size to " + width + "x" + height);
		if (ppsfv != null) {
			parms.setPreviewSize(ppsfv.width, ppsfv.height);
		}
	}

	/**
	 * Stops camera preview, and releases the camera to the system.
	 */
	private void releaseCamera() {
		if (VERBOSE)
			Log.d(TAG, "releasing camera");
		if (mCamera != null) {
			mCamera.stopPreview();
			mCamera.release();
			mCamera = null;
		}
	}

	/**
	 * Configures SurfaceTexture for camera preview. Initializes mStManager, and
	 * sets the associated SurfaceTexture as the Camera's "preview texture".
	 * <p>
	 * Configure the EGL surface that will be used for output before calling
	 * here.
	 */
	private void prepareSurfaceTexture() {
		 
		mStManager = new SurfaceTextureManager();
		 
		SurfaceTexture st = mStManager.getSurfaceTexture();
		 
		try {
			mCamera.setPreviewTexture(st);
		} catch (IOException ioe) {
			throw new RuntimeException("setPreviewTexture failed", ioe);
		}
		// Surface 由以下两个得到
		//   SurfaceView.getHolder().getSurface()		<---- SurfaceView		可以跟EGLSurface关联  用于绘图
		//
		//	 new Surface(SurfaceTexture)				<---- SurfaceTexture	可以用于Camera MediaPlayer MediaCodec的输出,然后做纹理处理
		//		data
		//			-->ANativeWindow-->
		//			-->Surface
		//			-->SurfaceTexture.OnFrameAvailableListener --> 设置标记 让Renderer.onDrawFrame可以从SurfaceTexture获取数据 并画图
		//		
		//		GLSurfaceView.setRenderer(SGLSurfaceView.Renderer)
		//		setRenderMode(RENDERMODE_CONTINUOUSLY) 这样就会不定底调用Renderer.onDrawFrame
		//		在GLSurfaceView.Renderer.onDrawFrame回调中使用OpenGL就是把图画到了GLSurfaceView(内部把surfaceView和EGLSurface关联了 EGL.makeCurrent)
		
	}

	/**
	 * Releases the SurfaceTexture.
	 */
	private void releaseSurfaceTexture() {
		if (mStManager != null) {
			mStManager.release();
			mStManager = null;
		}
	}

	/**
	 * Configures encoder and muxer state, and prepares the input Surface.
	 * Initializes mEncoder, mMuxer, mInputSurface, mBufferInfo, mTrackIndex,
	 * and mMuxerStarted.
	 */
	private void prepareEncoder(int width, int height, int bitRate) {
		mBufferInfo = new MediaCodec.BufferInfo();

		// 配置一些编码参数 如果不支持的话 configure会抛出异常
		// 编码器输入颜色空间是 COLOR_FormatSurface 因为我们从Camera preview那边获取数据作为输入
		//  
		// 从MediaCodec获取的Surface给到CodecInputSurface用于EGL工作
		//
		// 如果需要两个EGL context, 一个显示 一个录像  (这个测试APK没有显示，只有录像)
		// 你可能需要延迟实例化 CodecInputSurface 直到 "display" EGL context 被创建
		// 然后修改 'recoder'EGL contect的 eglGetCurrentContext() 中 share_context参数为'display' context
	 
		// 获得一个Surface作为编码器输入  而不是用dequeueInputBuffer
		// surface 会给到Camera作为 preview
		
		MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, width, height);
		format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
		format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
		format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
		format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);
		Log.d(TAG, "here format: " + format);
		try {
			mEncoder = MediaCodec.createEncoderByType(MIME_TYPE);
		} catch (IOException e) {
			Log.e(TAG, "create MediaCodec Error ! ");
			Log.e(TAG, "create MediaCodec Error ! ");
			Log.e(TAG, "create MediaCodec Error ! ");
			e.printStackTrace();
			return;
		}
		Log.d(TAG,"createEncoderByType");
		mEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
		Surface surface = mEncoder.createInputSurface();
		Log.d(TAG,"createInputSurface 1 ");
		
		try{ // 重复获取Surface会有错误  测试在Mali 和 晓龙820平台
			Surface temp = mEncoder.createInputSurface();
			Log.d(TAG, "duplicated called surface = " + surface + " temp = " + temp );
		}catch ( android.media.MediaCodec.CodecException ex ){
			Log.d(TAG, "duplicated called CodecException " );
		}

		mInputSurface = new CodecInputSurface(surface);
		mEncoder.start();

	
		// 创建一个MediaMuxer 但是不能在这里添加video track和 start()这个Muxer
		// 只能在Encoder编码一定数据后 INFO_OUTPUT_FORMAT_CHANGED 的时候再添加video track和start()
		// 我们只是把 raw H264 ES(elementary stream )保存到mp4 没有声音
		String outputPath = new File(OUTPUT_DIR, "CamRecbyOpenGL_" + width + "x" + height + ".mp4").toString();
		Log.i(TAG, "Output file is " + outputPath);
		try {
			mMuxer = new MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
		} catch (IOException ioe) {
			throw new RuntimeException("MediaMuxer creation failed", ioe);
		}
		mTrackIndex = -1;
		mMuxerStarted = false;
		
	}

	/**
	 * Releases encoder resources.
	 */
	private void releaseEncoder() {
		if (VERBOSE)
			Log.d(TAG, "releasing encoder objects");
		if (mEncoder != null) {
			mEncoder.stop();
			mEncoder.release();
			mEncoder = null;
		}
		if (mInputSurface != null) {
			mInputSurface.release();
			mInputSurface = null;
		}
		if (mMuxer != null) {
			mMuxer.stop();
			mMuxer.release();
			mMuxer = null;
		}
	}

	/**
	 * Extracts all pending data from the encoder and forwards it to the muxer.
	 * <p>
	 * If endOfStream is not set, this returns when there is no more data to
	 * drain. If it is set, we send EOS to the encoder, and then iterate until
	 * we see EOS on the output. Calling this with endOfStream set should be
	 * done once, right before stopping the muxer.
	 * <p>
	 * We're just using the muxer to get a .mp4 file (instead of a raw H.264
	 * stream). We're not recording audio.
	 */
	private void drainEncoder(boolean endOfStream) {
		final int TIMEOUT_USEC = 10000;

		if (endOfStream) {
				Log.d(TAG, "sending EOS to encoder");
			mEncoder.signalEndOfInputStream(); // 这样产生一个结束流输出
		}

		ByteBuffer[] encoderOutputBuffers = mEncoder.getOutputBuffers();
		while (true) {
			int encoderStatus = mEncoder.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);
			if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
				// no output available yet
				if (!endOfStream) {
					break; // out of while
				} else {
					if (VERBOSE)
						Log.d(TAG, "no output available, spinning to await EOS");
				}
			} else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
				// not expected for an encoder
				encoderOutputBuffers = mEncoder.getOutputBuffers();
			} else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
				// should happen before receiving buffers, and should only
				// happen once
				if (mMuxerStarted) {
					throw new RuntimeException("format changed twice");
				}
				MediaFormat newFormat = mEncoder.getOutputFormat();
				Log.d(TAG, "encoder output format changed: " + newFormat);

				// now that we have the Magic Goodies, start the muxer
				mTrackIndex = mMuxer.addTrack(newFormat);
				mMuxer.start();
				mMuxerStarted = true;
			} else if (encoderStatus < 0) {
				Log.w(TAG, "unexpected result from encoder.dequeueOutputBuffer: " + encoderStatus);
				// let's ignore it
			} else {
				ByteBuffer encodedData = encoderOutputBuffers[encoderStatus];
				if (encodedData == null) {
					throw new RuntimeException("encoderOutputBuffer " + encoderStatus + " was null");
				}

				//测试 修改编码码率之后 pps sps是否提供新的: 目前MTK上使用没有问题 但是高通上使用有问题 sps和pps都没有提供新的
				//Log.d(TAG, " encodedData type   " + Integer.toHexString( (int)encodedData.get(4) ) );
				
				if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
					// The codec config data was pulled out and fed to the muxer
					// when we got
					// the INFO_OUTPUT_FORMAT_CHANGED status. Ignore it.
					//if (VERBOSE)
					Log.d(TAG, "ignoring BUFFER_FLAG_CODEC_CONFIG ");
					mBufferInfo.size = 0;
				}

				if (mBufferInfo.size != 0) {
					if (!mMuxerStarted) {
						throw new RuntimeException("muxer hasn't started");
					}

					// 根据BufferInfo的信息 更新ByteBuffer的位置
					// ?? 可以不用
					encodedData.position(mBufferInfo.offset);
					encodedData.limit(mBufferInfo.offset + mBufferInfo.size);

					// MediaMuxter写数据到文件中
					// writeSampleData(int trackIndex, ByteBuffer byteBuf, BufferInfo bufferInfo)
					mMuxer.writeSampleData(mTrackIndex, encodedData, mBufferInfo);
					if (VERBOSE)
						Log.d(TAG, "sent " + mBufferInfo.size + " bytes to muxer");
				}

				mEncoder.releaseOutputBuffer(encoderStatus, false);

				if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
					if (!endOfStream) {
						Log.w(TAG, "reached end of stream unexpectedly");
					} else {
						if (VERBOSE)
							Log.d(TAG, "end of stream reached");
					}
					break; // out of while
				}
			}
		}
	}

	/**
	 * Tests encoding of AVC video from Camera input. The output is saved as an
	 * MP4 file.
	 */
	private void encodeCamera2mp4_thread() {
		// arbitrary but popular values

		Log.d(TAG, MIME_TYPE + " output " + ENC_WIDTH 
				+ "x" + ENC_HEIGHT + " @" + ENC_BITRATE);

		try{ // 测试 BitmapFactory 0,0 是图片的左上角
			
		
		Bitmap bmp =BitmapFactory.decodeFile("/mnt/sdcard/test_memory.bmp");
		int color = bmp.getPixel(0, 0);
		Log.d("TOM" , "(0,0) " + Integer.toHexString(color )
				+ " (0,640) " + Integer.toHexString( bmp.getPixel(640 - 1  ,0 ) ) );
		
		int[] pixels = new int[640*480];
		Log.d("TOM" , "new done " );
		bmp.getPixels(pixels, 0, 640, 0, 0, 640, 480);
		Log.d("TOM" , "getPixels done " );
		Log.d("TOM", "first memory " +  Integer.toHexString(pixels[0]) + " (640,0) = " +  Integer.toHexString(pixels[640 - 1 ]));
		
		}catch(Exception ex){
			Log.e(TAG,"Test Bitmap read pixel Fail ");
			ex.printStackTrace();
			//return ;
		}
		try {
			
			prepareCamera(ENC_WIDTH, ENC_HEIGHT);
			
			prepareEncoder(ENC_WIDTH, ENC_HEIGHT, ENC_BITRATE); 
			
			mInputSurface.makeCurrent(); 
			// 必须在prepareSurfaceTexture之前 进行 EGL14.eglMakeCurrent
			// 否则 后面 进行GLES操作 比如  GLES20.glGetShaderiv  就会出现错误
			
			// 从SurfaceManager获得一个SurfaceTexture给到Camera preview
			prepareSurfaceTexture();

			mCamera.startPreview();

			long startWhen = System.nanoTime(); // 记录开始的时刻 用来计算相对时间
			long desiredEnd = startWhen + DURATION_SEC * 1000000000L;//只录8秒
			SurfaceTexture st = mStManager.getSurfaceTexture();
			int frameCount = 0;

			while (System.nanoTime() < desiredEnd) {
				// Feed any pending encoder output into the muxer.
				drainEncoder(false); // 从encoder获取数据 并写到muxer中

				// 每15帧 改变颜色
				// Besides demonstrating the use of fragment shaders for video editing 
				// 结果:如果摄像头capture是15帧率 那么颜色会每秒改变
				if ((frameCount % 15) == 0) {
					String fragmentShader = null;
					if ((frameCount & 0x01) != 0) {
						fragmentShader = SWAPPED_FRAGMENT_SHADER;
					}
					mStManager.changeFragmentShader(fragmentShader);
					Log.d(TAG, "Fragment Change " );
				}
				
	 
				 
				if( ( frameCount ==  15*4  && ENC_NEW_BITRATE != -1 ) ){
					Bundle para = new Bundle();
					para.putInt (MediaCodec.PARAMETER_KEY_VIDEO_BITRATE , ENC_NEW_BITRATE);
					mEncoder.setParameters( para);
					Log.d(TAG, "Codec Bitrate Change From " + ENC_BITRATE + " to " + ENC_NEW_BITRATE );
				}
				
				frameCount++;

				/**
				 *  已经有新的帧到来 使用SurfaceTexture.updateTexImage将其加载到 SurfaceTexture
				 *  然后调用drawFrame将其render(使用gl操作 到这个纹理上 surfaceTexture)
				 *  
				 *  如果有 GLSurfaceView 作为显示， 我们可以 切换 EGL contexts 和 之后 调用 drawImage() 
				 *  使其 render it 在屏幕上
				 *  texture是可以被EGL contexts共享 通过在 eglCreateContext() 传递参数 share_context
				 */
				mStManager.awaitNewImage(); // mSurfaceTexture.updateTexImage();
				mStManager.drawImage(); 	// mTextureRender.drawFrame(mSurfaceTexture);

				/**
				 * SurfaceTexture.getTimestamp()
				 * 提取这个texture image的时间戳(在最近一次调用updateTexImage时被设置) 绝对时间
				 */
				Log.d(TAG, "present: " + ((st.getTimestamp() - startWhen) / 1000000.0) + "ms");
				mInputSurface.setPresentationTime(st.getTimestamp());

				/**
				 *  提交给编码器. 如果输入缓存满的话  swapBuffers会阻塞,直到我们从输出中获取数据
				 *  由于我们在一个线程中推送数据和获取数据,
				 *  为了避免在这里堵塞,我们在drainEncoder(false)的时候,把所有输出output都获取写到文件
				 *  然后再 awaitNewImage --> drawImage --> swapBuffers	
				 */
				mInputSurface.swapBuffers();// 这样编码器就有输入数据了
				
				
//				if(mSharedEGLContext != null){
//					EGL14.eglMakeCurrent(mSharedEGLDisplay, mSharedEGLSurfaceRd, mSharedEGLSurfaceWr, mSharedEGLContext);
//					mStManager.drawImage(); 
//					EGL14.eglSwapBuffers(mSharedEGLDisplay, mSharedEGLSurfaceWr);
//					
//					mInputSurface.makeCurrent(); 
//				}else{
//					
//				}
				
			}

			// send end-of-stream to encoder, and drain remaining output
			drainEncoder(true);
		} finally {
			// release everything we grabbed
			releaseCamera();
			releaseEncoder();
			releaseSurfaceTexture();
		}
	}

	/**
	 * Configures Camera for video capture. Sets mCamera.
	 * <p>
	 * Opens a Camera and sets parameters. Does not start preview.
	 */
	private void prepareCamera(int encWidth, int encHeight) {
		if (mCamera != null) {
			throw new RuntimeException("camera already initialized");
		}

		int cam_id = 0;
		
		Camera.CameraInfo info = new Camera.CameraInfo();

		// Try to find a front-facing camera (e.g. for videoconferencing).
		int numCameras = Camera.getNumberOfCameras();
		for (int i = 0; i < numCameras; i++) {
			Camera.getCameraInfo(i, info);
			if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) { // CAMERA_FACING_FRONT  CAMERA_FACING_BACK
				mCamera = Camera.open(i); 
				cam_id = i ;
				break;
			}
		}
		
		if (mCamera == null) {
			Log.d(TAG, "No front-facing camera found; opening default");
			mCamera = Camera.open(); // opens first back-facing camera
		}
		if (mCamera == null) {
			throw new RuntimeException("Unable to open camera");
		}

		Camera.Parameters parms = mCamera.getParameters();

		choosePreviewSize(parms, encWidth, encHeight);
		
		// set framerate to 15fps but record 30fps
		parms.setPreviewFrameRate(15);
		//parms.setPreviewSize(1280, 960);
		
		mCamera.setParameters(parms);

		parms = mCamera.getParameters();
		Camera.Size size = parms.getPreviewSize();
		Log.d(TAG, "Camera preview size is " + size.width + "x" + size.height);
		Log.d(TAG, "Camera PreviewFormat " + parms.getPreviewFormat() ); 
		// Camera PreviewFormat 17   = ImageFormat.NV21
		
		try{
			//mCamera.setDisplayOrientation(0);	
			setCameraDisplayOrientation(this ,cam_id,mCamera);
		}catch(Exception ex ){
			Log.e("TOM" , "setDisplayOrientation fail" + ex.getMessage());
			ex.printStackTrace();
			return ;
		}
		
		
		
		
		/*
		 * 前  orientation 270
		 * 后  orientation 90
		 */
		android.hardware.Camera.CameraInfo cinfo =
		            new android.hardware.Camera.CameraInfo();
		android.hardware.Camera.getCameraInfo(cam_id , cinfo);
		Log.d(TAG, "Camera orientation " + info.orientation );
		 
	}
	
	
	 public static void setCameraDisplayOrientation(Activity activity,
	         int cameraId, android.hardware.Camera camera) {
	     android.hardware.Camera.CameraInfo info =
	             new android.hardware.Camera.CameraInfo();
	     android.hardware.Camera.getCameraInfo(cameraId, info);
	     int rotation = activity.getWindowManager().getDefaultDisplay()
	             .getRotation();
	     int degrees = 0;
	     switch (rotation) {
	         case Surface.ROTATION_0: degrees = 0; break;
	         case Surface.ROTATION_90: degrees = 90; break;
	         case Surface.ROTATION_180: degrees = 180; break;
	         case Surface.ROTATION_270: degrees = 270; break;
	     }
	     Log.d("TOM","windows rotation " + rotation + " degrees = " + degrees );

	     int result;
	     if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
	         result = (info.orientation + degrees) % 360;
	         result = (360 - result) % 360;  // compensate the mirror
	     } else {  // back-facing
	         result = (info.orientation - degrees + 360) % 360;
	     }
	     Log.d("TOM","camera facing " + info.facing  + " camera orientation = " + info.orientation
	    		 + " setDisplayOrientation " + result );
	     
	     camera.setDisplayOrientation(result);
	 }


	/**
	 * Holds state associated with a Surface used for MediaCodec encoder input.
	 * <p>
	 * The constructor takes a Surface obtained from
	 * MediaCodec.createInputSurface(), and uses that to create an EGL window
	 * surface. Calls to eglSwapBuffers() cause a frame of data to be sent to
	 * the video encoder.
	 * <p>
	 * This object owns the Surface -- releasing this will release the Surface
	 * too.
	 */
	private class CodecInputSurface {
		private static final int EGL_RECORDABLE_ANDROID = 0x3142;

		private EGLDisplay mEGLDisplay = EGL14.EGL_NO_DISPLAY;
		private EGLContext mEGLContext = EGL14.EGL_NO_CONTEXT;
		private EGLSurface mEGLSurface = EGL14.EGL_NO_SURFACE;

		private Surface mSurface;

		/**
		 * Creates a CodecInputSurface from a Surface.
		 */
		public CodecInputSurface(Surface surface) {
			if (surface == null) {
				throw new NullPointerException();
			}
			mSurface = surface; // 从MediaCodec获取的一个Surface 作为MediaCodec的输入

			eglSetup(); // EGLDisplay 与 给定的 Surface (从MediaCodec获取的)关联 
		}

		/**
		 * Prepares EGL. We want a GLES 2.0 context and a surface that supports
		 * recording.
		 */
		private void eglSetup() {
			
			// display参数是native系统的窗口显示ID值 一般为 EGL_DEFAULT_DISPLAY 该参数实际的意义是平台实现相关的
			// 
			mEGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
			if (mEGLDisplay == EGL14.EGL_NO_DISPLAY) {
				throw new RuntimeException("unable to get EGL14 display");
			}
			int[] version = new int[2];
			if (!EGL14.eglInitialize(mEGLDisplay, version, 0, version, 1)) {
				throw new RuntimeException("unable to initialize EGL14");
			}
			Log.d(TAG, "EGL version " + version[0] + " " + version[1] ); // 1 0  ???
			// version中存放EGL 版本号，int[0]为主版本号，int[1]为子版本号
            // 在Android 4.2/API 17 以前的版本没有EGL14，只有EGL10和EGL11，而这两个版本是不支持OpengGL ES 2.x的

            // Configure EGL for recording and OpenGL ES 2.0.  
            // 构造需要的特性列表  配置EGL可以用于录像 和 OpenGL ES渲染绘图
			
			int[] attribList = { 
					EGL14.EGL_RED_SIZE, 			8, // 指定RGB中的R大小（bits）
					EGL14.EGL_GREEN_SIZE, 			8, 
					EGL14.EGL_BLUE_SIZE, 			8,
					EGL14.EGL_ALPHA_SIZE, 			8, // 指定Alpha大小，以上四项实际上指定了像素格式
					EGL14.EGL_RENDERABLE_TYPE, 		EGL14.EGL_OPENGL_ES2_BIT,
														// 指定渲染api类别 OPENGL_ES2	
														// EGL14支持openGL ES 2
					EGL_RECORDABLE_ANDROID, 1,			// recordable on android 可以录像 
					EGL14.EGL_NONE };					// 总是以EGL10.EGL_NONE结尾
			
			/* 获取所有可用的configs，每个config都是EGL系统  根据特定规则选择出来的最符合特性列表要求的  一组特性
			 *  这里只选择第一   EGLConfig数组长度是1 
             	boolean android.opengl.EGL14.eglChooseConfig(
             	EGLDisplay dpy, 
             	int[] attrib_list, int attrib_listOffset,  					//	配置列表  第一个配置在配置列表的偏移
             	EGLConfig[] configs, int configsOffset, int config_size,  	//	存放输出的configs  
             	int[] num_config, int num_configOffset)						//	满足attributes的config一共有多少个
        
            	EGL14.eglGetConfigAttrib(EGLDisplay EGLConfig  ) 		    //  获取配置指定属性
			 * 
			 */
			EGLConfig[] configs = new EGLConfig[1];
			int[] numConfigs = new int[1];
			EGL14.eglChooseConfig(mEGLDisplay, attribList, 0, configs, 0, configs.length, numConfigs, 0);
			checkEglError("eglCreateContext RGB888+recordable ES2");

			
            /**
             * Configure context for OpenGL ES 2.0.
             * 根据EGLConfig,创建 EGL显示上下文 (EGL rendering context)
             * 
             * EGLContext eglCreateContext(EGLDisplay display, 
             * 									EGLConfig config, 
             * 									EGLContext share_context 是否有context共享？共享的contxt之间亦共享所有数据 EGL_NO_CONTEXT代表不共享
             * 									int[] attrib_list);
             * 
             * 目前可用属性只有EGL_CONTEXT_CLIENT_VERSION, 1代表OpenGL ES 1.x, 2代表2.0
             * 
             * 如果share_context不是EGL_NO_CONTEXT 那么context中所有可以分享的数据 
             * 任意数量的正在显示(rendering)的contexts可以共享数据
             * 但是共享数据的所有context 必须在一个地址空间
             * 如果在一个进程中，两个正在显示(rendering)的contexts是共享地址空间 
             * 
             * OpenGL ES 渲染命令假定为异步的 如果调用了任何绘制运算 不保证在调用返回时渲染已经完成
             * 多线程环境中经常需要同步 CPU-GPU 或 GPU-GPU 操作 ,  OpenGL ES 借助 glFinish() 和 glFlush() 命令提供显式同步机制
             * 使用时应当慎重，否则会损害性能。一些其他函数以隐式方式强制同步
             * 
             * 两个线程共享纹理对象
             * 使用多线程最好的使用方式是一个线程用于纹理加载，另外一个线程用于绘图，不建议两个线程同时进行绘图操作
             */
			int[] attrib_list = { 
						EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, 
						EGL14.EGL_NONE };
			if( mSharedEGLContext != null){
				Log.d(TAG, "create shared context ");
				mEGLContext = EGL14.eglCreateContext(mEGLDisplay, 
						configs[0], 
						mSharedEGLContext , 
						attrib_list,
						0);
			}else{
				Log.d(TAG, "do NOT create shared context ");
				mEGLContext = EGL14.eglCreateContext(mEGLDisplay, 
						configs[0], 
						EGL14.EGL_NO_CONTEXT, 
						attrib_list,
						0);
			}
			
			checkEglError("eglCreateContext");

            /* Create a window surface, and attach it to the Surface we received.
             * 在一个显示屏EGLDisplay, 创建一个window surface(EGLSurface) 并且关联到给定的Surface 
             * 获取显存 
            	EGLSurface android.opengl.EGL14.eglCreateWindowSurface(
            			EGLDisplay dpy, EGLConfig config, 
            			Object win, 
            			int[] attrib_list, int offset)
            			
            	surfaceAttribs	用于描述WindowSurface类型 
            		EGL_RENDER_BUFFER 	用于描述渲染buffer 所有的绘制在此buffer中进行 
            		EGL_SINGLE_BUFFER 	单缓冲 绘制的同时用户即可见
					EGL_BACK_BUFFER		后者属于双缓冲，前端缓冲用于显示
										OpenGL ES 在后端缓冲中进行绘制，绘制完毕后使用eglSwapBuffers()交换前后缓冲，用户即看到在后缓冲中的内容
										
             * */
			int[] surfaceAttribs = { EGL14.EGL_NONE };  
			mEGLSurface = EGL14.eglCreateWindowSurface(mEGLDisplay, 
														configs[0], 
														mSurface, 
														surfaceAttribs, 
														0);
			checkEglError("eglCreateWindowSurface");
		}

		/**
		 * Discards all resources held by this class, notably the EGL context.
		 * Also releases the Surface that was passed to our constructor.
		 */
		public void release() {
			if (mEGLDisplay != EGL14.EGL_NO_DISPLAY) {
				EGL14.eglMakeCurrent(mEGLDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
				EGL14.eglDestroySurface(mEGLDisplay, mEGLSurface);
				EGL14.eglDestroyContext(mEGLDisplay, mEGLContext);
				EGL14.eglReleaseThread();
				EGL14.eglTerminate(mEGLDisplay);
			}
			mSurface.release();

			mEGLDisplay = EGL14.EGL_NO_DISPLAY;
			mEGLContext = EGL14.EGL_NO_CONTEXT;
			mEGLSurface = EGL14.EGL_NO_SURFACE;

			mSurface = null;
		}

	 
		public void makeCurrent() {
			/**
			 * 使一个线程成为渲染线程
			 * 
			 * boolean android.opengl.EGL14.eglMakeCurrent(EGLDisplay dpy, EGLSurface draw, EGLSurface read, EGLContext ctx)
			 * 
			 * display : EGL显示连接 (EGL display connection)
			 * draw:	 EGL写surface
			 * read:	 EGL读surface
			 * context:  Surface将要附在的EGL上下文(EGL rendering context)  
			 * 
			 * 绑定一个 EGLcontext 到  当前的线程 (current rendering thread) 和 draw/read Surface
			 * 如果一个线程已经有一个EGLContext作为current context, 这个EGLContext将会flushed 而且被标记为不是current
			 * 第一次EGLContext被标记为current(作为某个线程的current context),viewport and scissor dimensions 被设置为draw surface的大小
			 * 后面EGLContext再被设置为current viewport and scissor将不会改变
			 *  
			 * 如果要释放当前线程的context 这样调用 eglMakeCurrent( dpy, EGL_NO_SURFACE , EGL_NO_SURFACE , EGL_NO_CONTEXT ) 
			 * 通过eglGetCurrentContext, eglGetCurrentDisplay, and eglGetCurrentSurface获取 当前线程的context和 display surface 
			 * 
			 * 
			 * OpenGL ES定义了客户端和服务端状态,所以OpenGL ES的contex包含了客户和服务端两个状态
			 * 设置为当前的渲染环境 OpengGL的客户端, API采用了隐含的context作为粉刷入口,而不是在绘图函数传入Context参数
			 * 因此EGL提供一个函数makeCurrent使某个Context变成当前使用状态
			 * 1. 每个线程只能有一个渲染上下文处于活动状态 
			 * 2. 一个给定上下文只能对一个线程处于活动状态
			 * 
			 */
			EGL14.eglMakeCurrent(mEGLDisplay, mEGLSurface, mEGLSurface, mEGLContext);
			checkEglError("eglMakeCurrent");
			
			/**
			 * EGLBoolean eglBindAPI( EGLenum api);
			 * 为调用这个的线程所在EGL设置当前渲染API
			 * 这个api是 EGL实现支持的其中一个 客户端渲染API 
			 * 影响其他EGL命令的行为 包含 eglCreateContext eglGetCurrentXXXX eglMakeCurrent eglWaitClient eglWaitNative
			 * 
			 * EGL version >= 1.2 才支持 EGL_OPENGL_ES_API  EGL_OPENVG_API  
			 * EGL version >= 1.4才支持 EGL_OPENGL_API 
			 * 
			 * 可选值:
			 * If api is EGL_OPENGL_API, 当前渲染API是 OpenGL API.
			 * If api is EGL_OPENGL_ES_API, 当前渲染API是  OpenGL ES API.
			 * If api is EGL_OPENVG_API, 当前渲染API是  OpenVG API.
			 * 
			 * 默认值:
			 * EGL_OPENGL_ES_API 
			 * */
		}


		public void setPresentationTime(long nsecs) {
			/**
			 * boolean eglPresentationTimeANDROID (EGLDisplay dpy, EGLSurface sur, long time)   
			 * 更新一个时间戳给到EGL. 单位是 nanoseconds
			 * 如果EGLSurafce对应的是MediaCodec编码器createInputSurfaced的Surface
			 * 那么这个将会是pts
			 */
			EGLExt.eglPresentationTimeANDROID(mEGLDisplay, mEGLSurface, nsecs);
			checkEglError("eglPresentationTimeANDROID");
		}
		
	 
		public boolean swapBuffers() {
			/**
			 * 	boolean eglSwapBuffers (EGLDisplay dpy,  EGLSurface surface)
			 * 	调用eglSwapBuffers会去触发queuebuffer，dequeuebuffer
			 *	queuebuffer将画好的buffer交给surfaceflinger处理
			 *	(如果Surface是来自SurfaceView;但如果surface来自 MediaCodec.codecInputSurface的话 就去到MediaCodec)
			 *	
			 *	dequeuebuffer新创建一个buffer用来画图
			 *  
			 */
			boolean result = EGL14.eglSwapBuffers(mEGLDisplay, mEGLSurface);
			checkEglError("eglSwapBuffers");
			return result;
		}



		/**
		 * Checks for EGL errors. Throws an exception if one is found.
		 */
		private void checkEglError(String msg) {
			int error;
			if ((error = EGL14.eglGetError()) != EGL14.EGL_SUCCESS) {
				throw new RuntimeException(msg + ": EGL error: 0x" + Integer.toHexString(error));
			}
		}
	}

	/**
	 * Manages a SurfaceTexture. Creates SurfaceTexture and TextureRender
	 * objects, and provides functions that wait for frames and render them to
	 * the current EGL surface.
	 * <p>
	 * The SurfaceTexture can be passed to Camera.setPreviewTexture() to receive
	 * camera output.
	 */
	private static class SurfaceTextureManager implements SurfaceTexture.OnFrameAvailableListener {
		private SurfaceTexture mSurfaceTexture;
		private STextureRender mTextureRender;

		private Object mFrameSyncObject = new Object(); // guards
														// mFrameAvailable
		private boolean mFrameAvailable;

		/**
		 * Creates instances of TextureRender and SurfaceTexture.
		 */
		public SurfaceTextureManager() {
			mTextureRender = new STextureRender();
			mTextureRender.createSurface();

			Log.d(TAG, "textureID=" + mTextureRender.getTextureId());
			mSurfaceTexture = new SurfaceTexture(mTextureRender.getTextureId());

			// ????????????
			// This doesn't work if this object is created on the thread that
			// CTS started for these test cases.
			//
			// The CTS-created thread has a Looper, and the SurfaceTexture
			// constructor will
			// create a Handler that uses it. The "frame available" message is
			// delivered
			// there, but since we're not a Looper-based thread we'll never see
			// it. For
			// this to do anything useful, OutputSurface must be created on a
			// thread without
			// a Looper, so that SurfaceTexture uses the main application Looper
			// instead.
			//

			// 如果 SurfaceTexture 有一个 a new image frame 就回调
			mSurfaceTexture.setOnFrameAvailableListener(this);
		}

		public void release() {
			// this causes a bunch of warnings that appear harmless but might
			// confuse someone:
			// W BufferQueue: [unnamed-3997-2] cancelBuffer: BufferQueue has
			// been abandoned!
			// mSurfaceTexture.release();

			mTextureRender = null;
			mSurfaceTexture = null;
		}

		/**
		 * Returns the SurfaceTexture.
		 */
		public SurfaceTexture getSurfaceTexture() {
			return mSurfaceTexture;
		}

		/**
		 * Replaces the fragment shader.
		 */
		public void changeFragmentShader(String fragmentShader) {
			mTextureRender.changeFragmentShader(fragmentShader);
		}

		/**
		 * Latches the next buffer into the texture. Must be called from the
		 * thread that created the OutputSurface object. ??????????
		 */
		public void awaitNewImage() {
			final int TIMEOUT_MS = 2500;

			synchronized (mFrameSyncObject) {
				while (!mFrameAvailable) {
					try {
						// Wait for onFrameAvailable() to signal us. Use a
						// timeout to avoid
						// stalling the test if it doesn't arrive.
						mFrameSyncObject.wait(TIMEOUT_MS);
						if (!mFrameAvailable) {
							// TODO: if "spurious wakeup", continue while loop
							throw new RuntimeException("Camera frame wait timed out");
						}
					} catch (InterruptedException ie) {
						// shouldn't happen
						throw new RuntimeException(ie);
					}
				}
				mFrameAvailable = false;
			}

			// Latch the data.
			// Update the texture image to the most recent frame from the image stream. 
			mTextureRender.checkGlError("before updateTexImage");
			mSurfaceTexture.updateTexImage();
		}

		/**
		 * Draws the data from SurfaceTexture onto the current EGL surface.
		 * 从SurfaceTextrue中的数据 经过Render处理 画到 EGL surface.
		 */
		public void drawImage() {
			mTextureRender.drawFrame(mSurfaceTexture);
		}

		@Override
		public void onFrameAvailable(SurfaceTexture st) {
			
        	/*
        	 * 	SurfaceTexture给到了Camera  Camera有预览数据的时候会回调这个
        	 * 	SurfaceTexture.OnFrameAvailableListener 
        	 */
			if (VERBOSE)
				Log.d(TAG, "new frame available");
			synchronized (mFrameSyncObject) {
				if (mFrameAvailable) {
					throw new RuntimeException("mFrameAvailable already set, frame could be dropped");
				}
				mFrameAvailable = true;
				mFrameSyncObject.notifyAll();
			}
		}
	}

	/**
	 * Code for rendering a texture onto a surface using OpenGL ES 2.0.
	 */
	private static class STextureRender {
		private static final int FLOAT_SIZE_BYTES = 4;
		private static final int TRIANGLE_VERTICES_DATA_STRIDE_BYTES = 5 * FLOAT_SIZE_BYTES;
		private static final int TRIANGLE_VERTICES_DATA_POS_OFFSET = 0;
		private static final int TRIANGLE_VERTICES_DATA_UV_OFFSET = 3;
//		private final float[] mTriangleVerticesData = {
//				// X, Y, Z, U, V
//				 -1.0f, -1.0f, 0,  		0.f, 0.f,  /*顶点坐标     纹理坐标*/
//				 1.0f, -1.0f, 0,   		1.f, 0.f, 
//				 -1.0f, 1.0f, 0,  		0.f, 1.f,
//				  1.0f, 1.0f, 0,      	1.f, 1.f, };

		// 如果是照片的话 要这样对应
//		private final float[] mTriangleVerticesData = {
//				// X, Y, Z, U, V
//				-1.0f, 	1.0f, 	0, 		0.f, 0.f,  /*顶点坐标     纹理坐标*/
//				1.0f,	1.0f, 	0,		1.f, 0.f, 
//				-1.0f, 	-1.0f, 	0,		0.f, 1.f,
//				1.0f, 	-1.0f, 	0,		1.f, 1.f, 
//				};
		
		// 如果是摄像头预览的话 , 要这样对应 (同样顶点坐标 纹理坐标Y轴 0-1调换)
		private final float[] mTriangleVerticesData = {
			// X, Y, Z, U, V
				-1.0f, -1.0f, 0,		0.f, 0.f,
				1.0f, -1.0f, 0, 		1.f, 0.f,
				-1.0f, 1.0f, 0, 		0.f, 1.f,
				1.0f, 1.0f, 0, 			1.f, 1.f,
			};
		 
		private FloatBuffer mTriangleVertices;

		private static final String VERTEX_SHADER = 
				"uniform mat4 uMVPMatrix;\n" + 
				"uniform mat4 uSTMatrix;\n"
				+ "attribute vec4 aPosition;\n" 
				+ "attribute vec4 aTextureCoord;\n" 
				+ "varying vec2 vTextureCoord;\n"
				+ "void main() {\n" 
				+ "    gl_Position = uMVPMatrix * aPosition;\n"
				//+ "    mat4 temp = uSTMatrix ;"
				//+ "    vTextureCoord = (aTextureCoord).xy;\n" 
				+ "    vTextureCoord = (uSTMatrix*aTextureCoord).xy;\n" 
				+ "}\n";

		private static final String FRAGMENT_SHADER = "#extension GL_OES_EGL_image_external : require\n"
				+ "precision mediump float;\n"  // highp here doesn't seem to matter
				+ "varying vec2 vTextureCoord;\n"
				+ "uniform samplerExternalOES sTexture;\n" 
				+ "void main() {\n"
				+ "    gl_FragColor = texture2D(sTexture, vTextureCoord);\n" 
				+ "}\n";

		private float[] mMVPMatrix = new float[16];
		private float[] mSTMatrix = new float[16];

		private int mProgram;
		private int mTextureID = -12345;
		private int muMVPMatrixHandle;
		private int muSTMatrixHandle;
		private int maPositionHandle;
		private int maTextureHandle;

		public STextureRender() {
			int size = mTriangleVerticesData.length * FLOAT_SIZE_BYTES ; //FLOAT_SIZE_BYTES=4 每个float用4个字节存储
			ByteBuffer data  = ByteBuffer.allocateDirect(size);
			data.order(ByteOrder.nativeOrder());
			mTriangleVertices = data.asFloatBuffer();
			mTriangleVertices.put(mTriangleVerticesData);
			mTriangleVertices.position(0);

			Matrix.setIdentityM(mSTMatrix, 0);
		}

		public int getTextureId() {
			return mTextureID;
		}

		int testOESDraw = 0 ;

		public void drawFrame(SurfaceTexture st) {
			checkGlError("onDrawFrame start");
			
			/**
			 * void	getTransformMatrix(float[] mtx)
			 * 获取对应纹理照片(texture image)的  4x4纹理坐标变换矩阵 
			 * 由最近一次调用 updateTexImage 来更新 
			 */
			 
			st.getTransformMatrix(mSTMatrix);
			Log.d(TAG, "> " + Arrays.toString(mSTMatrix) );

			// 忽略纹理转换坐标
			//Matrix.setIdentityM(mSTMatrix, 0);

			// (optional) clear to green so we can see if we're failing to set
			// pixels
			
			
			/* 	  
			 * 	OpenGL 和编程语言、平台无关的一套interface ，主要是为了rendering 2D 和 3D图形等 
						一般这套接口是用来和GPU进行交互的，使用GPU进行rendering 硬件加速
				OpenGL ES就是专为嵌入式设备设计的
				android提供了两种类型的实现：软件实现，硬件实现 
				
				a, 硬件实现，前面提到这组函数接口主要是为了和GPU这个硬件进行打交道的。所以各个硬件厂商会提供相关的实现，例如高通平台的adreno解决方案；

				b，软件实现，android也提供了一套OpenGL ES的软件实现，就是说不用GPU了，完全用软件实现画图的相关功能，也就是libagl
							代码 frameworks\native\opengl\libagl
							软件实现最终编译完保存在system\lib\egl\libGLES_android.so
							
				EGL是OpenGL ES和底层的native window system之间的接口
				
							OpenGL ES(Khronos rendering APIs) 或者  OpenVG 与底层 native platform window system之间的接口
				
				*/
			GLES20.glClearColor(0.0f, 1.0f, 0.0f, 1.0f);
			GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT | GLES20.GL_COLOR_BUFFER_BIT);

			GLES20.glUseProgram(mProgram);
			checkGlError("glUseProgram");

			GLES20.glActiveTexture(GLES20.GL_TEXTURE0);	// 把  纹理单元 0 设置为当前纹理单元 
			GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mTextureID); // 纹理目标 绑定到 当前纹理单元
										
			// 顶点坐标 
			mTriangleVertices.position(TRIANGLE_VERTICES_DATA_POS_OFFSET);
			GLES20.glVertexAttribPointer(maPositionHandle, 3, GLES20.GL_FLOAT, false,
					TRIANGLE_VERTICES_DATA_STRIDE_BYTES, mTriangleVertices);
			checkGlError("glVertexAttribPointer maPosition");
			GLES20.glEnableVertexAttribArray(maPositionHandle);
			checkGlError("glEnableVertexAttribArray maPositionHandle");

			// 纹理坐标   纹理坐标  和  顶点坐标都在 一个ByteBuffer中 
			mTriangleVertices.position(TRIANGLE_VERTICES_DATA_UV_OFFSET);
			GLES20.glVertexAttribPointer(maTextureHandle, 2, GLES20.GL_FLOAT, false,
					TRIANGLE_VERTICES_DATA_STRIDE_BYTES, mTriangleVertices);
			checkGlError("glVertexAttribPointer maTextureHandle");
			GLES20.glEnableVertexAttribArray(maTextureHandle);
			checkGlError("glEnableVertexAttribArray maTextureHandle");
			
			Matrix.setIdentityM(mMVPMatrix, 0);
			GLES20.glUniformMatrix4fv(muMVPMatrixHandle, 1, false, mMVPMatrix, 0);
			GLES20.glUniformMatrix4fv(muSTMatrixHandle, 1, false, mSTMatrix, 0);

			GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
			checkGlError("glDrawArrays");




			if( testOESDraw == 0 ){
				int[] texIDs = new int[1];
				GLES20.glGenTextures(1, texIDs, 0);
				GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES , texIDs[0]);

				// init texture parameters
				GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
				GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
				GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
				GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

				int [] fboIds = new int[1];
				GLES20.glGenFramebuffers(1, fboIds, 0);
				GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboIds[0]);
				GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES11Ext.GL_TEXTURE_EXTERNAL_OES , texIDs[0], 0);

				testOESDraw = fboIds[0] ;
				Log.d(TAG,"glDraw to OES Begin");
				GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
				try{
					checkGlError("glDrawArrayOES");

				}catch(Exception e ){
					Log.e(TAG, "Exception " + e.getMessage() );
				}
				GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,0);
				Log.d(TAG,"glDraw to OES End");

				/*
				*  ARM(MAli) mt6797 和 晓龙820
				*  渲染到OES会遇到
				*  GL ERROR ; 1286: Invalid framebuffer operation
				* */
			}






			// IMPORTANT: 在有些设备 如果你共享外部纹理(external texture)在两个Context中
			// 其中一个context可能看不到texture的更新 除非 un-bind和re-bind它
			// 如果你不共享EGLContext, 你不需要在这里bind绑定texture 0 
			GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);
		}

		/**
		 * Initializes GL state. Call this after the EGL surface has been
		 * created and made current.
		 */
		public void createSurface() {
			mProgram = createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
			if (mProgram == 0) {
				throw new RuntimeException("failed creating program");
			}
			maPositionHandle = GLES20.glGetAttribLocation(mProgram, "aPosition");
			checkLocation(maPositionHandle, "aPosition");
			maTextureHandle = GLES20.glGetAttribLocation(mProgram, "aTextureCoord");
			checkLocation(maTextureHandle, "aTextureCoord");

			muMVPMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix");
			checkLocation(muMVPMatrixHandle, "uMVPMatrix");
			muSTMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uSTMatrix");
			checkLocation(muSTMatrixHandle, "uSTMatrix");

			/**
			 * khronos 
			 * void glGenTextures (int n, int[] textures, int offset)
			 * 返回n个纹理名字
			 * 保存在textures中
			 * 不保证n个纹理名字是连续的整数集合 
			 * 保证n个纹理名字都没有在使用
			 * 
			 * 产生的纹理textures都没有维度
			 * 纹理 假定是  他们第一 绑定的纹理目标 (texture target) 的 维度
			 * 
			 * 返回的纹理名字 不会再被后续的glGenTextures返回 , 除非调用了glDeleteTextures
			 */
			int[] textures = new int[1];
			GLES20.glGenTextures(1, textures, 0);
			mTextureID = textures[0];
			
			/**
			 * 绑定一个已命名的纹理(纹理名字) 到一个 正在纹理 的目标 (texturing target)
			 * void glBindTexture(GLenum target, GLuint texture);
			 * 
			 * target:	纹理将要绑定的当前激活纹理单元的目标  可选值为  GL_TEXTURE_2D   GL_TEXTURE_CUBE_MAP
			 * texture: 一个纹理的名字
			 * 
			 * 创建或者使用一个已命名纹理 
			 * 把   纹理名字  绑定  当前激活的纹理单元的目标  
			 * 当一个纹理绑定到一个目标 这个目标之前的绑定会自动断开
			 * 
			 * 纹理名字是无符号整数 
			 * 0被保留代表 所有纹理目标的默认纹理 
			 * 纹理名字 和 对应纹理内容 是本地的   在当前的 GL rendering context的共享对象空间 
			 * 
			 * 当纹理第一次绑定，会假定这样的目标(target)
			 * 如果绑定到GL_TEXTURE_2D, 就变成 一个两维的纹理 (two-dimensional)
			 * 如果绑定到GL_TEXTURE_CUBE_MAP, 就变成 一个 立方体映射的纹理  (a cube-mapped texture)
			 * 
			 * 第一次绑定后 二维纹理的状态 立刻等于   默认GL_TEXTURE_2D在GL initialization阶段的状态   , 立体映射纹理也相识
			 * 
			 * 一个纹理绑定之后 在已绑定目标(target)上的GL操作(GL operations)影响绑定的纹理 
			 * 查询已绑定的目标 会返回 绑定纹理(bound texture) 的状态  
			 * 实际上 纹理目标 (texture targets) 变成了  当前绑定纹理 的 化身(别名)
			 * 
			 * 纹理名字0 代表 纹理目标 在初始 绑定的默认纹理
			 * 
			 * 由glBindTexture创建的纹理绑定 一直有效 
			 * 直到 
			 * 		下一个不同的纹理(different texture)绑定到同样目标 (the same target)
			 * 		已绑定的纹理调用  glDeleteTextures.
			 * 
			 * 一旦创建,一个命名纹理,可以在需要时 被重新绑定到 初始目标 (same original target)
			 * 通常,使用glBindTexture 来绑定  一个已命名的纹理(an existing named texture)到 一个纹理目标
			 * 		比 使用 glTexImage2D 重新加载 纹理图片  texture image 
			 * 
			 * GLES20.glGetError() 
			 * 返回  
			 * GL_INVALID_ENUM 		如果目标不是允许值
			 * GL_INVALID_OPERATION	纹理已经被创建 到一个目标 ,而且又不是现在传入的目标
			 * */
			GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mTextureID);
			checkGlError("glBindTexture mTextureID");

			/**
			 * http://docs.gl/es2/glTexParameter
			 * 
			 * glTexParameter 设置纹理参数 
			 * void glTexParameterf(	GLenum target, GLenum pname, GLfloat param);
			 * void glTexParameteri(	GLenum target, GLenum pname, GLint param);
			 * 
			 * target: 	活跃纹理单元的目标  取值   GL_TEXTURE_2D or GL_TEXTURE_CUBE_MAP
			 * pname:	单值的纹理参数名字 可选   GL_TEXTURE_MIN_FILTER, GL_TEXTURE_MAG_FILTER, GL_TEXTURE_WRAP_S, or GL_TEXTURE_WRAP_T.
			 * param:	pname的值
			 * 
			 * 纹理映射 是将一个图片(image)像玻璃纸 或者贴花纸那样 敷在一个 物体的表面 
			 * 图片(image)被创建 在一个(s,t)坐标系统的 纹理空间 
			 * 一个纹理 是 一个 两位或者立方体映射的照片 和 一系列参数(决定如何从图像中导出样本)
			 * 
			 * 
			 * GL_TEXTURE_MIN_FILTER
			 * 纹理缩小函数  当像素即将被纹理  映射到一个区域 大于 一个纹理元素(one texture element)
			 * 已定义六种缩小函数
			 * 其中两种使用 nearest : 最接近的一个或四个纹理元素 来计算一个纹理值(texture value)
			 * 其余四中使用mipmaps
			 * 
			 * 一个mipmap是一个有序数组 代表一个同样图片 在 逐渐降低的分辨率 
			 * 如果textrue是w x h的维数 那么有floor(log2(max(w,h)))+1 个mipmap级别
			 * 第一个mipmap级别是初始纹理(w x h)
			 * 子mipmap级别是 max(1,floor(w/2^i))×max(1,floor(h/2^i)) i是mipmap的级别 
			 * 最后的mipmap级别是  1×1 
			 * 
			 * 要定义mipmap的级别  可以在调用  glTexImage2D, glCompressedTexImage2D, or glCopyTexImage2D
			 * 来传递 level 参数  指示 mipmap的介数 
			 * 级别0  是初始纹理 
			 * 级别 floor(log2(max(w,h)))是最后一个级别 1 x 1
			 * 
			 * 		GL_NEAREST	返回 最接近被纹理像素中央(曼哈顿距离) 的 纹理元素 的值
			 * 		GL_LINEAR	返回 最接近被纹理像素中央(曼哈顿距离) 的4个 纹理元素  的 权重均值 
			 * 		GL_NEAREST_MIPMAP_NEAREST
			 * 		GL_LINEAR_MIPMAP_NEAREST
			 * 		GL_NEAREST_MIPMAP_LINEAR
			 * 		GL_LINEAR_MIPMAP_LINEAR
			 * 
			 * 因为很多纹理元素 通过缩小程序采样, 少量的变形(fewer aliasing artifacts)很显然
			 * 然而 GL_NEAREST and GL_LINEAR 缩小程序  比其他4种要快 他们只采样4个纹理元素(texture elements) 
			 * 来决定  即将被render的像素 的纹理值 ，而且能够产生 波纹图形和 凹凸不平的过渡
			 * 
			 * 默认 GL_TEXTURE_MIN_FILTER是 GL_NEAREST_MIPMAP_LINEAR.
			 * 
			 * GL_TEXTURE_MAG_FILTER
			 *		either GL_NEAREST or GL_LINEAR
			 * 如果一个即将被纹理的像素pixel  映射到 少于或这等于一个纹理元素(texture element)的一个区域 
			 * GL_NEAREST处理的更快  但是产生边角的纹理图片
			 *
			 * 默认GL_TEXTURE_MAG_FILTER是 GL_LINEAR.
			 * 
			 * GL_TEXTURE_WRAP_S
			 * 	可选值: GL_CLAMP_TO_EDGE, GL_MIRRORED_REPEAT, or GL_REPEAT
			 *	默认值: GL_REPEAT
			 *	设置纹理坐标s的包装参数(wrap parameter)是 可选值之一
			 *  GL_CLAMP_TO_EDGE 导致 s限制在 [1/2N,1−1/2N]  N是纹理的大小在限制的方向
			 * 
			 * GL_TEXTURE_WRAP_T
			 * 	可选值: GL_CLAMP_TO_EDGE, GL_MIRRORED_REPEAT, or GL_REPEAT
			 * 	默认值: GL_REPEAT
			 */
			GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
			GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
			GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S,
					GLES20.GL_CLAMP_TO_EDGE);
			GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T,
					GLES20.GL_CLAMP_TO_EDGE);
			checkGlError("glTexParameter");
		}

		/**
		 * Replaces the fragment shader. Pass in null to reset to default.
		 */
		public void changeFragmentShader(String fragmentShader) {
			if (fragmentShader == null) {
				fragmentShader = FRAGMENT_SHADER;
			}
			GLES20.glDeleteProgram(mProgram);
			mProgram = createProgram(VERTEX_SHADER, fragmentShader);
			if (mProgram == 0) {
				throw new RuntimeException("failed creating program");
			}
		}

		private int loadShader(int shaderType, String source) {
			int shader = GLES20.glCreateShader(shaderType);
			checkGlError("glCreateShader type=" + shaderType);
			GLES20.glShaderSource(shader, source);
			GLES20.glCompileShader(shader);
			int[] compiled = new int[1];
			// 如果没有进行 EGL14.eglMakeCurrent 这里就出错 即compiled[0] == 0
			GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
			if (compiled[0] == 0) {
				Log.e(TAG, "Could not compile shader " + shaderType + ":");
				Log.e(TAG, " " + GLES20.glGetShaderInfoLog(shader));
				GLES20.glDeleteShader(shader);
				shader = 0;
			}
			return shader;
		}

		private int createProgram(String vertexSource, String fragmentSource) {
			int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource);
			if (vertexShader == 0) {
				return 0;
			}
			int pixelShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
			if (pixelShader == 0) {
				return 0;
			}

			int program = GLES20.glCreateProgram();
			if (program == 0) {
				Log.e(TAG, "Could not create program");
			}
			GLES20.glAttachShader(program, vertexShader);
			checkGlError("glAttachShader");
			GLES20.glAttachShader(program, pixelShader);
			checkGlError("glAttachShader");
			GLES20.glLinkProgram(program);
			int[] linkStatus = new int[1];
			GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
			if (linkStatus[0] != GLES20.GL_TRUE) {
				Log.e(TAG, "Could not link program: ");
				Log.e(TAG, GLES20.glGetProgramInfoLog(program));
				GLES20.glDeleteProgram(program);
				program = 0;
			}
			return program;
		}

		public void checkGlError(String op) {
			int error;
			while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
				Log.e(TAG, op + ": glError " + error);
				throw new RuntimeException(op + ": glError " + error);
			}
		}

		public static void checkLocation(int location, String label) {
			if (location < 0) {
				Log.e(TAG, "Unable to locate '" + label + "' in program");
				throw new RuntimeException("Unable to locate '" + label + "' in program");
			}
		}
	}

}
