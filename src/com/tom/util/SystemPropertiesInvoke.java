package com.tom.util;

import java.lang.reflect.Method;
import android.util.Log;

public class SystemPropertiesInvoke {

	private static final String TAG = "SystemPropertiesInvoke";
    private static Method getLongMethod = null;
    private static Method getBooleanMethod = null;
    private static Method getStringMethod = null;
    
    public static long getLong(final String key, final long def) {
        try {
            if (getLongMethod == null) {
                getLongMethod = Class.forName("android.os.SystemProperties")
                        .getMethod("getLong", String.class, long.class);
            }
            
            return ((Long) getLongMethod.invoke(null, key, def)).longValue();
        } catch (Exception e) {
            Log.e(TAG, "Platform error: " + e.toString());
            return def;
        }
    }
    
    public static String getString(final String key, final String def) {
        try {
            if (getStringMethod == null) {
            	getStringMethod = Class.forName("android.os.SystemProperties")
                        .getMethod("get", String.class, String.class);
            }
            
            return (String) getStringMethod.invoke(null, key, def) ;
        } catch (Exception e) {
            Log.e(TAG, "Platform error: " + e.toString());
            return def;
        }
    }
    
    
    public static boolean getBoolean(final String key, final boolean def) {
        try {
            if (getBooleanMethod == null) {
                getBooleanMethod = Class.forName("android.os.SystemProperties")
                        .getMethod("getBoolean", String.class,boolean.class);
            }
          
           //Log.i(TAG,"getBoolean:"+"key:"+key+" def:"+def); 
           //Log.i(TAG,"getBoolean:"+getBooleanMethod.invoke(null, key, def)); 
            
            return (Boolean)getBooleanMethod.invoke(null, key, def);
        } catch (Exception e) {
            Log.e(TAG, "Platform error: " + e.toString());
            return def;
        }
    }
    
}
