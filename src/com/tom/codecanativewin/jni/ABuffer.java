package com.tom.codecanativewin.jni;

import java.nio.ByteBuffer;

import android.util.Log;

public class ABuffer {

	private final String TAG = "ABuffer" ;
	
	private ABuffer(long self , int type , int time , int cap ,int act_size , ByteBuffer data)
	{
		mSelf = self;
		mDataType = type;
		mTimestamp = time ;
		mCaptical = cap;
		mActualSize = act_size;
		mData = data;
	}
	
	public long mSelf ; // Native Object
	public int mDataType;
	public int mTimestamp;
	public int mCaptical ;
	public int mActualSize ;
	public ByteBuffer mData ;


	public static native long native_malloc(int size);
	public static native void native_free(long ptr);

	public static native long native_new(int size);
	public static native void native_del(long ptr);

	public void release(){
		native_release(mSelf);
		mSelf = 0 ;
	}
	
	private native void native_release(long self);
	

	@Override
	protected void finalize() throws Throwable {
		Log.d(TAG, "finalize = " + Long.toHexString(mSelf));
		if( mSelf != 0 ){
			native_release(mSelf);
			mSelf = 0 ;
		}
		super.finalize();
	}




	static {
		System.loadLibrary("Abuffer");
	}
}
