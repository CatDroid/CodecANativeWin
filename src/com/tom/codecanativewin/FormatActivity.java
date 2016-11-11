package com.tom.codecanativewin;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import com.tom.util.SystemPropertiesInvoke;

import android.app.Activity;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaCodec.BufferInfo;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.util.Range;
import android.view.Menu;
import android.view.MenuItem;

public class FormatActivity extends Activity {

	private final String TAG = "FormatActivity" ; 
	
	/* 芯片编码 规格 mt6735
	 * 
	 * 	cpu : mt6735
		codec name: OMX.MTK.VIDEO.ENCODER.AVC
		Bitrate:1~62500000
		FrameRate:0~960
		height:96~1072
		width:128~1920
		width_align:16 height_align:16
		640, 480 support ? true
		640, 480 15fps support ? true
		1920, 1080 60fps support ? false
		1920, 1080 30fps support ? false
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
	 *  
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_format);
		
		Log.d(TAG, "cpu : " + SystemPropertiesInvoke.getString("ro.board.platform", "unknown") );  
		// 测试是否能够创建指定颜色空间的编码器
		testCodec();

		// NDK 没有 MediaCodecList MediaCodecInfo
		dumpCodecInfo(true, "video/avc");
		
	}
	
	//  surely semi-planar but maybe NV21 or NV12 
	private byte[] createTestImage(int width , int height  ) {   
		int pixels =  width * height ; 
		byte[]  image = new byte[ 3 * pixels / 2 ];
		for (int i=0;i<pixels;i++) {
			image[i] = (byte) ( 40 + i % 199);
		}
		for (int i= pixels ; i < 3 * pixels / 2 ; i += 2) {
			image[i] = (byte) (40+ i % 200);
			image[i+1] = (byte) (40+ ( i + 99 ) %200 );
		}
		return image ;
	}
	
	
	/* 常见颜色空间
	 * COLOR_Format32bitARGB8888 = 16; // 0x10 
	 * COLOR_Format32bitBGRA8888 = 15; // 0xf 
	 * COLOR_FormatYUV420Planar = 19; // surely plannar but maybe I420 or YV12
	 * COLOR_FormatYUV420SemiPlanar = 21; //surely semi-planar but maybe NV12 or NV21 
	 * COLOR_FormatYUV420Flexible = 2135033992; 
	 * COLOR_FormatSurface = 2130708361;
	 */
	public void testCodec() {
		String MIME_TYPE = "video/avc";
		int width  = 1280 ; 
		int height = 960 ;
		int frame_rate = 20 ;
		int bit_rate = 9000000 ;
		int i_frame_interval =  1 ;
		// MTK 
		int input_color_format = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar ;
		// 高通
		// int input_color_format = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar ;
		
		MediaFormat format = new MediaFormat();
		format.setString(MediaFormat.KEY_MIME, MIME_TYPE );
		format.setInteger(MediaFormat.KEY_BIT_RATE, bit_rate);  				// required  for encoder 
		format.setInteger(MediaFormat.KEY_WIDTH, width);  						// required  
		format.setInteger(MediaFormat.KEY_HEIGHT, height);  					// required 
		format.setInteger(MediaFormat.KEY_FRAME_RATE, frame_rate);				// required for encoders
		format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, i_frame_interval); 	// required for encoders
																// 关键帧间隔时间 单位1s  这样会转换成GOP间隔 = 19  
																// 实际是否1秒种有20帧 并且有一个IDR帧是"不确定的"
	
		format.setInteger(MediaFormat.KEY_COLOR_FORMAT, input_color_format);	// required 
				
		String current = String.format("width:%d height:%d bitrate:%d framerate:%d i_inteval:%d color:%d",  
				width,height,bit_rate,	frame_rate , i_frame_interval , input_color_format);
		
