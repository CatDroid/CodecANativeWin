
#include <jni.h>
#include <rs/cpp/util/RefBase.h>
#include <android/native_window_jni.h> // ANativeWindow

#include <fcntl.h>
#include <pthread.h>
#include <errno.h>

#include <media/NdkMediaCodec.h>
#include <media/NdkMediaFormat.h>
#include <media/NdkMediaExtractor.h>

#define LOG_TAG "jni_nwc"
#include "vortex.h"
#define NWC_CLASS_PATH "com/tom/codecanativewin/jni/NativeWinCodec"

enum{
	THREAD_STARTED = 0 ,
	THREAD_STOP = 1
};

struct cnw_java_fields_t {
    jfieldID    context; 	// native object pointer
    jmethodID   post_event; // post event to Java Layer/Callback Function
} g_cnw_java_fields;


int jniThrowException(JNIEnv* env, const char* className, const char* msg) {
    if (env->ExceptionCheck()) {
    	ALOGE("jniThrowException Exception in Exception : Clear and Throw New Excepiion");
    	env->ExceptionClear();
    }

     jclass expclass =  env->FindClass(className );
    if (expclass == NULL) {
        ALOGE("Unable to find exception class %s", className);
        return -1;
    }

    if ( env ->ThrowNew( expclass , msg) != JNI_OK) {
        ALOGE("Failed throwing '%s' '%s'", className, msg);
        return -1;
    }

    return 0;
}
int jniThrowNullPointerException(JNIEnv* env, const char* msg) {
    return jniThrowException(env, "java/lang/NullPointerException", msg);
}

int jniThrowRuntimeException(JNIEnv* env, const char* msg) {
    return jniThrowException(env, "java/lang/RuntimeException", msg);
}

int jniThrowIOException(JNIEnv* env, int errnum) {
    char buffer[80];
    const char* message = strerror(errnum);
    return jniThrowException(env, "java/io/IOException", message);
}



// ----------------------------------------------------------------------------------
class NativeNWC
{
public:
	jobject java_obj_ref ; // Java NativeWinCodec Obj
	ANativeWindow* pSurfaceWindow;
	JavaVM* jvm;
	int fd ;
	pthread_t thread  ;
	bool forceClose = false;
	JNIEnv* jenv ; // 对应一个attach到虚拟机JVM的线程

};

NativeNWC* get_native_nwc(JNIEnv *env, jobject thiz)
{
	NativeNWC* context = reinterpret_cast<NativeNWC*>(env->GetLongField(thiz, g_cnw_java_fields.context));
    if (context != NULL) {
        return context ;
    }else{
    	ALOGI("Native Camera Not Create or Release");
    	return NULL;
    }

}
//--------------------------------------------------------

