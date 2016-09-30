
#include <jni.h>
#include <rs/cpp/util/RefBase.h>
#include <android/native_window_jni.h> // ANativeWindow

#include <fcntl.h>
#include <pthread.h>
#include <errno.h>
#include <sys/mman.h>
#include <stdlib.h>

#include <media/NdkMediaCodec.h>
#include <media/NdkMediaFormat.h>
#include <media/NdkMediaExtractor.h>

#include <list>


#define LOG_TAG "jni_h264"
#include "vortex.h"
#include "native_msg.h"


#define JAVA_CLASS_PATH "com/tom/codecanativewin/jni/DecodeH264"
#define TEST_H264_FILE "/mnt/sdcard/client.h264"
#define	H264_FILE  			3
#define DECODEOUT_THREAD 	1

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


static jboolean checkCallbackThread(JavaVM* vm , JNIEnv* isTargetEnv) {
	JNIEnv* currentThreadEnv = NULL;
    if ( vm->GetEnv( (void**) &currentThreadEnv, JNI_VERSION_1_4) != JNI_OK) {
    	return JNI_FALSE;
    }

    if (isTargetEnv != currentThreadEnv || isTargetEnv == NULL) {
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

static void AttachThread2JVM( JavaVM* vm , JNIEnv** ppEnv ,/* out */
									const char* const threadName)
{
	JavaVMAttachArgs args;
	args.version = JNI_VERSION_1_4;
	args.name = threadName ;
	args.group = NULL;
	vm->AttachCurrentThread(ppEnv, &args);
}

static void DetachThread2JVM(JavaVM* vm  , JNIEnv*pEnv /* in */ )
{
	if (!checkCallbackThread(vm, pEnv)) {
		return;
    }
	vm->DetachCurrentThread();

}

int64_t system_nanotime()
{
    timespec now;
    clock_gettime(CLOCK_MONOTONIC, &now);
    return now.tv_sec * 1000000000LL + now.tv_nsec;
}

// ----------------------------------------------------------------------------------


struct cnw_java_fields_t {
    jfieldID    context; 	// NOT IN USED
    jmethodID   post_event;
} g_java_fields;

typedef struct{
	int msg_type ;
	int arg1 ;
	int arg2 ;
	void* ptr ;
} JNINativeMsg ;


class NativeContext
{
public:
	JavaVM* mJvm ;
	ANativeWindow* mpSurfaceWindow;
	int mFd ;
	pthread_t mDeocdeTh  ;
	bool mforceClose = false;

	AMediaCodec* mDecoder = NULL;

	jclass mJavaClass ;
	jobject mJavaThizWef ;
	pthread_t mEventLoopTh ;
	pthread_mutex_t mNativeEventMutex ;
	pthread_cond_t mNativeEventCond ;
	jboolean mEventLoopExit ;
	std::list<JNINativeMsg*> mEventList ;


	pthread_t mDeocdeOutTh  ;

};

static void* cbEventThread(void* argv)
{
	JNIEnv* jenv = NULL;
	NativeContext *pData = (NativeContext *)argv;
	AttachThread2JVM( pData->mJvm , &jenv , "cbEventThread");


	ALOGD("cbEventThread loop enter");

	while( ! pData->mEventLoopExit ){

		JNINativeMsg* msg = NULL ;
		pthread_mutex_lock(&pData->mNativeEventMutex);
		if( pData->mEventList.empty() == false )
		{

			msg = pData->mEventList.front();
			pData->mEventList.pop_front();
			ALOGD("cbEventThread pop one msg = %p", msg);
		}else{
			ALOGD("cbEventThread loop wait");
			pthread_cond_wait(&pData->mNativeEventCond , &pData->mNativeEventMutex );
			ALOGD("cbEventThread loop wait done ");
		}
		pthread_mutex_unlock(&pData->mNativeEventMutex);

		if( msg != NULL ){

			ALOGD("cbEventThread loop process msg");

			switch( msg->msg_type ){
				case MEDIA_BUFFER_DATA :
					{
						jobject objdir = jenv->NewDirectByteBuffer(  (void*)msg->ptr ,  (jlong)msg->arg1);
						jenv->CallStaticVoidMethod(pData->mJavaClass, g_java_fields.post_event, pData->mJavaThizWef ,
								msg->msg_type, msg->arg1, msg->arg2, objdir);

						jenv->DeleteLocalRef(objdir);
					}
					break;

				case MEDIA_TIME_UPDATE:
				{
					ALOGD("loop MEDIA_TIME_UPDATE");
					jenv->CallStaticVoidMethod(pData->mJavaClass, g_java_fields.post_event, pData->mJavaThizWef ,
							MEDIA_TIME_UPDATE, msg->arg1, msg->arg2, NULL);
				}
				break;
				case THREAD_STOPED:
					jenv->CallStaticVoidMethod(pData->mJavaClass, g_java_fields.post_event, pData->mJavaThizWef ,
							THREAD_STOPED, 0, 0, NULL);
					pData->mEventLoopExit = true ;
					break;
				default:
					jenv->CallStaticVoidMethod(pData->mJavaClass, g_java_fields.post_event, pData->mJavaThizWef ,
							msg->msg_type, msg->arg1, msg->arg2, NULL);
					break;
			}

			delete msg ; // allocate(new) by sendCallbackEvent
		}
	}

	ALOGD("cbEventThread loop exit");

	DetachThread2JVM( pData->mJvm , jenv);
	return NULL;
}


static void sendCallbackEvent( void * platform_data ,  int msg_type , int arg1 , int arg2,  void* ptr )
{
	NativeContext* pData = (NativeContext*)platform_data ;

	JNINativeMsg* pmsg = new JNINativeMsg();
	pmsg->msg_type = msg_type;
	pmsg->arg1 = arg1;
	pmsg->arg2 = arg2;
	pmsg->ptr = ptr;


	pthread_mutex_lock(&pData->mNativeEventMutex);
	bool old_empty = pData->mEventList.empty() ;
	pData->mEventList.push_back(pmsg);
	if( old_empty ){
		ALOGD("old_empty");
		pthread_cond_signal(&pData->mNativeEventCond);
	}
	pthread_mutex_unlock(&pData->mNativeEventMutex);

}

static void cbEventThCreate( void * platform_data )
{
	NativeContext* pData = (NativeContext*)platform_data ;

	// create EventLoop Thread
	pData->mEventLoopTh = -1 ;
	pData->mEventLoopExit = false ;
	pthread_mutex_init(&pData->mNativeEventMutex, NULL );
	pthread_cond_init(&pData->mNativeEventCond , NULL);
	int ret = ::pthread_create(&pData->mEventLoopTh, NULL, ::cbEventThread, (void*)platform_data );
	if( ret != 0 ){
		// dump Error Info.
	}
	return ;
}

static void cbEventThExit(  void *  platform_data )
{
	NativeContext* pData = (NativeContext*)platform_data ;

	if( pData->mEventLoopTh != -1 ){
		pData->mEventLoopExit = true ; // drop all the pending message in the list
		sendCallbackEvent(platform_data,THREAD_LOOP_END , 0 , 0 , NULL);
		::pthread_join(pData->mEventLoopTh , NULL);
		pthread_mutex_destroy(&pData->mNativeEventMutex);
		pthread_cond_destroy(&pData->mNativeEventCond);
	}
}




#if DECODEOUT_THREAD
static void* decodeOuth264_thread(void* argv)
{
	NativeContext* ctx = (NativeContext*)argv;
	AMediaCodec* decoder  = ctx->mDecoder ;
	bool sawOutputEOS = false ;

	while( !sawOutputEOS && !ctx->mforceClose ){
		if (!sawOutputEOS) {
			AMediaCodecBufferInfo info;
			ssize_t status = AMediaCodec_dequeueOutputBuffer(decoder, &info, 50000); // 50ms
			if (status >= 0) {
				if (info.flags & AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM) {
					ALOGD("decode output EOS");
					sawOutputEOS = true;
				}

				sendCallbackEvent((void*)ctx , MEDIA_TIME_UPDATE , (int)(info.presentationTimeUs/1000/1000),  info.size , NULL);

				// render = true 如果configure时候配置了surface 就render到surface上
				AMediaCodec_releaseOutputBuffer(decoder, status, info.size != 0);
			} else if (status == AMEDIACODEC_INFO_OUTPUT_BUFFERS_CHANGED) {
				ALOGD("output buffers changed");
			} else if (status == AMEDIACODEC_INFO_OUTPUT_FORMAT_CHANGED) {
				AMediaFormat *format = NULL;
				format = AMediaCodec_getOutputFormat(decoder);
				ALOGD("format changed to: %s", AMediaFormat_toString(format));
				AMediaFormat_delete(format);
			} else if (status == AMEDIACODEC_INFO_TRY_AGAIN_LATER) {
				//ALOGD("no output buffer right now, try again later ");
			} else {
				ALOGD("unexpected info code: %d", status);
			}
		}
	}
}
#endif


static void* decodeh264_thread(void* argv)
{
	NativeContext* ctx = (NativeContext*)argv;
	JNIEnv* decodeh264_env ;

	do{
		int file_size = 0 ;
		int sample_count = 0 ;
		if( ctx->mFd  < 0 ){
				ALOGE("open fail %d %s " , ctx->mFd , strerror(errno) );
				break;
		}
		file_size = lseek(ctx->mFd, 0, SEEK_END);
		int cur = lseek(ctx->mFd, 0, SEEK_SET);
		ALOGD("file_size %d byte cur %d " , file_size , cur );

		unsigned char * filemap = (unsigned char*)mmap(NULL , file_size  ,  PROT_READ , MAP_SHARED  , ctx->mFd,  0);
		unsigned char * pCur = filemap ;
		unsigned char * pEnd = filemap + file_size - 1 ;
		bool sawInputEOS = false, sawOutputEOS = false ;

		if (file_size < 4 ||  *pCur!=0 || *(pCur+1)!=0 || *(pCur+2) != 0 || *(pCur+3)!=1 ){
			break;
		}

		while( (!sawInputEOS || !sawOutputEOS) && !ctx->mforceClose ){
			ssize_t bufidx = -1;
			if (!sawInputEOS) {
			        bufidx = AMediaCodec_dequeueInputBuffer(ctx->mDecoder, 50000);
			        if (bufidx >= 0) {
			        	++sample_count ;
			            size_t bufsize;// buffer的大小
			            uint8_t *buf = AMediaCodec_getInputBuffer(ctx->mDecoder, bufidx, &bufsize);

			            //  在这里读取文件
			            int sampleSize = 0 ;
			            unsigned char* p = pCur + 4 ;
			            while( *p!=0 || *(p+1)!=0 || *(p+2) != 0 || *(p+3)!=1 ){
			            	p++;
			            	if( p + 3 >= pEnd ) {
			            		p = pEnd ;
			            		sawInputEOS = true ;
			            		break;
			            	}
			            }
			            sampleSize = p - pCur ;
			            if( bufsize < sampleSize){
			            	ALOGE("MediaCodec Buffer Size is too Small break!");
			            	break;
			            }
			            memcpy( buf , pCur, sampleSize );
			            pCur = p ;

		    			struct timeval cur_time;
			    		gettimeofday(&cur_time, NULL);
			    		uint64_t presentationTimeUs = cur_time.tv_sec * 1000UL * 1000UL + cur_time.tv_usec ;
				        AMediaCodec_queueInputBuffer(ctx->mDecoder,
				        								bufidx,
				        								0,
				        								sampleSize,
				        								presentationTimeUs,
				        								sawInputEOS ? AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM : 0);

#if 1
				        usleep(30000);
#else
				       	if( sample_count % 100 == 0 ){
				       		int sleepRand = rand()%10 ;
				    		ALOGD("sleep %d " ,  sleepRand );
				    		sleep(sleepRand);
				    	 }
#endif

			        }else{
			        		//ALOGD("no input buffer right now, bufidx = %d " , bufidx);
			        }
			}


#if DECODEOUT_THREAD

#else
			if (!sawOutputEOS) {
				AMediaCodecBufferInfo info;
				ssize_t status = AMediaCodec_dequeueOutputBuffer(ctx->mDecoder, &info, 0);
				if (status >= 0) {
						if (info.flags & AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM) {
							ALOGD("decode output EOS");
							sawOutputEOS = true;
						}
						// 显示???
						AMediaCodec_releaseOutputBuffer(ctx->mDecoder, status, info.size != 0); // render = true 如果configure时候配置了surface 就render到surface上
				} else if (status == AMEDIACODEC_INFO_OUTPUT_BUFFERS_CHANGED) {
					ALOGD("output buffers changed");
				} else if (status == AMEDIACODEC_INFO_OUTPUT_FORMAT_CHANGED) {
					AMediaFormat *format = NULL;
					format = AMediaCodec_getOutputFormat(ctx->mDecoder);
					ALOGD("format changed to: %s", AMediaFormat_toString(format));
					AMediaFormat_delete(format);
				} else if (status == AMEDIACODEC_INFO_TRY_AGAIN_LATER) {
					//ALOGD("no output buffer right now, try again later ");
				} else {
					ALOGD("unexpected info code: %zd", status);
				}
			}
#endif

		}

		munmap(filemap ,file_size );

	}while(0);

	return NULL;
}


JNIEXPORT void JNICALL native_start(JNIEnv * env , jobject nwc_jobj , jlong ctx , jobject objSurface , jobject weak_ref )
{
	int ret ;
	char result[256];

	ALOGD("ctx = %p" , (NativeContext*)ctx);
	NativeContext* pData = (NativeContext*)ctx ;


	pData->mpSurfaceWindow = ANativeWindow_fromSurface(env, objSurface);
	pData->mJavaThizWef = env->NewGlobalRef(weak_ref); // for callback event

	do{
		ALOGD("open %s", TEST_H264_FILE);
		int tempFd = open(TEST_H264_FILE , O_RDONLY);
		if( tempFd < 0){
			snprintf(result, sizeof(result), "Can Not Open File %s with %s!", TEST_H264_FILE , strerror(errno));
			ALOGE("%s",result);
			jniThrowRuntimeException(env, result );
			return ;
		}else{
			pData->mFd = tempFd;
		}

		pData->mDecoder = AMediaCodec_createDecoderByType("video/avc");
		if (NULL == pData->mDecoder) {
			ALOGE("AMediaCodec_createEncoderByType fail\n");
			break;
		}

		AMediaFormat *format  = AMediaFormat_new();
		if(format == NULL){
			ALOGE("AMediaFormat_new fail\n");
			break;
		}
		AMediaFormat_setString(format, AMEDIAFORMAT_KEY_MIME, "video/avc");
		AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_WIDTH, 1280);
		AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_HEIGHT, 960);
		AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_MAX_INPUT_SIZE, 17449 );
		AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_MAX_WIDTH, 656);
		AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_MAX_HEIGHT, 936 );

		unsigned char sps[] = {
	#if H264_FILE == 1  // mp4
				0x00,0x00,0x00,0x01,
				0x67,0x4d,0x40,0x1e,
				0xe8,0x80,0xf0,0x53,
				0x42,0x00,0x05,0x17,
				0x62,0x01,0x31,0x2d,
				0x01,0x1e,0x2c,0x5a,
				0x24,
	#elif H264_FILE == 2 // 3gp
				0x00,0x00,0x00,0x01, // 如果发送rtp包的话  要跟IDR帧那样  去掉 00 00 00 01
				0x67,0x42,0xc0,0x16,
				0xda,0x02,0x80,0xf6,
				0xff,0xc0,0x01,0x00,
				0x00,0xc4,0x00,0x00,
				0x03,0x00,0x04,0x00,
				0x00,0x03,0x00,0x78,
				0x3c,0x58,0xba,0x80,
	#elif H264_FILE == 3 // 机器
				0x00,0x00,0x00,0x01,
				0x67,0x42,0x00,0x29,
				0x8d,0x8d,0x40,0x28,
				0x03,0xcd,0x00,0xf0,
				0x88,0x45,0x38,
	#endif
		};
		AMediaFormat_setBuffer(format , "csd-0" , sps , sizeof(sps) );	// sps

		unsigned char pps[] = {
	#if H264_FILE == 1
				0x00,0x00,0x00,0x01,
				0x68,0xeb,0xec,0x4c,
				0x80,
	#elif H264_FILE == 2
				0x00,0x00,0x00,0x01, // 如果发送rtp包的话  要跟IDR帧那样  去掉 00 00 00 01
				0x68,0xce,0x3c,0x80,

	#elif H264_FILE == 3
				0x00,0x00,0x00,0x01,
				0x68,0xca,0x43,0xc8,
	#endif
		};

		AMediaFormat_setBuffer(format , "csd-1" , pps , sizeof(pps) ); 	// pps

		const char *sformat = AMediaFormat_toString(format);
		ALOGD("decode format: %s", sformat);
		media_status_t status ;
		status = AMediaCodec_configure(pData->mDecoder, format, pData->mpSurfaceWindow, NULL, 0);
		if (status != AMEDIA_OK) {
			ALOGE ("HWENCODE AMediaCodec_configure res<%d>\n",ret);
			break;
		}

		status = AMediaCodec_start(pData->mDecoder);
		if (status != AMEDIA_OK) {
			ALOGE ("HWENCODE AMediaCodec_start res<%d>\n",ret);
			break;
		}

		if (NULL != format) {
			AMediaFormat_delete(format);
		} else {
			ALOGE("HWENCODE format id NULL..\n");
			break;
		}
	}while(0);

	if( pData->mFd == -1 || pData->mDecoder == NULL){
		if(pData->mFd >=0 ) {
			close(pData->mFd);
		}
		if(pData->mDecoder !=0 )  {
			AMediaCodec_stop(pData->mDecoder);
			AMediaCodec_delete(pData->mDecoder);
		}
		ALOGE("open file or create decoder ERROR");
		return ;
	}

	// 创建回调事件线程
	cbEventThCreate(pData);

	// 创建解码输入线程
	ret  = ::pthread_create(&pData->mDeocdeTh, NULL, ::decodeh264_thread, pData );
	if(  ret != 0 ){

		snprintf(result, sizeof(result), "decodeOuth264_thread create error! with %s %d %d", strerror(errno),errno, ret );
		ALOGE("%s",result);

		jstring post_event =  env->NewStringUTF("decodeOuth264_thread create error");
		env->CallVoidMethod(nwc_jobj, g_java_fields.post_event, THREAD_EXCEPTION , 0, 0 , post_event);
		env->DeleteLocalRef(post_event);
		goto ERROR;
	}


