/*
 * NativeContext.cpp
 *
 *  Created on: 2016年10月2日
 *      Author: hanlon
 */


#define LOG_TAG "jni_nc"
#include "vortex.h"
#include "NativeContext.h"


void* NativeContext::cbEventThread(void* argv)
{
	JNIEnv* jenv = NULL;
	NativeContext *pData = (NativeContext *)argv;
	AttachThread2JVM( pData->mJvm , &jenv , "cbEventThread");

	// 在Native线程上 不能 FindClass
	// Pending exception java.lang.ClassNotFoundException:
	//		Didn't find class "com.tom.codecanativewin.jni.DecodeH264" on path:
	//		DexPathList[[directory "."],nativeLibraryDirectories=[/vendor/lib64, /system/lib64]]
	//															^ 库目录没有包含 应用自己的
	//
	// jclass clazz = jenv->FindClass(JAVA_CLASS_PATH );
	// if( jenv->ExceptionCheck() ){ ALOGE("can NOT get Class in NativeThread") ; return NULL;}

	ALOGD("cbEventThread loop enter");


	while( ! pData->mEventLoopExit ){

		JNINativeMsg* msg = NULL ;
		pthread_mutex_lock(&pData->mNativeEventMutex);
		if( pData->mEventList.empty() == false )
		{
			msg = pData->mEventList.front();
			pData->mEventList.pop_front();
		}else{
			pthread_cond_wait(&pData->mNativeEventCond , &pData->mNativeEventMutex );
		}
		pthread_mutex_unlock(&pData->mNativeEventMutex);

		if( msg != NULL ){
			switch( msg->msg_type ){
				case MEDIA_BUFFER_DATA :
				{
						jobject objdir = jenv->NewDirectByteBuffer(  (void*)msg->ptr ,  (jlong)msg->arg1);
						jenv->CallStaticVoidMethod(pData->mJavaClass, pData->mJavaMethodID, pData->mJavaThizWef ,
								msg->msg_type, msg->arg1, msg->arg2, objdir);

						jenv->DeleteLocalRef(objdir);
				}
					break;
				case MEDIA_H264_SAMPLE:
				{
					ABuffer* buf = (ABuffer*)msg->ptr ;

					jobject byteBuffer = jenv->NewDirectByteBuffer( buf->mData ,  buf->mActualSize );
				  	//jobject readOnlyBuffer = jenv->CallObjectMethod(
				  	//							byteBuffer, pData->jByteBuffer.asReadOnlyBuffer);

					/*
					 * asReadOnlyBuffer:
					 * ByteBuffer: [1479453319 9827][0 0 0 1][pos:0 lef:9827 cap:9827 lim:9827 dir:true]
					 * ByteBuffer: [1479453319 11140][0 0 0 1][pos:0 lef:11140 cap:11140 lim:11140 dir:true]
					 * ByteBuffer: [1479453318 5297][0 0 0 1][pos:0 lef:5297 cap:5297 lim:5297 dir:true]
					 *
					 * 不是 asReadOnlyBuffer
					 * ByteBuffer: [1479453722 9431][0 0 0 1][pos:0 lef:9431 cap:9431 lim:9431 dir:true rd:false]
					 * ByteBuffer: [1479453722 3399][0 0 0 1][pos:0 lef:3399 cap:3399 lim:3399 dir:true rd:false]
					 * ByteBuffer: [1479453722 9677][0 0 0 1][pos:0 lef:9677 cap:9677 lim:9677 dir:true rd:false]
					 * ByteBuffer: [1479453722 10880][0 0 0 1][pos:0 lef:10880 cap:10880 lim:10880 dir:true rd:false]
					 *
					 */
				  	//jenv->DeleteLocalRef(byteBuffer);

				  	if (jenv->ExceptionCheck() ) {
				  		ALOGE("exceptioin 1 %p %d " , buf->mData ,  buf->mActualSize  );
				  	}
				  	// ABuffer(long self , int type , int time , int cap ,int act_size , ByteBuffer data)
				  	//
				  	/*
				  	APP_ABI := arm64-v8a armeabi-v7a armeabi
					#APP_ABI := armeabi
				  	注意 :
						如果是64bit的机器 加载armeabi的库 会导致 这样回调Java接口的参数传递错误 !
						1.传多参数的话 都会乱掉
						2.如果传一个参数 目前发现没有问题
				  	 	ALOGI("%p" , sizeof(void*) );
					原因:
				  		由于java层的long和jlong是 64bits 而底层long是 32bits
				  		如果上层需要的是long  那么底层应该定义为int64_t的参数
				  		如果定义成int 多个参数就会错误, 涉及到 <stdarg.h>

				  			jobject jbqlbuffer = env->NewObject( jBQLBuffer.thizClass, jBQLBuffer.constructor ,
							(uint64_t)pBuf, 	// long nativeThiz
							(uint32_t)0 ,		// int data_type
							jbuf ,				// ByteBuffer data
							(int64_t)0,			// long arg1
							(int64_t)0,			// long arg2
							(int64_t)0,			// long arg3
							(int64_t)0  		// long arg4
							);

						如果是 常量 12,那么 入栈的长度就是4个字节,结果在java层获取long,就用了8个字节,这样会导致va_arg(arg_ptr,参数类型);后面的就有出错

				  	 */

				  	jobject jabuffer = jenv->NewObject(
				  			pData->jABuffer.thizClass, pData->jABuffer.constructor,
				  			buf , buf->mDataType ,buf->mTimestamp, buf->mCaptical , buf->mActualSize , byteBuffer);//readOnlyBuffer);

				  	if (jenv->ExceptionCheck() ) {
				  		ALOGE("exceptioin 2 %p %d " , buf->mData ,  buf->mActualSize  );
				  	}

					jenv->CallStaticVoidMethod(pData->mJavaClass, pData->mJavaMethodID, pData->mJavaThizWef ,
							MEDIA_H264_SAMPLE, msg->arg1, msg->arg2, jabuffer);


					/*
					 * adb logcat -s jni_decodeh264 java_decodeh264  ByteBuffer ABuffer abuffer jni_nc
					 *
					 * Note 1:
					 * 如果这里没有释放的话 ， GC不会回收ABuffer 也就是ABuffer::finalize 不会被调用
					 * JNI ERROR (app bug): local reference table overflow (max=512)
					 *
					 * Note 2:
					 * 同时如果线程退出的话,这个所以这个线程中New出来的LocalRef都会释放掉的
					 * 但是如果线程没有退出,New出来的LocalRef太多 (max=512) 就会导致local reference table overflow
					 *
					 * Note 3:
					 *	NewObject 返回的是 LocalRef
					 *	NewGlobalRef 返回的是 GlobalRef 指向不同的jobject
					 *
					 *  不能对 NewGlobalRef 返回的对象进行: DeleteLocalRef
					 *	expected reference of kind local reference but found global reference: 0x4ce'
					 *
					 * Note 4:
					 *	如果object本地有GlobalRef  也会导致ABuffer::finalize不会被调用
					 *	结果,
					 * 	JNI ERROR (app bug): global reference table overflow (max=51200)
					 *
					 */
					jenv->DeleteLocalRef(byteBuffer);
					//jenv->DeleteLocalRef(readOnlyBuffer);
					jenv->DeleteLocalRef(jabuffer);

					pData->abuffer_num ++ ;
					ALOGD("abuffer_num = %llu " , pData->abuffer_num );

				}
					break;
				case MEDIA_TIME_UPDATE:
				{
					jenv->CallStaticVoidMethod(pData->mJavaClass, pData->mJavaMethodID,
							pData->mJavaThizWef , MEDIA_TIME_UPDATE, msg->arg1, msg->arg2, NULL);
				}
				break;
				case THREAD_STOPED:
					jenv->CallStaticVoidMethod(pData->mJavaClass, pData->mJavaMethodID, pData->mJavaThizWef ,
							THREAD_STOPED, 0, 0, NULL);
					break;
				default:
					jenv->CallStaticVoidMethod(pData->mJavaClass, pData->mJavaMethodID, pData->mJavaThizWef ,
							msg->msg_type, msg->arg1, msg->arg2, NULL);
					break;
			}

			delete msg ; // allocate(new) by sendCallbackEvent
		}
	}

	// clear msg in queue
	pthread_mutex_lock(&pData->mNativeEventMutex);
	while ( pData->mEventList.empty() == false )
	{
		JNINativeMsg* msg = NULL ;
		msg = pData->mEventList.front();
		pData->mEventList.pop_front();
		ALOGD("drop one msg %d in the loop " , msg->msg_type );
		delete msg ;
	}
	pthread_mutex_unlock(&pData->mNativeEventMutex);

	ALOGD("cbEventThread loop exit");

	// Global Ref in setupEventLoop
	jenv->DeleteGlobalRef(pData->mJavaThizWef);pData->mJavaThizWef = NULL;
	jenv->DeleteGlobalRef(pData->mJavaClass);pData->mJavaClass = NULL;
	jenv->DeleteGlobalRef(pData->jABuffer.thizClass);pData->jABuffer.thizClass = NULL;
	jenv->DeleteGlobalRef(pData->jByteBuffer.thizClass);pData->jByteBuffer.thizClass = NULL;

	DetachThread2JVM( pData->mJvm , jenv);
	return NULL;
}


