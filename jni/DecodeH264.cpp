
#include <jni.h>
#include <android/native_window_jni.h> // ANativeWindow

#include <fcntl.h>
#include <pthread.h>
#include <errno.h>
#include <sys/mman.h>
#include <stdlib.h>

#include <sys/prctl.h>

#include <media/NdkMediaCodec.h>
#include <media/NdkMediaFormat.h>
#include <media/NdkMediaExtractor.h>

#include <list>


#define LOG_TAG "jni_decodeh264"
#include "vortex.h"
#include "native_msg.h"
#include "NativeContext.h"
#include "NativeBuffer.h"

#define JAVA_CLASS_PATH "com/tom/codecanativewin/jni/DecodeH264"
#define TEST_H264_FILE "/mnt/sdcard/client.h264"
#define	H264_FILE  			3
#define DECODEOUT_THREAD 	1

struct cnw_java_fields_t {
    jfieldID    context; 	// NOT IN USED
    jmethodID   post_event;
} g_java_fields;


#if DECODEOUT_THREAD
static void* decodeOuth264_thread(void* argv)
{
	NativeContext* ctx = (NativeContext*)argv;
	AMediaCodec* decoder  = ctx->mDecoder ;
	bool sawOutputEOS = false ;

	prctl(PR_SET_NAME,"decodeOuth264_th") ;

	while( !sawOutputEOS && !ctx->mforceClose ){
		if (!sawOutputEOS) {
			AMediaCodecBufferInfo info;
			ssize_t status = AMediaCodec_dequeueOutputBuffer(decoder, &info, 50000); // 50ms
			if (status >= 0) {
				if (info.flags & AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM) {
					ALOGD("decode output EOS");
					sawOutputEOS = true;
				}

	            // 测试帧率
				if( ctx->mframeCount++ != 0 ){
					if( ctx->mframeCount % 100 == 0 ){
							struct timeval cur_time;
							gettimeofday(&cur_time, NULL);
							uint64_t nowms = cur_time.tv_sec * 1000LL + cur_time.tv_usec / 1000LL;
							ALOGD("fps = %lu ctx = %p " ,
									ctx->mframeCount * 1000 / (nowms - ctx->mStartMs ),
									ctx );
						}
				}else{
					struct timeval current_time;
					gettimeofday(&current_time, NULL);
					ctx->mStartMs = current_time.tv_sec * 1000LL + current_time.tv_usec / 1000LL;
				}


				NativeContext::sendCallbackEvent((void*)ctx , MEDIA_TIME_UPDATE , (int)(info.presentationTimeUs/1000/1000),  info.size , NULL);

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
				/**
				 * 	size_t x;
					ssize_t y;
					printf("%zu\n", x);  // prints as unsigned decimal
					printf("%zx\n", x);  // prints as hex
					printf("%zd\n", y);  // prints as signed decimal
				 */
				ALOGD("unexpected info code: %zu", status);
			}
		}
	}

	return NULL;
}
#endif


static void* decodeh264_thread(void* argv)
{
	NativeContext* ctx = (NativeContext*)argv;
	JNIEnv* decodeh264_env ;

	prctl(PR_SET_NAME,"decodeh264_th") ;

	do{
		int file_size = 0 ;
		int sample_count = 0 ;
		if( ctx->mFd  < 0 ){
				ALOGE("open fail %d %s " , ctx->mFd , strerror(errno) );
				break;
		}
		file_size = lseek(ctx->mFd, 0, SEEK_END);
		int cur = lseek(ctx->mFd, 0, SEEK_SET);
		ALOGD("file_size %d byte file_position %d " , file_size , cur );

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
		    			struct timeval cur_time;
			    		gettimeofday(&cur_time, NULL);
			    		uint64_t presentationTimeUs = cur_time.tv_sec * 1000UL * 1000UL + cur_time.tv_usec ;

			    		int triSize = sampleSize ;

				        ABuffer * pbuffer = ctx->m_pABufferManager->obtainBuffer();
				        if( pbuffer != NULL){
							ALOGD(" obtainBuffer %p " , pbuffer  );
							if(triSize > pbuffer->mCaptical ){
								ALOGE("sample too huge 1 ");
								triSize = pbuffer->mCaptical ;
							}
							ALOGD(" pbuffer->mData = %p ",  pbuffer->mData );
							memcpy( pbuffer->mData  , pCur  , triSize );
							pbuffer->mDataType =  1 ;
							pbuffer->mActualSize = triSize ;
							pbuffer->mTimestamp = (int)(presentationTimeUs/1000/1000) ;
							NativeContext::sendCallbackEvent((void*)ctx , MEDIA_H264_SAMPLE , 0 ,  0 , pbuffer);
				        }else{
				        	ALOGE("BufferManager->obtainBuffer abort");
				        }

			            pCur = p ;
				        AMediaCodec_queueInputBuffer(ctx->mDecoder,
				        								bufidx,
				        								0,
				        								sampleSize,
				        								presentationTimeUs,
				        								sawInputEOS ? AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM : 0);



#if 1
				        //usleep(30000);
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
		ALOGD("umap file ");
	}while(0);

	return NULL;
}


