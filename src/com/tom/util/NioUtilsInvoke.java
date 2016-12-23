package com.tom.util;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;

import android.util.Log;

public class NioUtilsInvoke {

	
	private static final String TAG = "NioUtilsInvoke";
    private static Method unsafeArrayMethod = null;
    private static Method unsafeArrayOffsetMethod = null;
    
    
    /*
      
     
		public static byte[] unsafeArray(ByteBuffer b) {
			return ((ByteArrayBuffer) b).backingArray;
		}
		
	    public static int unsafeArrayOffset(ByteBuffer b) {
	        return ((ByteArrayBuffer) b).arrayOffset;
	    }
	    
     */
    
    public static byte[] unsafeArray(ByteBuffer b) {
        try {
            if (unsafeArrayMethod == null) {
            	/*
            	 *	调用getMethods方法输出的是自身的public方法和父类Object的public方法
            	 * 	调用getDeclaredMethods方法输出的是自身的public、protected、private方法
            	 */
            	unsafeArrayMethod = Class.forName("java.nio.NioUtils")
                        .getMethod("unsafeArray", java.nio.ByteBuffer.class );
            }
            
            return (byte[]) unsafeArrayMethod.invoke(null,b);
        } catch (Exception e) {
            Log.e(TAG, "Platform error: " + e.toString());
            return null;
        }
    }
    
    public static int unsafeArrayOffset(ByteBuffer b) {
        try {
            if (unsafeArrayOffsetMethod == null) {
      
            	unsafeArrayOffsetMethod = Class.forName("java.nio.NioUtils")
                        .getMethod("unsafeArrayOffset", java.nio.ByteBuffer.class );
            }
            
            return (int) unsafeArrayOffsetMethod.invoke(null,b);
        } catch (Exception e) {
            Log.e(TAG, "Platform error: " + e.toString());
            return -1;
        }
    }
    
}
