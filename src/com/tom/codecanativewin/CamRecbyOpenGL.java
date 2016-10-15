package com.tom.codecanativewin;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import android.app.Activity;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Surface;

import android.view.SurfaceView;
// import android.hardware.camera2.CameraDevice;
// import android.hardware.camera2.CameraManager;
// import android.hardware.camera2.CaptureResult;
// import android.hardware.camera2.CaptureRequest;
//
import android.hardware.Camera;
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
	private static final int IFRAME_INTERVAL = 5; // 5 seconds between I-frames
	private static final long DURATION_SEC = 8; // 8 seconds of video

	// Fragment shader that swaps color channels around.
	private static final String SWAPPED_FRAGMENT_SHADER = "#extension GL_OES_EGL_image_external : require\n"
			+ "precision mediump float;\n" + "varying vec2 vTextureCoord;\n" + "uniform samplerExternalOES sTexture;\n"
			+ "void main() {\n" + "  gl_FragColor = texture2D(sTexture, vTextureCoord).gbra;\n" + "}\n";

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

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_camera);
		
		CameraToMpegWrapper wrapper = new CameraToMpegWrapper();
		Thread th = new Thread(wrapper, "codec test");
		th.start();
		//th.join();
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
		//   SurfaceView.getHolder().getSurface()		<---- SurfaceView 
		//
		//	 new Surface(SurfaceTexture)				<---- SurfaceTexture 
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
		// 你可能需要延迟实例化 CodecInputSurface 知道 "display" EGL context 被创建
		// 然后修改 'recoder'EGL contect的 eglGetCurrentContext() 中 share_context参数为'display' context
	 
		// 获得一个Surface作为编码器输入  而不是用dequeueInputBuffer
		// surface 会给到Camera作为 preview
		
		MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, width, height);
		format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
		format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
		format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
		format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);
		Log.d(TAG, "format: " + format);
		try {
			mEncoder = MediaCodec.createEncoderByType(MIME_TYPE);
		} catch (IOException e) {
			Log.e(TAG, "create MediaCodec Error ! ");
			Log.e(TAG, "create MediaCodec Error ! ");
			Log.e(TAG, "create MediaCodec Error ! ");
			e.printStackTrace();
			return;
		}
		mEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
		Surface surface = mEncoder.createInputSurface();
		mInputSurface = new CodecInputSurface(surface);
		mEncoder.start();

	
		// 创建一个MediaMuxer 但是不能在这里添加video track和 start()这个Muxer
		// 只能在Encoder编码一定数据后 INFO_OUTPUT_FORMAT_CHANGED 的时候再添加video track和start()
		// 我们只是把 raw H264 ES(elementary stream )保存到mp4 没有声音
		String outputPath = new File(OUTPUT_DIR, "test." + width + "x" + height + ".mp4").toString();
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

				if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
					// The codec config data was pulled out and fed to the muxer
					// when we got
					// the INFO_OUTPUT_FORMAT_CHANGED status. Ignore it.
					if (VERBOSE)
						Log.d(TAG, "ignoring BUFFER_FLAG_CODEC_CONFIG");
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
		int encWidth = 640;
		int encHeight = 480;
		int encBitRate = 6000000; // Mbps
		Log.d(TAG, MIME_TYPE + " output " + encWidth + "x" + encHeight + " @" + encBitRate);

		
		try {
			prepareCamera(encWidth, encHeight);
			prepareEncoder(encWidth, encHeight, encBitRate);
			mInputSurface.makeCurrent(); 
			// 必须在prepareSurfaceTexture之前 进行 EGL14.eglMakeCurrent
			// 否则 后面 进行GLES操作 比如  GLES20.glCompileShader  就会出现错误
			
			// 从SurfaceManager获得一个SurfaceTexture给到Camera preview
			prepareSurfaceTexture();

			mCamera.startPreview();

			long startWhen = System.nanoTime();
			long desiredEnd = startWhen + DURATION_SEC * 1000000000L;
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
				}
				frameCount++;

				
				// 请求a new frame作为输入,render他到Surface
				// 如果有 GLSurfaceView 作为显示， 我们可以 切换 EGL contexts 和 之后 调用 drawImage() 
				// 使其 render it 在屏幕上
				// texture是可以被EGL contexts共享 通过在 eglCreateContext() 传递参数 share_context
			
				mStManager.awaitNewImage(); // mSurfaceTexture.updateTexImage();
				mStManager.drawImage(); // mTextureRender.drawFrame(mSurfaceTexture);

				// 根据SurfaceTexture来设置 the presentation time stamp 
				// MediaMuxer用他来设置mp4中的PTS  
				Log.d(TAG, "present: " + ((st.getTimestamp() - startWhen) / 1000000.0) + "ms");
				mInputSurface.setPresentationTime(st.getTimestamp());

				// 提交给编码器. 如果输入缓存满的话  swapBuffers会阻塞,直到我们从输出中获取数据
				// 由于我们在一个线程中推送数据和获取数据，为了避免在这里堵塞
				// 我们在drainEncoder(false)的时候，把所有输出output都获取写到文件
				// 然后再 awaitNewImage --> drawImage --> swapBuffers	 
				mInputSurface.swapBuffers();// 这样编码器就有输入数据了
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

		Camera.CameraInfo info = new Camera.CameraInfo();

		// Try to find a front-facing camera (e.g. for videoconferencing).
		int numCameras = Camera.getNumberOfCameras();
		for (int i = 0; i < numCameras; i++) {
			Camera.getCameraInfo(i, info);
			if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
				mCamera = Camera.open(i);
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
		// leave the frame rate set to default
		mCamera.setParameters(parms);

		Camera.Size size = parms.getPreviewSize();
		Log.d(TAG, "Camera preview size is " + size.width + "x" + size.height);
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
	private static class CodecInputSurface {
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

			
            /* Configure context for OpenGL ES 2.0.
             * 根据EGLConfig,创建 上下文 EGLContext   OpenGL ES 2.0 
             * 
             * EGLContext  eglCreateContext(EGLDisplay display, 
             * 									EGLConfig config, 
             * 									EGLContext share_context 是否有context共享？共享的contxt之间亦共享所有数据 EGL_NO_CONTEXT代表不共享
             * 									int[] attrib_list);
             * 
             * 目前可用属性只有EGL_CONTEXT_CLIENT_VERSION, 1代表OpenGL ES 1.x, 2代表2.0
             */
			int[] attrib_list = { 
						EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, 
						EGL14.EGL_NONE };
			mEGLContext = EGL14.eglCreateContext(mEGLDisplay, 
													configs[0], 
													EGL14.EGL_NO_CONTEXT, 
													attrib_list,
													0);
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

		/**
		 * Makes our EGL context and surface current.
		 */
		public void makeCurrent() {
			// OpenGL ES定义了客户端和服务端状态,所以OpenGL ES的contex包含了客户和服务端两个状态
        	// 设置为当前的渲染环境 OpengGL的客户端API采用了隐含的context作为粉刷入口,而不是在绘图函数传入Context参数
			// 因此EGL提供一个函数makeCurrent使某个Context变成当前使用状态
			// 1. 每个线程最多可以为每个支持/使用客户端API(openGL)创建一个当前粉刷Cotext
			// 2. 同一时刻 一个Context只能被一个线程设置为当前
        	// boolean android.opengl.EGL14.eglMakeCurrent(EGLDisplay dpy, EGLSurface draw, EGLSurface read, EGLContext ctx)
			EGL14.eglMakeCurrent(mEGLDisplay, mEGLSurface, mEGLSurface, mEGLContext);
			checkEglError("eglMakeCurrent");
            // 环境初始化完毕，开始使用OpenGL ES 2.0 API 进行绘制
            // GLES20.glxxxxxx ...
            // 
		}

		/**
		 * Calls eglSwapBuffers. Use this to "publish" the current frame.
		 */
		public boolean swapBuffers() {
			//	调用eglSwapBuffers会去触发queuebuffer，dequeuebuffer，
		    //	queuebuffer将画好的buffer交给surfaceflinger处理，
		    //	dequeuebuffer新创建一个buffer用来画图
			boolean result = EGL14.eglSwapBuffers(mEGLDisplay, mEGLSurface);
			checkEglError("eglSwapBuffers");
			return result;
		}

		/**
		 * Sends the presentation time stamp to EGL. Time is expressed in
		 * nanoseconds.
		 */
		public void setPresentationTime(long nsecs) {
			EGLExt.eglPresentationTimeANDROID(mEGLDisplay, mEGLSurface, nsecs);
			checkEglError("eglPresentationTimeANDROID");
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
		private final float[] mTriangleVerticesData = {
				// X, Y, Z, U, V
				-1.0f, -1.0f, 0, 0.f, 0.f, 1.0f, -1.0f, 0, 1.f, 0.f, -1.0f, 1.0f, 0, 0.f, 1.f, 1.0f, 1.0f, 0, 1.f,
				1.f, };

		private FloatBuffer mTriangleVertices;

		private static final String VERTEX_SHADER = "uniform mat4 uMVPMatrix;\n" + "uniform mat4 uSTMatrix;\n"
				+ "attribute vec4 aPosition;\n" + "attribute vec4 aTextureCoord;\n" + "varying vec2 vTextureCoord;\n"
				+ "void main() {\n" + "    gl_Position = uMVPMatrix * aPosition;\n"
				+ "    vTextureCoord = (uSTMatrix * aTextureCoord).xy;\n" + "}\n";

		private static final String FRAGMENT_SHADER = "#extension GL_OES_EGL_image_external : require\n"
				+ "precision mediump float;\n" + // highp here doesn't seem to
													// matter
				"varying vec2 vTextureCoord;\n" + "uniform samplerExternalOES sTexture;\n" + "void main() {\n"
				+ "    gl_FragColor = texture2D(sTexture, vTextureCoord);\n" + "}\n";

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

		public void drawFrame(SurfaceTexture st) {
			checkGlError("onDrawFrame start");
			
			// 拿到SurfaceTexture的坐标系
			st.getTransformMatrix(mSTMatrix);

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

			GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
			GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mTextureID);

			mTriangleVertices.position(TRIANGLE_VERTICES_DATA_POS_OFFSET);
			GLES20.glVertexAttribPointer(maPositionHandle, 3, GLES20.GL_FLOAT, false,
					TRIANGLE_VERTICES_DATA_STRIDE_BYTES, mTriangleVertices);
			checkGlError("glVertexAttribPointer maPosition");
			GLES20.glEnableVertexAttribArray(maPositionHandle);
			checkGlError("glEnableVertexAttribArray maPositionHandle");

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

			// IMPORTANT: on some devices, if you are sharing the external
			// texture between two
			// contexts, one context may not see updates to the texture unless
			// you un-bind and
			// re-bind it. If you're not using shared EGL contexts, you don't
			// need to bind
			// texture 0 here.
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

			// GenTextures  产生一个GLES20纹理 
			int[] textures = new int[1];
			GLES20.glGenTextures(1, textures, 0);
			mTextureID = textures[0];
			
			// BindTexture 绑定指定纹理
			GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mTextureID);
			checkGlError("glBindTexture mTextureID");

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
				throw new RuntimeException("Unable to locate '" + label + "' in program");
			}
		}
	}

}
