

#include <fcntl.h>
#include <pthread.h>
#include <errno.h>
#include <sys/mman.h>
#include <stdlib.h>
#include <media/NdkMediaCodec.h>
#include <media/NdkMediaFormat.h>
#include <media/NdkMediaExtractor.h>
#include <android/native_window_jni.h> // ANativeWindow

#include "HWH264DecodeAndEncode.h"

#define LOG_TAG "jni_deen"
#include "vortex.h"
#define De2FileAndEn2File_CLASS_PATH "com/tom/codecanativewin/jni/De2FileAndEn2File"

class JNIContext
{
public:
	jobject java_obj_ref ; // Java NativeWinCodec Obj
	ANativeWindow* pSurfaceWindow;
	JavaVM* jvm;
	int fd ;
	pthread_t thread  ;
	bool forceClose = false;
	JNIEnv* jenv ; // 对应一个attach到虚拟机JVM的线程
	AMediaCodec* decorder = NULL ;

};

struct De2FileAndEn2File_java_fields_t {
    jfieldID    context; 	// native object pointer
    jmethodID   post_event; // post event to Java Layer/Callback Function
} g_De2FileAndEn2File_java_fields;


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

static int msgCallback( int a ,int b)
{
	//ALOGD("msgCallback ! a %d  b %d " , a , b );
	return 0;
}

static void* decode_and_encode_thread(void* argv)
{
	JNIContext* nwc = (JNIContext*)argv;
	AttachDetachThread2JVM(nwc->jvm ,true,&nwc->jenv );


	Hwh264DecodeAndEncode* hdecodeAndEncode = new Hwh264DecodeAndEncode();

	hdecodeAndEncode->setCallbackftn(msgCallback);
	hdecodeAndEncode->Decode(0,0,nwc->pSurfaceWindow,NULL);

	delete hdecodeAndEncode ;


	nwc->thread = -1 ;
	nwc->jenv->SetLongField(nwc->java_obj_ref ,g_De2FileAndEn2File_java_fields.context, 0);
	nwc->jenv->DeleteGlobalRef(nwc->java_obj_ref);

	ANativeWindow_release(nwc->pSurfaceWindow);

	JavaVM * jvm = nwc->jvm ;
	JNIEnv * env = nwc->jenv ;
	delete nwc;
	AttachDetachThread2JVM(jvm , false, &env);

	return NULL;
}

JNIContext* get_native_nwc(JNIEnv *env, jobject thiz)
{
	JNIContext* context = reinterpret_cast<JNIContext*>(env->GetLongField(thiz, g_De2FileAndEn2File_java_fields.context));
    if (context != NULL) {
        return context ;
    }else{
    	ALOGI("Native Object Not Create or Release");
    	return NULL;
    }

}


JNIEXPORT void JNICALL native_decodeAndEncode(JNIEnv * env , jobject defile_and_encode_jobj , jobject objSurface)
{
	char result[256];
	JNIContext* nwc = get_native_nwc(env,defile_and_encode_jobj);
	if( nwc != NULL){
		// 重复调用
		ALOGE("Can Not setAndPlay running !");
		return ;
	}else{
		JNIContext* nwc = new JNIContext();
		nwc->thread = -1 ;
		nwc->java_obj_ref = env->NewGlobalRef(defile_and_encode_jobj);
		env->GetJavaVM(&nwc->jvm);
		nwc->pSurfaceWindow = ANativeWindow_fromSurface(env, objSurface);
		env->SetLongField(defile_and_encode_jobj ,g_De2FileAndEn2File_java_fields.context,  (long)nwc);

		// 创建线程
		int ret = pthread_create(&nwc->thread, NULL, decode_and_encode_thread, nwc );
		if(  ret != 0 ){

			ANativeWindow_release(nwc->pSurfaceWindow);
			env->DeleteGlobalRef(nwc->java_obj_ref);
			env->SetLongField(nwc->java_obj_ref ,g_De2FileAndEn2File_java_fields.context, 0);

			// android/libnativehelper
			snprintf(result, sizeof(result), "Can Not Create Thread with %s %d!", strerror(errno),ret);
			ALOGE("%s",result);


			return ;
		}
	}
}

