/*
 * NativeContext.h
 *
 *  Created on: 2016年10月2日
 *      Author: hanlon
 */

#ifndef NATIVECONTEXT_H_
#define NATIVECONTEXT_H_

#include "jni.h"
#include "native_msg.h"

#include <fcntl.h>
#include <pthread.h>
#include <errno.h>
#include <sys/mman.h>
#include <stdlib.h>
#include <string.h>

#include <media/NdkMediaCodec.h>
#include <media/NdkMediaFormat.h>
#include <media/NdkMediaExtractor.h>

#include <list>

typedef struct{
	int msg_type ;
	int arg1 ;
	int arg2 ;
	void* ptr ;
} JNINativeMsg ;

class NativeContext
{
public:
	NativeContext(JavaVM* jvm);
	~NativeContext();

public:
	JavaVM* mJvm ;

	// Decode-In-Thread Related
	ANativeWindow* mpSurfaceWindow;
	int mFd ;
	pthread_t mDeocdeTh  ;
	bool mforceClose = false;
	AMediaCodec* mDecoder  ;

	// Decode-Out-Thread Related
	pthread_t mDeocdeOutTh  ;
	int mframeCount ;
	int64_t mStartMs ;

private:
	// Event-loop related
	jclass mJavaClass ;
	jobject mJavaThizWef ;
	jmethodID mJavaMethodID;
	pthread_t mEventLoopTh ;
	pthread_mutex_t mNativeEventMutex ;
	pthread_cond_t mNativeEventCond ;
	jboolean mEventLoopExit ;

	std::list<JNINativeMsg*> mEventList ;

	static void* cbEventThread(void* argv);
	void cbEventThCreate();
	void cbEventThExit();

public :
	// 1.异步发送事件 缺点是导致 ptr不能是栈内存 可以是常量字符串或者堆内存
	// 2.如果线程退出了 cbEventThExit 还调用 sendCallbackEvent 会出现异常 所以本事件线程要在其他线程退出后退出
	static void sendCallbackEvent( void * thiz ,  int msg_type , int arg1 , int arg2,  void* ptr );
	jboolean setupEventLoop(JNIEnv*env,jobject cbObj ,jobject cbObj_wef , jmethodID mid );


};



#endif /* NATIVECONTEXT_H_ */