static jboolean checkCallbackThread(JavaVM* vm , JNIEnv* isTargetEnv) {

	JNIEnv* currentThreadEnv = NULL;
    if ( vm->GetEnv( (void**) &currentThreadEnv, JNI_VERSION_1_6) != JNI_OK) {
    	ALOGE("checkCallbackThread : Can Not Get JENV , please make sure run in Thread attached JVM ");
    	return JNI_FALSE;
    }

    if (isTargetEnv != currentThreadEnv || isTargetEnv == NULL) {
        ALOGE("checkCallbackThread : Not in the Same Thread  current : %p,  target: %p", currentThreadEnv, isTargetEnv);
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

static void AttachDetachThread2JVM( JavaVM* vm ,
									jboolean attach ,
									JNIEnv** ppEnv /* if attach = ture,out; if attach = false,in*/ ) {

    if (attach  == JNI_TRUE) {
        JavaVMAttachArgs args;
        args.version = JNI_VERSION_1_6;
        args.name = "NativeThread";// 这样DDMS中就可以看到有一个线程NativeThread
        args.group = NULL;
        vm->AttachCurrentThread( ppEnv, &args);
        ALOGD("Callback thread attached: %p", *ppEnv);
    } else {
    	if (!checkCallbackThread(vm, *ppEnv)) {
    		ALOGE("Callback: '%s' is not called on the correct thread", __FUNCTION__);
    		return;
        }
    	vm->DetachCurrentThread(); // detachCurrentThread 需要在原来attached的线程中调用
    }
    ALOGD("AttachDetachThread2JVM %s Done" , (attach?"Attach":"Detach"));
}

int64_t system_nanotime()
{
    timespec now;
    clock_gettime(CLOCK_MONOTONIC, &now);
    return now.tv_sec * 1000000000LL + now.tv_nsec;
}


static void* playback_thread(void* argv)
{
	NativeNWC* nwc = (NativeNWC*)argv;
	AttachDetachThread2JVM(nwc->jvm ,true,&nwc->jenv );
	ALOGD("-----1");
	//jobject thread_jobj = nwc->jenv->NewGlobalRef(nwc->java_obj_ref);
	ALOGD("-----2");
	nwc->jenv->CallVoidMethod(nwc->java_obj_ref, g_cnw_java_fields.post_event, THREAD_STARTED , "thread get started");
	ALOGD("-----3");
	AMediaCodec * codec = NULL;
	AMediaExtractor* extract = AMediaExtractor_new();
	media_status_t err = AMediaExtractor_setDataSourceFd(extract, nwc->fd, 0 , LONG_MAX);
	// close(fd);  setDataSourceFd 会dup
	if (err != AMEDIA_OK) {
		ALOGE("setDataSource error: %d", err);
	}

	int numtracks = AMediaExtractor_getTrackCount(extract);

	ALOGD("input has %d tracks", numtracks);
	for (int i = 0; i < numtracks && codec == NULL ; i++) {
		AMediaFormat *format = AMediaExtractor_getTrackFormat(extract, i);
		const char *s = AMediaFormat_toString(format);

		const char *mime;
		bool isMIME = AMediaFormat_getString(format, AMEDIAFORMAT_KEY_MIME, &mime);
		ALOGD("track %d format: %s , mime : %s", i, s , mime);
		if (!isMIME) {
			ALOGD("no mime type");
			continue ;
		} else if (!strncmp(mime, "video/", 6)) {
			int width = 0 , height = 0 ;
			AMediaFormat_getInt32(format, AMEDIAFORMAT_KEY_WIDTH , &width  );
			AMediaFormat_getInt32(format, AMEDIAFORMAT_KEY_WIDTH , &height  );

			int buffwidth = ANativeWindow_getWidth(nwc->pSurfaceWindow) ;
			int buffheight = ANativeWindow_getWidth(nwc->pSurfaceWindow) ;
			AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_WIDTH,buffwidth);
			AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_HEIGHT,buffheight);

			ALOGD("w:h  FileSize( %d : %d ) => BufferSize( %d : %d )" ,
						width  , height , buffwidth , buffheight  );


			AMediaExtractor_selectTrack(extract, i);
			codec = AMediaCodec_createDecoderByType(mime);
			s = AMediaFormat_toString(format);
			ALOGD("codec %d format: %s", i, s);
			AMediaCodec_configure(codec, format, nwc->pSurfaceWindow, NULL, 0);

			AMediaCodec_start(codec);
		}
		AMediaFormat_delete(format);
	}

	if(codec == NULL){
		AMediaExtractor_delete(extract);
		ALOGE("No Video Found !");
		// 退出
	}

	// 循环
	bool sawInputEOS = false, sawOutputEOS = false ;
	int64_t start_vender_time = -1;
	while( (!sawInputEOS || !sawOutputEOS) && !nwc->forceClose ){
		ssize_t bufidx = -1;
	    if (!sawInputEOS) {
	        bufidx = AMediaCodec_dequeueInputBuffer(codec, 2000);
	        ALOGD("input buffer %zd", bufidx);
	        if (bufidx >= 0) {
	            size_t bufsize;// buffer的大小
	            uint8_t *buf = AMediaCodec_getInputBuffer(codec, bufidx, &bufsize);
	            ssize_t sampleSize = AMediaExtractor_readSampleData(extract, buf, bufsize);
	            if (sampleSize < 0) {
	                sampleSize = 0;
	                sawInputEOS = true;
	                ALOGD("file input EOS");
	            }
	            int64_t presentationTimeUs = AMediaExtractor_getSampleTime(extract);

		        AMediaCodec_queueInputBuffer(codec,
			        bufidx,
			        0,
			        sampleSize,
			        presentationTimeUs,
	                sawInputEOS ? AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM : 0);
	            AMediaExtractor_advance(extract);
	        }
	    }

	    if (!sawOutputEOS) {
	        AMediaCodecBufferInfo info;
	        ssize_t status = AMediaCodec_dequeueOutputBuffer(codec, &info, 0);
		    ALOGD("out status %zd", status);
	        if (status >= 0) {
	            if (info.flags & AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM) {
	            	ALOGD("decode output EOS");
	                sawOutputEOS = true;
	            }

	            // 控制播放的速度
	            int64_t presentationNano = info.presentationTimeUs * 1000;
	            int64_t now = system_nanotime() ;
	            if (start_vender_time < 0) {
	            	start_vender_time = now  -  presentationNano;
	            }
	            int64_t delay = (start_vender_time + presentationNano) - now ;
	            if (delay > 0 && ! nwc->forceClose ) {
	            	ALOGD("sleep %ld us " , delay/1000);
	                usleep(delay / 1000);
	            }

	            /*
	             * 	在 AMediaCodec_configure 指定了 SurfaceView/ANativeWindow 这里
	             *  不需要对ANativeWindow进行操作
	             *	ANativeWindow_setBuffersGeometry
	             *	ANativeWindow_lock
	             *	ANativeWindow_unlockAndPost
	             *
	             *  也就是说如果用AMediaCodec的话解码的话 可以不用lock unlockAndPost
	             *  如果直接从Camera过来(直接YUV数据,不需解码) 或者软件解码ffmpeg的话 就需要 lock unlockAndPost
	             *
	             *  MediaCodec这里放进去解码后还要dequeueOutput取出来 是因为要控制速度?? 但是解码后的数据已经放到的ANativeWindow
	             */

	            AMediaCodec_releaseOutputBuffer(codec, status, info.size != 0);

	        } else if (status == AMEDIACODEC_INFO_OUTPUT_BUFFERS_CHANGED) {
	        	ALOGD("output buffers changed");
	        } else if (status == AMEDIACODEC_INFO_OUTPUT_FORMAT_CHANGED) {
	            AMediaFormat *format = NULL;
	            format = AMediaCodec_getOutputFormat(codec);
	            ALOGD("format changed to: %s", AMediaFormat_toString(format));
	            AMediaFormat_delete(format);
	        } else if (status == AMEDIACODEC_INFO_TRY_AGAIN_LATER) {
	        	ALOGD("no output buffer right now");
	        } else {
	        	ALOGD("unexpected info code: %zd", status);
	        }
	    }

	}


	if(codec != NULL){
	    AMediaCodec_stop(codec);
        AMediaCodec_delete(codec);
		ALOGD("AMediaCodec release ");
	}
	if(extract != NULL){
		AMediaExtractor_delete(extract);
		ALOGD("AMediaExtractor");
	}

	nwc->thread = -1 ;
	close(nwc->fd);

	nwc->jenv->CallVoidMethod(nwc->java_obj_ref, g_cnw_java_fields.post_event, THREAD_STOP , "thread is stop");

	ANativeWindow_release(nwc->pSurfaceWindow);
	nwc->jenv->SetLongField(nwc->java_obj_ref ,g_cnw_java_fields.context, 0);
	nwc->jenv->DeleteGlobalRef(nwc->java_obj_ref);

	JavaVM * jvm = nwc->jvm ;
	JNIEnv * env = nwc->jenv ;

	delete nwc;
	AttachDetachThread2JVM(jvm , false, &env);

	return NULL;
}


