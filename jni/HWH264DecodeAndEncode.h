#ifndef HW_H264_DECODE_AND_ENCODE_H
#define HW_H264_DECODE_AND_ENCODE_H


#include <stdarg.h>
#include "media/NdkMediaCodec.h"
#include "media/NdkMediaFormat.h"
#include "media/NdkMediaExtractor.h"
#include <android/native_window_jni.h>
#include <stdio.h>
#include <pthread.h>

class Hwh264DecodeAndEncode
{
	public:
		Hwh264DecodeAndEncode();
		~Hwh264DecodeAndEncode();

		int Decode( int32_t width, int32_t height, ANativeWindow *display, char* url );
		void setEOF(bool flag);
		void createCodecFormat(int width, int height, ANativeWindow *window, char* url);
		void DecodeMediaFile();
	private:
		
		int64_t systemnanotime();

	private:
		ANativeWindow      *window;

		int64_t            renderstart;
		AMediaExtractor    *extract;
		bool             sawInputEOS;
		bool             sawOutputEOS;
		bool             flagEOF;
	    void 				*mutex;
		int 				 num;
		unsigned             alltime;
		AMediaCodec			*mEncode;
		AMediaCodec         *mDecode;
		pthread_t			mDecodeTh;
		pthread_t			mEncodeTh;
		int64_t			mStartPlayNs ;
		int64_t			mStartDecodeTimeMs ;
		int64_t			mStartEncodeTimeMs ;
		int64_t		 	mExtractFrameCount ;
		int64_t			mDecodeFrameCount ;
		int64_t			mEncodeFrameCount ;
		void*			mCodecComplete ;
		void*			mPauseExtractor ;
		int 			mFd ;
	public:
		static void*  encode_thread(void* argv);
		static void*  decode_thread(void* argv);

typedef int ( * callbackftn ) ( int a ,int b) ;
		callbackftn mCallbackFtn;
		void setCallbackftn(callbackftn ftn);

};


#endif
