package com.tom.codecanativewin.jni;

import android.graphics.Bitmap;

/**
 * Created by hl.he on 2017/8/28.
 */

public class JBitmap {

    static
    {
        System.loadLibrary("JniBitmap");
    }

    /**
     * rotates a bitmap by 90 degrees counter-clockwise . <br/>
     * notes:<br/>
     * -the input bitmap will be recycled and shouldn't be used anymore <br/>
     * -returns the rotated bitmap . <br/>
     * -could take some time , so do the operation in a new thread
     */
    static public native Bitmap rotateBitmapCcw90(Bitmap bitmap);

}