void createEncoder()
{

	AMediaCodec  *codec = NULL;
	AMediaFormat *format = AMediaFormat_new();
	AMediaFormat_setString(format, AMEDIAFORMAT_KEY_MIME, "video/avc" );
	AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_WIDTH, 1280);
	AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_HEIGHT, 960);
	AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_FRAME_RATE, 15);
	AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_COLOR_FORMAT, 19);//field public static final int COLOR_FormatYUV420Planar = 19;
	AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_BIT_RATE, 2048*2048);
	AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_I_FRAME_INTERVAL, 1);

	const char *s = AMediaFormat_toString(format);

	codec = AMediaCodec_createEncoderByType("video/avc");
	if(NULL == codec)
	{
		ALOGE("AMediaCodec_createEncoderByType Error \n");
	}

	media_status_t ret;
	ret = AMediaCodec_configure(codec, format, NULL, NULL, 1);
	if(ret != AMEDIA_OK){}

	ret = AMediaCodec_start(codec);
	if(ret != AMEDIA_OK){
		ALOGE("AMediaCodec_start ret<%d>\n",ret);}

	if(NULL != format){AMediaFormat_delete(format);}

}

void createDecoder(int32_t width, int32_t height, ANativeWindow *display, char* url)
{
	int IFRAME_INTERVAL = 1; // 5 seconds between I-frames

	AMediaFormat *format = AMediaFormat_new();
	AMediaFormat_setString(format, AMEDIAFORMAT_KEY_MIME, "video/avc");
	AMediaFormat_setInt64(format, AMEDIAFORMAT_KEY_DURATION, 10000000);
	AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_WIDTH, 1280);
	AMediaFormat_setInt32(format,AMEDIAFORMAT_KEY_HEIGHT, 720);
	AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_MAX_INPUT_SIZE, 1572864);
	AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_FRAME_RATE, 24);
	AMediaFormat_setInt32(format, "isDMCMMExtractor", 1);
    const char *s = AMediaFormat_toString(format);
	ALOGD("track format: %s", s);

	AMediaCodec* codec = AMediaCodec_createDecoderByType("video/avc");//h264 decoder
	AMediaCodec_configure(codec, format, display, NULL, 0); // 把ANativeWindow给到ndkMediaCodec之后就不能lock ANativeWindow
	AMediaCodec_start(codec);
	if(NULL != format){
		AMediaFormat_delete(format);
   }

}
//--------------------------------------------------------
JNIEXPORT void JNICALL native_setAndplay(JNIEnv * env , jobject nwc_jobj , jstring jstr , jobject objSurface )
{
	char result[256];
	NativeNWC* nwc = get_native_nwc(env,nwc_jobj);
	if( nwc != NULL){
		// 重复调用
		ALOGE("Can Not setAndPlay running !");
		return ;
	}else{
		const char* path = env->GetStringUTFChars(jstr,NULL);
		int tempFd = open(path , O_RDONLY);
		if( tempFd < 0){
			snprintf(result, sizeof(result), "Can Not Open File %s with %s!", path , strerror(errno));
			ALOGE("%s",result);
			jniThrowRuntimeException(env, result );
			return ;
		}
		env->ReleaseStringUTFChars(jstr,path);

		NativeNWC* nwc = new NativeNWC();
		nwc->thread = -1 ;
		nwc->pSurfaceWindow = ANativeWindow_fromSurface(env, objSurface);
		nwc->java_obj_ref = env->NewGlobalRef(nwc_jobj);
		nwc->fd = tempFd;
		env->GetJavaVM(&nwc->jvm);
		env->SetLongField(nwc_jobj ,g_cnw_java_fields.context,  (long)nwc);


		// 创建线程
		int ret = pthread_create(&nwc->thread, NULL, playback_thread, nwc );
		if(  ret != 0 ){


			env->CallVoidMethod(nwc->java_obj_ref, g_cnw_java_fields.post_event, THREAD_STOP , "thread is stop");


			ANativeWindow_release(nwc->pSurfaceWindow);
			close(nwc->fd); nwc->fd = -1 ;
			env->DeleteGlobalRef(nwc->java_obj_ref);
			env->SetLongField(nwc->java_obj_ref ,g_cnw_java_fields.context, 0);

			// android/libnativehelper
			snprintf(result, sizeof(result), "Can Not Create Thread %s with %s %d!", path , strerror(errno),ret);
			ALOGE("%s",result);
			// APP_CFLAGS += -Wno-error=format-security
			//
			jniThrowRuntimeException(env, result);
			return ;
		}
	}
}

