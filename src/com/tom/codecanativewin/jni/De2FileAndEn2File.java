package com.tom.codecanativewin.jni;

import android.view.Surface;

public class De2FileAndEn2File {

	native public void decodeAndEncode(Surface surface);

	private long mNativeContext = 0; // for 64bits
	
	static {
		System.loadLibrary("DeFileAndEnCode");
	}
}