void NativeContext::sendCallbackEvent( void * platform_data ,  int msg_type , int arg1 , int arg2,  void* ptr )
{
	NativeContext* pData = (NativeContext*)platform_data ;

	pthread_mutex_lock(&pData->mNativeEventMutex);

	if(pData->mEventLoopExit ) {
		ALOGI("Event Loop Already Exit ! drop msg !");
		return ;
	}else if ( msg_type  == THREAD_LOOP_END){
		pData->mEventLoopExit = true ; // drop all the pending message in the list
	}
	JNINativeMsg* pmsg = new JNINativeMsg();
	pmsg->msg_type = msg_type;
	pmsg->arg1 = arg1;
	pmsg->arg2 = arg2;
	pmsg->ptr = ptr;

	bool old_empty = pData->mEventList.empty() ;
	pData->mEventList.push_back(pmsg);
	if( old_empty ){
		pthread_cond_signal(&pData->mNativeEventCond);
	}
	pthread_mutex_unlock(&pData->mNativeEventMutex);

}

void NativeContext::cbEventThCreate( )
{
	NativeContext* pData = this ;

	// create EventLoop Thread
	pData->mEventLoopTh = -1 ;
	pData->mEventLoopExit = false ;
	pthread_mutex_init(&pData->mNativeEventMutex, NULL );
	pthread_cond_init(&pData->mNativeEventCond , NULL);
	int ret = ::pthread_create(&pData->mEventLoopTh, NULL, NativeContext::cbEventThread, (void*)pData );
	if( ret != 0 ){
		// dump Error Info.
	}
	return ;
}