JNIEXPORT void JNICALL native_stop(JNIEnv * env , jobject nwc_jobj )
{
	NativeNWC* nwc = get_native_nwc(env,nwc_jobj);
	if( nwc != NULL){

		if( nwc->thread != -1 ){
			nwc->forceClose = true;
			pthread_join(nwc->thread , NULL);
			nwc->thread = -1 ;
			ALOGD("Child Thread Close");
		}
	}else{
		ALOGE("Native STOP Before");
	}
}

JNIEXPORT void JNICALL native_testLockANWwithCodec(JNIEnv * env , jobject nwc_jobj )
{
	NativeNWC* nwc = get_native_nwc(env,nwc_jobj);
	if( nwc != NULL){
		if (NULL != nwc->pSurfaceWindow )
		{
			ALOGD("Try to LOCK ANativeWindow ");
			ANativeWindow_Buffer buffer;
			if ( ANativeWindow_lock(nwc->pSurfaceWindow, &buffer, NULL) == 0){
				ALOGD("Try to LOCK ANativeWindow OK");
				ANativeWindow_unlockAndPost(nwc->pSurfaceWindow);
			}else{
				ALOGE("Try to LOCK ANativeWindow Fail");
			}
			// 如果把ANativeWindow 在 configure给ndkMediaCodec 就不能再lockANativeWindow
			// AMediaCodec_configure(codec, format, nwc->pSurfaceWindow, NULL, 0);

		}
	}else{
		ALOGE("Native STOP Before");
	}
}



JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved)
{
    JNIEnv* env ;
    if ( vm->GetEnv( (void**) &env, JNI_VERSION_1_6 )  != JNI_OK) {
    	ALOGE("GetEnv Err");
    	return JNI_ERR;
    }

	jclass clazz;
    clazz = env->FindClass(NWC_CLASS_PATH );
    if (clazz == NULL) {
		ALOGE("%s:Class Not Found" , NWC_CLASS_PATH );
		return JNI_ERR ;
    }

    JNINativeMethod method_table[] = {
		/*
		 * Method:    setAndplay
		 * Signature: (Ljava/lang/String;Ljava/lang/Object;)V
		 */
    	{ "setAndplay", "(Ljava/lang/String;Landroid/view/Surface;)V", (void*)native_setAndplay },
    	/*
    	 * Method:    stop
    	 * Signature: ()V
    	 */
    	{ "stop","()V", (void*)native_stop },


    	{ "testLockANWwithCodec","()V", (void*)native_testLockANWwithCodec },

    };
	jniRegisterNativeMethods( env, NWC_CLASS_PATH ,  method_table, NELEM(method_table)) ;

	// 查找Java对应field属性
    field fields_to_find[] = {
        { NWC_CLASS_PATH , "mNativeContext",  "J", &g_cnw_java_fields.context },
    };

    find_fields( env , fields_to_find, NELEM(fields_to_find) );

    // 查找Java对应method方法
    g_cnw_java_fields.post_event = env->GetMethodID(clazz,
    											"postEventFromNative",
    			 								"(ILjava/lang/String;)V");

    if (g_cnw_java_fields.post_event == NULL) {
        ALOGE("Can't find android/hardware/Camera.postEventFromNative");
        return -1;
    }

	return JNI_VERSION_1_6 ;
}