		MediaCodec mediaCodec = null;
		try {
			mediaCodec = MediaCodec.createEncoderByType(MIME_TYPE);
		} catch (IOException e) {
			Log.d(TAG, "createEncoderByType ERR");
			e.printStackTrace();
		}

		try {
			if (mediaCodec != null) {
				mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
				Log.d(TAG, "configure done");
			} else {
				Log.d(TAG, "configure fail no such encode type " + MIME_TYPE);
				return ;
			}
			mediaCodec.start(); 
		} catch (MediaCodec.CodecException ce) {
			Log.d(TAG, "CodecException DiagnosticInfo:" + ce.getDiagnosticInfo());
			Log.d(TAG, "CodecException Message:" + ce.getMessage());
			Log.d(TAG, "CodecException : do NOT supprot format while configure");
			return ; 
		} catch (Exception ex) {
			Log.d(TAG, "Exception ex = " + ex.getMessage());
			Log.d(TAG, "Exception : do NOT supprot format while configure");
			return ; 
		}
		
		byte[] input_image = createTestImage(width, height);
		if( searchSPSandPPS(mediaCodec ,input_image , 3000000 ,  frame_rate ) >= 0 ){
			Log.d(TAG, current );	
			Log.d(TAG, "sps = " + byteArray2Hex(mSPS));
			Log.d(TAG, "pps = " + byteArray2Hex(mPPS));
		}else{
			Log.e(TAG, "can NOT get sps and pps");
		}
		

		mediaCodec.stop();
		mediaCodec.release();
		
