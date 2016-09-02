package com.tom.codecanativewin;


import java.io.IOException;

import com.tom.codecanativewin.jni.NativeWinCodec;

import android.app.Activity;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.hardware.camera2.CameraDevice;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.os.Bundle;
import android.util.Log;
import android.util.Range;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;

public class NativeWinCodecActivity extends Activity {

	final public static String TAG = "nwc" ; // Native Window Codec 
	private SurfaceView mSv = null;
	private SurfaceHolder mSh = null;
	private NativeWinCodec mNativeWinCodec = null;
	
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); 
		setContentView(R.layout.activity_native_win_codec);
		
		// 测试是否能够创建指定颜色空间的编码器
		testCodec();
		
		// NDK 没有 MediaCodecList MediaCodecInfo
		dumpCodecInfo(true, "video/avc");
		
		
		mSv = (SurfaceView) findViewById(R.id.mySurface);
		mSh = mSv.getHolder();
		mSh.setFormat(PixelFormat.RGBA_8888);// RGBX8888 RGBA8888 RGB565
		
		mSh.setKeepScreenOn(true);
		
		//mSh.setFixedSize(800, 1280);
		// surfaceHolder.setFixedSize(?, ?);是设置分辨率
		// 视频窗口的大小是由surfaceView的大小决定的。只要设置surfaceView的layout就行了,
		//mSh.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
		mSh.addCallback(new SurfaceCallback());
		
		Button btn = (Button)findViewById(R.id.bTestLockANWwithCodec);
		btn.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				if(mNativeWinCodec!=null){
					mNativeWinCodec.testLockANWwithCodec();
				}
				
			}
		});
		

	}
	
	public void testFileFps(){
	    MediaMetadataRetriever retriever = new MediaMetadataRetriever(); 
	    retriever.setDataSource("/mnt/sdcard/test.mp4");
	    String time = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
	    String tracks = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_NUM_TRACKS);
	    
	    
	    int seconds = Integer.valueOf(time) / 1000; 
	    
	    
	}
	
	
	public void testCodec(){
		MediaFormat  format = new MediaFormat();
		format.setString(MediaFormat.KEY_MIME, "video/avc");
		format.setInteger(MediaFormat.KEY_WIDTH, 640 ) ; //1280);
		format.setInteger(MediaFormat.KEY_HEIGHT, 480 ) ; //960);
		format.setInteger(MediaFormat.KEY_FRAME_RATE, 15);
		format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar);  
							/*
							  	COLOR_Format32bitARGB8888 = 16; // 0x10    
								COLOR_Format32bitBGRA8888 = 15; // 0xf	
								COLOR_FormatYUV420Planar = 19;  // I420 
								COLOR_FormatYUV420SemiPlanar = 21; // NV12
								
								COLOR_FormatYUV420Flexible = 2135033992;
								COLOR_FormatSurface = 2130708361;
								
							*/
		format.setInteger(MediaFormat.KEY_FRAME_RATE, 15);  
		format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1); //关键帧间隔时间 单位s 
		format.setInteger(MediaFormat.KEY_BIT_RATE, 15000);  
		
     
		MediaCodec mediaCodec = null;
		try {
			mediaCodec = MediaCodec.createEncoderByType("video/avc");
		} catch (IOException e) {
			Log.d(TAG, "createEncoderByType ERR" );
			e.printStackTrace();
		}
		
		try {
			if(mediaCodec!=null){
				mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
				Log.d(TAG, "configure done");
				/*
	 				I ExtendedACodec: setupVideoEncoder()
					W ACodec  : do not know color format 0x7fa30c06 = 2141391878
					W ACodec  : do not know color format 0x7fa30c04 = 2141391876
					W ACodec  : do not know color format 0x7fa30c08 = 2141391880
					W ACodec  : do not know color format 0x7fa30c07 = 2141391879
					W ACodec  : do not know color format 0x7f000789 = 2130708361
					E ACodec  : [OMX.qcom.video.encoder.avc] does not support color format 19
					E ACodec  : [OMX.qcom.video.encoder.avc] configureCodec returning error -2147483648
					E ACodec  : signalError(omxError 0x80001001, internalError -2147483648)
					E MediaCodec: Codec reported err 0x80001001, actionCode 0, while in state 3
					E MediaCodec: configure failed with err 0x80001001, resetting...
					E OMX-VENC: async_venc_message_thread interrupted to be exited
				 * */
			}else{
				Log.d(TAG, "configure fail");
			}
		}catch( MediaCodec.CodecException ce){
			Log.d(TAG, "codecException" + ce.getDiagnosticInfo() );
			Log.d(TAG, "ce = " + ce.getMessage());
			ce.printStackTrace();
		}catch (Exception ex){
			Log.d(TAG, "ex = " + ex.getMessage());
			ex.printStackTrace();
		}
		
		// 	android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
		//	
		//	mediaCodec.getInputImage(0);
		//	mediaCodec.getOutputImage(0);
	 
		 
	}
	
	// 打印指定 编解码器isencode 对 某种类型mime 支持的颜色空间
	public void dumpCodecInfo(boolean isencode , String mime ){
		int numCodecs = MediaCodecList.getCodecCount();
		MediaCodecInfo codecInfo = null;
		for(int i = 0 ; i < numCodecs ; i ++){
			codecInfo = MediaCodecList.getCodecInfoAt(i);
			if(  codecInfo.isEncoder() != isencode  ){
				continue ;
			}
			
			String[] sysported_type = codecInfo.getSupportedTypes();
			for(String loop : sysported_type){
				if( loop.equals(mime)){
					
					/* h264 video/avc编码器 支持的颜色格式
					 	MTK  MTK6735   
							codec name: OMX.MTK.VIDEO.ENCODER.AVC
								mime:video/avc support formats = 2130706944		???
								mime:video/avc support formats = 2130708361    	COLOR_FormatSurface
								mime:video/avc support formats = 2130706944		???
								mime:video/avc support formats = 2135033992		COLOR_FormatYUV420Flexible
								mime:video/avc support formats = 19				COLOR_FormatYUV420Planar
								mime:video/avc support formats = 6				COLOR_Format16bitRGB565
								mime:video/avc support formats = 11				COLOR_Format24bitRGB888
								mime:video/avc support formats = 16				COLOR_Format32bitARGB8888
								mime:video/avc support formats = 2130707200		???	
								mime:video/avc support formats = 15				COLOR_Format32bitBGRA8888

						高通820/小米5 
							codec name: OMX.qcom.video.encoder.avc
								range:1~100000000
								frame:1~240
								height:64~2160
								width:96~4096
								width_align:2 height_align:2
								640, 480 support ? true
								640, 480 15fps support ? true
								complex:0~0
									mime:video/avc support formats = 2141391878		???	
									mime:video/avc support formats = 2141391876		???	
									mime:video/avc support formats = 2141391880		???	
									mime:video/avc support formats = 2141391879		???	
									mime:video/avc support formats = 2130708361		COLOR_FormatSurface
									mime:video/avc support formats = 2135033992		COLOR_FormatYUV420Flexible
									mime:video/avc support formats = 21				COLOR_FormatYUV420SemiPlanar
			
							codec name: OMX.google.h264.encoder
								range:1~12000000
								frame:0~960
								height:16~1088
								width:16~1920
								width_align:2 height_align:2
								640, 480 support ? true
								640, 480 15fps support ? true
								complex:0~0
									mime:video/avc support formats = 2135033992		COLOR_FormatYUV420Flexible
									mime:video/avc support formats = 19				COLOR_FormatYUV420Planar
									mime:video/avc support formats = 21				COLOR_FormatYUV420SemiPlanar
									mime:video/avc support formats = 2130708361		COLOR_FormatSurface
				
					LG Nexus 5X/MSM8992
							codec name: OMX.qcom.video.encoder.avc
								range:1~100000000
								frame:0~960
								height:64~2160
								width:96~3840
								width_align:2 height_align:2
								640, 480 support ? true
								640, 480 15fps support ? true
								complex:0~0
									mime:video/avc support formats = 2141391876
									mime:video/avc support formats = 2130708361
									mime:video/avc support formats = 2135033992
									mime:video/avc support formats = 21


						OPPO R9/mt6755
							codec name: OMX.MTK.VIDEO.ENCODER.AVC
								range:1~62500000
								frame:0~960
								height:96~1072
								width:128~1920
								width_align:16 height_align:16
								640, 480 support ? true
								640, 480 15fps support ? true
								complex:0~0
									mime:video/avc support formats = 2130706944
									mime:video/avc support formats = 2130708361		COLOR_FormatSurface
									mime:video/avc support formats = 2130706944
									mime:video/avc support formats = 2135033992		COLOR_FormatYUV420Flexible
									mime:video/avc support formats = 19				COLOR_FormatYUV420Planar
									mime:video/avc support formats = 6				COLOR_Format16bitRGB565
									mime:video/avc support formats = 11				COLOR_Format24bitRGB888
									mime:video/avc support formats = 16				COLOR_Format32bitARGB8888
									mime:video/avc support formats = 2130707200		
									mime:video/avc support formats = 15				COLOR_Format32bitBGRA8888
							
							
						
						高通820小米5 配置文件:
						media_codecs.xml 
							会包含下面的谷歌软库
							media_codecs_google_audio.xml			谷歌的audio编解码库
							media_codecs_google_video.xml			谷歌的video编解码库
							media_codecs_google_telephony.xml		针对"audio/gsm"类型
							
						media_codecs_performance.xml
			


	
					 * */
					Log.d(TAG, "codec name: " + codecInfo.getName() );
					MediaCodecInfo.CodecCapabilities cap = codecInfo.getCapabilitiesForType(mime);
					Range<Integer> range = cap.getVideoCapabilities().getBitrateRange();
					Log.d(TAG , "range:" + range.getLower() + "~" + range.getUpper()  );
					Range<Integer> frame =  cap.getVideoCapabilities().getSupportedFrameRates();
					Log.d(TAG , "frame:" + frame.getLower() + "~" + frame.getUpper()  );
					Range<Integer> height = cap.getVideoCapabilities().getSupportedHeights();
					Log.d(TAG , "height:" + height.getLower() + "~" + height.getUpper()  );
					Range<Integer> width = cap.getVideoCapabilities().getSupportedWidths();
					Log.d(TAG , "width:" + width.getLower() + "~" + width.getUpper()  );
					

					Log.d(TAG , "width_align:" + cap.getVideoCapabilities().getWidthAlignment()    
								+ " height_align:"+ cap.getVideoCapabilities().getHeightAlignment()  );
					boolean support_size = cap.getVideoCapabilities().isSizeSupported(640, 480);
					Log.d(TAG , "640, 480 support ? " + support_size ); 
					boolean support_size_rate = cap.getVideoCapabilities().areSizeAndRateSupported(640, 480, 15);
					Log.d(TAG , "640, 480 15fps support ? " + support_size_rate ); 
					
					Range<Integer> complex = cap.getEncoderCapabilities().getComplexityRange();
					Log.d(TAG , "complex:" + complex.getLower() + "~" + complex.getUpper()  );
					for(int formats : cap.colorFormats){
						Log.d(TAG, "mime:" + mime  + " support formats = " + formats );
					}
							
					break;
					
					
				}
			}
		}
		
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
			Log.d(TAG , "surfaceCreated play");
			mNativeWinCodec = new NativeWinCodec();
			mNativeWinCodec.setAndplay("/mnt/sdcard/19201080.mp4",  arg0.getSurface());
		}

		@Override
		public void surfaceDestroyed(SurfaceHolder arg0) {
			 
			Log.d(TAG , "surfaceCreated close");
			mNativeWinCodec.stop();
		}
	}
}