#if DECODEOUT_THREAD
	// 创建解码输出线程
	ret = ::pthread_create(&pData->mDeocdeOutTh, NULL, ::decodeOuth264_thread, pData );
	if( ret < 0){

		snprintf(result, sizeof(result), "decodeOuth264_thread create error! with %s %d %d", strerror(errno),errno, ret );
		ALOGE("%s",result);

		jstring post_event =  env->NewStringUTF("decodeOuth264_thread create error");
		env->CallVoidMethod(nwc_jobj, g_java_fields.post_event, THREAD_EXCEPTION , 0, 0 , post_event);
		env->DeleteLocalRef(post_event);
		ALOGE("decodeOuth264_thread create error!");
	}
#endif

	return ;
ERROR:

	pData->mforceClose = true ;

	if(pData->mDeocdeOutTh != -1){
		pthread_join(pData->mDeocdeOutTh,NULL);
	}

	if(pData->mDeocdeTh != -1){
		pthread_join(pData->mDeocdeTh,NULL);
	}


	cbEventThExit(pData);


	if(pData->mDecoder != NULL){
		AMediaCodec_stop(pData->mDecoder);
		AMediaCodec_delete(pData->mDecoder);
		pData->mDecoder = NULL;
	}

	ANativeWindow_release(pData->mpSurfaceWindow);
	if(pData->mFd >=0 ){ close(pData->mFd) ;  pData->mFd=-1; }

	env->DeleteGlobalRef(pData->mJavaThizWef);

	jniThrowRuntimeException(env, result);

	return ;
}




