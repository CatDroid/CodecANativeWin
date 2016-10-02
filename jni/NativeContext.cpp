/*
 * NativeContext.cpp
 *
 *  Created on: 2016年10月2日
 *      Author: hanlon
 */

#include "NativeContext.h"
#define LOG_TAG "jni_nc"
#include "vortex.h"

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
		sendCallbackEvent(pData,THREAD_LOOP_END , 0 , 0 , NULL);
		::pthread_join(pData->mEventLoopTh , NULL);
		pthread_mutex_destroy(&pData->mNativeEventMutex);
		pthread_cond_destroy(&pData->mNativeEventCond);
	}
}


NativeContext::NativeContext(JavaVM* jvm ):mJvm(jvm),
			mpSurfaceWindow(NULL),mFd(-1),mDeocdeTh(-1),mforceClose(false),mDecoder(NULL),
			mDeocdeOutTh(-1),mframeCount(0),mStartMs(0L),
			mJavaClass(NULL),mJavaThizWef(NULL),mEventLoopTh(-1),mEventLoopExit(false),mJavaMethodID(0)
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
	cbEventThCreate();
	return JNI_TRUE;
}