void NativeContext::cbEventThExit( )
{
	NativeContext* pData = this ;

	if( pData->mEventLoopTh != -1 ){
		ALOGI("send msg to exit loop and wait...");
		sendCallbackEvent(pData,THREAD_LOOP_END , 0 , 0 , NULL);
		::pthread_join(pData->mEventLoopTh , NULL);
		ALOGI("loop exit done");
		pthread_mutex_destroy(&pData->mNativeEventMutex);
		pthread_cond_destroy(&pData->mNativeEventCond);
	}
}


NativeContext::NativeContext(JavaVM* jvm ):mJvm(jvm),
			mpSurfaceWindow(NULL),mFd(-1),mDeocdeTh(-1),mforceClose(false),mDecoder(NULL),
			mDeocdeOutTh(-1),mframeCount(0),mStartMs(0L),abuffer_num(0L),
			m_pABufferManager(NULL),mJavaClass(NULL),mJavaThizWef(NULL),mEventLoopTh(-1),mEventLoopExit(false),mJavaMethodID(0)
{

}

NativeContext::~NativeContext()
{
	cbEventThExit();
}

jboolean NativeContext::setupEventLoop(JNIEnv*env,jobject cbObj ,jobject cbObj_wef , jmethodID mid )
{
	// GetObjectClass之后如果不对class进行NewGlobalRef, 会遇到 accessed stale local reference 异常
	jclass temp = env->GetObjectClass(cbObj) ;
	this->mJavaClass = (jclass)env->NewGlobalRef( temp );
	env->DeleteLocalRef(temp);
	this->mJavaThizWef = env->NewGlobalRef(cbObj_wef);
	this->mJavaMethodID = mid ;

	////
	jclass clazz = (jclass)env->FindClass("java/nio/ByteBuffer");
	this->jByteBuffer.order = env->GetMethodID(
    		clazz,
            "order",
            "(Ljava/nio/ByteOrder;)Ljava/nio/ByteBuffer;");
	this->jByteBuffer.asReadOnlyBuffer = env->GetMethodID(
    		clazz, "asReadOnlyBuffer", "()Ljava/nio/ByteBuffer;");
	this->jByteBuffer.position = env->GetMethodID(
    		clazz, "position", "(I)Ljava/nio/Buffer;");
	this->jByteBuffer.limit  = env->GetMethodID(
    		clazz, "limit", "(I)Ljava/nio/Buffer;");
	this->jByteBuffer.thizClass = (jclass)env->NewGlobalRef(clazz);
    env->DeleteLocalRef(clazz);

    //ALOGD("find class start ");
    clazz = (jclass)env->FindClass("com/tom/codecanativewin/jni/ABuffer");
	this->jABuffer.constructor = env->GetMethodID(
			clazz,
			"<init>",
			"(JIIIILjava/nio/ByteBuffer;)V");
	this->jABuffer.thizClass = (jclass)env->NewGlobalRef(clazz);
	env->DeleteLocalRef(clazz);
	//ALOGD("find class done ");
	//第一次使用java类(Java或者Native层)的话,就会调用java类的static{} 加载对应java类的jni库

	cbEventThCreate();
	return JNI_TRUE;
}


