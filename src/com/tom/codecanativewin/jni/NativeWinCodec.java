package com.tom.codecanativewin.jni;

import com.tom.entry.MainActivity;

import android.util.Log;
import android.view.Surface;

public class NativeWinCodec {

	final public static String TAG = MainActivity.TAG;

	native public void setAndplay(String fileFullpath, Surface surface);

	native public void testLockANWwithCodec(); // 把ANativeWindow给到ndkMediaCodec之后
												// 能否再ANativeWindow_lock

	native public void stop();

	interface iEventFromNative {
		void onEventFromNativeCallback(int what, String msg);
	}

	private iEventFromNative mListenr = null;

	public void setEventCallback(iEventFromNative i) {
		mListenr = i;
	}

	private void postEventFromNative(int what, String msg) {
		Log.d(TAG, "postEventFromNative " + what + " " + msg);
		if (mListenr != null) {
			mListenr.onEventFromNativeCallback(what, msg);
		}
	}

	private long mNativeContext = 0; // for 64bits

	static {
		System.loadLibrary("CodecANativeWin");
	}
}