#define H264_FILE 3
#define DECODEOUT_THREAD 1

// 创建线程
#if DECODEOUT_THREAD
static void* decodeOuth264_thread(void* argv)
{
	JNIContext* nwc = (JNIContext*)argv;
	AMediaCodec* decoder  = nwc->decorder ;
	bool sawOutputEOS = false ;

	while( !sawOutputEOS && !nwc->forceClose ){
		if (!sawOutputEOS) {
			AMediaCodecBufferInfo info;
			ssize_t status = AMediaCodec_dequeueOutputBuffer(decoder, &info, 0);
			if (status >= 0) {
					if (info.flags & AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM) {
						ALOGD("decode output EOS");
						sawOutputEOS = true;
					}
					// 显示???
					AMediaCodec_releaseOutputBuffer(decoder, status, info.size != 0); // render = true 如果configure时候配置了surface 就render到surface上
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
				ALOGD("unexpected info code: %zd", status);
			}
		}
	}


}
#endif

static void* decodeh264_thread(void* argv)
{
	JNIContext* nwc = (JNIContext*)argv;
	AttachDetachThread2JVM(nwc->jvm ,true,&nwc->jenv );
	/*Start ... */

	AMediaCodec* decoder = NULL;
	do{
		decoder = AMediaCodec_createDecoderByType("video/avc");
		if (NULL == decoder) {
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
		//AMediaFormat_setBuffer(format , "csd-0" , sps , sizeof(sps) );	// sps
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

		// 如果有sps pps帧的话 可以不用设置csd-0 csd-1参数
		//AMediaFormat_setBuffer(format , "csd-1" , pps , sizeof(pps) ); 	// pps



		const char *s2 = AMediaFormat_toString(format);
		ALOGD("decode format: %s", s2);
		media_status_t res2;
		res2 = AMediaCodec_configure(decoder, format, nwc->pSurfaceWindow, NULL, 0);
		if (res2 != AMEDIA_OK) {
			ALOGE ("HWENCODE AMediaCodec_configure res<%d>\n",res2);
			break;
		}

		res2 = AMediaCodec_start(decoder);
		if (res2 != AMEDIA_OK) {
			ALOGE ("HWENCODE AMediaCodec_start res<%d>\n",res2);
			break;
		}

		if (NULL != format) {
			AMediaFormat_delete(format);
		} else {
			ALOGE("HWENCODE format id NULL..\n");
			break;
		}


		int fd = 0 ;
		int file_size = 0 ;
		int sample_count = 0 ;
		fd = open("/mnt/sdcard/client.h264",O_RDONLY);
		if( fd < 0 ){
				ALOGE("open fail %d %s " , fd , strerror(errno) );
				break;
		}
		file_size = lseek(fd, 0, SEEK_END);
		int cur = lseek(fd, 0, SEEK_SET);
		ALOGD("file_size %d byte cur %d " , file_size , cur );

		unsigned char * filemap = (unsigned char*)mmap(NULL , file_size  ,  PROT_READ ,MAP_SHARED  , fd,  0);
		unsigned char * pCur = filemap ;
		unsigned char * pEnd = filemap + file_size - 1 ;
		bool sawInputEOS = false, sawOutputEOS = false ;

		if (file_size < 4 ||  *pCur!=0 || *(pCur+1)!=0 || *(pCur+2) != 0 || *(pCur+3)!=1 ){
			break;
		}

#if DECODEOUT_THREAD
		pthread_t tempTh = 0 ;
		nwc->decorder = decoder;
		int retTempTh = pthread_create(&tempTh, NULL, decodeOuth264_thread, nwc );
		if( retTempTh < 0){
			ALOGD("decodeOuth264_thread create error!");
		}
#endif

		while( (!sawInputEOS || !sawOutputEOS) && !nwc->forceClose ){
			ssize_t bufidx = -1;
			if (!sawInputEOS) {

			        bufidx = AMediaCodec_dequeueInputBuffer(decoder, 2000);

			        if (bufidx >= 0) {


			        	++sample_count ;


			            size_t bufsize;// buffer的大小
			            uint8_t *buf = AMediaCodec_getInputBuffer(decoder, bufidx, &bufsize);

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
				        AMediaCodec_queueInputBuffer(decoder,
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
				ssize_t status = AMediaCodec_dequeueOutputBuffer(decoder, &info, 0);
				if (status >= 0) {
						if (info.flags & AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM) {
							ALOGD("decode output EOS");
							sawOutputEOS = true;
						}
						// 显示???
						AMediaCodec_releaseOutputBuffer(decoder, status, info.size != 0); // render = true 如果configure时候配置了surface 就render到surface上
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
					ALOGD("unexpected info code: %zd", status);
				}
			}
#endif

		}

		munmap(filemap ,file_size );

		close(fd);



	}while(0);

	if(decoder != NULL){
		AMediaCodec_stop(decoder);
		AMediaCodec_delete(decoder);
	}


	/*End 	... */
	nwc->thread = -1 ;
	nwc->jenv->SetLongField(nwc->java_obj_ref ,g_De2FileAndEn2File_java_fields.context, 0);
	nwc->jenv->DeleteGlobalRef(nwc->java_obj_ref);

	ANativeWindow_release(nwc->pSurfaceWindow);

	JavaVM * jvm = nwc->jvm ;
	JNIEnv * env = nwc->jenv ;
	delete nwc;
	AttachDetachThread2JVM(jvm , false, &env);

	return NULL;
}


JNIEXPORT void JNICALL native_decodeH264File(JNIEnv * env , jobject defile_and_encode_jobj , jobject objSurface)
{
	char result[256];
	JNIContext* nwc = get_native_nwc(env,defile_and_encode_jobj);
	if( nwc != NULL){
		// 重复调用
		ALOGE("native_decodeH264File Err it's running  !");
		return ;
	}else{
		JNIContext* nwc = new JNIContext();
		nwc->thread = -1 ;
		nwc->java_obj_ref = env->NewGlobalRef(defile_and_encode_jobj);
		env->GetJavaVM(&nwc->jvm);
		nwc->pSurfaceWindow = ANativeWindow_fromSurface(env, objSurface);
		env->SetLongField(defile_and_encode_jobj ,g_De2FileAndEn2File_java_fields.context,  (long)nwc);

		// 创建线程
		int ret = pthread_create(&nwc->thread, NULL, decodeh264_thread, nwc );
		if(  ret != 0 ){
			ANativeWindow_release(nwc->pSurfaceWindow);
			env->DeleteGlobalRef(nwc->java_obj_ref);
			env->SetLongField(nwc->java_obj_ref ,g_De2FileAndEn2File_java_fields.context, 0);

			snprintf(result, sizeof(result), "Can Not Create Thread with %s %d!", strerror(errno),ret);
			ALOGE("%s",result);
			return ;
		}
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
    clazz = env->FindClass(De2FileAndEn2File_CLASS_PATH );
    if (clazz == NULL) {
		ALOGE("%s:Class Not Found" , De2FileAndEn2File_CLASS_PATH );
		return JNI_ERR ;
    }

    JNINativeMethod method_table[] = {
    	/*
    	 * Method:    decodeAndEncode
    	 * Signature: ()V
    	 */
    	{ "decodeAndEncode","(Landroid/view/Surface;)V", (void*)native_decodeAndEncode },

    	{ "decodeH264File","(Landroid/view/Surface;)V", (void*)native_decodeH264File },


    };
	jniRegisterNativeMethods( env, De2FileAndEn2File_CLASS_PATH ,  method_table, NELEM(method_table)) ;

	// 查找Java对应field属性
    field fields_to_find[] = {
        { De2FileAndEn2File_CLASS_PATH , "mNativeContext",  "J", &g_De2FileAndEn2File_java_fields.context },
    };

    find_fields( env , fields_to_find, NELEM(fields_to_find) );


	return JNI_VERSION_1_6 ;
}

