package com.tom.opengl;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

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
        setContentView(R.layout.opengl_camera);

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
	                        Log.d(TAG, String.format("time:%d format:%d h:%d w:%d num_planes %d crop[ %d  %d ] ", 
	                        						timestamp, format, img_height , img_width , num_planes ,
	                        						crop_width , crop_height )
	                        					  );
	                        
	                        if(!isImageFormatSupported(image)){
	                        	return ;
	                        }
	                      
	                        // isDirect
	                        // ReadOnly		java.nio.ReadOnlyBufferException	不能array()
	                        ByteBuffer yBuffer = planes[0].getBuffer();
	                        ByteBuffer uBuffer = planes[1].getBuffer();
	                        ByteBuffer vBuffer = planes[2].getBuffer();
	                        //Log.d(TAG, String.format("y %d u %d v %d isDirect %b pos %d",
	                        //						yBuffer.capacity() , uBuffer.capacity() , vBuffer.capacity() , vBuffer.isDirect()
	                        //						, vBuffer.position() ));
	                        
	                        mRender.update(yBuffer, uBuffer, vBuffer);
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
}
