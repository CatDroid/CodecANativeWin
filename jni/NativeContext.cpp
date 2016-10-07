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
				  	jobject readOnlyBuffer = jenv->CallObjectMethod(
				  			byteBuffer, pData->jByteBuffer.asReadOnlyBuffer);
				  	jenv->DeleteLocalRef(byteBuffer);

				  	if (jenv->ExceptionCheck() ) {
				  		ALOGE("exceptioin 1 %p %d " , buf->mData ,  buf->mActualSize  );
				  	}
				  	// ABuffer(long self , int type , int time , int cap ,int act_size , ByteBuffer data)
				  	//
				  	jobject jabuffer = jenv->NewObject(
				  			pData->jABuffer.thizClass, pData->jABuffer.constructor,
				  			buf , buf->mDataType ,buf->mTimestamp, buf->mCaptical , buf->mActualSize , readOnlyBuffer);

				  	if (jenv->ExceptionCheck() ) {
				  		ALOGE("exceptioin 2 %p %d " , buf->mData ,  buf->mActualSize  );
				  	}

					jenv->CallStaticVoidMethod(pData->mJavaClass, pData->mJavaMethodID, pData->mJavaThizWef ,
							MEDIA_H264_SAMPLE, msg->arg1, msg->arg2, jabuffer);


					jenv->DeleteLocalRef(readOnlyBuffer);
					jenv->DeleteLocalRef(jabuffer);

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
			mDeocdeOutTh(-1),mframeCount(0),mStartMs(0L),
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


