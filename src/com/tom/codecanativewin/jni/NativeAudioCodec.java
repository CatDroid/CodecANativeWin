package com.tom.codecanativewin.jni;

import android.view.Surface;

public class NativeAudioCodec {

	native public boolean decodeAudioFile(Surface surface);
	native public void decodeForceClose();
	
	private long mNativeContext = 0; // for 64bits

	static {
		System.loadLibrary("CodecAudio");
	}
}
