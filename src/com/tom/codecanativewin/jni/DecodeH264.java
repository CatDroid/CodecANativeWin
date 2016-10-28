package com.tom.codecanativewin.jni;

import java.lang.ref.WeakReference;

import android.util.Log;
import android.view.Surface;

public class DecodeH264 {

	private final static String TAG = "java_decodeh264" ;
	
	public static final int MEDIA_PREPARED 				 	= 1;
	public static final int MEDIA_SEEK_COMPLETE				= 2;
	public static final int MEDIA_INFO_PLAY_COMPLETED		= 3;
	public static final int MEDIA_INFO_PAUSE_COMPLETED		= 4;
	public static final int MEDIA_BUFFER_DATA				= 5;
	public static final int MEDIA_TIME_UPDATE				= 6;
	public static final int MEDIA_H264_SAMPLE				= 7;
	
	public static final int THREAD_LOOP_END 				= 999;
	public static final int THREAD_STARTED					= (THREAD_LOOP_END + 1);
	public static final int THREAD_STOPED					= (THREAD_LOOP_END + 2);
	public static final int THREAD_EXCEPTION				= (THREAD_LOOP_END + 3);
	
	
	private long mNativeContext = 0; // for 64bits
	
	public DecodeH264(){
		mNativeContext = native_setup();
	}
	
	public void start(Surface surface , String path , byte[] sps , byte pps[]){
		native_start(mNativeContext ,  surface , path ,   sps ,   pps ,  new WeakReference<DecodeH264>(this) );
	}
	
	public void release(){
		native_stop( mNativeContext );
	}
	
	
	
	public interface onInfoListener 
	{
		public void onInfo( int type , int arg1 ,int arg2);
	}
	private onInfoListener mOnInfoListener = null;
	public void setInfoListener(onInfoListener info){
		mOnInfoListener = info ;
	}
	
	
	public interface onDataListener 
	{
		public void onData( int data_type  ,  ABuffer data);
	}
	private onDataListener mOnDataListener = null;
	public void setOnDataListener(onDataListener data){
		mOnDataListener = data ;
	}
	
	
	native private long native_setup();
	native private void native_start( long ctx , Surface surface , String path , byte[] sps , byte pps[] , Object wek_thiz );
	native private void native_stop( long ctx );
	
    static private void postEventFromNative(Object weak_ref, int what, int arg1, int arg2, Object obj)
    {
    	DecodeH264 mp = (DecodeH264)((WeakReference<DecodeH264>)weak_ref).get();
    	if (mp == null) {
    		Log.e(TAG, "postEventFromNative: Null mp! what=" + what + ", arg1=" + arg1 + ", arg2=" + arg2);
    		return;
    	}
    	
    	switch(what){
    	case THREAD_STOPED:
    		mp.mNativeContext = 0;
    		Log.d(TAG, "native thread is over done!");
    		break;
    		
    	case THREAD_EXCEPTION:
    		Log.e(TAG, "postEventFromNative msg = " + (String)obj);
    		break;
    		
    	case MEDIA_TIME_UPDATE:
    		if( mp.mOnInfoListener !=null){
    			mp.mOnInfoListener.onInfo(MEDIA_TIME_UPDATE , arg1 , arg2 );
    		}
    		break;
    	case MEDIA_H264_SAMPLE:
    		if( mp.mOnDataListener !=null){
    			mp.mOnDataListener.onData(MEDIA_H264_SAMPLE,(ABuffer)obj);
    		}else{
    			ABuffer buffer = (ABuffer)obj ; 
    			buffer.release();
    		}
    		break;
    	default:
    		Log.i(TAG, "postEventFromNative default type ? : what=" + what + ", arg1=" + arg1 + ", arg2=" + arg2 );
    		break;
    	}
    }
    
    
	static {
		System.loadLibrary("DecodeH264");
	}
			
}
