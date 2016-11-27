package com.tom.opengl.three;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

import com.tom.codecanativewin.R;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

public class DecodeYUVGLActivity extends Activity {

	public final static String TAG = "DecodeYUVGLActivity" ; 
    private String outputDir;
    
    private YUVGLSurfaceView mYuvGlView = null; 
    private DecodeThread mThread = null; 

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.opengl_texture_three);

        mYuvGlView = (YUVGLSurfaceView) findViewById(R.id.vYUVSurface);
        
        final Button buttonStart = (Button) findViewById(R.id.button_start);
        buttonStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
            	if(mThread == null){
            		mThread = new DecodeThread();
            		mThread.start();
            	}
            }
        });
        
        final Button buttonStop = (Button) findViewById(R.id.button_stop);
        buttonStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
            	if(mThread != null){
            		mThread.mStopDecode = true ;
            		try {
						mThread.join();
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
            		mThread = null;
            	}
            }
        });
        
    }
    
    public final String DECODE_FILE_NAME = "/mnt/sdcard/wushun.3gp" ;
    
    /*
     * 	YUV420SemiPlanar格式Image
     *  image format: 35		<<<<<<
		get data from 3 planes
		pixelStride 1
		rowStride 1920
		width 1920
		height 1080
		buffer size 2088960
		Finished reading data from plane 0
		pixelStride 1			<<<<<<
		rowStride 960			<<<<<<
		width 1920
		height 1080
		buffer size 522240
		Finished reading data from plane 1
		pixelStride 1
		rowStride 960
		width 1920
		height 1080
		buffer size 522240
		Finished reading data from plane 2
		
		YUV420SemiPlanar格式Image
		image format: 35
		get data from 3 planes
		pixelStride 1
		rowStride 1920
		width 1920
		height 1080
		buffer size 2088960
		Finished reading data from plane 0
		pixelStride 2
		rowStride 1920
		width 1920
		height 1080
		buffer size 1044479
		Finished reading data from plane 1
		pixelStride 2			<<<<<<                ,但是每隔2个字节才是一个V分量
		rowStride 1920			<<<<<<  每一行有这么多字节 
		width 1920
		height 1080
		buffer size 1044479
		Finished reading data from plane 2
     */
    public class DecodeThread extends Thread
    {
    	private final int DEFAULT_TIMEOUT_US = 200000;
    	private final int DECODE_COLOR_FORMAT = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible;
    	public boolean mStopDecode = false ;
    	private MediaCodec.BufferInfo mInfo = new MediaCodec.BufferInfo();
    	
        private void showSupportedColorFormat(MediaCodecInfo.CodecCapabilities caps) {
            Log.d(TAG, "supported color format:");
            for (int c : caps.colorFormats) {
                Log.d(TAG , c + "\t");
            }
        }
        
        private boolean isColorFormatSupported(int colorFormat, MediaCodecInfo.CodecCapabilities caps) {
            for (int c : caps.colorFormats) {
                if (c == colorFormat) {
                    return true;
                }
            }
            return false;
        }
        
        private boolean isImageFormatSupported(Image image) {
            int format = image.getFormat();
            switch (format) {
                case ImageFormat.YUV_420_888:
                // https://developer.android.com/reference/android/media/Image.html
                // Image 没有支持下面两种格式 ！！
                //case ImageFormat.NV21: 
                //case ImageFormat.YV12:
                    return true;
            }
            return false;
        }
        

		@Override
		public void run() {
			super.run();
			
			MediaExtractor extractor = null;
	        MediaCodec decoder = null;
	        try {
	            //File videoFile = new File(DECODE_FILE_NAME);
	            extractor = new MediaExtractor();
	           
				extractor.setDataSource(DECODE_FILE_NAME );
				
				GLFrameRender mRender = mYuvGlView.getRenderUpdate();
	            
	            int numTracks = extractor.getTrackCount();
	            int selected_track = 0 ;
	            for ( selected_track = 0 ; selected_track < numTracks; selected_track++) {
	                MediaFormat format = extractor.getTrackFormat(selected_track);
	                String mime = format.getString(MediaFormat.KEY_MIME);
	                if (mime.startsWith("video/")) {
	                    Log.d(TAG, "Extractor selected track " + selected_track + " (" + mime + "): " + format);
	                    extractor.selectTrack(selected_track);
	                    break;
	                }
	            }
	            if( selected_track == numTracks ){
	            	Log.e(TAG, "NO video Track found");
	            	return ;
	            }
	            
	            MediaFormat mediaFormat = extractor.getTrackFormat(selected_track);
	            Log.d(TAG, "selected track format " + mediaFormat.toString() );
	            String mime = mediaFormat.getString(MediaFormat.KEY_MIME);
	            decoder = MediaCodec.createDecoderByType(mime);
	            showSupportedColorFormat(decoder.getCodecInfo().getCapabilitiesForType(mime));
	            if (isColorFormatSupported(DECODE_COLOR_FORMAT, decoder.getCodecInfo().getCapabilitiesForType(mime))) {
	                mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, DECODE_COLOR_FORMAT);
	                Log.i(TAG, "set decode color format to type " + DECODE_COLOR_FORMAT);
	            } else {
	                Log.i(TAG, "unable to set decode color format, color format type " + DECODE_COLOR_FORMAT + " not supported");
	                return ;
	            }
	            decoder.configure(mediaFormat, null, null, 0 ); // 没有配置window 
	            decoder.start();
	            
	            boolean sawInputEOS = false;
	            boolean sawOutputEOS = false;
	         
	
	            final int width = mediaFormat.getInteger(MediaFormat.KEY_WIDTH);
	            final int height = mediaFormat.getInteger(MediaFormat.KEY_HEIGHT);
	            
	            mRender.update(width, height);
	          
	            while (!sawOutputEOS && !mStopDecode) {
	                if (!sawInputEOS) {
	                    int inputBufferId = decoder.dequeueInputBuffer(DEFAULT_TIMEOUT_US);
	                    Log.i(TAG, "dequeueInputBuffer " + inputBufferId );
	                    if (inputBufferId >= 0) {
	                        ByteBuffer inputBuffer = decoder.getInputBuffer(inputBufferId);
	                      
	                        int sampleSize = extractor.readSampleData(inputBuffer, 0);
	                     
	                        if (sampleSize < 0) {
	                            decoder.queueInputBuffer(inputBufferId, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
	                            sawInputEOS = true;
	                        } else {
	                            long presentationTimeUs = extractor.getSampleTime();
	                            decoder.queueInputBuffer(inputBufferId, 0, sampleSize, presentationTimeUs, 0);
	                            extractor.advance();
	                        }
	                    }
	                }
	               
	                int outputBufferId = decoder.dequeueOutputBuffer(mInfo, DEFAULT_TIMEOUT_US);
	                Log.i(TAG, "dequeueOutputBuffer " + outputBufferId );
	                if (outputBufferId >= 0) {
	                	
	                	
	                    if ((mInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
	                        sawOutputEOS = true;
	                    }
	                   
	                    if (  mInfo.size != 0  ) {
	                       
	                        Image image = decoder.getOutputImage(outputBufferId);
	                        long timestamp = image.getTimestamp();
	                        int format = image.getFormat();
	                        int img_height = image.getHeight();
	                        int img_width = image.getWidth();
	                        Rect crop = image.getCropRect(); 
	                        int crop_width = crop.width();
	                        int crop_height = crop.height();
	                        Image.Plane[] planes = image.getPlanes();
	                         
	                        int num_planes = planes.length ; 
	                        Log.d(TAG, String.format("time:%d format:%d h:%d w:%d num_planes %d crop[ %d  %d ] 0:[%d %d] 1:[%d %d] 2:[%d %d]", 
	                        						timestamp, format, img_height , img_width , num_planes ,
	                        						crop_width , crop_height ,
	                        						planes[0].getPixelStride() ,planes[0].getRowStride() ,
	                        						planes[1].getPixelStride() ,planes[1].getRowStride() ,
	                        						planes[2].getPixelStride() ,planes[2].getRowStride() 
	                        						)
	                        						
	                        						 
	                        						// 需要根据 PixelStride 的间隔来区分U和V分量
	                        						// Image没有把U和V彻底分开
	                        						
	                        						// getPixelStride = 2 的时候 要以2为间隔取出U/V数据
	                        						// 这时候 ByteBuffer 的 remaining/capacity都是 W*H / 2 - 1
	                        						// 1280*960/2 - 1 = 1228800/2 - 1 = 614400 -1 = 614399
	                        						// 但是ByteBuffer的开头 都指向 U/V 各自的第一个字节
	                        						// U: planes[1].getBuffer()[0] [2] [4]
	                        						// V: planes[2].getBuffer()[0] [2] [4]
	                        					  );
	                        
	                        if(!isImageFormatSupported(image)){
	                        	return ;
	                        }
	                      
	                        // isDirect
	                        // ReadOnly		java.nio.ReadOnlyBufferException	不能array() 只能get到一个byte[]中
	                        ByteBuffer yBuffer = planes[0].getBuffer();
	                        ByteBuffer uBuffer = planes[1].getBuffer();
	                        ByteBuffer vBuffer = planes[2].getBuffer();
	                        Log.d(TAG, String.format("y %d u %d v %d isDirect %b pos %d",
	                        						yBuffer.capacity() , uBuffer.capacity() , vBuffer.capacity() , vBuffer.isDirect()
	                        						, vBuffer.position() ));
	                        
	                        int pixel_stride = planes[1].getPixelStride() ; 
	                        if( pixel_stride == 1){
	                        	mRender.update(yBuffer, uBuffer, vBuffer);
	                        }else{ // pixel_stride == 2 

	                        	if( mUByte == null){
	                        		byte[] b = new byte[ image.getHeight()* image.getWidth() / 4];
	                        		mUByte = ByteBuffer.wrap( b ) ;
	                        	}
	                        	if( mVByte == null){
	                        		byte[] b = new byte[ image.getHeight()* image.getWidth() / 4];
	                        		mVByte = ByteBuffer.wrap( b );  
	                        	}  
	                        	 
	                        	 
	                        	mUByte.clear(); // for write
	                        	mVByte.clear(); // for write
//	                        	int W = image.getWidth() ;
//	                        	int H = image.getHeight() ; 
//	                        	for (int row = 0; row < H / 2 ; row++) {
//	                            	for( int col = 0 ; col < W / 2 ; col++  ){
//		                        		mUByte.put( uBuffer.get( (W/2 * row + col )* pixel_stride )  ) ;
//		                        		mVByte.put( vBuffer.get( (W/2 * row + col )* pixel_stride )  );
//		                        	}
//	                        	}
	                        	
	                        	int total = uBuffer.capacity();
	                        	for(int i = 0 ; i < total ; i+=2){
	                        		mUByte.put( uBuffer.get(i) );
	                        		mVByte.put( vBuffer.get(i) );
	                        	}
	                        	
	                        	mUByte.flip(); // for read , set limit position 
	                        	mVByte.flip();
	                        	mRender.update(yBuffer, mUByte, mVByte);
	                        }
	                        
	                        
	                        //Log.d(TAG, String.format("pos %d", vBuffer.position()));
	                        image.close(); 
	                        // Free up this frame for reuse. 
	                        // attempting to read from or write to ByteBuffers returned by an earlier getBuffer() call 
	                        // will have undefined behavior. 
	                        
	                        Thread.sleep(30);
	                    }
	                    decoder.releaseOutputBuffer(outputBufferId, true);
	                   
	                }
	            }

	           // decoder.stop();
	         	
	        } catch (IOException e) {
				Log.d(TAG, "IOException error " + e.getMessage() );
				e.printStackTrace();
	        } catch (Exception e){
	        	Log.d(TAG, "Exception error " + e.getMessage() );
	        	e.printStackTrace();
	        } finally {
	        	Log.d(TAG, "final !");
	            if (decoder != null) {
	                decoder.stop();
	                decoder.release();
	                decoder = null;
	            }
	            if (extractor != null) {
	                extractor.release();
	                extractor = null;
	            }
	        }
		}
    }
    
    private ByteBuffer mUByte = null;
    private ByteBuffer mVByte = null; 
}