JNIEXPORT void JNICALL native_start(JNIEnv * env , jobject decodeH264_jobj ,
										jlong ctx , jobject objSurface , jstring strpath,
										jbyteArray jsps , jbyteArray jpps ,
										jobject weak_ref )
{
	int ret ;
	char result[256];

	ALOGD("ctx = %p" , (NativeContext*)ctx);
	NativeContext* pData = (NativeContext*)ctx ;

	if( objSurface != NULL){
		pData->mpSurfaceWindow = ANativeWindow_fromSurface(env, objSurface);
	}else{
		pData->mpSurfaceWindow = NULL;
	}

	do{
		const char*const path = env->GetStringUTFChars(strpath,NULL);
		ALOGD("open %s", path);
		int tempFd = open(path , O_RDONLY);
		if( tempFd < 0){
			snprintf(result, sizeof(result), "Can Not Open File %s with %s!", path , strerror(errno));
			ALOGE("%s",result);
			jniThrowRuntimeException(env, result );
			return ;
		}else{
			pData->mFd = tempFd;
		}
		env->ReleaseStringUTFChars(strpath,path);

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


		jbyte* csd = env->GetByteArrayElements(jsps,NULL);
		int csd_len = env->GetArrayLength(jsps);
		AMediaFormat_setBuffer(format , "csd-0" , csd , csd_len );	// sps
		env->ReleaseByteArrayElements(jsps,csd,JNI_ABORT);

		csd = env->GetByteArrayElements(jpps,NULL);
		csd_len = env->GetArrayLength(jpps);
		AMediaFormat_setBuffer(format , "csd-1" , csd , csd_len ); 	// pps
		env->ReleaseByteArrayElements(jpps,csd,JNI_ABORT);

		const char *sformat = AMediaFormat_toString(format);
		ALOGD("decode format: %s", sformat);
		media_status_t status ;
		if( pData->mpSurfaceWindow != NULL ){
			status = AMediaCodec_configure(pData->mDecoder, format, pData->mpSurfaceWindow, NULL, 0);
		}else{
			status = AMediaCodec_configure(pData->mDecoder, format, NULL , NULL, 0);// NOT Display!
		}
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
	pData->setupEventLoop(env , decodeH264_jobj ,  weak_ref , g_java_fields.post_event );

	// 创建解码输入线程
	ret  = ::pthread_create(&pData->mDeocdeTh, NULL, ::decodeh264_thread, pData );
	if(  ret != 0 ){

		snprintf(result, sizeof(result), "decodeOuth264_thread create error! with %s %d %d", strerror(errno),errno, ret );
		ALOGE("%s",result);

		//jstring post_event =  env->NewStringUTF("decodeh264_thread create error");
		//env->CallStaticVoidMethod( pData->mJavaClass , g_java_fields.post_event, THREAD_EXCEPTION , 0, 0 , post_event);
		//env->DeleteLocalRef(post_event);
		goto ERROR;
	}


#if DECODEOUT_THREAD
	// 创建解码输出线程
	ret = ::pthread_create(&pData->mDeocdeOutTh, NULL, ::decodeOuth264_thread, pData );
	if( ret < 0){

		snprintf(result, sizeof(result), "decodeOuth264_thread create error! with %s %d %d", strerror(errno),errno, ret );
		ALOGE("%s",result);

		//jstring post_event =  env->NewStringUTF("decodeOuth264_thread create error");
		//env->CallStaticVoidMethod(pData->mJavaClass, g_java_fields.post_event, THREAD_EXCEPTION , 0, 0 , post_event);
		//env->DeleteLocalRef(post_event);

	}
#endif

	ALOGD("native startup done");
	return ;
ERROR:

	pData->mforceClose = true ;

	if(pData->mDeocdeOutTh != -1){
		pthread_join(pData->mDeocdeOutTh,NULL);
	}

	if(pData->mDeocdeTh != -1){
		pthread_join(pData->mDeocdeTh,NULL);
	}

	if(pData->mDecoder != NULL){
		AMediaCodec_stop(pData->mDecoder);
		AMediaCodec_delete(pData->mDecoder);
		pData->mDecoder = NULL;
	}

	if( pData->mpSurfaceWindow != NULL ){
		ANativeWindow_release(pData->mpSurfaceWindow);
		pData->mpSurfaceWindow = NULL;
	}

	if(pData->mFd >=0 ){
		close(pData->mFd) ;
		pData->mFd=-1;
	}

	ABufferManager*bm =  pData->m_pABufferManager;
	delete pData; // stop event loop before free bufferManager
	if(bm != NULL) delete bm;

	jniThrowRuntimeException(env, result);

	return ;
}


static void* cleaner_up_thread(void* ptr)
{
	ALOGI("enter cleaner up thread ");
	pthread_detach(pthread_self());
	prctl(PR_SET_NAME,"decodeh264_th") ;
	ABufferManager* bm = (ABufferManager*)ptr ;
	if(bm != NULL) delete bm;
	ALOGI("exit cleaner up thread ");
	return NULL;
}


JNIEXPORT void JNICALL native_stop(JNIEnv * env , jobject jobj , jlong ctx )
{
	ALOGI("native_stop");
	NativeContext* pData = (NativeContext*)ctx ;
	if( pData != NULL){

		pData->mforceClose = true ;

		ABufferManager* bm =  pData->m_pABufferManager;
		bm->stop_offer();
		// 1. dont offer buffer any more
		// 2. all thread and even event thread exit
		// 3. ?? wait for all buffer release
		// 4. delele buffer manager

		if(pData->mDeocdeOutTh != -1){
			pthread_join(pData->mDeocdeOutTh,NULL);
			ALOGI("DeocdeOutTh exit");
		}

		if(pData->mDeocdeTh != -1){
			pthread_join(pData->mDeocdeTh,NULL);
			// 	如果这里不能返回一直join 将会导致
			// 	UI main线程上的Handler不能处理延时任务,比如postDelayed(new Runnable()
			//	最后出现ANR
			ALOGI("DeocdeTh exit");
		}



		if(pData->mDecoder != NULL){
			AMediaCodec_stop(pData->mDecoder);
			AMediaCodec_delete(pData->mDecoder);
			pData->mDecoder = NULL;
		}

		if( pData->mpSurfaceWindow != NULL ){
			ANativeWindow_release(pData->mpSurfaceWindow);
			pData->mpSurfaceWindow = NULL;
		}
		if(pData->mFd >=0 ){
			ALOGI("close file !");
			close(pData->mFd) ;
			pData->mFd=-1;
		}


		delete pData; // stop event loop before free bufferManager

		// wait for buffer release / wound block
		//
		pthread_t temp ;
		::pthread_create(&temp , NULL, ::cleaner_up_thread, bm );

	}else{
		ALOGE("Native STOP Before");
	}
}



JNIEXPORT long native_setup(JNIEnv * env , jobject jobj)
{
	JavaVM* jvm ;
	env->GetJavaVM(&jvm);
	NativeContext* pData = new NativeContext(jvm);
	pData->m_pABufferManager = new ABufferManager(1920*960*3/2 , 10);
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
	if( env->ExceptionCheck() ){
		jthrowable jt = env->ExceptionOccurred();
		env->ExceptionClear();
		ALOGD("Error Occured GetStaticMethodID !");
	}

    JNINativeMethod method_table[] = {
    	{ "native_setup", "()J", (void*)native_setup },
    	{ "native_start", "(JLandroid/view/Surface;Ljava/lang/String;[B[BLjava/lang/Object;)V", (void*)native_start },
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

	if( env->ExceptionCheck() ){
		jthrowable jt = env->ExceptionOccurred();
		env->ExceptionClear();
		ALOGD("Error Occured GetStaticMethodID !");
	}


	return JNI_VERSION_1_6 ;
}
