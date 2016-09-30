package com.tom.codecanativewin.jni;

import java.lang.ref.WeakReference;

import android.util.Log;
import android.view.Surface;

public class DecodeH264 {

	private final static String TAG = "DecodeH264" ;
	
	public static final int MEDIA_PREPARED 				 	= 1;
	public static final int MEDIA_SEEK_COMPLETE				= 2;
	public static final int MEDIA_INFO_PLAY_COMPLETED		= 3;
	public static final int MEDIA_INFO_PAUSE_COMPLETED		= 4;
	public static final int MEDIA_BUFFER_DATA				= 5;
	public static final int MEDIA_TIME_UPDATE				= 6;
	
	public static final int THREAD_LOOP_END 				= 999;
	public static final int THREAD_STARTED					= (THREAD_LOOP_END + 1);
	public static final int THREAD_STOPED					= (THREAD_LOOP_END + 2);
	public static final int THREAD_EXCEPTION				= (THREAD_LOOP_END + 3);
	
	
	private long mNativeContext = 0; // for 64bits
	
	public DecodeH264(){
		mNativeContext = native_setup();
	}
	
	public void start(Surface surface){
		native_start(mNativeContext ,  surface , new WeakReference<DecodeH264>(this) );
	}
	
	public void release(){
		native_stop( mNativeContext );
	}
	
	native private long native_setup();
	native private void native_start( long ctx , Surface surface , Object wek_thiz );
	native private void native_stop( long ctx );
	
    static private void postEventFromNative(Object weak_ref, int what, int arg1, int arg2, Object obj)
    {
    	DecodeH264 mp = (DecodeH264)((WeakReference)weak_ref).get();
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
    		Log.d(TAG, "postEventFromNative update time = " + arg1 + " sec ; " + " size = " + arg2 );
    	default:
    		break;
    	}
    	 

    	Log.i(TAG, "postEventFromNative: what=" + what + ", arg1=" + arg1 + ", arg2=" + arg2 );
    }
    
    
	static {
		System.loadLibrary("DecodeH264");
	}
			
}