		// android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
		//
		// mediaCodec.getInputImage(0);
		// mediaCodec.getOutputImage(0);
		
	}
	
	/* 高通 820 小米5 测试 sps 和 pps   支持颜色空间 COLOR_FormatYUV420SemiPlanar  21 
	 	
		sps len = 17 pps len = 8
		width:640 height:480 bitrate:2000000 framerate:15 i_inteval:1 color:21
		sps = 67,42,80,16,da,2,80,f6,80,6d,a,13,50
		pps = 68,ce,6,e2
		
		sps len = 17 pps len = 8
		width:640 height:480 bitrate:9000000 framerate:15 i_inteval:1 color:21	<= 修改bitrate 
		sps = 67,42,80,1e,da,2,80,f6,80,6d,a,13,50								<= sps有变 pps没有变 
		pps = 68,ce,6,e2
		
		
		sps len = 17 pps len = 8
		width:640 height:480 bitrate:9000000 framerate:30 i_inteval:1 color:21	<= 修改帧率(GOP)
		sps = 67,42,80,1e,da,2,80,f6,80,6d,a,13,50								<= sps都没改变	
		pps = 68,ce,6,e2
		
		
		sps len = 17 pps len = 8
		width:640 height:480 bitrate:9000000 framerate:30 i_inteval:5 color:21 	<= 修改IDR间隔(GOP)
		sps = 67,42,80,1e,da,2,80,f6,80,6d,a,13,50								<= sps都没改变	
		pps = 68,ce,6,e2
		
		sps len = 17 pps len = 8	
		width:1280 height:960 bitrate:9000000 framerate:30 i_inteval:5 color:21	<= 修改宽高
		sps = 67,42,80,20,da,1,40,1e,68,6,d0,a1,35								<= sps有变 pps没有变 
		pps = 68,ce,6,e2

	 */
	byte[] mSPS = null;
	byte[] mPPS = null;
	private long searchSPSandPPS(MediaCodec encoder , byte[] input_image , long total_time_ms , int frame_rate ) { // 之前需要 创建好 编码器的参数

		ByteBuffer[] inputBuffers = encoder.getInputBuffers();
		ByteBuffer[] outputBuffers = encoder.getOutputBuffers();
		BufferInfo info = new BufferInfo();
		byte[] csd = new byte[128];
		int len = 0, p = 4, q = 4;
		long elapsed = 0, now = timestamp();


		
		while (elapsed < total_time_ms && (mSPS==null || mPPS==null)) {

			// 有些编码器 不会提供sps 和 pps 除非他们接收到东西开始编码
			int bufferIndex = encoder.dequeueInputBuffer( 1000000/frame_rate ); // 通过帧率 来控制 超时
			if ( bufferIndex >= 0 ) {
				if( inputBuffers[bufferIndex].capacity() < input_image.length){
					Log.e(TAG, "The input buffer is not big enough." );
					return -1;
				}
				inputBuffers[bufferIndex].clear();
				inputBuffers[bufferIndex].put(input_image, 0, input_image.length);
				encoder.queueInputBuffer(bufferIndex, 0, input_image.length, timestamp(), 0 );  
			} else {
				//Log.i(TAG,"No buffer available !");
			}

			// We are looking for the SPS and the PPS here. As always, Android is very inconsistent, I have observed that some
			// encoders will give those parameters through the MediaFormat object (that is the normal behaviour).
			// But some other will not, in that case we try to find a NAL unit of type 7 or 8 in the byte stream outputed by the encoder...
			
			int index = encoder.dequeueOutputBuffer(info, 1000000/frame_rate );

			if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {

				// The PPS and PPS shoud be there
				MediaFormat format = encoder.getOutputFormat();
				ByteBuffer spsb = format.getByteBuffer("csd-0");
				ByteBuffer ppsb = format.getByteBuffer("csd-1");
				
				Log.d(TAG , "sps len = " + spsb.capacity() +
							" pps len = " + ppsb.capacity() );
				mSPS = new byte[spsb.capacity()-4];  // 去掉头部的 00 00 00 01 四个字节
				spsb.position(4);
				spsb.get(mSPS,0,mSPS.length);
				
				mPPS = new byte[ppsb.capacity()-4];
				ppsb.position(4);
				ppsb.get(mPPS,0,mPPS.length);
				
				Log.d(TAG, "we got sps and pps in INFO_OUTPUT_FORMAT_CHANGED");
				break;

			} else if (index == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
				outputBuffers = encoder.getOutputBuffers();
			} else if ( index >= 0 ) {

				len = info.size;
				if ( len < 128) { // sps 和  pps 都不可能大于 128  也就是 pps 和 sps 最大到 128 个字节
					
					outputBuffers[index].get(csd,0,len);
					
					if ( len > 0 && csd[0]==0 && csd[1]==0 && csd[2]==0 && csd[3]==1) {
						//	Parses the SPS and PPS, they could be in two different packets and in a different order 
						//	depending on the phone so we don't make any assumption about that
						while (p<len) {
							while (!(csd[p+0]==0 && csd[p+1]==0 && csd[p+2]==0 && csd[p+3]==1) && p+3<len) p++;
							if (p+3>=len) p=len;
							if ((csd[q]&0x1F)==7) {
								Log.d(TAG, "we got sps and pps in NAL unit 7 ");
								mSPS = new byte[p-q];
								System.arraycopy(csd, q, mSPS, 0, p-q);
							} else {
								Log.d(TAG, "we got sps and pps in NAL unit 8 ");
								mPPS = new byte[p-q];
								System.arraycopy(csd, q, mPPS, 0, p-q);
							}
							p += 4;
							q = p;
						}
					}					
				}
				encoder.releaseOutputBuffer(index, false);
			}

			elapsed = timestamp() - now; // 已经花费的时间
		}

		if( mPPS == null || mSPS == null ){
			Log.e(TAG, "Could not determine the SPS & PPS.") ;
			return -1 ;
		}

		// Log.d(TAG, "elapsed = " + elapsed ); 可能是0 
		return elapsed;
	}
	
	
	
	
	// 打印指定 编解码器isencode 对 某种类型mime 支持的颜色空间
	public void dumpCodecInfo(boolean isencode, String mime) {
		int numCodecs = MediaCodecList.getCodecCount();
		MediaCodecInfo codecInfo = null;
		for (int i = 0; i < numCodecs; i++) {

			codecInfo = MediaCodecList.getCodecInfoAt(i);
			if (codecInfo.isEncoder() != isencode) { // 确定是编码codec
				continue;
			}
			Log.d(TAG, " ");
			Log.d(TAG, " ");
			
			String[] sysported_type = codecInfo.getSupportedTypes();
			for (String loop : sysported_type) {
				
				if (loop.equals(mime)) { // 与MIME类型一样 video/h264 
					Log.d(TAG, " ");

					/*
					 * 高通820小米5 配置文件: media_codecs.xml 会包含下面的谷歌软库
					 * 		media_codecs_google_audio.xml 谷歌的audio编解码库
					 * 		media_codecs_google_video.xml 谷歌的video编解码库
					 * 		media_codecs_google_telephony.xml 针对"audio/gsm"类型
					 * 
					 * 		media_codecs_performance.xml
					 * 
					 */
					Log.d(TAG, "codec name: " + codecInfo.getName());
					MediaCodecInfo.CodecCapabilities cap = codecInfo.getCapabilitiesForType(mime);
					Range<Integer> range = cap.getVideoCapabilities().getBitrateRange();
					Log.d(TAG, "Bitrate:" + range.getLower() + "~" + range.getUpper());
					Range<Integer> frame = cap.getVideoCapabilities().getSupportedFrameRates();
					Log.d(TAG, "FrameRate:" + frame.getLower() + "~" + frame.getUpper());
					Range<Integer> height = cap.getVideoCapabilities().getSupportedHeights();
					Log.d(TAG, "height:" + height.getLower() + "~" + height.getUpper());
					Range<Integer> width = cap.getVideoCapabilities().getSupportedWidths();
					Log.d(TAG, "width:" + width.getLower() + "~" + width.getUpper());

					Log.d(TAG, "width_align:" + cap.getVideoCapabilities().getWidthAlignment() + " height_align:"
							+ cap.getVideoCapabilities().getHeightAlignment());
					
					// 测试  编码  是否支持 分辨率 和 帧率 
					boolean support_size = cap.getVideoCapabilities().isSizeSupported(640, 480);
					Log.d(TAG, "640, 480 support ? " + support_size);
					boolean support_size_rate = cap.getVideoCapabilities().areSizeAndRateSupported(640, 480, 15);
					Log.d(TAG, "640, 480 15fps support ? " + support_size_rate);
					support_size_rate = cap.getVideoCapabilities().areSizeAndRateSupported(1920, 1080, 60);
					Log.d(TAG, "1920, 1080 60fps support ? " + support_size_rate);
					support_size_rate = cap.getVideoCapabilities().areSizeAndRateSupported(1920, 1080, 30);
					Log.d(TAG, "1920, 1080 30fps support ? " + support_size_rate);
					
					Range<Integer> complex = cap.getEncoderCapabilities().getComplexityRange();
					Log.d(TAG, "complex:" + complex.getLower() + "~" + complex.getUpper());
					for (int formats : cap.colorFormats) {
						Log.d(TAG, "mime:" + mime + " support formats = " + formats);
					}

					break;

				}
			}
		}

	}
		
	public void testFileFps() {
		MediaMetadataRetriever retriever = new MediaMetadataRetriever();
		retriever.setDataSource("/mnt/sdcard/test1080p60fps.mp4");
		String time = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
		String tracks = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_NUM_TRACKS);

		int seconds = Integer.valueOf(time) / 1000;

	}
	
	String byteArray2Hex(byte[] array)
	{	
		StringBuilder sb = new StringBuilder();
		for( int j = 0 ; j < array.length ; j++ ){
			sb.append( Integer.toHexString( 0xFF & array[j] ) ) ;
			if( j != array.length -1 ) sb.append(",");
		}
		return sb.toString();
	}
	
	private long timestamp() {
		return System.nanoTime()/1000;
	}
}
