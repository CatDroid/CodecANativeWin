package com.tom.codecanativewin.jni;

import java.nio.ByteBuffer;

public class ABuffer {

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

	
	public void release(){
		native_release(mSelf);
	}
	
	private native void native_release(long self);
	
	static {
		System.loadLibrary("Abuffer");
	}
}