JNIEXPORT void JNICALL native_stop(JNIEnv * env , jobject jobj , jlong ctx )
{
	NativeContext* pData = (NativeContext*)ctx ;
	if( pData != NULL){
		pData->mforceClose = true ;

		if(pData->mDeocdeOutTh != -1){
			pthread_join(pData->mDeocdeOutTh,NULL);
		}

		if(pData->mDeocdeTh != -1){
			pthread_join(pData->mDeocdeTh,NULL);
		}

		cbEventThExit(pData);

		if(pData->mDecoder != NULL){
			AMediaCodec_stop(pData->mDecoder);
			AMediaCodec_delete(pData->mDecoder);
			pData->mDecoder = NULL;
		}

		ANativeWindow_release(pData->mpSurfaceWindow);
		if(pData->mFd >=0 ){ close(pData->mFd) ;  pData->mFd=-1; }

		env->DeleteGlobalRef(pData->mJavaThizWef);

	}else{
		ALOGE("Native STOP Before");
	}
}


JNIEXPORT long native_setup(JNIEnv * env , jobject jobj)
{
	NativeContext* pData = new NativeContext();
	env->GetJavaVM(&pData->mJvm);

	pData->mpSurfaceWindow = NULL;
	pData->mFd = -1 ;
	pData->mDeocdeTh = -1 ;
	pData->mforceClose = false ;
	pData->mDecoder = NULL;

	pData->mEventLoopExit = false;

	pData->mDeocdeOutTh = -1 ;

	ALOGD("setup done pData = %p  " , pData);
	return (long)pData ;
}


JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved)
{
    JNIEnv* env ;
    if ( vm->GetEnv( (void**) &env, JNI_VERSION_1_6 )  != JNI_OK) {
    	ALOGE("GetEnv Err");
    	return JNI_ERR;
    }

	jclass clazz;
    clazz = env->FindClass(JAVA_CLASS_PATH );
    if (clazz == NULL) {
		ALOGE("%s:Class Not Found" , JAVA_CLASS_PATH );
		return JNI_ERR ;
    }

    JNINativeMethod method_table[] = {
    	{ "native_setup", "()J", (void*)native_setup },
    	{ "native_start", "(JLandroid/view/Surface;Ljava/lang/Object;)V", (void*)native_start },
    	{ "native_stop",  "(J)V", (void*)native_stop },
    };
	jniRegisterNativeMethods( env, JAVA_CLASS_PATH ,  method_table, NELEM(method_table)) ;

	// NOT IN USED
    //field fields_to_find[] = {
    //   { JAVA_CLASS_PATH , "mNativeContext",  "J", &g_java_fields.context },
    //};

    //find_fields( env , fields_to_find, NELEM(fields_to_find) );


    g_java_fields.post_event = env->GetStaticMethodID(clazz, "postEventFromNative",
                                               "(Ljava/lang/Object;IIILjava/lang/Object;)V");

    if (g_java_fields.post_event == NULL) {
        ALOGE("Can't find android/hardware/Camera.postEventFromNative");
        return -1;
    }

	return JNI_VERSION_1_6 ;
}
