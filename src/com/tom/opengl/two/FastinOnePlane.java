package com.tom.opengl.two;

import java.nio.ByteBuffer;

import android.graphics.ImageFormat;

public class FastinOnePlane {

	static final public int TO_420P = ImageFormat.YUY2;// 420p I420
	static final public int TO_420SP = ImageFormat.NV21 ; // 420sp 
	
	static public boolean convert(ByteBuffer uv_target , ByteBuffer u , ByteBuffer v , int width , int height , int stride , int to){
		if( uv_target.isDirect() && u.isDirect() && v.isDirect() && (stride==1 || stride == 2)){
			return native_convert(uv_target , u , v , width  , height , stride , to );
			  
		}
		return false;
	}
	
	static public boolean convert2(ByteBuffer yuv_target , ByteBuffer y, ByteBuffer u , ByteBuffer v , int width , int height , int stride , int to){
		if( yuv_target.isDirect() && y.isDirect() && u.isDirect() && v.isDirect() && (stride==1 || stride == 2)){
			return native_convert2(yuv_target , y, u , v , width  , height , stride , to );
		}
		return false;
	}
	
	static public native boolean native_convert(ByteBuffer uv_target , ByteBuffer u , ByteBuffer v , int width  , int height , int stride , int to );
	static public native boolean native_convert2(ByteBuffer yuv_target , ByteBuffer y, ByteBuffer u , ByteBuffer v , int width  , int height , int stride , int to );
	
	static{
		System.loadLibrary("fast_convert");
	}
}
